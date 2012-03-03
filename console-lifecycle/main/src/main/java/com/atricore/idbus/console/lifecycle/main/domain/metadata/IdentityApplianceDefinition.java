/*
 * Atricore IDBus
 *
 *   Copyright 2009, Atricore Inc.
 *
 *   This is free software; you can redistribute it and/or modify it
 *   under the terms of the GNU Lesser General Public License as
 *   published by the Free Software Foundation; either version 2.1 of
 *   the License, or (at your option) any later version.
 *
 *   This software is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *   Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this software; if not, write to the Free
 *   Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *   02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package com.atricore.idbus.console.lifecycle.main.domain.metadata;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class IdentityApplianceDefinition implements Serializable {

    private static final long serialVersionUID = -2497495468272480318L;

    private long id;

    private String name;

    private String displayName;

    private String description;

    private String namespace;

    private int revision;

    private Date lastModification;

    private Location location;

    private UserDashboardBranding userDashboardBranding;

    // RFU
    private Set<Feature> activeFeatures;

    // RFU
    private Set<ProviderRole> supportedRoles;

    private Set<Provider> providers;

    private Set<IdentitySource> identitySources;
    
    private Set<ServiceResource> serviceResources;

    private Set<ExecutionEnvironment> executionEnvironments;

    private Set<AuthenticationService> authenticationServices;
    
    private Keystore keystore;

    private Location uiLocation;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Location getUiLocation() {
        return uiLocation;
    }

    public void setUiLocation(Location uiLocation) {
        this.uiLocation = uiLocation;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public Set<ProviderRole> getSupportedRoles() {
        if(supportedRoles == null){
            supportedRoles = new HashSet<ProviderRole>();
        }
        return supportedRoles;
    }

    public void setSupportedRoles(Set<ProviderRole> supportedRoles) {
        this.supportedRoles = supportedRoles;
    }

    public Set<Feature> getActiveFeatures() {
        if(activeFeatures == null){
            activeFeatures = new HashSet<Feature>();
        }
        return activeFeatures;
    }

    public void setActiveFeatures(Set<Feature> activeFeatures) {
        this.activeFeatures = activeFeatures;
    }

    public Set<Provider> getProviders() {
        if(providers == null){
            providers = new HashSet<Provider>();
        }
        return providers;
    }

    public void setProviders(Set<Provider> providers) {
        this.providers = providers;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Set<IdentitySource> getIdentitySources() {
        if(identitySources == null){
            identitySources = new HashSet<IdentitySource>();
        }
        return identitySources;
    }

    public void setIdentitySources(Set<IdentitySource> identitySources) {
        this.identitySources = identitySources;
    }

    public Set<ServiceResource> getServiceResources() {
        if (serviceResources == null) {
            serviceResources = new HashSet<ServiceResource>();
        }
        return serviceResources;
    }

    public void setServiceResources(Set<ServiceResource> serviceResources) {
        this.serviceResources = serviceResources;
    }

    public Set<ExecutionEnvironment> getExecutionEnvironments() {
        return executionEnvironments;
    }

    public void setExecutionEnvironments(Set<ExecutionEnvironment> executionEnvironments) {
        this.executionEnvironments = executionEnvironments;
    }

    public Date getLastModification() {
        return lastModification;
    }

    public void setLastModification(Date lastModification) {
        this.lastModification = lastModification;
    }

    public Keystore getKeystore() {
        return keystore;
    }

    public void setKeystore(Keystore keystore) {
        this.keystore = keystore;
    }

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public Set<AuthenticationService> getAuthenticationServices() {
        if (authenticationServices == null) {
            authenticationServices = new HashSet<AuthenticationService>();
        }
        return authenticationServices;
    }

    public void setAuthenticationServices(Set<AuthenticationService> authenticationServices) {
        this.authenticationServices = authenticationServices;
    }

    public UserDashboardBranding getUserDashboardBranding() {
        return userDashboardBranding;
    }

    public void setUserDashboardBranding(UserDashboardBranding userDashboardBranding) {
        this.userDashboardBranding = userDashboardBranding;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdentityApplianceDefinition)) return false;

        IdentityApplianceDefinition that = (IdentityApplianceDefinition) o;

        if(id == 0) return false;

        if (id != that.id) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

}
