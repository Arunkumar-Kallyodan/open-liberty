/*******************************************************************************
 * Copyright (c) 2017, 2024 IBM Corporation and others.
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
package com.ibm.ws.rest.handler.validator.fat;

import static org.junit.Assert.assertEquals;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import componenttest.rules.repeater.EE9PackageReplacementHelper;
import componenttest.rules.repeater.JakartaEEAction;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.HttpsRequest;

@RunWith(Suite.class)
@SuiteClasses({
                ValidateDataSourceTest.class,
                ValidateJCATest.class,
                ValidateJMSTest.class,
                ValidateDSCustomLoginModuleTest.class,
                ValidateOpenApiSchemaTest.class
})

public class FATSuite {

    private static final EE9PackageReplacementHelper packageReplacementHelper = new EE9PackageReplacementHelper();

    public static HttpsRequest createHttpsRequestWithAdminUser(LibertyServer server, String path) {
        return new HttpsRequest(server, path).allowInsecure().basicAuth("adminuser", "adminpwd");
    }

    public static void setupServerSideAnnotations(LibertyServer server) {
        if (JakartaEEAction.isEE9OrLaterActive()) {
            server.addEnvVar("CONNECTION_FACTORY", "jakarta.resource.cci.ConnectionFactory");
            server.addEnvVar("QUEUE_FACTORY", "jakarta.jms.QueueConnectionFactory");
            server.addEnvVar("TOPIC_FACTORY", "jakarta.jms.TopicConnectionFactory");
            server.addEnvVar("QUEUE_INTERFACE", "jakarta.jms.Queue");
            server.addEnvVar("TOPIC_INTERFACE", "jakarta.jms.Topic");
            server.addEnvVar("DESTINATION_INTERFACE", "jakarta.jms.Destination");
        } else {
            server.addEnvVar("CONNECTION_FACTORY", "javax.resource.cci.ConnectionFactory");
            server.addEnvVar("QUEUE_FACTORY", "javax.jms.QueueConnectionFactory");
            server.addEnvVar("TOPIC_FACTORY", "javax.jms.TopicConnectionFactory");
            server.addEnvVar("QUEUE_INTERFACE", "javax.jms.Queue");
            server.addEnvVar("TOPIC_INTERFACE", "javax.jms.Topic");
            server.addEnvVar("DESTINATION_INTERFACE", "javax.jms.Destination");
        }
    }

    public static void assertClassEquals(String message, String expected, String actual) {
        if (JakartaEEAction.isEE9OrLaterActive()) {
            expected = packageReplacementHelper.replacePackages(expected);
        }
        assertEquals(message, expected, actual);
    }

    public static String expectedJmsProviderSpecVersion() {
        if (JakartaEEAction.isEE10OrLaterActive()) {
            return "3.1";
        } else if (JakartaEEAction.isEE9Active()) {
            return "3.0";
        } else {
            return "2.0";
        }
    }
}