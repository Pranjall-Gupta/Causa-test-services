package com.causa.testservices.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class OTelRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final OpenTelemetry openTelemetry;

    private static final TextMapSetter<HttpRequest> setter = (carrier, key, value) -> {
        if (carrier != null) {
            carrier.getHeaders().set(key, value);
        }
    };

    public OTelRestTemplateInterceptor(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Tracer tracer = openTelemetry.getTracer("com.causa.testservices");
        Span span = tracer.spanBuilder("HTTP " + request.getMethod() + " " + request.getURI().getPath())
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Inject context into headers
            openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), request, setter);
            
            ClientHttpResponse response = execution.execute(request, body);
            
            if (response.getStatusCode().is5xxServerError()) {
                span.setStatus(StatusCode.ERROR, "HTTP Server Error: " + response.getStatusCode());
            } else {
                span.setStatus(StatusCode.OK);
            }
            return response;
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());
            throw t;
        } finally {
            span.end();
        }
    }
}
