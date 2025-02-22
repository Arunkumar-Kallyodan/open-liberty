/*******************************************************************************
 * Copyright (c) 2022, 2023 IBM Corporation and others.
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
package io.openliberty.microprofile.telemetry10.internal.rest;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;

import io.openliberty.checkpoint.spi.CheckpointPhase;
import io.openliberty.microprofile.telemetry.internal.common.AgentDetection;
import io.openliberty.microprofile.telemetry.internal.common.rest.AbstractTelemetryClientFilter;
import io.openliberty.microprofile.telemetry.internal.interfaces.OpenTelemetryAccessor;
import io.openliberty.microprofile.telemetry.spi.OpenTelemetryInfo;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TelemetryClientFilter extends AbstractTelemetryClientFilter implements ClientRequestFilter, ClientResponseFilter {

    private static final TraceComponent tc = Tr.register(TelemetryClientFilter.class);

    private Instrumenter<ClientRequestContext, ClientResponseContext> instrumenter;
    private volatile boolean lazyCreate = false;
    private final AtomicReference<Instrumenter<ClientRequestContext, ClientResponseContext>> lazyInstrumenter = new AtomicReference<>();

    private final String configString = "otel.span.client.";

    private static final NetClientAttributesGetterImpl NET_CLIENT_ATTRIBUTES_GETTER = new NetClientAttributesGetterImpl();
    private static final HttpClientAttributesGetterImpl HTTP_CLIENT_ATTRIBUTES_GETTER = new HttpClientAttributesGetterImpl();

    TelemetryClientFilter() {
        if (!CheckpointPhase.getPhase().restored()) {
            lazyCreate = true;
        } else {
            instrumenter = createInstrumenter();
        }
    }

    private Instrumenter<ClientRequestContext, ClientResponseContext> getInstrumenter() {
        if (instrumenter != null) {
            return instrumenter;
        }
        if (lazyCreate) {
            instrumenter = lazyInstrumenter.updateAndGet((i) -> {
                if (i == null) {
                    return createInstrumenter();
                } else {
                    return i;
                }
            });
            lazyCreate = false;
        }
        return instrumenter;
    }

    private Instrumenter<ClientRequestContext, ClientResponseContext> createInstrumenter() {
        OpenTelemetryInfo openTelemetryInfo = OpenTelemetryAccessor.getOpenTelemetryInfo();
        try {
            if (openTelemetryInfo.isEnabled() && !AgentDetection.isAgentActive()) {
                InstrumenterBuilder<ClientRequestContext, ClientResponseContext> builder = Instrumenter.builder(openTelemetryInfo.getOpenTelemetry(),
                                                                                                                "Client filter",
                                                                                                                HttpSpanNameExtractor.create(HTTP_CLIENT_ATTRIBUTES_GETTER));

                Instrumenter<ClientRequestContext, ClientResponseContext> result = builder
                                .setSpanStatusExtractor(HttpSpanStatusExtractor.create(HTTP_CLIENT_ATTRIBUTES_GETTER))
                                .addAttributesExtractor(HttpClientAttributesExtractor.create(HTTP_CLIENT_ATTRIBUTES_GETTER))
                                .addAttributesExtractor(NetClientAttributesExtractor.create(NET_CLIENT_ATTRIBUTES_GETTER))
                                .buildClientInstrumenter(new ClientRequestContextTextMapSetter());
                return result;
            } else {
                return null;
            }
        } catch (Exception e) {
            Tr.error(tc, Tr.formatMessage(tc, "CWMOT5002.telemetry.error", e));
            return null;
        }
    }

    @Override
    public void filter(final ClientRequestContext request) {
        Instrumenter<ClientRequestContext, ClientResponseContext> currentInstrumenter = getInstrumenter();
        if (currentInstrumenter != null) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    try {
                        Context parentContext = Context.current();
                        if (currentInstrumenter.shouldStart(parentContext, request)) {
                            Context spanContext = currentInstrumenter.start(parentContext, request);
                            request.setProperty(configString + "context", spanContext);
                            request.setProperty(configString + "parentContext", parentContext);
                        }
                    } catch (Exception e) {
                        Tr.error(tc, Tr.formatMessage(tc, "CWMOT5002.telemetry.error", e));
                    }
                    return null;
                }
            });
        }
    }

    @Override
    public void filter(final ClientRequestContext request, final ClientResponseContext response) {
        Instrumenter<ClientRequestContext, ClientResponseContext> currentInstrumenter = getInstrumenter();
        if (currentInstrumenter != null) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    try {
                        Context spanContext = (Context) request.getProperty(configString + "context");
                        if (spanContext == null) {
                            return null;
                        }
                        try {
                            currentInstrumenter.end(spanContext, request, response, null);
                        } finally {
                            request.removeProperty(configString + "context");
                            request.removeProperty(configString + "parentContext");
                        }
                    } catch (Exception e) {
                        Tr.error(tc, Tr.formatMessage(tc, "CWMOT5002.telemetry.error", e));
                    }
                    return null;
                }
            });
        }
    }

    /**
     * @return false if OpenTelemetry is disabled
     *         Indicated by instrumenter being set to null
     * @return true when instrumenter is not null or if it is called during checkpoint
     */
    @Override
    public boolean isEnabled() {
        if (!CheckpointPhase.getPhase().restored()) {
            return true;
        }
        return getInstrumenter() != null;
    }

    private static class ClientRequestContextTextMapSetter implements TextMapSetter<ClientRequestContext> {
        @Override
        public void set(final ClientRequestContext carrier, final String key, final String value) {
            if (carrier != null) {
                carrier.getHeaders().put(key, singletonList(value));
            }
        }
    }

    private static class NetClientAttributesGetterImpl implements NetClientAttributesGetter<ClientRequestContext, ClientResponseContext> {

        @Override
        public String transport(ClientRequestContext request, ClientResponseContext response) {
            return SemanticAttributes.NetTransportValues.IP_TCP;
        }

        @Override
        public String peerName(ClientRequestContext request) {
            return request.getUri().getHost();
        }

        @Override
        public Integer peerPort(ClientRequestContext request) {
            return request.getUri().getPort();
        }
    }

    private static class HttpClientAttributesGetterImpl implements HttpClientAttributesGetter<ClientRequestContext, ClientResponseContext> {

        @Override
        public String flavor(final ClientRequestContext request, final ClientResponseContext response) {
            return null;
        }

        //Required
        @Override
        public String method(final ClientRequestContext request) {
            return request.getMethod();
        }

        //If one was sent
        @Override
        public Integer statusCode(final ClientRequestContext request, final ClientResponseContext response, @Nullable Throwable error) {
            return response.getStatus();
        }

        @Override
        public String url(final ClientRequestContext request) {
            return request.getUri().toString();
        }

        @Override
        public List<String> requestHeader(final ClientRequestContext request, final String name) {
            return request.getStringHeaders().getOrDefault(name, emptyList());
        }

        @Override
        public List<String> responseHeader(final ClientRequestContext request, final ClientResponseContext response,
                                           final String name) {
            return response.getHeaders().getOrDefault(name, emptyList());
        }
    }
}