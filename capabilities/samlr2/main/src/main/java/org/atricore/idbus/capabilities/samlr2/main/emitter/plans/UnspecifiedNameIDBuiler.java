package org.atricore.idbus.capabilities.samlr2.main.emitter.plans;

import oasis.names.tc.saml._2_0.assertion.NameIDType;
import oasis.names.tc.saml._2_0.protocol.NameIDPolicyType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.atricore.idbus.capabilities.samlr2.support.core.NameIDFormat;
import org.atricore.idbus.kernel.main.authn.SSONameValuePair;
import org.atricore.idbus.kernel.main.authn.SSOUser;

import javax.security.auth.Subject;

/**
 *
 * @author <a href=mailto:sgonzalez@atricore.org>Sebastian Gonzalez Oyuela</a>
 */
public class UnspecifiedNameIDBuiler extends  AbstractSubjectNameIDBuilder  {

    private static final Log logger = LogFactory.getLog(UnspecifiedNameIDBuiler.class);

    // The name of the property to be used as name ID
    private String ssoUserProperty;

    public boolean supportsPolicy(NameIDPolicyType nameIDPolicy) {
        return nameIDPolicy.getFormat().equals(NameIDFormat.UNSPECIFIED.getValue());
    }

    public NameIDType buildNameID(NameIDPolicyType nameIDPolicy, Subject s) {

        // Subject Name Identifier
        SSOUser ssoUser = getSsoUser(s);
        String nameId = null;

        if (ssoUserProperty != null) {
            nameId = getPropertyValue(ssoUser, ssoUserProperty);
            if (logger.isDebugEnabled())
                logger.debug("NameID ("+ssoUserProperty+")" + nameId);
        }

        if (nameId == null)
            nameId = ssoUser.getName();

        NameIDType subjectNameID = new NameIDType();
        subjectNameID.setValue(nameId);
        subjectNameID.setFormat(NameIDFormat.UNSPECIFIED.getValue());

        return subjectNameID;
    }

    public String getSsoUserProperty() {
        return ssoUserProperty;
    }

    public void setSsoUserProperty(String ssoUserProperty) {
        this.ssoUserProperty = ssoUserProperty;
    }
}
