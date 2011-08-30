package com.atricore.idbus.console.lifecycle.main.transform.transformers;

import com.atricore.idbus.console.lifecycle.main.domain.IdentityAppliance;
import com.atricore.idbus.console.lifecycle.main.domain.metadata.IdentityProvider;
import com.atricore.idbus.console.lifecycle.main.domain.metadata.Keystore;
import com.atricore.idbus.console.lifecycle.main.domain.metadata.ProviderRole;
import com.atricore.idbus.console.lifecycle.main.domain.metadata.SamlR2ProviderConfig;
import com.atricore.idbus.console.lifecycle.main.exception.TransformException;
import com.atricore.idbus.console.lifecycle.main.transform.IdProjectModule;
import com.atricore.idbus.console.lifecycle.main.transform.IdProjectResource;
import com.atricore.idbus.console.lifecycle.main.transform.TransformEvent;
import com.atricore.idbus.console.lifecycle.support.springmetadata.model.Bean;
import com.atricore.idbus.console.lifecycle.support.springmetadata.model.Beans;
import com.atricore.idbus.console.lifecycle.support.springmetadata.model.Description;
import com.atricore.idbus.console.lifecycle.support.springmetadata.model.Entry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.atricore.idbus.capabilities.samlr2.main.binding.SamlR2BindingFactory;
import org.atricore.idbus.capabilities.samlr2.main.binding.logging.SSOLogMessageBuilder;
import org.atricore.idbus.capabilities.samlr2.main.binding.logging.SamlR2LogMessageBuilder;
import org.atricore.idbus.capabilities.samlr2.main.binding.plans.SamlR2ArtifactResolveToSamlR2ArtifactResponsePlan;
import org.atricore.idbus.capabilities.samlr2.main.binding.plans.SamlR2ArtifactToSamlR2ArtifactResolvePlan;
import org.atricore.idbus.capabilities.samlr2.main.emitter.plans.SamlR2SecurityTokenToAuthnAssertionPlan;
import org.atricore.idbus.capabilities.samlr2.main.idp.IdPSessionEventListener;
import org.atricore.idbus.capabilities.samlr2.main.idp.SamlR2IDPMediator;
import org.atricore.idbus.capabilities.samlr2.main.idp.plans.IDPInitiatedAuthnReqToSamlR2AuthnReqPlan;
import org.atricore.idbus.capabilities.samlr2.main.idp.plans.SamlR2AuthnRequestToSamlR2ResponsePlan;
import org.atricore.idbus.capabilities.samlr2.main.idp.plans.SamlR2SloRequestToSamlR2RespPlan;
import org.atricore.idbus.capabilities.samlr2.main.idp.plans.SamlR2SloRequestToSpSamlR2SloRequestPlan;
import org.atricore.idbus.capabilities.samlr2.support.core.SamlR2KeystoreKeyResolver;
import org.atricore.idbus.capabilities.samlr2.support.core.encryption.XmlSecurityEncrypterImpl;
import org.atricore.idbus.capabilities.samlr2.support.core.signature.JSR105SamlR2SignerImpl;
import org.atricore.idbus.capabilities.samlr2.support.metadata.SAMLR2MetadataConstants;
import org.atricore.idbus.kernel.main.federation.metadata.CircleOfTrustImpl;
import org.atricore.idbus.kernel.main.federation.metadata.CircleOfTrustManagerImpl;
import org.atricore.idbus.kernel.main.mediation.camel.component.logging.CamelLogMessageBuilder;
import org.atricore.idbus.kernel.main.mediation.camel.component.logging.HttpLogMessageBuilder;
import org.atricore.idbus.kernel.main.mediation.camel.logging.DefaultMediationLogger;
import org.atricore.idbus.kernel.main.mediation.provider.IdentityProviderImpl;
import org.atricore.idbus.kernel.main.session.SSOSessionEventManager;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static com.atricore.idbus.console.lifecycle.support.springmetadata.util.BeanUtils.*;
import static com.atricore.idbus.console.lifecycle.support.springmetadata.util.BeanUtils.setPropertyValue;

/**
 * @author <a href="mailto:sgonzalez@atricore.org">Sebastian Gonzalez Oyuela</a>
 * @version $Id$
 */
public class IdPLocalTransformer extends AbstractTransformer implements InitializingBean {

    private static final Log logger = LogFactory.getLog(IdPLocalTransformer.class);

    private Keystore sampleKeystore;

    public void afterPropertiesSet() throws Exception {

        if (sampleKeystore.getStore() != null &&
                (sampleKeystore.getStore().getValue() == null ||
                sampleKeystore.getStore().getValue().length == 0)) {
            resolveResource(sampleKeystore.getStore());
        }

        if (sampleKeystore.getStore() == null &&
            sampleKeystore.getStore().getValue() == null ||
                sampleKeystore.getStore().getValue().length == 0) {
            logger.debug("Sample Keystore invalid or not found!");
        } else {
            logger.debug("Sample Keystore size " + sampleKeystore.getStore());
        }

    }

    @Override
    public boolean accept(TransformEvent event) {
        return event.getData() instanceof IdentityProvider &&
                !((IdentityProvider)event.getData()).isRemote();
    }

    @Override
    public void before(TransformEvent event) throws TransformException {


        IdentityProvider provider = (IdentityProvider) event.getData();

        Date now = new Date();
        Beans idpBeans = newBeans(provider.getName() + " : IdP Configuration generated by Atricore Identity Bus Server on " + now.toGMTString());
        Beans baseBeans = (Beans) event.getContext().get("beans");
        Beans beansOsgi = (Beans) event.getContext().get("beansOsgi");

        String idauPath = (String) event.getContext().get("idauPath");

        // Publish root element so that other transformers can use it.
        event.getContext().put("idpBeans", idpBeans);

        if (logger.isDebugEnabled())
            logger.debug("Generating IDP " + provider.getName() + " configuration model");

        IdentityAppliance appliance = event.getContext().getProject().getIdAppliance();

        // Define all required beans! (We cab break down this in the future ...)

        // ----------------------------------------
        // Identity Provider
        // ----------------------------------------

        Bean idpBean = newBean(idpBeans, normalizeBeanName(provider.getName()),
                IdentityProviderImpl.class.getName());

        event.getContext().put("idpBean", idpBean);

        // Name
        setPropertyValue(idpBean, "name", idpBean.getName());
        setPropertyValue(idpBean, "description", provider.getDisplayName());

        // Role
        if (!provider.getRole().equals(ProviderRole.SSOIdentityProvider)) {
            logger.warn("Provider " + provider.getId() + " is defined as ["+provider.getRole()+"], forcing IDP role! ");
        }
        setPropertyValue(idpBean, "role", SAMLR2MetadataConstants.IDPSSODescriptor_QNAME.toString());

        // unitContainer
        setPropertyRef(idpBean, "unitContainer", provider.getIdentityAppliance().getName() + "-container");

        // COT Manager
        Collection<Bean> cotMgrs = getBeansOfType(baseBeans, CircleOfTrustManagerImpl.class.getName());
        if (cotMgrs.size() == 1) {
            Bean cotMgr = cotMgrs.iterator().next();
            setPropertyRef(idpBean, "cotManager", cotMgr.getName());
        } else if (cotMgrs.size() > 1) {
            throw new TransformException("Invalid number of COT Managers defined " + cotMgrs.size());
        }

        // State Manager
        setPropertyRef(idpBean, "stateManager", provider.getIdentityAppliance().getName() + "-state-manager");

        // ----------------------------------------
        // Identity Provider Mediator
        // ----------------------------------------
        Bean idpMediator = newBean(idpBeans, idpBean.getName() + "-samlr2-mediator",
                SamlR2IDPMediator.class.getName());
        setPropertyValue(idpMediator, "logMessages", true);

        // artifactQueueManager
        setPropertyRef(idpMediator, "artifactQueueManager", provider.getIdentityAppliance().getName() + "-aqm");

        // bindingFactory
        setPropertyBean(idpMediator, "bindingFactory", newAnonymousBean(SamlR2BindingFactory.class));

        // logger
        List<Bean> idpLogBuilders = new ArrayList<Bean>();
        idpLogBuilders.add(newAnonymousBean(SamlR2LogMessageBuilder.class));
        idpLogBuilders.add(newAnonymousBean(SSOLogMessageBuilder.class));
        idpLogBuilders.add(newAnonymousBean(CamelLogMessageBuilder.class));
        idpLogBuilders.add(newAnonymousBean(HttpLogMessageBuilder.class));

        Bean idpLogger = newAnonymousBean(DefaultMediationLogger.class.getName());
        idpLogger.setName(idpBean.getName() + "-mediation-logger");
        setPropertyValue(idpLogger, "category", appliance.getNamespace() + "." + appliance.getName() + ".wire." + idpBean.getName());
        setPropertyAsBeans(idpLogger, "messageBuilders", idpLogBuilders);
        setPropertyBean(idpMediator, "logger", idpLogger);

        // errorUrl
        setPropertyValue(idpMediator, "errorUrl", resolveLocationBaseUrl(provider) + "/idbus-ui/error.do");

        // warningUrl
        setPropertyValue(idpMediator, "warningUrl", resolveLocationBaseUrl(provider) + "/idbus-ui/warn/policy-enforcement.do");

        SamlR2ProviderConfig cfg = (SamlR2ProviderConfig) provider.getConfig();

        Keystore signKs = null;
        Keystore encryptKs = null;

        if (cfg != null) {
            signKs = cfg.getSigner();
            if (signKs == null && cfg.isUseSampleStore()) {
                logger.warn("Using Sample keystore for signing : " + cfg.getName());
                signKs = sampleKeystore;
            }

            encryptKs = cfg.getEncrypter();
            if (encryptKs == null && cfg.isUseSampleStore()) {
                logger.warn("Using Sample keystore for encryption : " + cfg.getName());
                encryptKs = sampleKeystore;
            }
        }

        // ----------------------------------------
        // Signer
        // ----------------------------------------


        if (signKs != null) {

            String signerResourceFileName = signKs.getStore().getName() + "." +
                    ("PKCS#12".equalsIgnoreCase(signKs.getType()) ? "pkcs12" : "jks");

            IdProjectResource<byte[]> signerResource = new IdProjectResource<byte[]>(idGen.generateId(),
                    idauPath + idpBean.getName() + "/", signerResourceFileName,
                    "binary", signKs.getStore().getValue());
            signerResource.setClassifier("byte");

            Bean signer = newBean(idpBeans, idpBean.getName() + "-samlr2-signer", JSR105SamlR2SignerImpl.class);
            signer.setInitMethod("init");

            Description signerDescr = new Description();
            signerDescr.getContent().add(signKs.getDisplayName());
            signer.setDescription(signerDescr);

            Bean keyResolver = newAnonymousBean(SamlR2KeystoreKeyResolver.class);
            setPropertyValue(keyResolver, "keystoreType", signKs.getType());
            setPropertyValue(keyResolver, "keystoreFile", "classpath:" + idauPath + idpBean.getName() + "/" + signerResourceFileName);
            setPropertyValue(keyResolver, "keystorePass", signKs.getPassword());
            setPropertyValue(keyResolver, "privateKeyAlias", signKs.getPrivateKeyName());
            setPropertyValue(keyResolver, "privateKeyPass", signKs.getPrivateKeyPassword());
            setPropertyValue(keyResolver, "certificateAlias", signKs.getCertificateAlias());

            setPropertyBean(signer, "keyResolver", keyResolver);
            setPropertyBean(idpMediator, "signer", signer);

            setPropertyValue(idpMediator, "signRequests", provider.isSignRequests());
            setPropertyValue(idpMediator, "validateRequestsSignature", provider.isWantSignedRequests());

            event.getContext().getCurrentModule().addResource(signerResource);

            // signer
            setPropertyRef(idpMediator, "signer", signer.getName());
        } else {
            throw new TransformException("No Signer defined for " + provider.getName());
        }

        // ----------------------------------------
        // Encrypter
        // ----------------------------------------
        if (encryptKs != null) {

            String encrypterResourceFileName = encryptKs.getStore().getName() + "." +
                    ("PKCS#12".equalsIgnoreCase(encryptKs.getType()) ? "pkcs12" : "jks");

            IdProjectResource<byte[]> encrypterResource = new IdProjectResource<byte[]>(idGen.generateId(),
                    idauPath + idpBean.getName() + "/", encrypterResourceFileName,
                    "binary", encryptKs.getStore().getValue());
            encrypterResource.setClassifier("byte");

            Bean encrypter = newBean(idpBeans, idpBean.getName() + "-samlr2-encrypter", XmlSecurityEncrypterImpl.class);

            setPropertyValue(encrypter, "symmetricKeyAlgorithmURI", "http://www.w3.org/2001/04/xmlenc#aes128-cbc");
            setPropertyValue(encrypter, "kekAlgorithmURI", "http://www.w3.org/2001/04/xmlenc#rsa-1_5");

            Bean keyResolver = newAnonymousBean(SamlR2KeystoreKeyResolver.class);
            setPropertyValue(keyResolver, "keystoreType", encryptKs.getType());
            setPropertyValue(keyResolver, "keystoreFile", "classpath:" + idauPath + idpBean.getName() + "/" + encrypterResourceFileName);
            setPropertyValue(keyResolver, "keystorePass", encryptKs.getPassword());
            setPropertyValue(keyResolver, "privateKeyAlias", encryptKs.getPrivateKeyName());
            setPropertyValue(keyResolver, "privateKeyPass", encryptKs.getPrivateKeyPassword());
            setPropertyValue(keyResolver, "certificateAlias", encryptKs.getCertificateAlias());

            setPropertyBean(encrypter, "keyResolver", keyResolver);
            setPropertyBean(idpMediator, "encrypter", encrypter);

            event.getContext().getCurrentModule().addResource(encrypterResource);

            // encrypter
            setPropertyRef(idpMediator, "encrypter", encrypter.getName());
        } else {
            throw new TransformException("No Encrypter defined for " + provider.getName());
        }

        // ----------------------------------------
        // MBean
        // ----------------------------------------
        Bean mBean = newBean(idpBeans, idpBean.getName() + "-mbean", "org.atricore.idbus.capabilities.samlr2.management.internal.IdentityProviderMBeanImpl");
        setPropertyRef(mBean, "identityProvider", idpBean.getName());

        Bean mBeanExporter = newBean(idpBeans, idpBean.getName() + "-mbean-exporter", "org.springframework.jmx.export.MBeanExporter");
        setPropertyRef(mBeanExporter, "server", "mBeanServer");

        // mbeans
        List<Entry> mBeans = new ArrayList<Entry>();

        Bean mBeanKey = newBean(idpBeans, mBean.getName() + "-key", String.class);
        setConstructorArg(mBeanKey, 0, "java.lang.String", appliance.getNamespace() + "." +
                event.getContext().getCurrentModule().getId() +
                ":type=IdentityProvider,name=" + provider.getIdentityAppliance().getName() + "." + idpBean.getName());

        Entry mBeanEntry = new Entry();
        mBeanEntry.setKeyRef(mBeanKey.getName());
        mBeanEntry.setValueRef(mBean.getName());
        mBeans.add(mBeanEntry);

        setPropertyAsMapEntries(mBeanExporter, "beans", mBeans);

        // -------------------------------------------------------
        // Session Manager bean
        // -------------------------------------------------------
        Bean sessionManager = newBean(idpBeans, idpBean.getName() + "-session-manager",
                "org.atricore.idbus.kernel.main.session.service.SSOSessionManagerImpl");

        // Properties
        setPropertyValue(sessionManager, "maxInactiveInterval", "30");
        setPropertyValue(sessionManager, "maxSessionsPerUser", "-1");
        setPropertyValue(sessionManager, "invalidateExceedingSessions", "false");
        setPropertyValue(sessionManager, "sessionMonitorInterval", "10000");

        // Session ID Generator
        Bean sessionIdGenerator = newAnonymousBean("org.atricore.idbus.kernel.main.session.service.SessionIdGeneratorImpl");
        setPropertyValue(sessionIdGenerator, "algorithm", "MD5");

        // Session Store
        //Bean sessionStore = newAnonymousBean("org.atricore.idbus.idojos.memorysessionstore.MemorySessionStore");
        Bean sessionStore = newAnonymousBean("org.atricore.idbus.idojos.ehcachesessionstore.EHCacheSessionStore");
        sessionStore.setInitMethod("init");
        setPropertyRef(sessionStore, "cacheManager", provider.getIdentityAppliance().getName() + "-cache-manager");
        setPropertyValue(sessionStore, "cacheName", provider.getIdentityAppliance().getName() +
                "-" + idpBean.getName() + "-sessionsCache");

        // Wiring
        setPropertyBean(sessionManager, "sessionIdGenerator", sessionIdGenerator);
        setPropertyBean(sessionManager, "sessionStore", sessionStore);
    }

    @Override
    public Object after(TransformEvent event) throws TransformException {

        IdentityProvider provider = (IdentityProvider) event.getData();
        IdProjectModule module = event.getContext().getCurrentModule();
        Beans baseBeans = (Beans) event.getContext().get("beans");
        Beans idpBeans = (Beans) event.getContext().get("idpBeans");

        Bean idpBean = getBeansOfType(idpBeans, IdentityProviderImpl.class.getName()).iterator().next();

        // Wire provider to COT
        Collection<Bean> cots = getBeansOfType(baseBeans, CircleOfTrustImpl.class.getName());
        if (cots.size() == 1) {
            Bean cot = cots.iterator().next();
            addPropertyBeansAsRefsToSet(cot, "providers", idpBean);
            String dependsOn = cot.getDependsOn();
            if (dependsOn == null || dependsOn.equals("")) {
                cot.setDependsOn(idpBean.getName());
            } else {
                cot.setDependsOn(dependsOn + "," + idpBean.getName());
            }
        }

        // Wire session event listener
        Collection<Bean> sessionEventManagers = getBeansOfType(baseBeans, SSOSessionEventManager.class.getName());
        if (sessionEventManagers.size() == 1) {
            Bean sessionEventManager = sessionEventManagers.iterator().next();
            Bean idpListener = newAnonymousBean(IdPSessionEventListener.class);
            setPropertyRef(idpListener, "identityProvider", idpBean.getName());
            addPropertyBean(sessionEventManager, "listeners", idpListener);
        }

        IdProjectResource<Beans> rBeans = new IdProjectResource<Beans>(idGen.generateId(), idpBean.getName(), idpBean.getName(), "spring-beans", idpBeans);
        rBeans.setClassifier("jaxb");
        rBeans.setNameSpace(idpBean.getName());

        module.addResource(rBeans);

        return rBeans;
    }

    public Keystore getSampleKeystore() {
        return sampleKeystore;
    }

    public void setSampleKeystore(Keystore sampleKeystore) {
        this.sampleKeystore = sampleKeystore;
    }
}
