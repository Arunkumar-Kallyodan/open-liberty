/*******************************************************************************
 * Copyright (c) 2018, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.ws.security.saml.fat.logout.IDPInitiated_Login;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.security.saml.fat.logout.common.OneServerLTPALogoutTests;
import com.ibm.ws.security.saml20.fat.commonTest.SAMLConstants;
import com.ibm.ws.security.saml20.fat.commonTest.SAMLMessageConstants;

import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServerWrapper;

/**
 * The testcases in this class were ported from tWAS' test SamlWebSSOTests.
 * If a tWAS test is not applicable, it will be noted in the comments below.
 * If a tWAS test fits better into another test class, it will be noted
 * which test project/class it now resides in.
 * In general, these tests perform a simple IdP initiated SAML Web SSO, using
 * httpunit to simulate browser requests. In this scenario, a Web client
 * accesses a static Web page on IdP and obtains a a SAML HTTP-POST link
 * to an application installed on a WebSphere SP. When the Web client
 * invokes the SP application, it is redirected to a TFIM IdP which issues
 * a login challenge to the Web client. The Web Client fills in the login
 * form and after a successful login, receives a SAML 2.0 token from the
 * TFIM IdP. The client invokes the SP application by sending the SAML
 * 2.0 token in the HTTP POST request.
 */
@LibertyServerWrapper
@Mode(TestMode.FULL)
@RunWith(FATRunner.class)
public class IDPInitiatedLogin_IDPInitiated_LogoutUrl_LTPA_Tests extends OneServerLTPALogoutTests {

    private static final Class<?> thisClass = IDPInitiatedLogin_IDPInitiated_LogoutUrl_LTPA_Tests.class;

    @BeforeClass
    public static void setupBeforeTest() throws Exception {

        flowType = SAMLConstants.IDP_INITIATED;
        // set cookie variables based on the types of cookies we'll be using (LTPA, SP Cookies, or Mixed (LTPA and SP Cookies)
        setCookieSettings(CookieType.LTPACOOKIE);
        testUsers = new Testusers(chooseUsers()); // randomly chooses either the same or different users to use for this instance

        msgUtils.printClassName(thisClass.toString());
        Log.info(thisClass, "setupBeforeTest", "Prep for test");
        // add any additional messages that you want the "start" to wait for
        // we should wait for any providers that this test requires
        List<String> extraMsgs = getDefaultSAMLStartMsgs();

        List<String> extraApps = new ArrayList<String>();
        extraApps.add(SAMLConstants.SAML_CLIENT_APP);

        startSPWithIDPServer("com.ibm.ws.security.saml.sso_fat.logout", "server_SPLogoutFalse" + cookieInfo.getCookieFileExtension() + ".xml", extraMsgs, extraApps, true);

        testSAMLServer.addIgnoredServerException(SAMLMessageConstants.CWWKS5207W_SAML_CONFIG_IGNORE_ATTRIBUTES);

        /* Set some properties that will dictate the behavior of the common logout tests */
        /* This includes the endpoint that we'll invoke, the steps that need to occur in the logout proces, ... */

        // login is IDP initiated, logout starts on the IDP
        setLogoutFlowSettings(testSettings, SAMLConstants.IDP_INITIATED, SAMLConstants.IDP_INITIATED, LOGOUT_INVOLVES_IDP);

        //        testSettings.setIdpUserName("john_vmmUser");
        //        testSettings.setIdpUserPwd("john_vmmUser");
        //        testSettings.setIdpUserName("ping_vmmUser");
        //        testSettings.setIdpUserPwd("ping_vmmUser");
        //        testSettings.setIdpUserName("pong_vmmUser");
        //        testSettings.setIdpUserPwd("pong_vmmUser");

        //        helpers.testSleep(300);
    }

}
