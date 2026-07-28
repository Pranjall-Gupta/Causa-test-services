package com.causa.testservices.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Configuration
public class OTelConfig {

    @Value("${service.name:unknown-service}")
    private String serviceName;

    @Bean
    public OpenTelemetry openTelemetry() {
        // Register our custom JSON exporter
        SpanExporter exporter = new CustomJsonSpanExporter(serviceName);

        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), serviceName)));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .setResource(resource)
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }

    @Bean
    public RestTemplate restTemplate(OpenTelemetry openTelemetry) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new OTelRestTemplateInterceptor(openTelemetry));
        return restTemplate;
    }

    // Custom SpanExporter that serializes telemetry as application/json compatible with causa-backend
    public static class CustomJsonSpanExporter implements SpanExporter {
        private final String serviceName;
        private final HttpClient httpClient = HttpClient.newHttpClient();
        private final ObjectMapper objectMapper = new ObjectMapper();

        public CustomJsonSpanExporter(String serviceName) {
            this.serviceName = serviceName;
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            try {
                Map<String, Object> payload = new HashMap<>();
                List<Map<String, Object>> resourceSpans = new ArrayList<>();

                Map<String, Object> resourceSpan = new HashMap<>();
                Map<String, Object> resource = new HashMap<>();
                List<Map<String, Object>> attributes = new ArrayList<>();

                Map<String, Object> serviceNameAttr = new HashMap<>();
                serviceNameAttr.put("key", "service.name");
                Map<String, Object> valMap = new HashMap<>();
                valMap.put("stringValue", serviceName);
                serviceNameAttr.put("value", valMap);
                attributes.add(serviceNameAttr);

                resource.put("attributes", attributes);
                resourceSpan.put("resource", resource);

                List<Map<String, Object>> scopeSpans = new ArrayList<>();
                Map<String, Object> scopeSpan = new HashMap<>();
                List<Map<String, Object>> spansList = new ArrayList<>();

                for (SpanData span : spans) {
                    Map<String, Object> spanMap = new HashMap<>();
                    spanMap.put("traceId", span.getTraceId());
                    spanMap.put("spanId", span.getSpanId());
                    
                    if (span.getParentSpanContext().isValid()) {
                        spanMap.put("parentSpanId", span.getParentSpanContext().getSpanId());
                    } else {
                        spanMap.put("parentSpanId", null);
                    }
                    
                    spanMap.put("name", span.getName());
                    spanMap.put("kind", "SPAN_KIND_" + span.getKind().name());
                    spanMap.put("startTimeUnixNano", String.valueOf(span.getStartEpochNanos()));
                    spanMap.put("endTimeUnixNano", String.valueOf(span.getEndEpochNanos()));

                    Map<String, Object> statusMap = new HashMap<>();
                    int code = span.getStatus().getStatusCode() == io.opentelemetry.api.trace.StatusCode.ERROR ? 2 : 1;
                    statusMap.put("code", code);
                    if (span.getStatus().getDescription() != null && !span.getStatus().getDescription().isEmpty()) {
                        statusMap.put("message", span.getStatus().getDescription());
                    }
                    spanMap.put("status", statusMap);

                    spansList.add(spanMap);
                }

                scopeSpan.put("spans", spansList);
                scopeSpans.add(scopeSpan);
                resourceSpan.put("scopeSpans", scopeSpans);
                resourceSpans.add(resourceSpan);
                payload.put("resourceSpans", resourceSpans);

                byte[] body = objectMapper.writeValueAsBytes(payload);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:5000/v1/traces"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();

                // Send OTel spans asynchronously in background thread to avoid blocking client web responses
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

                return CompletableResultCode.ofSuccess();
            } catch (Exception e) {
                return CompletableResultCode.ofFailure();
            }
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
