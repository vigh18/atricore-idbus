package org.atricore.idbus.capabilities.management.command;

import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.atricore.idbus.capabilities.management.main.spi.IdentityApplianceManagementService;
import org.atricore.idbus.capabilities.management.main.spi.request.UndeployIdentityApplianceRequest;
import org.atricore.idbus.capabilities.management.main.spi.response.UndeployIdentityApplianceResponse;

/**
 * @author <a href="mailto:sgonzalez@atricore.org">Sebastian Gonzalez Oyuela</a>
 * @version $Id$
 */
@Command(scope = "appliance", name = "undeploy", description = "Undeploy Identity Appliance")
public class UndeployApplianceCommand extends ManagementCommandSupport {

    @Argument(index = 0, name = "id", description = "The id of the identity appliance", required = true, multiValued = false)
    String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    protected Object doExecute(IdentityApplianceManagementService svc) throws Exception {

        UndeployIdentityApplianceRequest req = new UndeployIdentityApplianceRequest();
        req.setApplianceId(id);
        UndeployIdentityApplianceResponse res = svc.undeployIdentityAppliance(req);
        return null;
    }

}
