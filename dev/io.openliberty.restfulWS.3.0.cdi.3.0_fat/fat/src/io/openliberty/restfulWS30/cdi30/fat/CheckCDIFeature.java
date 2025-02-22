/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package io.openliberty.restfulWS30.cdi30.fat;

import static org.junit.Assert.assertNotNull;

import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;

public class CheckCDIFeature {
    public static LibertyServer checkCDIFeature(LibertyServer server, String hellowar, String cdiVersion) throws Exception {
        server = LibertyServerFactory.getLibertyServer("com.ibm.ws.jaxrs20.cdi.fat." + hellowar + cdiVersion);
        server.addInstalledAppForValidation(hellowar);
        server.startServer(hellowar + cdiVersion + ".log", true);
        // Make sure server is started
        assertNotNull("The server did not start", server.waitForStringInLog("CWWKF0011I"));
        cdiVersion = cdiVersion.replaceFirst(cdiVersion.substring(0, 1), cdiVersion.substring(0, 1) + ".");
        assertNotNull("The server enables feature cdi-" + cdiVersion, server.getServerConfiguration().getFeatureManager().getFeatures().toString().contains("cdi-" + cdiVersion));
        System.out.println("The cdi-" + cdiVersion + " feature is enabled in server.xml: " + server.getServerConfiguration().getFeatureManager().getFeatures().toString());

        return server;
    }
}
