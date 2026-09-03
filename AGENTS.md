# CAUSA System Orientation & Agent Guide

This document serves as an orientation guide for human developers and AI coding agents (such as Antigravity) working on the **CAUSA** failure diagnosis system codebase.

---

## 1. What This Repo Is

The CAUSA system is composed of three interconnected sub-repositories operating as a full-stack telemetry ingestion, topology visualization, and root-cause analysis platform:
1. **`Causa-main` (Frontend)**: A React (v16.13) dashboard with D3.js (v5.16) topology visualization running on port `3000`.
2. **`Causa-backend-main` (Backend)**: A Spring Boot 3 (Java 21) REST service running on port `5000` backed by a persistent file-based H2 database, responsible for OpenTelemetry (OTel) data ingestion, dynamic topology compilation, background anomaly detection, and Root Cause Analysis (RCA) trajectory scoring.
3. **`Causa-test-services-main` (Test Microservices)**: A simulated 4-microservice cluster (`checkout-api`, `order-service`, `payment-service`, `inventory-service`) running on ports `8081`–`8084` equipped with OpenTelemetry instrumentation and live chaos injection endpoints.

---

## 2. Current State

The following features are fully implemented, verified, and operational in the codebase:

### Frontend (`Causa-main`)
- **Dashboard UI**: Visualizes active incident alerts, interactive D3 service node topology graphs, fault-path propagation lines (highlighted in red), detailed pod metric cards, and RCA trajectory ranking.
- **Opt-in Mock Mode**: Supports standalone frontend testing via `REACT_APP_USE_MOCK=true` in `.env`, using an Axios request interceptor (`src/mock.js`). When disabled (`false`), requests route directly to the Spring Boot backend (`http://localhost:5000`).

### Backend (`Causa-backend-main`)
- **Batch Telemetry Ingestion**: High-throughput endpoints (`/v1/traces`, `/v1/metrics`, `/v1/logs`) using `@Transactional` batch persistence (`saveAll()`) via JPA repositories.
- **Payload Validation & Security**: Pre-flight validation in `IngestionController` returning HTTP `400 Bad Request` for malformed payloads, with server-side SLF4J stack trace logging.
- **Dynamic Topology Compilation**: `TopologyService` queries spans from a rolling 15-minute window (`findByStartTimeUnixNanoBetween`) and caches built `TopologyGraph` instances in memory for 5 seconds (`CACHE_TTL_MS = 5000`).
- **Scheduled Anomaly Detection**: `AnomalyDetectionService` executes background scans every 10 seconds (`@Scheduled(fixedRate = 10000)`) evaluating 5-minute rolling window error rates (>5%) and latencies (>1500ms) to update `alertCache` with 30-minute TTL eviction.
- **RCA Trajectory Engine**: `RcaService` implements Breadth-First Search (BFS) pathfinding and distance-decay heuristic scoring to rank root cause propagation paths.
- **Persistent Data Storage & Profile Security**: Uses a file-based H2 database (`jdbc:h2:file:./data/causadb;AUTO_SERVER=TRUE`). H2 web console access is secured by default and gated behind `spring.profiles.active=dev` (`application-dev.properties`).

### Test Microservices (`Causa-test-services-main`)
- **Multi-Service Call Chain**: Simulates real inter-service calls (`checkout-api` $\rightarrow$ `order-service` $\rightarrow$ `payment-service` & `inventory-service`).
- **OTel Instrumentation**: Implements `OTelTraceFilter` for incoming server span context extraction, `OTelRestTemplateInterceptor` for outgoing client W3C context propagation, and `CustomJsonSpanExporter` for background batch exporting to `http://localhost:5000/v1/traces`.
- **Live Chaos Injection**: Exposes `/chaos/enable` and `/chaos/disable` on `payment-service` (port `8083`) to inject random latency (1.8s–2.8s) and HTTP `500` errors for end-to-end RCA testing.

---

## 3. Architecture Notes

Key source files and their responsibilities across the sub-repositories:

### Frontend (`Causa-main`)
- [`src/index.js`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-main/src/index.js): Application entry point; handles conditional loading of `src/mock.js` after static imports.
- [`src/components/Graph.js`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-main/src/components/Graph.js) & [`GraphUtils.js`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-main/src/components/GraphUtils.js): D3 topology rendering engine and automated red fault-path calculation.
- [`src/mock.js`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-main/src/mock.js) & [`mockData.js`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-main/src/mockData.js): Axios mock adapter intercepting `/v1/alerts`, `/v1/graph`, and `/v1/rca`.

### Backend (`Causa-backend-main`)
- [`IngestionController.java`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-backend-main/src/main/java/com/causa/backend/controller/IngestionController.java): REST endpoints for trace, metric, and log ingestion with input validation and SLF4J logging.
- [`IngestionService.java`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-backend-main/src/main/java/com/causa/backend/service/IngestionService.java): `@Transactional` service performing `saveAll()` batch persistence into H2 database tables.
- [`TopologyService.java`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-backend-main/src/main/java/com/causa/backend/service/TopologyService.java): Builds dynamic service/pod/alert topology graph over a 15-minute span window with 5-second TTL caching.
- [`AnomalyDetectionService.java`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-backend-main/src/main/java/com/causa/backend/service/AnomalyDetectionService.java): `@Scheduled` worker performing 10-second background anomaly scans and managing `alertCache`.
- [`RcaService.java`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-backend-main/src/main/java/com/causa/backend/service/RcaService.java): BFS graph algorithm computing distance-decay candidate root cause trajectory scores.
- [`CausaBackendApplication.java`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-backend-main/src/main/java/com/causa/backend/CausaBackendApplication.java): Main Spring Boot application annotated with `@EnableScheduling`.

### Test Services (`Causa-test-services-main`)
- [`ServiceController.java`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-test-services-main/src/main/java/com/causa/testservices/controller/ServiceController.java): Handles microservice endpoints and `/chaos/enable` / `/chaos/disable` state toggles on `payment-service`.
- [`OTelConfig.java`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-test-services-main/src/main/java/com/causa/testservices/otel/OTelConfig.java): OTel SDK initialization and asynchronous `CustomJsonSpanExporter` sending JSON payloads to `/v1/traces`.
- [`OTelTraceFilter.java`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-test-services-main/src/main/java/com/causa/testservices/otel/OTelTraceFilter.java) & [`OTelRestTemplateInterceptor.java`](file:///c:/Users/soham/OneDrive/Desktop/New%20folder/Causa-test-services-main/src/main/java/com/causa/testservices/otel/OTelRestTemplateInterceptor.java): Inbound/outbound HTTP W3C trace context propagation.

---

## 4. Known Gotchas

Developers and AI agents should be aware of the following potential pitfalls:

1. **Frontend Mock Mode & ESLint Rules (`Causa-main`)**:
   - `REACT_APP_USE_MOCK` in `.env` determines whether HTTP requests hit `src/mock.js` or the real backend. Set `REACT_APP_USE_MOCK=false` when testing integration with `causa-backend`.
   - Create React App enforces ESLint `import/first`. The conditional `if (process.env.REACT_APP_USE_MOCK === 'true') { require('./mock'); }` block in `src/index.js` **must** remain placed immediately *after* all static `import` statements, not before them.

2. **Backend Profile Gating & Scheduled Cache (`Causa-backend-main`)**:
   - H2 Console access is disabled by default in base `application.properties`. To access `/h2-console`, ensure `spring.profiles.active=dev` is active (which activates `application-dev.properties`).
   - `AnomalyDetectionService` evaluates anomalies asynchronously every 10 seconds via `@Scheduled(fixedRate = 10000)`. Newly ingested spans will not immediately reflect in `/v1/alerts` until the next scheduled scan completes.
   - `IngestionController` validates request structure pre-flight; sending non-list `resourceSpans` or null payloads will return HTTP `400 Bad Request`.

3. **Test Microservices Port Bindings & Chaos Toggle (`Causa-test-services-main`)**:
   - Calling `POST http://localhost:8083/chaos/enable` modifies static runtime state in `payment-service`, causing persistent 500 errors and delays until `POST http://localhost:8083/chaos/disable` is called.
   - Microservices bind ports `8081` through `8084`. If restarting background processes, use `.\run_all_services.ps1 -Stop` to terminate stale Java instances and free bound TCP ports.

---

## 5. What's Not Done Yet

CAUSA is actively under development. The following capabilities are planned or in progress (see `PROJECT_STATUS.md` and `BACKEND_DESIGN.md` in `Causa-backend-main` for the full cross-repo roadmap):

- **Production OTLP Receivers**: Standardizing telemetry ingestion to support native OpenTelemetry Protocol (OTLP/gRPC and OTLP/HTTP) protobuf receivers alongside JSON endpoints.
- **Authentication & RBAC**: Adding Spring Security JWT/OAuth2 authentication and role-based access control across API endpoints.
- **Persistent Production Storage**: Extending JPA platform support beyond H2 to production-grade relational (PostgreSQL / TimescaleDB) or time-series databases.
- **ML-Assisted RCA Scoring**: Enhancing the current heuristic BFS scoring model with historical incident pattern matching and log anomaly correlation.
- **Cloud-Native Deployment**: Packaging container definitions, Kubernetes Helm charts, and infrastructure-as-code manifests for cluster deployments.
