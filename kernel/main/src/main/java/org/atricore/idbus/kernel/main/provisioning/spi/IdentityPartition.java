package org.atricore.idbus.kernel.main.provisioning.spi;

import org.atricore.idbus.kernel.main.authn.SecurityToken;
import org.atricore.idbus.kernel.main.provisioning.domain.*;
import org.atricore.idbus.kernel.main.provisioning.exception.ProvisioningException;
import org.atricore.idbus.kernel.main.store.identity.IdentityStore;

import java.util.Collection;

/**
 * Low-level view of users repository.  Originally conceived as a provisioning resource.  Its role changed once
 * the IdM module was added, and resources are hidden by that module.
 *
 * @author <a href=mailto:sgonzalez@atricore.org>Sebastian Gonzalez Oyuela</a>
 */
public interface IdentityPartition {

    String getName();

    String getDescription();

    IdentityStore getIdentityStore();
    
    Group findGroupByOid(String oid) throws ProvisioningException;

    Group findGroupByName(String name) throws ProvisioningException;

    Collection<Group> findGroupsByUserName(String name) throws ProvisioningException;

    Collection<Group> findAllGroups() throws ProvisioningException;

    Group addGroup(Group group) throws ProvisioningException;

    Group updateGroup(Group group) throws ProvisioningException;

    void deleteGroup(String oid) throws ProvisioningException;

    User addUser(User user) throws ProvisioningException;

    void deleteUser(String oid) throws ProvisioningException;

    User findUserByOid(String oid) throws ProvisioningException;

    User findUserByUserName(String username) throws ProvisioningException;

    Collection<User> findAllUsers() throws ProvisioningException;

    User updateUser(User user) throws ProvisioningException;

    Collection<User> getUsersByGroup(Group group) throws ProvisioningException;

    long getUserCount() throws ProvisioningException;

}


