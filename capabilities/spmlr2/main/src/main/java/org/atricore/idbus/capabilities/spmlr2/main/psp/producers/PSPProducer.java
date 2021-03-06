package org.atricore.idbus.capabilities.spmlr2.main.psp.producers;

import oasis.names.tc.spml._2._0.*;
import oasis.names.tc.spml._2._0.atricore.*;
import oasis.names.tc.spml._2._0.password.ResetPasswordRequestType;
import oasis.names.tc.spml._2._0.password.SetPasswordRequestType;
import oasis.names.tc.spml._2._0.search.SearchQueryType;
import oasis.names.tc.spml._2._0.search.SearchRequestType;
import oasis.names.tc.spml._2._0.search.SearchResponseType;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.Pointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.atricore.idbus.capabilities.spmlr2.main.SPMLR2Constants;
import org.atricore.idbus.capabilities.spmlr2.main.common.producers.SpmlR2Producer;
import org.atricore.idbus.capabilities.spmlr2.main.psp.SpmlR2PSPMediator;
import org.atricore.idbus.capabilities.spmlr2.main.util.XmlUtils;
import org.atricore.idbus.kernel.main.federation.metadata.EndpointDescriptor;
import org.atricore.idbus.kernel.main.federation.metadata.EndpointDescriptorImpl;
import org.atricore.idbus.kernel.main.mediation.MediationMessageImpl;
import org.atricore.idbus.kernel.main.mediation.camel.AbstractCamelEndpoint;
import org.atricore.idbus.kernel.main.mediation.camel.component.binding.CamelMediationExchange;
import org.atricore.idbus.kernel.main.mediation.camel.component.binding.CamelMediationMessage;
import org.atricore.idbus.kernel.main.mediation.channel.ProvisioningChannel;
import org.atricore.idbus.kernel.main.mediation.provider.ProvisioningServiceProvider;
import org.atricore.idbus.kernel.main.provisioning.domain.Group;
import org.atricore.idbus.kernel.main.provisioning.domain.GroupAttributeDefinition;
import org.atricore.idbus.kernel.main.provisioning.domain.User;
import org.atricore.idbus.kernel.main.provisioning.domain.UserAttributeDefinition;
import org.atricore.idbus.kernel.main.provisioning.exception.*;
import org.atricore.idbus.kernel.main.provisioning.spi.ProvisioningTarget;
import org.atricore.idbus.kernel.main.provisioning.spi.request.*;
import org.atricore.idbus.kernel.main.provisioning.spi.response.*;
import org.w3c.dom.Element;

import javax.xml.bind.JAXBElement;
import java.util.Iterator;
import java.util.List;

/**
 * // TODO : Split this in several producers ...
 *
 * @author <a href=mailto:sgonzalez@atricore.org>Sebastian Gonzalez Oyuela</a>
 */
public class PSPProducer extends SpmlR2Producer {

    private static final Log logger = LogFactory.getLog(PSPProducer.class);

    public PSPProducer(AbstractCamelEndpoint<CamelMediationExchange> endpoint) {
        super(endpoint);
    }

    @Override
    protected void doProcess(CamelMediationExchange exchange) throws Exception {

        CamelMediationMessage out = (CamelMediationMessage) exchange.getOut();
        CamelMediationMessage in = (CamelMediationMessage) exchange.getIn();

        Object content = in.getMessage().getContent();

        ResponseType spmlResponse = null;

        if (logger.isDebugEnabled())
            logger.debug("Processing SPML " + content.getClass().getSimpleName() + " request");

        if (content instanceof ListTargetsRequestType) {
            spmlResponse = doProcessListTargetsRequest(exchange, (ListTargetsRequestType) content);
        } else if (content instanceof AddRequestType) {
            spmlResponse = doProcessAddRequest(exchange, (AddRequestType) content);
        } else if (content instanceof SearchRequestType) {
            spmlResponse = doProcessSearchRequest(exchange, (SearchRequestType) content);
        } else if (content instanceof LookupRequestType) {
            spmlResponse = doProcessLookupRequest(exchange, (LookupRequestType) content);
        } else if (content instanceof ModifyRequestType) {
            spmlResponse = doProcessModifyRequest(exchange, (ModifyRequestType) content);
        } else if (content instanceof DeleteRequestType) {
            spmlResponse = doProcessDeleteRequest(exchange, (DeleteRequestType) content);
        } else if (content instanceof SetPasswordRequestType) {
            spmlResponse = doProcessSetPassword(exchange, (SetPasswordRequestType) content);
        } else if (content instanceof ReplacePasswordRequestType) {
            spmlResponse = doProcessReplacePassword(exchange, (ReplacePasswordRequestType) content);
        } else if (content instanceof ResetPasswordRequestType) {
            // TODO :
            logger.error("Unknown SPML Request type : " + content.getClass().getName());
            spmlResponse.setStatus(StatusCodeType.FAILURE);
            spmlResponse.setError(ErrorCode.UNSUPPORTED_OPERATION);

        } else {

            // TODO : Send status=failure error= in response ! (use super producer or binding to build error
            // TODO : See SPMPL Section 3.1.2.2 Error (normative)
            logger.error("Unknown SPML Request type : " + content.getClass().getName());
            spmlResponse.setStatus(StatusCodeType.FAILURE);
            spmlResponse.setError(ErrorCode.UNSUPPORTED_OPERATION);

        }

        // Send response back.
        EndpointDescriptor ed = new EndpointDescriptorImpl(endpoint.getName(),
                endpoint.getType(), endpoint.getBinding(), null, null);

        out.setMessage(new MediationMessageImpl(idGen.generateId(),
                spmlResponse,
                spmlResponse.getClass().getSimpleName(),
                null,
                ed,
                in.getMessage().getState()));

        exchange.setOut(out);

    }

    protected ListTargetsResponseType doProcessListTargetsRequest(CamelMediationExchange exchange, ListTargetsRequestType spmlRequest) {
        // TODO : Use planning to convert SPML Request into kernel request
        ProvisioningServiceProvider provider = ((ProvisioningChannel)channel).getProvider();
        
        List<ProvisioningTarget> targets = provider.getProvisioningTargets();
        ListTargetsResponseType spmlResponse = new ListTargetsResponseType();

        for (ProvisioningTarget target : targets) {
            TargetType spmlTarget = new TargetType();

            // TODO : Check spec
            spmlTarget.setProfile("xsd");
            spmlTarget.setTargetID(target.getName());

            CapabilitiesListType capabilitiesList = new CapabilitiesListType();

            CapabilityType spmlSearchCap = new CapabilityType();
            spmlSearchCap.setNamespaceURI("urn:oasis:names:tc:SPML:2:0:search");
            spmlSearchCap.setNamespaceURI("urn:oasis:names:tc:SPML:2:0:update");

            spmlResponse.getTarget().add(spmlTarget);
        }

        return spmlResponse;

    }

    protected AddResponseType doProcessAddRequest(CamelMediationExchange exchane, AddRequestType spmlRequest) throws Exception {

        SpmlR2PSPMediator mediator = (SpmlR2PSPMediator) channel.getIdentityMediator();

        // TODO : User planning infrastructure to process requests!
        ProvisioningTarget target = lookupTarget(spmlRequest.getTargetID());

        AddResponseType spmlResponse = new AddResponseType();
        spmlResponse.setRequestID(spmlRequest.getRequestID());

        if (spmlRequest.getOtherAttributes().containsKey(SPMLR2Constants.userAttr)) {

            UserType spmlUser = (UserType) spmlRequest.getData();

            if (logger.isDebugEnabled())
                logger.debug("Processing SPML Add request for User " + spmlUser.getUserName());

            try {

                lookupUserByName(target, spmlUser.getUserName());
                
                // ERROR, username already exists
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.ALREADY_EXISTS );
                spmlResponse.getErrorMessage().add("Username '" + spmlUser.getUserName()+ "' already exitsts.");

                if (logger.isDebugEnabled())
                    logger.debug("Username '" + spmlUser.getUserName()+ "' already exitsts.");

                return spmlResponse;
                
            } catch(UserNotFoundException e) {
                // OK!
            }
                
            AddUserRequest req = toAddUserRequest(target, spmlRequest);

            AddUserResponse res = target.addUser(req);
            spmlResponse.setPso(toSpmlUser(target, res.getUser()));
            spmlResponse.setStatus(StatusCodeType.SUCCESS);

        } else if (spmlRequest.getOtherAttributes().containsKey(SPMLR2Constants.groupAttr)) {

            GroupType spmlGroup = (GroupType) spmlRequest.getData();
            if (logger.isDebugEnabled())
                logger.debug("Processing SPML Add request for Group " + spmlGroup.getName());

            try {

                lookupGroup(target, spmlGroup.getName());

                // ERROR, username already exists
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.ALREADY_EXISTS );
                spmlResponse.getErrorMessage().add("Group '" + spmlGroup.getName()+ "' already exitsts.");

                if (logger.isDebugEnabled())
                    logger.debug("Group '" + spmlGroup.getName()+ "' already exitsts.");

                return spmlResponse;

            } catch(GroupNotFoundException e) {
                // OK!
            }

            AddGroupRequest req = toAddGroupRequest(target, spmlRequest);
            
            AddGroupResponse res = target.addGroup(req);
            spmlResponse.setPso(toSpmlGroup(target, res.getGroup()));
            spmlResponse.setStatus(StatusCodeType.SUCCESS);

        } else if (spmlRequest.getOtherAttributes().containsKey(SPMLR2Constants.userAttributeAttr)) {

            UserAttributeType spmlUserAttribute = (UserAttributeType) spmlRequest.getData();
            if (logger.isDebugEnabled())
                logger.debug("Processing SPML Add request for UserAttribute " + spmlUserAttribute.getName());

            try {

                lookupUserAttributeByName(target, spmlUserAttribute.getName());

                // ERROR, user attribute name already exists
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.ALREADY_EXISTS );
                spmlResponse.getErrorMessage().add("UserAttribute '" + spmlUserAttribute.getName()+ "' already exitsts.");

                if (logger.isDebugEnabled())
                    logger.debug("UserAttribute '" + spmlUserAttribute.getName()+ "' already exitsts.");

                return spmlResponse;

            } catch(UserAttributeNotFoundException e) {
                // OK!
            }

            AddUserAttributeRequest req = toAddUserAttributeRequest(target, spmlRequest);
            AddUserAttributeResponse res = target.addUserAttribute(req);

            spmlResponse.setPso(toSpmlUserAttribute(target, res.getUserAttribute()));
            spmlResponse.setStatus(StatusCodeType.SUCCESS);

        } else if (spmlRequest.getOtherAttributes().containsKey(SPMLR2Constants.groupAttributeAttr)) {

            GroupAttributeType spmlGroupAttribute = (GroupAttributeType) spmlRequest.getData();
            if (logger.isDebugEnabled())
                logger.debug("Processing SPML Add request for GroupAttribute " + spmlGroupAttribute.getName());

            try {

                lookupGroupAttributeByName(target, spmlGroupAttribute.getName());

                // ERROR, group attribute name already exists
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.ALREADY_EXISTS );
                spmlResponse.getErrorMessage().add("GroupAttribute '" + spmlGroupAttribute.getName()+ "' already exitsts.");

                if (logger.isDebugEnabled())
                    logger.debug("GroupAttribute '" + spmlGroupAttribute.getName()+ "' already exitsts.");

                return spmlResponse;

            } catch(GroupAttributeNotFoundException e) {
                // OK!
            }

            AddGroupAttributeRequest req = toAddGroupAttributeRequest(target, spmlRequest);
            AddGroupAttributeResponse res = target.addGroupAttribute(req);

            spmlResponse.setPso(toSpmlGroupAttribute(target, res.getGroupAttribute()));
            spmlResponse.setStatus(StatusCodeType.SUCCESS);

        }

        return spmlResponse;
    }

    protected SearchResponseType doProcessSearchRequest(CamelMediationExchange exchane, SearchRequestType spmlRequest) throws Exception {

        SearchResponseType spmlResponse = new SearchResponseType ();
        spmlResponse.setRequestID(spmlRequest.getRequestID());

        SearchQueryType spmlQry = spmlRequest.getQuery();
        ProvisioningTarget target = lookupTarget(spmlQry.getTargetID());

        // TODO : Query support is limmited
        List<Object> any = spmlQry.getAny();

        Object o = any.get(0);

        if (logger.isTraceEnabled())
            logger.trace("Received SPML Selection as " + o);

        SelectionType spmlSelect = null;


        if (o instanceof SelectionType ) {
            // Already unmarshalled
            spmlSelect = (SelectionType) o;

        } else if (o instanceof Element) {
            // DOM Element
            Element e = (Element) o;
            spmlSelect = (SelectionType) XmlUtils.unmarshal(e, new String[] {SPMLR2Constants.SPML_PKG});
        }
        else {
            // JAXB Element
            JAXBElement e = (JAXBElement) o;
            logger.debug("SMPL JAXBElement " + e.getName() + "[" + e.getValue() + "]");
            spmlSelect = (SelectionType) e.getValue();
        }

        String uri = spmlSelect.getNamespaceURI();
        String path = spmlSelect.getPath();

        if (uri == null || !uri.equals("http://www.w3.org/TR/xpath20")) {
            logger.error("Unsupported query language " + uri);
            spmlResponse.setRequestID(spmlRequest.getRequestID());
            spmlResponse.setStatus(StatusCodeType.FAILURE);
            return spmlResponse;
        }

        if (logger.isDebugEnabled())
            logger.debug("Searching with path " + path);

        if (path.startsWith("/group")) {
            // TODO : Improve this

            ListGroupsResponse res = target.listGroups(new ListGroupsRequest());
            Group[] groups = res.getGroups();

            if (logger.isTraceEnabled())
                logger.trace("Searching {"+path+"} among " + groups.length);

            JXPathContext jxp = JXPathContext.newContext(new TargetContainer(groups));
            Iterator it = jxp.iteratePointers(path);
            while (it.hasNext()) {
                Pointer groupPointer = (Pointer) it.next();
                Group group = (Group) groupPointer.getValue();
                PSOType psoGroup = toSpmlGroup(target, group);
                spmlResponse.getPso().add(psoGroup);
            }

            if (logger.isTraceEnabled())
                logger.trace("Found " + spmlResponse.getPso().size() + " groups");

            spmlResponse.setStatus(StatusCodeType.SUCCESS);

        } else if (path.startsWith("/user")) {
            // TODO : Improve this
            ListUsersResponse res = target.listUsers(new ListUsersRequest());
            User[] users = res.getUsers();

            JXPathContext jxp = JXPathContext.newContext(new TargetContainer(users));
            Iterator it = jxp.iteratePointers(path);
            while (it.hasNext()) {
                Pointer userPointer = (Pointer) it.next();
                User user = (User) userPointer.getValue();
                PSOType psoUser = toSpmlUser(target, user);
                spmlResponse.getPso().add(psoUser);
            }
            spmlResponse.setStatus(StatusCodeType.SUCCESS);

        } else if (path.startsWith("/attrUser")) {
            // TODO : Improve this
            ListUserAttributesResponse res = target.listUserAttributes(new ListUserAttributesRequest());
            UserAttributeDefinition[] userAttributes = res.getUserAttributes();

            JXPathContext jxp = JXPathContext.newContext(new TargetContainer(userAttributes));
            Iterator it = jxp.iteratePointers("/userAttributes");
            while (it.hasNext()) {
                Pointer userAttributePointer = (Pointer) it.next();
                UserAttributeDefinition userAttribute = (UserAttributeDefinition) userAttributePointer.getValue();
                PSOType psoUserAttribute = toSpmlUserAttribute(target, userAttribute);
                spmlResponse.getPso().add(psoUserAttribute);
            }
            spmlResponse.setStatus(StatusCodeType.SUCCESS);

        } else if (path.startsWith("/attrGroup")) {
            // TODO : Improve this
            ListGroupAttributesResponse res = target.listGroupAttributes(new ListGroupAttributesRequest());
            GroupAttributeDefinition[] groupAttributes = res.getGroupAttributes();

            JXPathContext jxp = JXPathContext.newContext(new TargetContainer(groupAttributes));
            Iterator it = jxp.iteratePointers("/groupAttributes");
            while (it.hasNext()) {
                Pointer groupAttributePointer = (Pointer) it.next();
                GroupAttributeDefinition groupAttribute = (GroupAttributeDefinition) groupAttributePointer.getValue();
                PSOType psoGroupAttribute = toSpmlGroupAttribute(target, groupAttribute);
                spmlResponse.getPso().add(psoGroupAttribute);
            }
            spmlResponse.setStatus(StatusCodeType.SUCCESS);

        } else {
            throw new UnsupportedOperationException("Select path not supported '"+path+"'");
        }

        return spmlResponse;

    }

    protected LookupResponseType doProcessLookupRequest(CamelMediationExchange exchane, LookupRequestType spmlRequest) throws Exception {

        PSOIdentifierType psoId = spmlRequest.getPsoID();
        ProvisioningTarget target = lookupTarget(psoId.getTargetID());

        LookupResponseType spmlResponse = new LookupResponseType();
        spmlResponse.setRequestID(spmlRequest.getRequestID());

        if (psoId.getOtherAttributes().containsKey(SPMLR2Constants.groupAttr)) {

            if (logger.isTraceEnabled())
                logger.trace("Looking for group using PSO-ID " + psoId.getID());

            // Lookup groups
            FindGroupByIdRequest req = new FindGroupByIdRequest();
            req.setId(psoId.getID());

            try {
                FindGroupByIdResponse res = target.findGroupById(req);

                spmlResponse.setPso(toSpmlGroup(target, res.getGroup()));
                spmlResponse.setStatus(StatusCodeType.SUCCESS );

            } catch (GroupNotFoundException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.NO_SUCH_IDENTIFIER);
                spmlResponse.getErrorMessage().add(e.getMessage());

            }


        } else if (psoId.getOtherAttributes().containsKey(SPMLR2Constants.userAttr)) {

            if (logger.isTraceEnabled())
                logger.trace("Looking for user using PSO-ID " + psoId.getID());

            FindUserByIdRequest req = new FindUserByIdRequest();
            req.setId(psoId.getID());

            try {
                FindUserByIdResponse res = target.findUserById(req);

                spmlResponse.setPso(toSpmlUser(target, res.getUser()));
                spmlResponse.setStatus(StatusCodeType.SUCCESS );
            } catch (UserNotFoundException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.NO_SUCH_IDENTIFIER);
                spmlResponse.getErrorMessage().add(e.getMessage());

            }
        } else if (psoId.getOtherAttributes().containsKey(SPMLR2Constants.userAttributeAttr)) {

            if (logger.isTraceEnabled())
                logger.trace("Looking for user attribute using PSO-ID " + psoId.getID());

            FindUserAttributeByIdRequest req = new FindUserAttributeByIdRequest();
            req.setId(psoId.getID());

            try {
                FindUserAttributeByIdResponse res = target.findUserAttributeById(req);

                spmlResponse.setPso(toSpmlUserAttribute(target, res.getUserAttribute()));
                spmlResponse.setStatus(StatusCodeType.SUCCESS );
            } catch (UserAttributeNotFoundException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.NO_SUCH_IDENTIFIER);
                spmlResponse.getErrorMessage().add(e.getMessage());

            }
        } else if (psoId.getOtherAttributes().containsKey(SPMLR2Constants.groupAttributeAttr)) {

            if (logger.isTraceEnabled())
                logger.trace("Looking for group attribute using PSO-ID " + psoId.getID());

            FindGroupAttributeByIdRequest req = new FindGroupAttributeByIdRequest();
            req.setId(psoId.getID());

            try {
                FindGroupAttributeByIdResponse res = target.findGroupAttributeById(req);

                spmlResponse.setPso(toSpmlGroupAttribute(target, res.getGroupAttribute()));
                spmlResponse.setStatus(StatusCodeType.SUCCESS );
            } catch (GroupAttributeNotFoundException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.NO_SUCH_IDENTIFIER);
                spmlResponse.getErrorMessage().add(e.getMessage());

            }
        } else {

            logger.error("Unknonw/Undefined PSO attribute that specifies entity type (Non-Normative)");

            spmlResponse.setStatus(StatusCodeType.FAILURE );
        }

        return spmlResponse;

    }

    protected ModifyResponseType doProcessModifyRequest(CamelMediationExchange exchange, ModifyRequestType spmlRequest) {

        if (spmlRequest.getOtherAttributes().containsKey(SPMLR2Constants.groupAttr)) {

            ModifyResponseType spmlResponse = new ModifyResponseType();
            spmlResponse.setRequestID(spmlRequest.getRequestID());

            ModificationType spmlMod = spmlRequest.getModification().get(0);
            GroupType spmlGroup = (GroupType) spmlMod.getData();

            ProvisioningTarget target = lookupTarget(spmlRequest.getPsoID().getTargetID());

            try {
                UpdateGroupRequest groupRequest = toUpdateGroupRequest(target, spmlRequest); 
                UpdateGroupResponse groupResponse = target.updateGroup(groupRequest);
                Group group = groupResponse.getGroup();

                spmlResponse.setPso(toSpmlGroup(target, group));
                spmlResponse.setStatus(StatusCodeType.SUCCESS);

            } catch (GroupNotFoundException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.NO_SUCH_IDENTIFIER);
                spmlResponse.getErrorMessage().add(e.getMessage());

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
            }

            return spmlResponse;

        } else if (spmlRequest.getOtherAttributes().containsKey(SPMLR2Constants.userAttr)) {

            ModifyResponseType spmlResponse = new ModifyResponseType();
            spmlResponse.setRequestID(spmlRequest.getRequestID());

            ProvisioningTarget target = lookupTarget(spmlRequest.getPsoID().getTargetID());

            try {
                UpdateUserRequest userRequest = toUpdateUserRequest(target, spmlRequest); 
                UpdateUserResponse userResponse = target.updateUser(userRequest);

                spmlResponse.setPso(toSpmlUser(target, userResponse.getUser()));
                spmlResponse.setStatus(StatusCodeType.SUCCESS);
            } catch (UserNotFoundException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.NO_SUCH_IDENTIFIER);
                spmlResponse.getErrorMessage().add(e.getMessage());

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
            }

            return spmlResponse;
        } else if (spmlRequest.getOtherAttributes().containsKey(SPMLR2Constants.userAttributeAttr)) {

            ModifyResponseType spmlResponse = new ModifyResponseType();
            spmlResponse.setRequestID(spmlRequest.getRequestID());

            ProvisioningTarget target = lookupTarget(spmlRequest.getPsoID().getTargetID());

            try {
                UpdateUserAttributeRequest userAttributeRequest = toUpdateUserAttributeRequest(target, spmlRequest);
                UpdateUserAttributeResponse userAttributeResponse = target.updateUserAttribute(userAttributeRequest);

                spmlResponse.setPso(toSpmlUserAttribute(target, userAttributeResponse.getUserAttribute()));
                spmlResponse.setStatus(StatusCodeType.SUCCESS);
            } catch (UserAttributeNotFoundException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.NO_SUCH_IDENTIFIER);
                spmlResponse.getErrorMessage().add(e.getMessage());

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
            }

            return spmlResponse;
        } else if (spmlRequest.getOtherAttributes().containsKey(SPMLR2Constants.groupAttributeAttr)) {

            ModifyResponseType spmlResponse = new ModifyResponseType();
            spmlResponse.setRequestID(spmlRequest.getRequestID());

            ProvisioningTarget target = lookupTarget(spmlRequest.getPsoID().getTargetID());

            try {
                UpdateGroupAttributeRequest groupAttributeRequest = toUpdateGroupAttributeRequest(target, spmlRequest);
                UpdateGroupAttributeResponse groupAttributeResponse = target.updateGroupAttribute(groupAttributeRequest);

                spmlResponse.setPso(toSpmlGroupAttribute(target, groupAttributeResponse.getGroupAttribute()));
                spmlResponse.setStatus(StatusCodeType.SUCCESS);
            } catch (GroupAttributeNotFoundException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.NO_SUCH_IDENTIFIER);
                spmlResponse.getErrorMessage().add(e.getMessage());

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
            }

            return spmlResponse;
        } else {
            throw new UnsupportedOperationException("SPML Request not supported");
        }



    }

    protected ResponseType doProcessDeleteRequest(CamelMediationExchange exchange, DeleteRequestType spmlRequest) {
        if (spmlRequest.getOtherAttributes().containsKey(SPMLR2Constants.groupAttr)) {

            ResponseType spmlResponse = new ResponseType();
            spmlResponse.setRequestID(spmlRequest.getRequestID());

            RemoveGroupRequest groupRequest = new RemoveGroupRequest ();
            groupRequest.setId(spmlRequest.getPsoID().getID());

            ProvisioningTarget target = lookupTarget(spmlRequest.getPsoID().getTargetID());

            try {
                RemoveGroupResponse groupResponse = target.removeGroup(groupRequest);
                spmlResponse.setStatus(StatusCodeType.SUCCESS);
                spmlResponse.getOtherAttributes().containsKey(SPMLR2Constants.groupAttr);

            } catch (GroupNotFoundException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.NO_SUCH_IDENTIFIER);
                spmlResponse.getErrorMessage().add(e.getMessage());

            } catch (ProvisioningException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);

            }

            return spmlResponse;
        } else if (spmlRequest.getOtherAttributes().containsKey(SPMLR2Constants.userAttr)) {

            ResponseType spmlResponse = new ResponseType();
            spmlResponse.setRequestID(spmlRequest.getRequestID());

            RemoveUserRequest userRequest = new RemoveUserRequest ();
            userRequest.setId(spmlRequest.getPsoID().getID());

            ProvisioningTarget target = lookupTarget(spmlRequest.getPsoID().getTargetID());

            try {
                RemoveUserResponse userResponse = target.removeUser(userRequest);

                spmlResponse.setStatus(StatusCodeType.SUCCESS);
                spmlResponse.getOtherAttributes().containsKey(SPMLR2Constants.groupAttr);

            } catch (UserNotFoundException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.NO_SUCH_IDENTIFIER);
                spmlResponse.getErrorMessage().add(e.getMessage());

            } catch (ProvisioningException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
            }

            return spmlResponse;


        } else if (spmlRequest.getOtherAttributes().containsKey(SPMLR2Constants.userAttributeAttr)) {

            ResponseType spmlResponse = new ResponseType();
            spmlResponse.setRequestID(spmlRequest.getRequestID());

            RemoveUserAttributeRequest userAttributeRequest = new RemoveUserAttributeRequest();
            userAttributeRequest.setId(spmlRequest.getPsoID().getID());

            ProvisioningTarget target = lookupTarget(spmlRequest.getPsoID().getTargetID());

            try {
                RemoveUserAttributeResponse userAttributeResponse = target.removeUserAttribute(userAttributeRequest);

                spmlResponse.setStatus(StatusCodeType.SUCCESS);
                
            } catch (UserAttributeNotFoundException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.NO_SUCH_IDENTIFIER);
                spmlResponse.getErrorMessage().add(e.getMessage());

            } catch (ProvisioningException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
            }

            return spmlResponse;


        } else if (spmlRequest.getOtherAttributes().containsKey(SPMLR2Constants.groupAttributeAttr)) {

            ResponseType spmlResponse = new ResponseType();
            spmlResponse.setRequestID(spmlRequest.getRequestID());

            RemoveGroupAttributeRequest groupAttributeRequest = new RemoveGroupAttributeRequest();
            groupAttributeRequest.setId(spmlRequest.getPsoID().getID());

            ProvisioningTarget target = lookupTarget(spmlRequest.getPsoID().getTargetID());

            try {
                RemoveGroupAttributeResponse groupAttributeResponse = target.removeGroupAttribute(groupAttributeRequest);

                spmlResponse.setStatus(StatusCodeType.SUCCESS);

            } catch (GroupAttributeNotFoundException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
                spmlResponse.setError(ErrorCode.NO_SUCH_IDENTIFIER);
                spmlResponse.getErrorMessage().add(e.getMessage());

            } catch (ProvisioningException e) {
                logger.error(e.getMessage(), e);
                spmlResponse.setStatus(StatusCodeType.FAILURE);
            }

            return spmlResponse;


        } else {
            throw new UnsupportedOperationException("SPML Request not supported");
        }
    }

    public ResponseType doProcessSetPassword(CamelMediationExchange exchange, SetPasswordRequestType spmlRequest) {

        ResponseType spmlResponse = new ResponseType();
        try {

            String currentPwd = spmlRequest.getCurrentPassword();
            String newPwd = spmlRequest.getPassword();

            SetPasswordRequest req = new SetPasswordRequest();

            req.setUserId(spmlRequest.getPsoID().getID());
            req.setCurrentPassword(spmlRequest.getCurrentPassword());
            req.setNewPassword(spmlRequest.getPassword());

            ProvisioningTarget target = lookupTarget(spmlRequest.getPsoID().getTargetID());

            target.setPassword(req);
            spmlResponse.setStatus(StatusCodeType.SUCCESS);

        } catch (ProvisioningException e) {
            logger.error(e.getMessage(), e);
            spmlResponse.setStatus(StatusCodeType.FAILURE);
        }

        return spmlResponse;
    }

    public ResponseType doProcessReplacePassword(CamelMediationExchange exchange, ReplacePasswordRequestType spmlRequest) {

        ResponseType spmlResponse = new ResponseType();
        try {

            ProvisioningTarget target = lookupTarget(spmlRequest.getPsoID().getTargetID());
            String userId = spmlRequest.getPsoID().getID();
            String newPwd = spmlRequest.getNewPassword();

            User user = this.lookupUser(target, userId);

            ResetPasswordRequest req = new ResetPasswordRequest (user);
            req.setNewPassword(newPwd);
            target.resetPassword(req);
            spmlResponse.setStatus(StatusCodeType.SUCCESS);

        } catch (ProvisioningException e) {
            logger.error(e.getMessage(), e);
            spmlResponse.setStatus(StatusCodeType.FAILURE);
        }

        return spmlResponse;
    }


    public class TargetContainer {

        public TargetContainer(Group[] groups) {
            this.groups = groups;
        }

        public TargetContainer(User[] users) {
            this.users = users;
        }

        public TargetContainer(UserAttributeDefinition[] userAttributes) {
            this.userAttributes = userAttributes;
        }

        public TargetContainer(GroupAttributeDefinition[] groupAttributes) {
            this.groupAttributes = groupAttributes;
        }

        public TargetContainer(Group[] groups, User[] users) {
            this.groups = groups;
            this.users = users;
        }

        private Group[] groups = new Group[0];

        private User[] users = new User[0];

        private UserAttributeDefinition[] userAttributes = new UserAttributeDefinition[0];

        private GroupAttributeDefinition[] groupAttributes = new GroupAttributeDefinition[0];
        
        public Group[] getGroups() {
            return groups;
        }

        public void setGroups(Group[] groups) {
            this.groups = groups;
        }

        public User[] getUsers() {
            return users;
        }

        public void setUsers(User[] users) {
            this.users = users;
        }

        public UserAttributeDefinition[] getUserAttributes() {
            return userAttributes;
        }

        public void setUserAttributes(UserAttributeDefinition[] userAttributes) {
            this.userAttributes = userAttributes;
        }

        public GroupAttributeDefinition[] getGroupAttributes() {
            return groupAttributes;
        }

        public void setGroupAttributes(GroupAttributeDefinition[] groupAttributes) {
            this.groupAttributes = groupAttributes;
        }
    }
    
}
