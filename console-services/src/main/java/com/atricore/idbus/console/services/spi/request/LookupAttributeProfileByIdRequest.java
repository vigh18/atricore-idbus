package com.atricore.idbus.console.services.spi.request;

/**
 * Author: Dejan Maric
 */
public class LookupAttributeProfileByIdRequest {

    private long attributeProfileId;

    public long getAttributeProfileId() {
        return attributeProfileId;
    }

    public void setAttributeProfileId(long attributeProfileId) {
        this.attributeProfileId = attributeProfileId;
    }
}