package org.atricore.idbus.kernel.main.provisioning.domain;

import java.io.Serializable;

/**
 * User account associated to a resource
 */
public class Account implements Serializable {

    private static final long serialVersionUID = 2989786148798290707L;

    private String oid;

    private String uid;

    public String getOid() {
        return oid;
    }

    public void setOid(String oid) {
        this.oid = oid;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }
}
