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

package org.atricore.idbus.capabilities.sso.main.common.plans.actions;

import oasis.names.tc.saml._2_0.assertion.AssertionType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.atricore.idbus.capabilities.sso.main.emitter.plans.actions.AbstractSSOAssertionAction;
import org.atricore.idbus.capabilities.sso.support.core.signature.SamlR2Signer;
import org.atricore.idbus.kernel.planning.IdentityArtifact;
import org.jbpm.graph.exe.ExecutionContext;

/**
 * @author <a href="mailto:sgonzalez@atricore.org">Sebastian Gonzalez Oyuela</a>
 * @version $Id: SignAssertionAction.java 1335 2009-06-24 16:34:38Z sgonzalez $
 */
public class SignAssertionAction extends AbstractSSOAssertionAction {

    private static final Log logger = LogFactory.getLog(SignAssertionAction .class);

    protected void doExecute(IdentityArtifact in, IdentityArtifact out, ExecutionContext executionContext) throws Exception {

        AssertionType assertion = (AssertionType) out.getContent();

        if (logger.isTraceEnabled())
            logger.trace("IDBUS-PERF METHODC [" + Thread.currentThread().getName() + "] /emitSamlAssertion STEP prepare signer");

        SamlR2Signer signer =
                (SamlR2Signer) executionContext.getContextInstance().getTransientVariable(VAR_SAMLR2_SIGNER);

        if (logger.isDebugEnabled())
            logger.debug("Signing assertion " + assertion.getID() + " with signer " + signer);
        
        assertion = signer.sign(assertion);

        if (logger.isTraceEnabled())
            logger.trace("IDBUS-PERF METHODC [" + Thread.currentThread().getName() + "] /emitSamlAssertion STEP signed");

        out.replaceContent(assertion);

        if (logger.isTraceEnabled())
            logger.trace("IDBUS-PERF METHODC [" + Thread.currentThread().getName() + "] /emitSamlAssertion END");

    }
}