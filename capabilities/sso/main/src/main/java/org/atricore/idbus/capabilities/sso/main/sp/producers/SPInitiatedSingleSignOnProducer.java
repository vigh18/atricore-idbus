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

package org.atricore.idbus.capabilities.sso.main.sp.producers;

import oasis.names.tc.saml._2_0.metadata.EndpointType;
import oasis.names.tc.saml._2_0.metadata.EntityDescriptorType;
import oasis.names.tc.saml._2_0.metadata.IDPSSODescriptorType;
import oasis.names.tc.saml._2_0.metadata.RoleDescriptorType;
import oasis.names.tc.saml._2_0.protocol.AuthnRequestType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.atricore.idbus.capabilities.sso.main.SSOException;
import org.atricore.idbus.capabilities.sso.main.common.producers.SSOProducer;
import org.atricore.idbus.capabilities.sso.main.select.spi.EntitySelectorConstants;
import org.atricore.idbus.capabilities.sso.main.sp.SPSecurityContext;
import org.atricore.idbus.capabilities.sso.main.sp.SSOSPMediator;
import org.atricore.idbus.capabilities.sso.main.sp.plans.SPInitiatedAuthnReqToSamlR2AuthnReqPlan;
import org.atricore.idbus.capabilities.sso.support.SAMLR2Constants;
import org.atricore.idbus.capabilities.sso.support.binding.SSOBinding;
import org.atricore.idbus.capabilities.sso.support.metadata.SSOMetadataConstants;
import org.atricore.idbus.capabilities.sts.main.SecurityTokenEmissionException;
import org.atricore.idbus.common.sso._1_0.protocol.*;
import org.atricore.idbus.kernel.auditing.core.ActionOutcome;
import org.atricore.idbus.kernel.main.federation.SubjectNameID;
import org.atricore.idbus.kernel.main.federation.metadata.CircleOfTrustMemberDescriptor;
import org.atricore.idbus.kernel.main.federation.metadata.EndpointDescriptor;
import org.atricore.idbus.kernel.main.federation.metadata.EndpointDescriptorImpl;
import org.atricore.idbus.kernel.main.federation.metadata.MetadataEntry;
import org.atricore.idbus.kernel.main.mediation.MediationMessageImpl;
import org.atricore.idbus.kernel.main.mediation.binding.BindingChannel;
import org.atricore.idbus.kernel.main.mediation.camel.AbstractCamelEndpoint;
import org.atricore.idbus.kernel.main.mediation.camel.component.binding.CamelMediationExchange;
import org.atricore.idbus.kernel.main.mediation.camel.component.binding.CamelMediationMessage;
import org.atricore.idbus.kernel.main.mediation.channel.FederationChannel;
import org.atricore.idbus.kernel.main.mediation.endpoint.IdentityMediationEndpoint;
import org.atricore.idbus.kernel.main.mediation.provider.FederatedLocalProvider;
import org.atricore.idbus.kernel.main.mediation.provider.FederatedProvider;
import org.atricore.idbus.kernel.main.util.UUIDGenerator;
import org.atricore.idbus.kernel.planning.*;

import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.Properties;
import java.util.Set;

/**
 *
 */
public class SPInitiatedSingleSignOnProducer extends SSOProducer {

    private static final Log logger = LogFactory.getLog( SPInitiatedSingleSignOnProducer.class );

    protected UUIDGenerator uuidGenerator = new UUIDGenerator();

    public SPInitiatedSingleSignOnProducer( AbstractCamelEndpoint<CamelMediationExchange> endpoint ) throws Exception {
        super(endpoint);
    }

    @Override
    protected void doProcess(CamelMediationExchange exchange) throws SSOException {

        logger.debug("Processing SP Initiated Single Sign-On on HTTP Redirect");

        // ------------------------------------------------------------------------------------------
        // We have to check if identity has been provided, if so. No need to go to IDP.
        // ------------------------------------------------------------------------------------------

        CamelMediationMessage in = (CamelMediationMessage) exchange.getIn();

        // May be used later by HTTP-Redirect binding!
        SSOSPMediator mediator = (SSOSPMediator) channel.getIdentityMediator();
        in.getMessage().getState().setAttribute("SAMLR2Signer", mediator.getSigner());


        Object content = in.getMessage().getContent();

        if (content == null || content instanceof SPInitiatedAuthnRequestType ) {
            doProcessSPInitiatedAuthnRequest(exchange);
        } else if (content instanceof SelectEntityResponseType) {
            doProcessSelectEntityResponse(exchange);
        } else {
            throw new SSOException("Unknown SSO message type " + content);
        }

    }

    protected void doProcessSPInitiatedAuthnRequest(CamelMediationExchange exchange) throws SSOException {

        logger.debug("Processing SP Initiated Single Sign-On on HTTP Redirect");

        try {
            CamelMediationMessage in = (CamelMediationMessage) exchange.getIn();

            // May be used later by HTTP-Redirect binding!
            SSOSPMediator mediator = (SSOSPMediator) channel.getIdentityMediator();
            in.getMessage().getState().setAttribute("SAMLR2Signer", mediator.getSigner());

            SPInitiatedAuthnRequestType ssoAuthnReq =
                (SPInitiatedAuthnRequestType) in.getMessage().getContent();

            // Use local state ID as our relay state,
            String relayState = in.getMessage().getState().getLocalState().getId();

            SPSecurityContext secCtx =
                    (SPSecurityContext) in.getMessage().getState().getLocalVariable(getProvider().getName().toUpperCase() + "_SECURITY_CTX");

            if (secCtx != null && secCtx.getSessionIndex() != null) {

                // Support no authentication request !
                if (ssoAuthnReq != null && ssoAuthnReq.getForceAuthn() != null && !ssoAuthnReq.getForceAuthn()) {

                    // TODO ! Check that the session belongs to the IdP associated with this request
                    logger.debug("SSO Session found " + secCtx.getSessionIndex());

                    SPAuthnResponseType ssoResponse = new SPAuthnResponseType ();
                    ssoResponse.setID(uuidGenerator.generateId());
                    ssoResponse.setIssuer(getProvider().getName());
                    SPInitiatedAuthnRequestType ssoRequest =
                            (SPInitiatedAuthnRequestType) in.getMessage().getState().
                                    getLocalVariable("urn:org:atricore:idbus:sso:protocol:SPInitiatedAuthnRequest");

                    if (ssoRequest != null) {
                        ssoResponse.setInReplayTo(ssoRequest.getID());
                    }

                    SubjectType subjectType = toSubjectType(secCtx.getSubject());
                    ssoResponse.setSessionIndex(secCtx.getSessionIndex());
                    ssoResponse.setSubject(subjectType);

                    String destinationLocation = resolveSpBindingACS();

                    EndpointDescriptor destination =
                        new EndpointDescriptorImpl("EmbeddedSPAcs",
                                "AssertionConsumerService",
                                SSOBinding.SSO_ARTIFACT.getValue(),
                                destinationLocation, null);

                    CamelMediationMessage out = (CamelMediationMessage) exchange.getOut();
                    out.setMessage(new MediationMessageImpl(ssoResponse.getID(),
                            ssoResponse, "SPAuthnResposne", relayState, destination, in.getMessage().getState()));

                    exchange.setOut(out);
                    return;
                } else {
                    logger.debug("SSO Session found " + secCtx.getSessionIndex() + ", but SP requested 'forceAuthn'. Destroying security context");
                    // Destroy current session/secCtx !
                    destroySPSecurityContext(exchange, secCtx);
                }

            }

            // ------------------------------------------------------
            // Resolve IDP configuration!
            // ------------------------------------------------------
            // TODO : Check select options ... do we have a select endpoint and multiple IdPs ?!
            BindingChannel bChannel = (BindingChannel) channel;
            Collection<CircleOfTrustMemberDescriptor> availableIdPs = getCotManager().lookupMembersForProvider(bChannel.getFederatedProvider(),
                    SSOMetadataConstants.IDPSSODescriptor_QNAME.toString());

            // Do we have to select an IdP
            if (availableIdPs.size() > 1) {
                // Use IDP Selector, build a context with enough information (provider state ?!) and send a request

                if (logger.isDebugEnabled())
                    logger.debug("Selecting from " + availableIdPs.size() + " IDps" );

                SelectEntityRequestType selectIdPRequest = buildSelectEntityRequest(bChannel, ssoAuthnReq, availableIdPs);

                // Look up for appliance entity select endpoint for IdPs
                String idpSelectorLocation  = mediator.getIdpSelector();

                EndpointDescriptor ed = new EndpointDescriptorImpl(
                        "IDPSelectorEndpoint",
                        "EntitySelector",
                        SSOBinding.SSO_ARTIFACT.toString(),
                        idpSelectorLocation,
                        null);

                if (ssoAuthnReq != null)
                    in.getMessage().getState().setLocalVariable(
                        "urn:org:atricore:idbus:sso:protocol:SPInitiatedAuthnRequest", ssoAuthnReq);
                else
                    in.getMessage().getState().removeLocalVariable("urn:org:atricore:idbus:sso:protocol:SPInitiatedAuthnRequest");

                // Send SAMLR2 Message back
                CamelMediationMessage out = (CamelMediationMessage) exchange.getOut();

                out.setMessage(new MediationMessageImpl(uuidGenerator.generateId(),
                        selectIdPRequest,
                        "SelectEntityRequest",
                        relayState,
                        ed,
                        in.getMessage().getState()));

                // Send request
                exchange.setOut(out);

                return;
            }

            if (availableIdPs.size() < 1)
                throw new SSOException("No Identity Providers available for " + channel.getName());

            CircleOfTrustMemberDescriptor idp = availableIdPs.iterator().next();

            if (logger.isDebugEnabled())
                logger.debug("Using IdP " + idp.getAlias());

            // Select endpoint, must be a SingleSingOnService endpoint from a IDPSSORoleD
            EndpointType idpSsoEndpoint = resolveIdpSsoEndpoint(idp);
            EndpointDescriptor ed = new EndpointDescriptorImpl(
                    "IDPSSOEndpoint",
                    "SingleSignOnService",
                    idpSsoEndpoint.getBinding(),
                    idpSsoEndpoint.getLocation(),
                    idpSsoEndpoint.getResponseLocation());

            // ------------------------------------------------------
            // Create AuthnRequest using identity plan
            // ------------------------------------------------------
            FederationChannel idpChannel = resolveIdpChannel(idp);
            if (logger.isDebugEnabled())
                logger.debug("Using IdP channel " + idpChannel.getName());

            AuthnRequestType authnRequest = buildAuthnRequest(exchange, idp, ed, idpChannel, ssoAuthnReq);

            Properties auditProps = new Properties();
            auditProps.put("idpAlias", idp.getAlias());
            recordInfoAuditTrail("SP-SSO", ActionOutcome.SUCCESS, null, exchange, auditProps);

            // ------------------------------------------------------
            // Send Authn Request to IDP
            // ------------------------------------------------------

            in.getMessage().getState().setLocalVariable(
                    "urn:org:atricore:idbus:sso:protocol:SPInitiatedAuthnRequest", ssoAuthnReq);

            in.getMessage().getState().setLocalVariable(
                    SAMLR2Constants.SAML_PROTOCOL_NS + ":AuthnRequest", authnRequest);

            // Send SAMLR2 Message back
            CamelMediationMessage out = (CamelMediationMessage) exchange.getOut();

            out.setMessage(new MediationMessageImpl(uuidGenerator.generateId(),
                    authnRequest,
                    "AuthnRequest",
                    relayState,
                    ed,
                    in.getMessage().getState()));

            exchange.setOut(out);

        } catch (Exception e) {
            throw new SSOException(e);
        }

    }


    protected void doProcessSelectEntityResponse(CamelMediationExchange exchange) throws SSOException {

        try {

            CamelMediationMessage in = (CamelMediationMessage) exchange.getIn();

            // May be used later by HTTP-Redirect binding!
            SSOSPMediator mediator = (SSOSPMediator) channel.getIdentityMediator();
            in.getMessage().getState().setAttribute("SAMLR2Signer", mediator.getSigner());

            SPInitiatedAuthnRequestType ssoAuthnReq =
                    (SPInitiatedAuthnRequestType) in.getMessage().getState().getLocalVariable(
                        "urn:org:atricore:idbus:sso:protocol:SPInitiatedAuthnRequest");

            String relayState = in.getMessage().getState().getLocalState().getId();
            SelectEntityResponseType response = (SelectEntityResponseType) in.getMessage().getContent();
            CircleOfTrustMemberDescriptor idp = getCotManager().lookupMemberById(response.getEntityId());

            if (logger.isDebugEnabled())
                logger.debug("Using IdP " + idp.getAlias());

            // Select endpoint, must be a SingleSingOnService endpoint from a IDPSSORoleD
            EndpointType idpSsoEndpoint = resolveIdpSsoEndpoint(idp);
            EndpointDescriptor ed = new EndpointDescriptorImpl(
                    "IDPSSOEndpoint",
                    "SingleSignOnService",
                    idpSsoEndpoint.getBinding(),
                    idpSsoEndpoint.getLocation(),
                    idpSsoEndpoint.getResponseLocation());

            // ------------------------------------------------------
            // Create AuthnRequest using identity plan
            // ------------------------------------------------------
            FederationChannel idpChannel = resolveIdpChannel(idp);
            if (logger.isDebugEnabled())
                logger.debug("Using IdP channel " + idpChannel.getName());

            AuthnRequestType authnRequest = buildAuthnRequest(exchange, idp, ed, idpChannel, ssoAuthnReq);

            // ------------------------------------------------------
            // Send Authn Request to IDP
            // ------------------------------------------------------

            in.getMessage().getState().setLocalVariable(
                    SAMLR2Constants.SAML_PROTOCOL_NS + ":AuthnRequest", authnRequest);

            // Send SAMLR2 Message back
            CamelMediationMessage out = (CamelMediationMessage) exchange.getOut();

            out.setMessage(new MediationMessageImpl(uuidGenerator.generateId(),
                    authnRequest,
                    "AuthnRequest",
                    relayState,
                    ed,
                    in.getMessage().getState()));

            exchange.setOut(out);

        } catch (Exception e) {
            throw new SSOException(e);
        }
    }

    protected AuthnRequestType buildAuthnRequest(CamelMediationExchange exchange,
                                                 CircleOfTrustMemberDescriptor idp,
                                                 EndpointDescriptor ed,
                                                 FederationChannel idpChannel,
                                                 SPInitiatedAuthnRequestType ssoAuthnRequest
    ) throws IdentityPlanningException, SSOException {

        IdentityPlan identityPlan = findIdentityPlanOfType(SPInitiatedAuthnReqToSamlR2AuthnReqPlan.class);
        IdentityPlanExecutionExchange idPlanExchange = createIdentityPlanExecutionExchange();

        // Publish IdP Metadata
        idPlanExchange.setProperty(VAR_DESTINATION_COT_MEMBER, idp);
        idPlanExchange.setProperty(VAR_DESTINATION_ENDPOINT_DESCRIPTOR, ed);
        idPlanExchange.setProperty(VAR_COT_MEMBER, idpChannel.getMember());
        idPlanExchange.setProperty(VAR_RESPONSE_CHANNEL, idpChannel);

        // Get SPInitiated authn request, if any!

        // Create in/out artifacts
        IdentityArtifact in =
            new IdentityArtifactImpl(new QName("urn:org:atricore:idbus:sso:protocol", "SPInitiatedAuthnRequest"), ssoAuthnRequest );
        idPlanExchange.setIn(in);

        IdentityArtifact<AuthnRequestType> out =
                new IdentityArtifactImpl<AuthnRequestType>(new QName(SAMLR2Constants.SAML_PROTOCOL_NS, "AuthnRequest"),
                        new AuthnRequestType());
        idPlanExchange.setOut(out);

        // Prepare execution
        identityPlan.prepare(idPlanExchange);

        // Perform execution
        identityPlan.perform(idPlanExchange);

        if (!idPlanExchange.getStatus().equals(IdentityPlanExecutionStatus.SUCCESS)) {
            throw new SecurityTokenEmissionException("Identity plan returned : " + idPlanExchange.getStatus());
        }

        if (idPlanExchange.getOut() == null)
            throw new SecurityTokenEmissionException("Plan Exchange OUT must not be null!");

        return (AuthnRequestType) idPlanExchange.getOut().getContent();

    }


    protected SelectEntityRequestType buildSelectEntityRequest(BindingChannel bChannel,
                                                         SPInitiatedAuthnRequestType ssoAuthnReq,
                                                         Collection<CircleOfTrustMemberDescriptor> availableIdPs) {

        SSOSPMediator mediator = (SSOSPMediator) bChannel.getIdentityMediator();

        SelectEntityRequestType selectIdPRequest = new SelectEntityRequestType();
        selectIdPRequest.setID(uuidGenerator.generateId());
        selectIdPRequest.setIssuer(bChannel.getFederatedProvider().getName());
        for (IdentityMediationEndpoint ed : channel.getEndpoints()) {
            if (ed.getBinding().equals(SSOBinding.SSO_ARTIFACT.getValue()) &&
                    ed.getType().equals(endpoint.getType())) {
                selectIdPRequest.setReplyTo(channel.getLocation() + ed.getLocation());
                break;
            }
        }

        for (CircleOfTrustMemberDescriptor idp : availableIdPs) {

            RequestAttributeType idpAttr = new RequestAttributeType();
            idpAttr.setName(SSOMetadataConstants.IDPSSODescriptor_QNAME.toString());
            idpAttr.setValue(idp.getAlias());

            selectIdPRequest.getRequestAttribute().add(idpAttr);
        }

        // Send the name of the SP asking for the selection
        RequestAttributeType spAttr = new RequestAttributeType();
        spAttr.setName(EntitySelectorConstants.ISSUER_SP_ATTR);
        spAttr.setValue(((BindingChannel)channel).getProvider().getName());

        selectIdPRequest.getRequestAttribute().add(spAttr);

        // Send the preferred IDP alias
        RequestAttributeType preferredIdp = new RequestAttributeType();
        preferredIdp.setName(EntitySelectorConstants.PREFERRED_IDP_ATTR);
        preferredIdp.setValue(mediator.getPreferredIdpAlias());

        selectIdPRequest.getRequestAttribute().add(preferredIdp);

        if (ssoAuthnReq != null) {

            // Add additional information about the environment ...

            RequestAttributeType authnCtx = new RequestAttributeType();
            authnCtx.setName(EntitySelectorConstants.AUTHN_CTX_ATTR);
            authnCtx.setValue(ssoAuthnReq.getAuthnCtxClass());

            selectIdPRequest.getRequestAttribute().add(authnCtx);

            // All request attributes
            if (ssoAuthnReq.getRequestAttribute() != null) {
                for (int i = 0; i < ssoAuthnReq.getRequestAttribute().size(); i++) {
                    RequestAttributeType a =
                            ssoAuthnReq.getRequestAttribute().get(i);

                    RequestAttributeType a1 = new RequestAttributeType();
                    a1.setName(a.getName());
                    a1.setValue(a.getValue());

                    selectIdPRequest.getRequestAttribute().add(a1);

                }
            }
        }

        return selectIdPRequest;
    }

    /**
     * @return
     */
    protected FederationChannel resolveIdpChannel(CircleOfTrustMemberDescriptor idpDescriptor) {
        // Resolve IdP channel, then look for the ACS endpoint
        BindingChannel bChannel = (BindingChannel) channel;
        FederatedLocalProvider sp = bChannel.getFederatedProvider();

        FederationChannel idpChannel = sp.getChannel();
        for (FederationChannel fChannel : sp.getChannels()) {

            FederatedProvider idp = fChannel.getTargetProvider();
            for (CircleOfTrustMemberDescriptor member : idp.getMembers()) {
                if (member.getAlias().equals(idpDescriptor.getAlias())) {

                    if (logger.isDebugEnabled())
                        logger.debug("Selected IdP channel " + fChannel.getName() + " for provider " + idp.getName());
                    idpChannel = fChannel;
                    break;
                }

            }

        }
        return idpChannel;
    }


    protected EndpointType resolveIdpSsoEndpoint(CircleOfTrustMemberDescriptor idp) throws SSOException {

        SSOSPMediator mediator = (SSOSPMediator) channel.getIdentityMediator();
        SSOBinding preferredBinding = mediator.getPreferredIdpSSOBindingValue();
        MetadataEntry idpMd = idp.getMetadata();

        if (idpMd == null || idpMd.getEntry() == null)
            throw new SSOException("No metadata descriptor found for IDP " + idp);

        if (idpMd.getEntry() instanceof EntityDescriptorType ) {
            EntityDescriptorType md = (EntityDescriptorType) idpMd.getEntry();

            for (RoleDescriptorType role : md.getRoleDescriptorOrIDPSSODescriptorOrSPSSODescriptor()) {

                if (role instanceof IDPSSODescriptorType) {
                    IDPSSODescriptorType idpSsoRole = (IDPSSODescriptorType) role;

                    EndpointType defaultEndpoint = null;

                    for (EndpointType idpSsoEndpoint : idpSsoRole.getSingleSignOnService()) {

                        SSOBinding b = SSOBinding.asEnum(idpSsoEndpoint.getBinding());
                        if (b.equals(preferredBinding))
                            return idpSsoEndpoint;

                        if (b.equals(SSOBinding.SAMLR2_ARTIFACT))
                            defaultEndpoint = idpSsoEndpoint;

                        if (defaultEndpoint == null)
                            defaultEndpoint = idpSsoEndpoint;
                    }
                    return defaultEndpoint;
                }
            }
        } else {
            throw new SSOException("Unknown metadata descriptor type " + idpMd.getEntry().getClass().getName());
        }

        logger.debug("No IDP Endpoint supporting binding : " + preferredBinding);
        throw new SSOException("IDP does not support preferred binding " + preferredBinding);

    }

    protected String resolveSpBindingACS() {
        return ((SSOSPMediator)channel.getIdentityMediator()).getSpBindingACS();
    }

}

