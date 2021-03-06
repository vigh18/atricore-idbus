/*
 * Atricore IDBus
 *
 * Copyright (c) 2009-2012, Atricore Inc.
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
package org.atricore.idbus.capabilities.sso.component.builtin.test

import org.specs2.mutable._
import org.atricore.idbus.capabilities.sso.dsl.core.directives.IdentityFlowDirectives
import org.atricore.idbus.capabilities.sso.dsl.util.Logging
import org.apache.camel._
import org.apache.camel.impl._
import org.atricore.idbus.kernel.main.mediation.camel.component.binding.{MediationBindingComponent, CamelMediationExchange}
import org.atricore.idbus.capabilities.sso.dsl.core.{NoMoreClaimEndpoints, IdentityFlowRequestContext}
import org.atricore.idbus.capabilities.sso.dsl.{Redirect, IdentityFlowResponse, IdentityFlowRequest}
import org.atricore.idbus.capabilities.sso.main.idp.IdPSecurityContext
import org.atricore.idbus.capabilities.sso.support.binding.SSOBinding
import org.atricore.idbus.capabilities.sso.main.idp.producers.AuthenticationState
import org.atricore.idbus.capabilities.sso.support.auth.AuthnCtxClass
import org.atricore.idbus.capabilities.sso.test.dsl.IdentityFlowDSLTestSupport
import org.atricore.idbus.capabilities.sso.component.builtin.directives.{MediationDirectives, ClaimDirectives}

class ClaimDirectivesSpec extends Specification
  with IdentityFlowDSLTestSupport
  with IdentityFlowDirectives
  with MediationDirectives
  with ClaimDirectives
  with Logging {

  "The claim directive" should {
    "select claim endpoints" in {

      val r1 =
        logRequestResponse("") {
          withNoSession {
            pendingRetries(2) {
              retryToCollectClaimsOnSameClaimChannel
            } ~
            pickClaimChannel {
              collectClaims(_, _)
            }
          }
        }

      val ctx : ContextBuilder = {
        exchange =>
        // define service provider
        val provider = newServiceProvider("sp1")

        // claim channel and endpoints
        val claimChannel1 = newClaimChannel("cc-1")
        val claimCh1Ep1 = newIdentityMediationEndpoint("cc-1-ep1", SSOBinding.SSO_LOCAL, AuthnCtxClass.UNSPECIFIED_AUTHN_CTX)
        val claimCh1Ep2 = newIdentityMediationEndpoint("cc-1-ep2", SSOBinding.SSO_LOCAL, AuthnCtxClass.PASSWORD_AUTHN_CTX)
        claimChannel1.getEndpoints.add(claimCh1Ep1)
        claimChannel1.getEndpoints.add(claimCh1Ep2)

        // define service provider channel
        val spChannel = newSpChannel("spc-1")
        spChannel.getClaimProviders.add(claimChannel1)

        val req = IdentityFlowRequest(exchange.asInstanceOf[CamelMediationExchange], provider, spChannel)
        IdentityFlowRequestContext(req)
      }

      val as = new AuthenticationState

      val response1 = test(ctx, r1, newExchange( newSecurityContext("sp1"),as))
      response1.response must beSome
      response1.response.get.statusCode.isSuccess must beTrue
      response1.response.get.statusCode.value mustEqual 1
      response1.response.get.statusCode.asInstanceOf[Redirect].channel.getName mustEqual "cc-1"
      response1.response.get.statusCode.asInstanceOf[Redirect].endpoint.getName mustEqual "cc-1-ep1"

      val response2 = test(ctx, r1, newExchange( newSecurityContext("sp1"),as))

      response2.response must beSome
      response2.response.get.statusCode.isSuccess must beTrue
      response2.response.get.statusCode.value mustEqual 1
      response2.response.get.statusCode.asInstanceOf[Redirect].channel.getName mustEqual "cc-1"
      response2.response.get.statusCode.asInstanceOf[Redirect].endpoint.getName mustEqual "cc-1-ep2"

      val response3 = test(ctx, r1, newExchange( newSecurityContext("sp1"),as))
      response3.response.get.statusCode.isSuccess must beTrue
      response3.response.get.statusCode.value mustEqual 1
      response3.response.get.statusCode.asInstanceOf[Redirect].channel.getName mustEqual "cc-1"
      response3.response.get.statusCode.asInstanceOf[Redirect].endpoint.getName mustEqual "cc-1-ep2"

      val response4 = test(ctx, r1, newExchange( newSecurityContext("sp1"),as))
      response4.response.get.statusCode.isSuccess must beTrue
      response4.response.get.statusCode.value mustEqual 1
      response4.response.get.statusCode.asInstanceOf[Redirect].channel.getName mustEqual "cc-1"
      response4.response.get.statusCode.asInstanceOf[Redirect].endpoint.getName mustEqual "cc-1-ep2"

      val response5 = test(ctx, r1, newExchange( newSecurityContext("sp1"),as))
      response5.response.get.statusCode.isSuccess must beTrue
      response5.response.get.statusCode.value mustEqual 1
      response5.response.get.statusCode.asInstanceOf[Redirect].channel.getName mustEqual "cc-1"
      response5.response.get.statusCode.asInstanceOf[Redirect].endpoint.getName mustEqual "cc-1-ep2"

      val response6 = test(ctx, r1, newExchange( newSecurityContext("sp1"),as))
      response6.rejections must beSome
      response6.rejections must beSome(Set(NoMoreClaimEndpoints))

    }

  }
}


