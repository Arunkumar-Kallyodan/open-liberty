/*******************************************************************************
 * Copyright (c) 2009, 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.ibm.ws.http.dispatcher.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import com.ibm.websphere.channelfw.osgi.CHFWBundle;
import com.ibm.websphere.event.EventEngine;
import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.websphere.ras.annotation.Trivial;
import com.ibm.ws.http.channel.internal.HttpConfigConstants;
import com.ibm.ws.http.internal.EncodingUtilsImpl;
import com.ibm.ws.http.internal.HttpDateFormatImpl;
import com.ibm.wsspi.bytebuffer.WsByteBufferPoolManager;
import com.ibm.wsspi.channelfw.ChannelFramework;
import com.ibm.wsspi.channelfw.ChannelFrameworkFactory;
import com.ibm.wsspi.http.EncodingUtils;
import com.ibm.wsspi.http.HttpDateFormat;
import com.ibm.wsspi.http.VirtualHostListener;
import com.ibm.wsspi.http.WorkClassifier;
import com.ibm.wsspi.http.channel.values.HttpHeaderKeys;
import com.ibm.wsspi.http.ee.behaviors.HttpBehavior;
import com.ibm.wsspi.http.ee7.HttpTransportBehavior;
import com.ibm.wsspi.kernel.service.utils.MetatypeUtils;
import com.ibm.wsspi.timer.ApproximateTime;
import com.ibm.wsspi.timer.QuickApproxTime;

/**
 * Component that handles configuration for the dispatching of inbound
 * HTTP traffic to registered HttpContainers
 */
@Component(configurationPid = "com.ibm.ws.http.dispatcher",
           service = HttpDispatcher.class,
           property = { "service.vendor=IBM" })
public class HttpDispatcher {
    /** trace variable */
    private static final TraceComponent tc = Tr.register(HttpDispatcher.class);

    /** String encoding utils service reference -- required, make lazy */
    private volatile EncodingUtils encodingSvc = null;
    /** Event service reference -- required */
    private volatile EventEngine eventSvc = null;
    /** ExecutorService reference for queueing inbound work -- required */
    private volatile ExecutorService executorService = null;
    /** Channel framework reference */
    private volatile CHFWBundle chfw = null;
    /** Classification Service -- optional */
    public volatile WorkClassifier workClassifier = null;

    private volatile ServiceReference<HttpTransportBehavior> behaviorRef;

    private static volatile boolean useEE7Streams = false;
    private static volatile Boolean useIOExceptionBehavior = null;

    //Servlet 6.0
    private volatile ServiceReference<HttpBehavior> cookieBehaviorRef;
    private static volatile boolean useEE10Cookies = false;

    //Servlet 6.1 (EE11)
    private static volatile boolean isEE11 = false;

    static final String CONFIG_ALIAS = "httpDispatcher";

    /** Property name for message string to override default string if the virtual host was not found" **/
    static final String PROP_VHOST_NOT_FOUND = "appOrContextRootMissingMessage";

    /** Property to check for in keys of properties for enabling / disabling of welcome page **/
    static final String PROP_ENABLE_WELCOME_PAGE = "enableWelcomePage";

    /** Property to check for in keys of properties for enabling / disabling of welcome page **/
    static final String PROP_PAD_VHOST_NOT_FOUND = "padAppOrContextRootMissingMessage";

    /**
     * Property that allows the user to restrict using private headers to requests
     * originating from certain hosts.
     */
    static final String PROP_TRUSTED_PRIVATE_HEADER_ORIGIN = "trustedHeaderOrigin";

    /**
     * WebContainer configuration for whether or not private headers should be trusted.
     *
     * @see #PROP_TRUSTED_PRIVATE_HEADER_ORIGIN
     */
    static final String PROP_WC_TRUSTED = "trusted";

    /**
     * Property that allows the user to restrict using private headers to requests
     * originating from certain hosts.
     */
    static final String PROP_TRUSTED_SENSITIVE_HEADER_ORIGIN = "trustedSensitiveHeaderOrigin";

    /**
     * Active HttpDispatcher instance. May be null between deactivate and activate
     * calls.
     */
    private static final AtomicReference<HttpDispatcher> instance = new AtomicReference<HttpDispatcher>();

    /** appOrContextRootMissingMessage custom property */
    private volatile String appOrContextRootNotFound = null;

    /** appOrContextRootMissingMessage custom property */
    private boolean padAppOrContextRootNotFoundMessage = true;

    /** Property for enabling/disabling the default welcome page */
    private volatile boolean enableWelcomePage = true;

    /** PM97514 - keep original value received from config for negotiating between dispatcher & webcontainer settings */
    private volatile String[] origHeaderOrigin = null;
    /** PM97514 - webcontainer trusted attribute */
    private volatile boolean wcTrusted = true;

    /** keep original value received from config for negotiating between dispatcher & webcontainer settings */
    private volatile String[] origSensitiveHeaderOrigin = null;

    /** keep track of the hosts for which private headers are trusted */
    private volatile TrustedHeaderOriginLists trustedLists = null;

    /** Web Container property for HSTS header value **/
    private volatile static String hstsHeader = "noValue";

    private static final AtomicInteger updateCount = new AtomicInteger(0);

    /**
     * Constructor.
     */
    public HttpDispatcher() {
    }

    /**
     * DS method to activate this component.
     *
     * @param context
     */
    @Activate
    protected void activate(Map<String, Object> properties) {
        modified(properties);

        // Set this as the active HttpDispatcher instance
        instance.set(this);

        if (TraceComponent.isAnyTracingEnabled() && tc.isEventEnabled()) {
            Tr.event(this, tc, "HttpDispatcher activated, id=" + properties.get(ComponentConstants.COMPONENT_ID));
        }
    }

    /**
     * DS method to deactivate this component.
     *
     * @param context
     */
    @Deactivate
    protected void deactivate(Map<String, Object> properties, int reason) {
        if (TraceComponent.isAnyTracingEnabled() && tc.isEventEnabled()) {
            Tr.event(this, tc, "HttpDispatcher deactivated, id=" + properties.get(ComponentConstants.COMPONENT_ID) + ",reason=" + reason);
        }

        // Clear this as the active HttpDispatcher instance (unless another has already replaced)
        instance.compareAndSet(this, null);
    }

    /**
     * DS method for runtime updates to configuration without stopping and
     * restarting the component.
     *
     * @param config
     */
    @Modified
    protected void modified(Map<String, Object> config) {
        if (null == config || config.isEmpty()) {
            return;
        }

        // config dictionaries are case-insensitive but case preserving per spec.
        // The default value type is String, unless a different type is indicated in metatype.

        setContextRootNotFoundMessage((String) config.get(PROP_VHOST_NOT_FOUND));

        enableWelcomePage(MetatypeUtils.parseBoolean(CONFIG_ALIAS,
                                                     PROP_ENABLE_WELCOME_PAGE,
                                                     config.get(PROP_ENABLE_WELCOME_PAGE),
                                                     true));

        setPadContextRootNotFoundMessage(MetatypeUtils.parseBoolean(CONFIG_ALIAS,
                                                                    PROP_PAD_VHOST_NOT_FOUND,
                                                                    config.get(PROP_PAD_VHOST_NOT_FOUND),
                                                                    false));

        origHeaderOrigin = MetatypeUtils.parseStringArray(CONFIG_ALIAS,
                                                          PROP_TRUSTED_PRIVATE_HEADER_ORIGIN,
                                                          config.get(PROP_TRUSTED_PRIVATE_HEADER_ORIGIN),
                                                          new String[] { "*" });

        origSensitiveHeaderOrigin = MetatypeUtils.parseStringArray(CONFIG_ALIAS,
                                                                   PROP_TRUSTED_SENSITIVE_HEADER_ORIGIN,
                                                                   config.get(PROP_TRUSTED_SENSITIVE_HEADER_ORIGIN),
                                                                   new String[] { "none" });

        parseTrustedPrivateHeaderOrigin(origHeaderOrigin, origSensitiveHeaderOrigin);
    }

    public static Boolean isWelcomePageEnabled() {
        HttpDispatcher f = instance.get();
        if (f != null)
            return f.enableWelcomePage;

        // In the absence of an Http dispatcher: don't show a welcome page. we're either on the
        // way up or (more likely) on the way down...
        return false;
    }

    private void enableWelcomePage(boolean value) {
        enableWelcomePage = value;
    }

    /**
     * Get the value for the appOrContextRootMissingMessage custom property. return null if it was not set.
     *
     * @return String the value for the appOrContextRootMissingMessage custom property, null if it was not set.
     */
    public static String getContextRootNotFoundMessage() {
        // this does not return a default string, since the caller may (and does in our case) choose to build a runtime
        // dependent string.

        HttpDispatcher f = instance.get();
        if (f != null)
            return f.appOrContextRootNotFound;

        return null;
    }

    /**
     * Set the value for the appOrContextRootMissingMessage custom property.
     *
     * @param value the new value for the appOrContextRootMissingMessage custom property.
     */
    private void setContextRootNotFoundMessage(String value) {
        appOrContextRootNotFound = value;
    }

    private void setPadContextRootNotFoundMessage(boolean value) {
        padAppOrContextRootNotFoundMessage = value;
    }

    public static boolean padContextRootNotFoundMessage() {
        HttpDispatcher f = instance.get();
        if (f != null)
            return f.padAppOrContextRootNotFoundMessage;

        // In the absence of an Http dispatcher: pad the error message if necessary
        return true;
    }

    /**
     * Parses the three private header config properties we have: trusted, trustedHeaderOrigin, and trustedSensitiveHeaderOrigin.
     *
     * This class uses these internal flags to keep track of private header behavior:
     * wcTrusted - true if any private headers are allowed; false if no private headers are allowed
     *
     * The helper class TrustedHeaderOriginLists is used to maintain lists of trusted hosts, and to perform lookups.
     *
     * @param trustedPrivateHeaderHosts   String[] of hosts to trust for non-sensitive private headers
     * @param trustedSensitiveHeaderHosts String[] of hosts to trust for sensitive private headers
     */
    private synchronized void parseTrustedPrivateHeaderOrigin(String[] trustedPrivateHeaderHosts, String[] trustedSensitiveHeaderHosts) {
        // bump the updated count every time we call this.
        updateCount.incrementAndGet();

        // If trusted=false (non-default), don't allow private headers from any host, regardless of other settings
        if (!wcTrusted) {
            if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
                Tr.debug(tc, "webcontainer trusted=false; private headers are not trusted from any host");
            }
            return;
        }
        // restore defaults
        trustedLists = new TrustedHeaderOriginLists();
        trustedLists.parseTrustedPrivateHeaderOrigin(trustedPrivateHeaderHosts, trustedSensitiveHeaderHosts);
    }

    /**
     * Create a InetAddress object from an address string. Returns null if an exception is encountered, or if
     * the passed address string is null
     *
     * @param String address
     * @return InetAddress
     */
    private static InetAddress getInetAddressFromString(String address) {
        InetAddress remoteAddr = null;
        if (address != null) {
            try {
                remoteAddr = InetAddress.getByName(address);
            } catch (UnknownHostException e) {
                if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
                    Tr.debug(tc, "exception encountered creating InetAddress from string=" + address + ": " + e);
                }
            }
        }
        return remoteAddr;
    }

    /**
     * @param hostAddr the remote address to check
     * @return true if private headers should be used (the default is true)
     */
    public static boolean usePrivateHeaders(String hostAddr) {
        InetAddress remoteAddr = getInetAddressFromString(hostAddr);
        return usePrivateHeaders(remoteAddr, null);
    }

    /**
     * @param addr     the remote address to check
     * @param HostName the remote host to check
     * @return true if private headers should be used (the default is true)
     */
    public static boolean usePrivateHeaders(InetAddress addr) {
        return usePrivateHeaders(addr, null);
    }

    /**
     * @param hostAddr   the remote address to check
     * @param headerName the name of the header to check
     * @return true if private headers should be used (the default is true when headerName is not sensitive)
     */
    public static boolean usePrivateHeaders(String hostAddr, String headerName) {
        InetAddress remoteAddr = getInetAddressFromString(hostAddr);
        return usePrivateHeaders(remoteAddr, headerName);
    }

    /**
     * @param hostAddr   the remote address to check
     * @param hostName   the remote host to check
     * @param headerName the name of the header to check
     * @return true if private headers should be used (the default is true when headerName is not sensitive)
     */
    public static boolean usePrivateHeaders(InetAddress addr, String headerName) {
        HttpDispatcher f = instance.get();

        if (f != null) {
            return f.isTrusted(addr, headerName);
        }

        // we don't know, use the default.
        if (headerName != null && HttpHeaderKeys.isSensitivePrivateHeader(headerName)) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Check to see if the source host address is one we allow for specification of private headers
     *
     *
     * @param hostAddr The source host address
     * @return true if hostAddr is a trusted source of private headers
     */
    public boolean isTrusted(String hostAddr, String headerName) {
        InetAddress remoteAddr = getInetAddressFromString(hostAddr);
        return isTrusted(remoteAddr, headerName);
    }

    /**
     * Check to see if the source host address is one we allow for specification of sensitive private headers
     *
     * @param hostAddr The source host address
     * @return true if hostAddr is a trusted source of sensitive private headers
     */
    public boolean isTrustedForSensitiveHeaders(String hostAddr) {
        InetAddress remoteAddr = getInetAddressFromString(hostAddr);
        return isTrustedForSensitiveHeaders(remoteAddr);
    }

    /**
     * Check to see if the source host address is one we allow for specification of private headers
     *
     * This takes into account the hosts defined in trustedHeaderOrigin and trustedSensitiveHeaderOrigin. Note,
     * trustedSensitiveHeaderOrigin takes precedence over trustedHeaderOrigin; so if trustedHeaderOrigin="none"
     * while trustedSensitiveHeaderOrigin="*", non-sensitive headers will still be trusted for all hosts.
     *
     * @param addr       the remote address to check
     * @param headerName the name of the header to check
     * @return true if hostAddr is a trusted source of private headers
     */
    public boolean isTrusted(InetAddress addr, String headerName) {
        if (!wcTrusted) {
            return false;
        }
        return trustedLists.isTrusted(addr, HttpHeaderKeys.isSensitivePrivateHeader(headerName));
    }

    /**
     * Check to see if the source host address is one we allow for specification of sensitive private headers
     *
     * @param addr the remote address to check
     * @return true if hostAddr is a trusted source of sensitive private headers
     */
    public boolean isTrustedForSensitiveHeaders(InetAddress addr) {
        if (!wcTrusted) {
            return false;
        }
        return trustedLists.isTrusted(addr, true);
    }

    /**
     * Access the HTTP date formatter service.
     * Make sure date format service is never null: even after this component has
     * been deactivated.
     *
     * @return HttpDateFormat
     */
    public static HttpDateFormat getDateFormatter() {
        return HttpDateFormatImpl.getInstance();
    }

    /**
     * DS method for setting the encoding utils service reference.
     *
     * @param service
     */
    @Reference(name = "encodingUtils")
    protected void setEncodingUtils(EncodingUtils service) {
        encodingSvc = service;
    }

    /**
     * DS method for removing the encoding utils service reference.
     *
     * @param service
     */
    protected void unsetEncodingUtils(EncodingUtils service) {
        if (encodingSvc == service)
            encodingSvc = null;
    }

    /**
     * Access the string encoding utils service.
     * Make sure encoding service never returns null: even after this component has
     * been deactivated.
     *
     * @return EncodingUtils
     */
    public static EncodingUtils getEncodingUtils() {
        HttpDispatcher f = instance.get();
        EncodingUtils svc = null;
        if (f != null) {
            svc = f.encodingSvc;
        }

        if (svc == null)
            svc = new EncodingUtilsImpl();

        return svc;
    }

    /**
     * DS method for setting the event service reference.
     *
     * @param service
     */
    @Reference(name = "eventService")
    protected void setEventService(EventEngine service) {
        eventSvc = service;
    }

    /**
     * DS method for removing the event service reference.
     *
     * @param service
     */
    protected void unsetEventService(EventEngine service) {
        if (eventSvc == service)
            eventSvc = null;
    }

    /**
     * Access the event engine service.
     *
     * @return EventEngine - null if not found
     */
    public static EventEngine getEventService() {
        HttpDispatcher f = instance.get();
        if (f != null)
            return f.eventSvc;

        return null;
    }

    /**
     * DS method for setting the collaboration engine reference.
     *
     * @param service
     */
    @Reference(name = "executorService")
    protected void setExecutorService(ExecutorService service) {
        executorService = service;
    }

    /**
     * DS method for removing the collaboration engine reference.
     *
     * @param service
     */
    protected void unsetExecutorService(ExecutorService service) {
        if (executorService == service)
            executorService = null;
    }

    /**
     * Access the collaboration engine.
     *
     * @return CollaborationEngine - null if not found
     */
    public static ExecutorService getExecutorService() {
        HttpDispatcher f = instance.get();
        if (f == null) {
            if (TraceComponent.isAnyTracingEnabled() && tc.isEventEnabled()) {
                Tr.event(tc, "HttpDispatcher instance not found");
            }
            return null;
        } else {
            if (TraceComponent.isAnyTracingEnabled() && tc.isEventEnabled()) {
                Tr.event(tc, "HttpDispatcher instance: " + f.toString());
            }
            return f.executorService;
        }

    }

    /**
     * Access the channel framework's {@link ApproximateTime} service.
     *
     * @return the approximate time service instance to use within the channel framework
     */
    public static long getApproxTime() {
        return QuickApproxTime.getApproxTime();
    }

    /**
     * Set the approximate time service reference.
     * This is a required reference: will be called before activation.
     * It is also dynamic: it may be replaced-- but we will always have one.
     *
     * @param ref new ApproximateTime service instance/provider
     */
    @Reference(name = "approxTime", policy = ReferencePolicy.DYNAMIC)
    protected void setApproxTime(ApproximateTime ref) {
        // do nothing: need the ref for activation of service
    }

    /**
     * Remove the reference to the approximate time service.
     * This is a required reference, will be called after deactivate.
     *
     * @param ref ApproximateTime service instance/provider to remove
     */
    protected void unsetApproxTime(ApproximateTime ref) {
        // do nothing: need the ref for activation of service
    }

    /**
     * DS method for setting the required channel framework service.
     *
     * @param bundle
     */
    @Reference(name = "chfwBundle")
    protected void setChfwBundle(CHFWBundle bundle) {
        chfw = bundle;
    }

    /**
     * DS method for removing the reference to the channel framework.
     *
     * @param bundle
     */
    protected void unsetChfwBundle(CHFWBundle bundle) {
        if (bundle == chfw) {
            chfw = null;
        }
    }

    /**
     * Access the channel framework bundle.
     *
     * @return CHFWBundle
     */
    public static CHFWBundle getCHFWBundle() {
        HttpDispatcher f = instance.get();
        if (f != null)
            return f.chfw;

        return null;
    }

    /**
     * Access the current reference to the bytebuffer pool manager.
     *
     * @return WsByteBufferPoolManager
     */
    public static WsByteBufferPoolManager getBufferManager() {
        final CHFWBundle chfw = getCHFWBundle();
        if (null == chfw) {
            return ChannelFrameworkFactory.getBufferManager();
        }
        return chfw.getBufferManager();
    }

    /**
     * Access the current reference to the channel framework instance.
     *
     * @return ChannelFramework
     */
    public static ChannelFramework getFramework() {
        final CHFWBundle chfw = getCHFWBundle();
        if (null == chfw) {
            return ChannelFrameworkFactory.getChannelFramework();
        }
        return chfw.getFramework();
    }

    @Trivial
    @Reference(policy = ReferencePolicy.DYNAMIC,
               service = VirtualHostListener.class,
               policyOption = ReferencePolicyOption.GREEDY,
               cardinality = ReferenceCardinality.OPTIONAL,
               target = "(service.pid=com.ibm.ws.webcontainer)")
    protected void setWebContainer(ServiceReference<VirtualHostListener> ref) {
        updatedWebContainer(ref);
    }

    /**
     * @param ref
     * @see #parseTrustedPrivateHeaderOrigin(String[])
     */
    protected void updatedWebContainer(ServiceReference<VirtualHostListener> ref) {

        boolean configUpdated = false;
        boolean currentConfigUpdate = false;
        boolean newTrusted = MetatypeUtils.parseBoolean("webContainer", PROP_WC_TRUSTED,
                                                        ref.getProperty(PROP_WC_TRUSTED), true);

        if (newTrusted != wcTrusted) {
            wcTrusted = newTrusted;

            // Check the value of trusted headers..
            parseTrustedPrivateHeaderOrigin(origHeaderOrigin, origSensitiveHeaderOrigin);
            // increment updateCount so listeners know the config has updated
            configUpdated = true;
        }

        String hstsPropertyValue = chooseWebContainerProperty(ref,
                                                              HttpConfigConstants.PROPNAME_HDR_HSTS_SHORTNAME,
                                                              HttpConfigConstants.PROPNAME_HDR_HSTS_FULLYQUALIFIED);

        if (hstsPropertyValue.isEmpty()) {
            if (!"noValue".equalsIgnoreCase(hstsHeader)) {
                hstsHeader = "noValue";
                configUpdated = true;
            }
        } else {
            currentConfigUpdate = parseHSTSHeader(hstsPropertyValue);
            if (!configUpdated) {
                configUpdated = currentConfigUpdate;
            }

        }

        if (configUpdated) {
            updateCount.getAndIncrement();
        }

    }

    /**
     * Web container properties sometimes accept either a short name or a fully qualified
     * name. This utility method will evaluate the fully qualified name first, and if the value is
     * null or empty, try the short name instead.
     *
     * The value returned will either be a configured value or an empty string.
     *
     * @param ref
     * @param propShortName
     * @param propFullyQualifiedName
     * @return
     */
    private String chooseWebContainerProperty(ServiceReference<VirtualHostListener> ref, String propShortName, String propFullyQualifiedName) {

        Object currentProperty = ref.getProperty(propFullyQualifiedName);
        String result = (currentProperty != null) ? ((String) currentProperty) : "";

        if (result.isEmpty()) {
            currentProperty = ref.getProperty(propShortName);
            result = (currentProperty != null) ? ((String) currentProperty) : "";

        }
        return result;
    }

    protected void unsetWebContainer(ServiceReference<VirtualHostListener> ref) {
    }

    /**
     * DS method for setting the Work Classification service reference.
     *
     * @param service
     */
    @Reference(name = "workClassifier", policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL)
    protected void setWorkClassifier(WorkClassifier service) {
        workClassifier = service;
    }

    /**
     * DS method for removing the Work Classification service reference.
     *
     * @param service
     */
    protected void unsetWorkClassifier(WorkClassifier service) {
        //TODO: need to hold this up from returning until this service is not inuse.
        if (workClassifier == service)
            workClassifier = null;
    }

    /**
     * Access to the WorkClassifier
     *
     * @return WorkClassifier - null if not found
     */
    public static WorkClassifier getWorkClassifier() {
        HttpDispatcher f = instance.get();
        if (f != null)
            return f.workClassifier;

        return null;
    }

    /**
     * @return
     */
    public static int getConfigUpdate() {
        return updateCount.get();
    }

    @Reference(service = HttpTransportBehavior.class, cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    protected synchronized void setBehavior(ServiceReference<HttpTransportBehavior> reference) {
        behaviorRef = reference;
        useEE7Streams = (Boolean) reference.getProperty(HttpTransportBehavior.USE_EE7_STREAMS);
        useIOExceptionBehavior = (Boolean) reference.getProperty(HttpTransportBehavior.USE_IOE_BEHAVIOR);
    }

    protected synchronized void unsetBehavior(ServiceReference<HttpTransportBehavior> reference) {
        if (reference == this.behaviorRef) {
            behaviorRef = null;
            useEE7Streams = false;
            useIOExceptionBehavior = null;
        }
    }

    public static boolean useEE7Streams() {
        return useEE7Streams;
    }

    public static Boolean useIOEForInboundConnectionsBehavior() {
        return useIOExceptionBehavior;
    }

    /*
     * Since Servlet 6.0 (EE10):
     * Follows RFC 6265.
     * Attributes are no longer accepted from the request Cookie header (section 4.2.2)
     * $ is used only for $Versions in the request Cookie; prefix any other will be treated as new cookie ($ is part of a cookie name)
     */
    @Reference(service = HttpBehavior.class, cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    protected synchronized void setCookiesBehavior(ServiceReference<HttpBehavior> reference) {
        cookieBehaviorRef = reference;
        useEE10Cookies = (Boolean) reference.getProperty(HttpBehavior.USE_EE10_COOKIES);
        isEE11 = reference.getProperty(HttpBehavior.IS_EE11) == null ? false : true;

        Tr.debug(tc, "setCookiesBehavior , useEE10Cookies [" + useEE10Cookies + "] , isEE11 [" + isEE11 + "]");
    }

    protected synchronized void unsetCookiesBehavior(ServiceReference<HttpBehavior> reference) {
        if (reference == this.cookieBehaviorRef) {
            cookieBehaviorRef = null;
            useEE10Cookies = false;
            isEE11 = false;

            Tr.debug(tc, "unsetCookiesBehavior , useEE10Cookies [" + useEE10Cookies + "] , isEE11 [" + isEE11 + "]");
        }
    }

    public static boolean useEE10Cookies() {
        return useEE10Cookies;
    }

    public static boolean isEE11() {
        return isEE11;
    }

    /**
     * Check the configuration to see if the HSTS header is configured to be added to
     * HTTPS responses when missing from the response.
     *
     * The possible values are:
     * Strict-Transport-Security:"max-age=31536000";
     * Strict-Transport-Security:"max-age=31536000; includeSubDomains";
     * Strict-Transport-Security:"max-age=31536000; includeSubDomains; preload";
     *
     * The requirements for the directives are as follows:
     * 1. The order of appearance of directives is not significant.
     * 2. All directives MUST appear only once in an STS header field.
     * 3. Directive names are case-insensitive
     * 4. UAs MUST ignore any STS header field containing directives, or other
     * header field value data, that does not conform to the syntax defined in
     * this specification.
     * 5. If an STS header field contains directive(s) not recognized by the
     * UA, the UA MUST ignore the unrecognized directives, and if the STS
     * header field otherwise satisfied the above requirements, the UA MUST
     * process the recognized directives.
     *
     * The max age directive value can be, optionally, quoted:
     * Strict-Transport-Security: max-age="31536000"
     *
     *
     * @param props
     */
    private boolean parseHSTSHeader(String configuredHSTSHeader) {

        boolean hasConfigError = false;
        boolean configUpdated = false;

        if (!"noValue".equalsIgnoreCase(configuredHSTSHeader)) {

            String parsedHSTSHeader = null;

            String[] tokens = configuredHSTSHeader.split(";");
            //Identifies if the three possible tokens have been parsed
            boolean foundMaxAgeToken = false, foundIncludeSubDomains = false, foundPreload = false;

            int parsedMaxAgeValue = -1;
            boolean shouldQuoteMaxAgeValue = false;

            for (String token : tokens) {

                if (hasConfigError) {
                    break;
                }

                if ((TraceComponent.isAnyTracingEnabled()) && (tc.isDebugEnabled())) {
                    Tr.debug(tc, "HSTS Header Config:", "parsing token found:  [" + token + "]");
                }

                if (token.toLowerCase().contains("max-age")) {
                    try {
                        if (foundMaxAgeToken) {
                            //Not first time we find this token. Web Container doesn't
                            //throw error, just picks first one so do the same here.
                            break;
                        }
                        foundMaxAgeToken = true;

                        String[] tok = token.split("=");
                        if (tok.length == 2) {
                            tok[1] = tok[1].trim();
                            if ((tok[1].startsWith("\"")) && (tok[1].endsWith("\""))) {
                                shouldQuoteMaxAgeValue = true;
                                tok[1] = tok[1].replace("\"", "");
                            }
                            parsedMaxAgeValue = Integer.parseInt(tok[1].trim());
                            if (parsedMaxAgeValue < 0) {
                                Tr.warning(tc, "config.hsts.invalid.max.age.value", token);
                                hasConfigError = true;
                            }
                        } else {
                            Tr.warning(tc, "config.hsts.invalid.max.age.value", token);
                            hasConfigError = true;
                        }

                    } catch (NumberFormatException e) {

                        Tr.warning(tc, "config.hsts.invalid.max.age.value", token);

                        hasConfigError = true;
                        break;

                    }
                }

                else if ("preload".equalsIgnoreCase(token.trim())) {
                    if (!foundPreload) {
                        //Not first time we find this token. Web Container chooses
                        //first found token, so skip.
                        foundPreload = true;
                    }

                }

                else if ("includeSubDomains".equalsIgnoreCase(token.trim())) {
                    if (!foundIncludeSubDomains) {
                        foundIncludeSubDomains = true;
                    }

                }

                else {
                    //Not-worthy of warning, but log unrecognized token, log that it will be ignored
                    if ((TraceComponent.isAnyTracingEnabled()) && (tc.isDebugEnabled())) {
                        Tr.debug(tc, "HSTS Header Config:", "The token [" + token + "] is not a recognized HSTS token. Value is ignored.");
                    }
                }

            }

            //Done parsing, if no errors construct the HSTS header
            if (!hasConfigError) {
                if (foundMaxAgeToken) {

                    parsedHSTSHeader = (shouldQuoteMaxAgeValue) ? "max-age=" + "\"" + parsedMaxAgeValue + "\"" : "max-age=" + parsedMaxAgeValue;

                } else {
                    Tr.warning(tc, "config.hsts.missing.max.age", configuredHSTSHeader);
                    hasConfigError = true;
                }
            }

            //If no errors have be found at this point, add the two optional
            //attributes if present. Then, update the class level hstsHeader
            //with the new value.
            if (!hasConfigError) {

                if (foundIncludeSubDomains) {
                    parsedHSTSHeader = parsedHSTSHeader + "; includeSubDomains";
                }

                if (foundPreload) {
                    parsedHSTSHeader = parsedHSTSHeader + "; preload";
                }

                if (!hstsHeader.equalsIgnoreCase(parsedHSTSHeader)) {
                    configUpdated = true;
                    hstsHeader = parsedHSTSHeader;
                }
            }

        }

        if (hasConfigError) {
            if (!"noValue".equalsIgnoreCase(hstsHeader)) {
                configUpdated = true;
                hstsHeader = "noValue";
            }
        }

        if ((TraceComponent.isAnyTracingEnabled()) && (tc.isDebugEnabled())) {
            Tr.debug(tc, "HSTS Header Config",
                     "Successful Parse [" + (!hasConfigError) + "] Value is set to: [" + hstsHeader + "]");
        }
        return configUpdated;
    }

    /**
     * Specified the configured HSTS header to add on HTTPS responses when the HSTS header is
     * missing.
     *
     * @return
     */
    public static String getHSTS() {

        return (("noValue".equalsIgnoreCase(hstsHeader)) ? null : hstsHeader);
    }
}
