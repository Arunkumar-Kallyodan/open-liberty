/*******************************************************************************
 * Copyright (c) 2020 IBM Corpo<ration and others.
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
package io.openliberty.opentracing.internal.test;

import java.io.File;
import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import com.ibm.websphere.simplicity.ShrinkHelper;

import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import junit.framework.Assert;

/**
 * Various utility methods.
 */
@Mode(TestMode.FULL)
public class FATTestBase {
    /**
     * Deploy jaxrsHelloWorld.war.
     * 
     * @param server The unstarted server.
     * @throws Exception Any issues adding the application.
     */
    protected static void deployHelloWorldApp(LibertyServer server) throws Exception {
        WebArchive serviceWar = ShrinkWrap.create(WebArchive.class, "jaxrsHelloWorld.war");
        serviceWar.addPackages(true, "io.openliberty.opentracing.internal.test.helloworld");
        serviceWar.addAsWebInfResource(
                                       new File("test-applications/jaxrsHelloWorld/resources/beans.xml"));
        ShrinkHelper.exportAppToServer(server, serviceWar);
    }
    
    /**
     * Test the helloWorld endpoint of jaxrsHelloWorld.war.
     * 
     * @param server The started server.
     * @throws Exception Any issues calling the web service.
     */
    protected static void testHelloWorld(LibertyServer server) throws Exception {
        String requestUrl = "http://" +
                            server.getHostname() + ":" +
                            server.getHttpDefaultPort() +
                            "/jaxrsHelloWorld/rest/ws/helloWorld";

        List<String> actualResponseLines = FATUtilsServer.gatherHttpRequest(FATUtilsServer.HttpRequestMethod.GET, requestUrl);

        Assert.assertEquals(1, actualResponseLines.size());
        Assert.assertEquals("Hello World", actualResponseLines.get(0));
    }
    
    /**
     * Stop a server with jaxrsHelloWorld.war and assert the
     * expected messages since there is no tracer factory configured.
     * 
     * @param server The started server.
     * @throws Exception Any issues stopping the server.
     */
    protected static void stopHelloWorldServer(LibertyServer server) throws Exception {
        try {
            Assert.assertNotNull("Expecting CWMOT0010W message", server.waitForStringInLogUsingMark("CWMOT0010W", 0));
        } finally {
            server.stopServer("CWMOT0010W");
        }
    }
}
