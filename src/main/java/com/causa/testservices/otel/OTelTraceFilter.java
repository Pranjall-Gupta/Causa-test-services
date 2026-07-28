package com.causa.testservices.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OTelTraceFilter implements Filter {

    @Autowired
    private OpenTelemetry openTelemetry;

    @Value("${service.name:unknown-service}")
    private String serviceName;

    private static final TextMapGetter<HttpServletRequest> getter = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpServletRequest carrier) {
            return () -> carrier.getHeaderNames().asIterator();
        }

        @Override
        public String get(HttpServletRequest carrier, String key) {
            return carrier.getHeader(key);
        }
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        // Skip paths like chaos toggles or browser checks to prevent messy telemetry
        if (path.contains("/chaos/") || path.contains("/favicon")) {
            chain.doFilter(request, response);
            return;
        }

        // Extract context from request headers
        Context parentContext = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), httpRequest, getter);

        Tracer tracer = openTelemetry.getTracer("com.causa.testservices");
        Span span = tracer.spanBuilder("HTTP " + httpRequest.getMethod() + " " + path)
                .setParent(parentContext)
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            chain.doFilter(request, response);
            
            int status = httpResponse.getStatus();
            if (status >= 500) {
                span.setStatus(StatusCode.ERROR, "HTTP Status " + status);
            } else {
                span.setStatus(StatusCode.OK);
            }
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());
            throw t;
        } finally {
            span.end();
        }
    }
}
