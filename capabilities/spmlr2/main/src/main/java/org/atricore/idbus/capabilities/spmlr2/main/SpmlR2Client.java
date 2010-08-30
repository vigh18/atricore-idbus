package org.atricore.idbus.capabilities.spmlr2.main;

import oasis.names.tc.spml._2._0.wsdl.SPMLRequestPortType;

import java.util.Collection;

/**
 * @author <a href=mailto:sgonzalez@atricor.org>Sebastian Gonzalez Oyuela</a>
 */
public interface SpmlR2Client extends SPMLRequestPortType {

    String getPSProviderName();

    boolean hasTarget(String psTargetName);


}
