package com.atricore.idbus.console.liveservices.liveupdate.main.service;

import com.atricore.idbus.console.liveservices.liveupdate.main.LiveUpdateException;
import com.atricore.idbus.console.liveservices.liveupdate.main.LiveUpdateManager;
import com.atricore.idbus.console.liveservices.liveupdate.main.engine.UpdateEngine;
import com.atricore.idbus.console.liveservices.liveupdate.main.profile.ProfileManager;
import com.atricore.idbus.console.liveservices.liveupdate.main.repository.ArtifactRepository;
import com.atricore.idbus.console.liveservices.liveupdate.main.repository.MetadataRepository;
import com.atricore.idbus.console.liveservices.liveupdate.main.repository.Repository;
import com.atricore.idbus.console.liveservices.liveupdate.main.repository.RepositoryTransport;
import com.atricore.idbus.console.liveservices.liveupdate.main.repository.impl.*;
import com.atricore.liveservices.liveupdate._1_0.md.InstallableUnitType;
import com.atricore.liveservices.liveupdate._1_0.md.UpdateDescriptorType;
import com.atricore.liveservices.liveupdate._1_0.md.UpdatesIndexType;
import com.atricore.liveservices.liveupdate._1_0.profile.ProfileType;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Periodically analyze MD and see if updates apply.
 * Keep track of current version/update
 * Queue update processes, to be triggered on reboot.
 * Manage update lifecycle.
 *
 * @author <a href=mailto:sgonzalez@atricore.org>Sebastian Gonzalez Oyuela</a>
 */
public class LiveUpdateManagerImpl implements LiveUpdateManager {

    private static final Log logger = LogFactory.getLog(LiveUpdateManagerImpl.class);

    private List<RepositoryTransport> transports;

    private ProfileManager profileManager;

    private UpdateEngine engine;

    private UpdatesMonitor updatesMonitor;

    private String dataFolder;

    private ScheduledThreadPoolExecutor stpe;

    private Properties config;

    private MetadataRepositoryManagerImpl mdManager;

    private ArtifactRepositoryManagerImpl artManager;

    public void init() throws LiveUpdateException {

        // Start update check thread.
        logger.info("Initializing LiveUpdate service ...");
        Set<String> used = new HashSet<String>();

        for (Object k : config.keySet()) {
            String key = (String) k;

            if (key.startsWith("repo.md.")) {

                // We need to configure a repo, get repo base key.
                String repoId = key.substring("repo.md.".length());
                repoId = repoId.substring(0, repoId.indexOf('.'));

                String repoKeys = "repo.md." + repoId;
                if (used.contains(repoKeys))
                    continue;

                used.add(repoKeys);

                try {
                    MetadataRepository repo = (MetadataRepository) buildVFSRepository(VFSMetadataRepositoryImpl.class, repoKeys);
                    logger.info("Using LiveUpdate MD Repository at " + repo.getLocation());
                    mdManager.addRepository(repo);

                } catch (LiveUpdateException e) {
                    logger.error("Ignoring MD repository definition : " + e.getMessage());

                    // When debugging, error log includs stack trace.
                    if (logger.isDebugEnabled())
                        logger.error("Ignoring MD repository definition : " + e.getMessage(), e);
                }


            } else if (key.startsWith("repo.art.")) {
                // We need to configure a repo, get repo base key.
                String repoId = key.substring("repo.art.".length());
                repoId = repoId.substring(0, repoId.indexOf('.'));

                String repoKeys = "repo.art." + repoId;
                if (used.contains(repoKeys))
                    continue;

                used.add(repoKeys);

                try {
                    ArtifactRepository repo = (ArtifactRepository) buildVFSRepository(VFSArtifactRepositoryImpl.class, repoKeys);
                    logger.info("Using LiveUpdate Artifact Repository at " + repo.getLocation());
                    artManager.addRepository(repo);
                } catch (LiveUpdateException e) {
                    logger.error("Ignoring Artifact repository definition : " + e.getMessage());

                    // When debugging, error log includs stack trace.
                    if (logger.isDebugEnabled())
                        logger.error("Ignoring Artifact repository definition : " + e.getMessage(), e);
                }
            }

        }
    }

    public ProfileType getCurrentProfile() throws LiveUpdateException {
        return this.profileManager.getCurrentProfile();
    }

    public Collection<Repository> getRepositories() {
        List<Repository> repos = new ArrayList<Repository>();

        repos.addAll(mdManager.getRepositories());
        repos.addAll(artManager.getRepositories());

        return repos;
    }

    public UpdatesIndexType getRepositoryUpdates(String repoId) throws LiveUpdateException {
        return mdManager.getUpdatesIndex(repoId, true);
    }

    public Collection<UpdateDescriptorType> getAvailableUpdates() throws LiveUpdateException {

        ProfileType profile = profileManager.getCurrentProfile();
        List<InstallableUnitType> ius = profile.getInstallableUnit();

        Map<String, UpdateDescriptorType> updates = new HashMap<String, UpdateDescriptorType>();

        for (InstallableUnitType iu : ius) {

            Collection<UpdateDescriptorType> uds = mdManager.getUpdates();

            for (UpdateDescriptorType ud : uds) {
                updates.put(ud.getID(), ud);
            }
        }

        return updates.values();
    }

    public void cleanRepository(String repoId) throws LiveUpdateException {
        // TODO : mdManager.clearRepository(repoId);
        // TODO : artManager.clearRepository(repoId);
    }

    public void cleanAllRepositories() throws LiveUpdateException {
        // TODO : mdManager.clearRepositories();
        // TODO : artManager.clearRepositories();
    }

    // Analyze MD and see if updates apply. (use license information ....)
    public Collection<UpdateDescriptorType> checkForUpdates() throws LiveUpdateException {
        Collection<UpdateDescriptorType> uds = mdManager.refreshRepositories();
        return getAvailableUpdates();
    }

    // Apply update
    public void applyUpdate(String group, String name, String version) throws LiveUpdateException {

        if (logger.isDebugEnabled())
            logger.debug("Trying to apply update for " + group + "/" + name + "/" + version);

        mdManager.refreshRepositories();

        Collection<UpdateDescriptorType> availableUpdates = getAvailableUpdates();
        InstallableUnitType installableUnit  = null;
        UpdateDescriptorType update = null;

        for (UpdateDescriptorType ud : availableUpdates) {
            for (InstallableUnitType iu : ud.getInstallableUnit()) {
                if (iu.getGroup().equals(group) && iu.getName().equals(name) && iu.getVersion().equals(version)) {
                    installableUnit = iu;
                    update = ud;
                    if (logger.isDebugEnabled())
                        logger.debug("Found IU " + iu.getID() + " for " + group + "/" + name + "/" + version);
                    break;
                }
            }
        }

        if (installableUnit == null) {
            throw new LiveUpdateException("Update not available for current setup : " +
                    group + "/" + name + "/" + version);
        }

        logger.info("Applying Update " + group + "/" + name + "/" + version);

        Collection<UpdateDescriptorType> updates = mdManager.getUpdates();
        ProfileType updateProfile = profileManager.buildUpdateProfile(update, updates);

        engine.execute("updatePlan", updateProfile);
    }

    public ProfileType getUpdateProfile(String group, String name, String version) throws LiveUpdateException {
        // TODO : Refresh repos every time ?
        mdManager.refreshRepositories();
        UpdateDescriptorType ud = mdManager.getUpdate(group, name, version);
        if (ud == null)
            throw new LiveUpdateException("No update found for " + group +"/"+name+"/"+version);
        
        return this.profileManager.buildUpdateProfile(ud, mdManager.getUpdates());
    }

    // -------------------------------------------< Utilities >

    protected AbstractVFSRepository buildVFSRepository(Class repoType, String repoKeys) throws LiveUpdateException {

        try {
            // Get id,name, location, enabled
            if (logger.isTraceEnabled())
                logger.trace("Adding new repository : " + repoKeys);

            // Repository ID
            String id = config.getProperty(repoKeys + ".id");
            if (id == null)
                throw new LiveUpdateException("Repository ID is required. Configuration keys " + repoKeys);

            // Repository Name
            String name = config.getProperty(repoKeys + ".name");
            if (name == null)
                throw new LiveUpdateException("Repository name is required for " + id);

            // Enabled
            boolean enabled = Boolean.parseBoolean(config.getProperty(repoKeys + ".enabled", "false"));

            // DS Validation
            boolean validateSignature = Boolean.parseBoolean(config.getProperty(repoKeys + ".validateSignature", "true"));

            // Certificate for DS validation
            String base64certificate =  config.getProperty(repoKeys + ".certificate");
            if (base64certificate == null && validateSignature) {
                throw new LiveUpdateException("Repository " + id + " has Digital Signature enabled, but no certificate was provided" );
            }
            byte[] certificate = Base64.decodeBase64(base64certificate.getBytes());
            URI location = null;
            try {
                // Since we're handling the configuration properties, we cannot rely on spring properties resolver.
                String l = config.getProperty(repoKeys + ".location");
                l = l.replaceAll("\\$\\{karaf\\.data\\}", dataFolder);
                location = new URI(l);

            } catch (Exception e) {
                logger.error("Invalid URI ["+config.getProperty(repoKeys + ".location")+"] for repository " + id + " " + name);
                return null;
            }

            if (logger.isDebugEnabled())
                logger.debug("Adding new VFS Repository ["+id+"] " + name +
                        (enabled ? "enabled" : "disabled" ) + " at " + location);


            AbstractVFSRepository repo = (AbstractVFSRepository) repoType.newInstance();
            repo.setId(id);
            repo.setName(name);
            repo.setLocation(location);
            repo.setEnabled(enabled);
            repo.setCertValue(certificate);
            repo.setSignatureValidationEnabled(validateSignature);
            // TODO : Take from license
            //repo.setUsername();
            //repo.setPassword();

            repo.setRepoFolder(new URI ("file://" + dataFolder + "/liveservices/liveupdate/repos/cache/" + id));

            return repo;
        } catch (Exception e) {
            throw new LiveUpdateException("Cannot create repository " + e.getMessage(), e);
        }

    }

// -------------------------------------------< Properties >
    public void setConfig(Properties config) {
        this.config = config;
    }

    public Properties getConfig() {
        return config;
    }

    public void setMetadataRepositoryManager(MetadataRepositoryManagerImpl metadataRepositoryManager) {
        this.mdManager = metadataRepositoryManager;
    }

    public MetadataRepositoryManagerImpl getMetadataRepositoryManager() {
        return mdManager;
    }

    public void setArtifactRepositoryManager(ArtifactRepositoryManagerImpl artifactRepositoryManager) {
        this.artManager = artifactRepositoryManager;
    }

    public ArtifactRepositoryManagerImpl getArtifactRepositoryManager() {
        return artManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public void setProfileManager(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    public UpdateEngine getEngine() {
        return engine;
    }

    public void setEngine(UpdateEngine engine) {
        this.engine = engine;
    }

    public String getDataFolder() {
        return dataFolder;
    }

    public void setDataFolder(String dataFolder) {
        this.dataFolder = dataFolder;
    }
    //
}