package com.atricore.idbus.console.licensing.main;

import com.atricore.josso2.licensing._1_0.license.*;

/**
 * @author <a href=mailto:sgonzalez@atricore.org>Sebastian Gonzalez Oyuela</a>
 */
public interface LicenseManager {

    /**
     * Activates binary license representation.  This is a base64 value of the unmarshalled license XML file.
     */
    LicenseType activateLicense(byte[] license) throws LicenseServiceError, InvalidLicenseException;

    /**
     * Validates binary license without activating it.
     */
    LicenseType validateLicense(byte[] license) throws LicenseServiceError, InvalidLicenseException;

    /**
     * Validate current license, the last one activated.
     * @throws InvalidLicenseException
     */
    void validateCurrentLicense() throws LicenseServiceError, InvalidLicenseException;

    /**
     * Check if a feature is valid in the current license.
     */
    void validateFeature(String group, String name, String version, LicenseType license) throws LicenseServiceError, InvalidFeatureException;

    /**
     * Retrive active license information
     */
    LicenseType getCurrentLicense() throws LicenseServiceError, InvalidLicenseException;

    void registerFeature(ProductFeature productFeature);

    void unregisterFeature(ProductFeature productFeature);

}