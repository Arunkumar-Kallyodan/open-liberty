/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
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
package com.ibm.ws.messaging.jms20.deliverydelay.fat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

import com.ibm.websphere.simplicity.ShrinkHelper;

import componenttest.topology.impl.LibertyServer;

public class TestUtils {

    public static WebArchive addWebApp(
        LibertyServer targetServer,
        String appName,
        String... packageNames) throws Exception {

        return addWebApp(targetServer, !IS_DROPIN, appName, packageNames);
    }

    public static WebArchive addDropinsWebApp(
        LibertyServer targetServer,
        String appName,
        String... packageNames) throws Exception {

        return addWebApp(targetServer, IS_DROPIN, appName, packageNames);
    }

    public static final boolean IS_DROPIN = true;

    public static WebArchive addWebApp(
        LibertyServer targetServer,
        boolean isDropin,
        String appName,
        String... packageNames) throws Exception {

        WebArchive webApp = ShrinkWrap.create(WebArchive.class, appName + ".war");
        webApp.addPackages(true, packageNames);

        File webInf = new File("test-applications/" + appName + "/resources/WEB-INF");
        if ( webInf.exists() ) {
            for ( File webInfElement : webInf.listFiles() ) {
                webApp.addAsWebInfResource(webInfElement);
            }
        }

        String appFolder = ( isDropin ? "dropins" : "apps" );
        ShrinkHelper.exportToServer(targetServer, appFolder, webApp);

        return webApp;
    }

    public static JavaArchive addDropinsJavaApp(
        LibertyServer targetServer,
        String appName,
        String... packageNames) throws Exception {

        return addJavaApp(targetServer, IS_DROPIN, appName, packageNames);
    }

    public static JavaArchive addJavaApp(
        LibertyServer targetServer,
        boolean isDropin,
        String appName,
        String... packageNames) throws Exception {

        JavaArchive javaApp = ShrinkHelper.buildJavaArchive(appName, packageNames);

        String appFolder = ( isDropin ? "dropins" : "apps" );
        ShrinkHelper.exportToServer(targetServer, appFolder, javaApp);

        return javaApp;
    }

    public static boolean runInServlet(
        String host, int port,
        String contextRoot, String test) throws IOException {

        return runInServlet(host, port, contextRoot, test, null);
    }

    public static boolean runInServlet(
        String host, int port,
        String contextRoot, String test, String localAddress) throws IOException {

        String urlText = "http://" + host + ":" + port + "/" + contextRoot + "?test=" + test;
        if ( localAddress != null ) {
            String encodedLocalAddress = URLEncoder.encode(localAddress, "UTF-8");
            // String decodedLocalAddress = URLDecoder.decode(encodedLocalAddress, "UTF-8");
            urlText += "&localAddress=" + encodedLocalAddress;
            // System.out.println("Local address [ " + localAddress + " ]");
            // System.out.println("Local address (encoded) [ " + encodedLocalAddress + " ]");
            // System.out.println("Local address (decoded) [ " + decodedLocalAddress + " ]");
        }
        // System.out.println("Test URL text [ " + urlText + " ]");

        URL servletUrl = new URL(urlText);
        System.out.println("Test URL [ " + servletUrl + " ]");

        HttpURLConnection con = (HttpURLConnection) servletUrl.openConnection();
        con.setDoInput(true);
        con.setDoOutput(true);
        con.setUseCaches(false);
        con.setRequestMethod("GET");

        try {
            con.connect();

            InputStream is = con.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String sep = System.lineSeparator();

            StringBuilder lines = new StringBuilder();
            String line;
            while ( (line = br.readLine()) != null ) {
                lines.append(line).append(sep);
            }

            String successMessage = "COMPLETED SUCCESSFULLY";
            boolean result;
            if ( lines.indexOf(successMessage) < 0 ) {
                fail( "Test [ " + test + " ] failed;\n" +
                      " message [ " + successMessage + " ] not found;" +
                      " output:\n" +
                      lines );
                result = false;
            } else {
                result = true;
            }
            return result;

        } finally {
            con.disconnect();
        }
    }

    //

    public static int occurrencesInLog(
        LibertyServer server, String logName, String text) throws Exception {

        String logFile = server.getLogsRoot() + logName;

        FileReader reader;
        try {
            reader = new FileReader(logFile);
        } catch ( FileNotFoundException ex ) {
            ex.printStackTrace();
            return 0;
        } catch ( IOException ex ) {
            ex.printStackTrace();
            return 0;
        }

        int count = 0;

        try {
            BufferedReader br = new BufferedReader(reader);

            String nextLine;
            while ( (nextLine = br.readLine()) != null ) {
                if ( nextLine.contains(text) ) {
                    count++;
                }
            }

        } catch ( IOException ex ) {
            ex.printStackTrace();

        } finally {
            try {
                reader.close();
            } catch ( IOException ex ) {
                ex.printStackTrace();
            }
        }

        return count;
    }

    public static List<String> readLines(LibertyServer server, String relLogName) throws FileNotFoundException, IOException {
        List<String> lines = new ArrayList<String>();

        String logFile = server.getLogsRoot() + '/' + relLogName;
        FileReader reader = new FileReader(logFile); // throws FileNotFoundException, IOException
        try {
            BufferedReader br = new BufferedReader(reader);

            String nextLine;
            while ( (nextLine = br.readLine()) != null ) { // throws IOException
                lines.add(nextLine);
            }

        } finally {
            reader.close(); // throws IOException
        }

        return lines;
    }
}
