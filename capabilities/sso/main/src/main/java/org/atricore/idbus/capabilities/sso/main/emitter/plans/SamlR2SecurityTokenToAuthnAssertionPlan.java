/*
 * Atricore IDBus
 *
 * Copyright (c) 2009, Atricore Inc.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.atricore.idbus.capabilities.sso.main.emitter.plans;

import org.atricore.idbus.capabilities.sso.main.common.plans.SSOPlanningConstants;
import org.atricore.idbus.capabilities.sso.main.emitter.SamlR2SecurityTokenEmissionContext;
import org.atricore.idbus.capabilities.sts.main.WSTConstants;
import org.atricore.idbus.common.sso._1_0.protocol.*;
import org.atricore.idbus.kernel.main.authn.*;
import org.atricore.idbus.kernel.main.federation.SubjectAttribute;
import org.atricore.idbus.kernel.main.store.SSOIdentityManager;
import org.atricore.idbus.kernel.main.store.exceptions.NoSuchUserException;
import org.atricore.idbus.kernel.main.store.exceptions.SSOIdentityException;
import org.atricore.idbus.kernel.planning.IdentityArtifact;
import org.atricore.idbus.kernel.planning.IdentityPlanExecutionExchange;
import org.atricore.idbus.kernel.planning.IdentityPlanningException;

import javax.security.auth.Subject;
import java.security.Principal;
import java.util.*;

/**
 * This plan will transform an input token (UsernameToken, etc) into a SAMLR2 Authentication Assertion.
 *
 * @org.apache.xbean.XBean element="sectoken-to-authassertion-plan"
 *
 * @author <a href="mailto:sgonzalez@atricore.org">Sebastian Gonzalez Oyuela</a>
 * @version $Id: SamlR2SecurityTokenToAuthnAssertionPlan.java 1335 2009-06-24 16:34:38Z sgonzalez $
 */
public class SamlR2SecurityTokenToAuthnAssertionPlan extends AbstractSSOAssertionPlan {

    public IdentityPlanExecutionExchange prepare(IdentityPlanExecutionExchange ex) throws IdentityPlanningException {
        
        IdentityArtifact artifact = ex.getIn();

        try {

            ex.setTransientProperty(SSOPlanningConstants.VAR_IGNORE_REQUESTED_NAMEID_POLICY, new Boolean(this.isIgnoreRequestedNameIDPolicy()));
            ex.setTransientProperty(SSOPlanningConstants.VAR_DEFAULT_NAMEID_BUILDER, getDefaultNameIDBuilder());
            ex.setTransientProperty(SSOPlanningConstants.VAR_NAMEID_BUILDERS, getNameIDBuilders());

            SamlR2SecurityTokenEmissionContext ctx = (SamlR2SecurityTokenEmissionContext) ex.getProperty(WSTConstants.RST_CTX);

            if (logger.isTraceEnabled())
                logger.trace("Using default SubjectNameID builder : " + getDefaultNameIDBuilder());

            if (logger.isTraceEnabled())
                logger.trace("Using SubjectNameID builders : " + (getNameIDBuilders() != null ? getNameIDBuilders().size() + "" : "<NULL>"));


            // Build subject and publish as execution context variable.
            Subject s = (Subject) ex.getProperty(WSTConstants.SUBJECT_PROP);

            if (s == null)
                throw new IdentityPlanningException("Subject not found in context (Property name:"+WSTConstants.SUBJECT_PROP+")");

            // If we had a subject already, use the private / public credentials!
            // This subject came probably from a JOSSO1 Auth Scheme.
            Set<SSOUser> ssoUsers = s.getPrincipals(SSOUser.class);
            Set<SimplePrincipal> simplePrincipals = s.getPrincipals(SimplePrincipal.class);

            if (ssoUsers != null && ssoUsers.size() > 0) {

                if (logger.isDebugEnabled())
                    logger.debug("Emitting token for Subject with SSOUser");

            } else if (simplePrincipals != null && simplePrincipals.size() > 0) {

                if (logger.isDebugEnabled())
                    logger.debug("Emitting token for Subject with SimplePrincipal");

                SimplePrincipal sp = simplePrincipals.iterator().next();
                String username = sp.getName();

                // All principals that will be added to the subject
                Set<Principal> principals = new HashSet<Principal>();
                SSOIdentityManager idMgr = getIdentityManager();

                // Obtain SSOUser principal
                SSOUser ssoUser = null;
                SSORole[] ssoRoles = null;
                if (idMgr != null) {
                    if (logger.isTraceEnabled())
                        logger.trace("Resolving SSOUser for " + username);

                    ssoUser = idMgr.findUser(username);
                    ssoRoles = idMgr.findRolesByUsername(username);
                } else {
                    if (logger.isTraceEnabled())
                        logger.trace("Moving forward with token issuance without identity manager");
                    ssoUser = new BaseUserImpl(username);
                    ssoRoles = new BaseRoleImpl[0];

                }

                // Additional information
                if (ctx.getProxyResponse() != null) {

                    // Proxied requests may provide additional information:
                    SPAuthnResponseType proxyResponse = ctx.getProxyResponse();

                    List<SSONameValuePair> proxyProps = new ArrayList();
                    List<SSORole> proxyRoles = new ArrayList();

                    for (SubjectAttributeType attr : proxyResponse.getSubjectAttributes()) {

                        if (logger.isTraceEnabled())
                            logger.trace("Adding subject attribute from proxy response " + attr.getName() + ":" + attr.getValue());

                        proxyProps.add(new SSONameValuePair(attr.getName(), attr.getValue()));
                    }

                    for (SubjectRoleType p : proxyResponse.getSubjectRoles()) {

                        if (logger.isTraceEnabled())
                            logger.trace("Adding subject role from proxy response " + p.getName());

                        proxyRoles.add(new BaseRoleImpl(p.getName()));
                    }

                    // Now check if the subject itself has additional information

                    if (proxyResponse.getSubject().getAbstractPrincipal() != null) {

                        for (AbstractPrincipalType p : proxyResponse.getSubject().getAbstractPrincipal()) {
                            if (p instanceof SubjectAttributeType) {
                                SubjectAttributeType attr = (SubjectAttributeType) p;
                                if (logger.isTraceEnabled())
                                    logger.trace("Adding subject attribute from proxy response subject " + attr.getName() + ":" + attr.getValue());

                                proxyProps.add(new SSONameValuePair(attr.getName(), attr.getValue()));

                            } else if (p instanceof SubjectRoleType) {
                                SubjectRoleType r = (SubjectRoleType) p;
                                if (logger.isTraceEnabled())
                                    logger.trace("Adding subject role from proxy response subject " + r.getName());

                                proxyRoles.add(new BaseRoleImpl(r.getName()));

                            }
                        }
                    }

                    if (proxyProps.size() > 0) {
                        if (logger.isDebugEnabled())
                            logger.debug("Merging proxy properties with SSO user " + proxyProps.size());

                        for (int i = 0 ; i < ssoUser.getProperties().length ; i ++) {
                            proxyProps.add(ssoUser.getProperties()[i]);
                        }

                        BaseUserImpl proxySsoUser = new BaseUserImpl(ssoUser.getName());
                        proxySsoUser.setProperties(proxyProps.toArray(new SSONameValuePair[proxyProps.size()]));
                        ssoUser = proxySsoUser;

                    }


                    if (proxyRoles.size() > 0) {

                        if (logger.isDebugEnabled())
                            logger.debug("Merging proxy roles with SSO roles " + proxyRoles.size());

                        // Add non-proxy roles
                        for (int i = 0; i < ssoRoles.length; i++) {
                            proxyRoles.add(ssoRoles[i]);
                        }
                        ssoRoles = proxyRoles.toArray(new SSORole[proxyRoles.size()]);
                    }
                }

                principals.add(ssoUser);
                principals.addAll(Arrays.asList(ssoRoles));

                // Use existing SSOPolicyEnforcement principals
                Set<SSOPolicyEnforcementStatement> ssoPolicies = s.getPrincipals(SSOPolicyEnforcementStatement.class);
                if (ssoPolicies != null) {
                    if (logger.isDebugEnabled())
                        logger.debug("Adding " + ssoPolicies.size() + " SSOPolicyEnforcement principals ");

                    principals.addAll(ssoPolicies);
                }

                // Build Subject
                s = new Subject(true, principals, s.getPublicCredentials(), s.getPrivateCredentials());
            } else {
                logger.debug("Invalid Subject, no SSOUser or SimplePrincipal found : " + s);
                throw new IdentityPlanningException("Invalid Subject, no SSOUser or SimplePrincipal found!");
            }

            // We have a valid IPD Subject now, with a SSOUser as one of the principals
            ex.setProperty(WSTConstants.SUBJECT_PROP, s);

        } catch (NoSuchUserException e) {
            throw new IdentityPlanningException(e.getMessage(), e);
        } catch (SSOIdentityException e) {
            throw new IdentityPlanningException(e.getMessage(), e);
        }

        return ex;
    }

    /**
     * Process fragment registry form SAMLR2 Authn Assertion generation from Subject.
     * @return
     */

    protected String getProcessDescriptorName() {
        return "samlr2-assertion-from-token-process";
    }

}
