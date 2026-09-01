# CAUSA Test Services (Phase 3)

Real, instrumented microservices used to generate live trace/log/metric data for testing `Causa-backend` end-to-end — as opposed to the frontend's built-in mock data layer. Four services (`checkout-api`, `order-service`, `payment-service`, `inventory-service`) are simulated by launching the **same Spring Boot jar four times** with different `--server.port` and `--service.name` flags, each manually instrumented with OpenTelemetry and **W3C trace context propagation** so calls between them produce real, correlated distributed traces.

---

## Project Repositories

| Repo | What it is |
|---|---|
| **Causa-test-services** (this repo) | Real instrumented microservices generating live telemetry for testing |
| [Causa](https://github.com/Pranjall-Gupta/Causa) | React frontend — dashboards, topology graph, RCA views |
| [Causa-backend](https://github.com/Pranjall-Gupta/Causa-backend) | Spring Boot backend — OTLP ingestion, dynamic topology, anomaly detection, heuristic RCA scoring |
| [Causa-plugin-java](https://github.com/soham-kolhe/Causa-plugin-java) | Java plugin developers add to their own services to emit data to CAUSA |

> **Repo layout requirement**: `Causa-test-services` and `Causa-backend` must be cloned as sibling folders (`.../causa-backend/`, `.../causa-test-services/`) — the build script (`run_all_services.ps1`) invokes Maven from `causa-backend`'s `tools/` directory rather than bundling its own.

---

## How It Works

### Codebase Map
- `src/main/java/com/causa/testservices/`
  - `CausaTestServicesApplication` — Spring Boot entry point; the same jar boots as any of the 4 services depending on the `--service.name` flag it's launched with
  - `controller/ServiceController.java` — the request-handling logic simulating each service's behavior, including the chaos toggle endpoints (`/chaos/enable`, `/chaos/disable`)
  - `otel/OTelConfig.java` — configures the OpenTelemetry SDK/exporter
  - `otel/OTelTraceFilter.java` — intercepts *incoming* requests to extract/continue the trace context
  - `otel/OTelRestTemplateInterceptor.java` — intercepts *outgoing* calls between services to propagate the W3C trace context downstream, so a single request across all 4 services shares one `traceId`

### Ports
| Service | Port |
|---|---|
| checkout-api | 8081 |
| order-service | 8082 |
| payment-service | 8083 |
| inventory-service | 8084 |

Telemetry is sent to `causa-backend`, expected on **port 5000**.

---

## Running the Harness

Build + start all 4 services in the background:
```powershell
.\run_all_services.ps1
```
This auto-cleans any existing jobs/port bindings first, rebuilds via Maven, then launches all 4 as PowerShell background jobs.

Stop everything (kills the jobs and force-frees ports 8081–8084 as a fallback):
```powershell
.\run_all_services.ps1 -Stop
```

Alternative: **keep-alive harness** — same 4 services, but stays in the foreground monitoring job health and printing logs if any service crashes (Ctrl+C to stop and clean up):
```powershell
.\run_harness.ps1
```

---

## Chaos Demo

`trigger_chaos_demo.ps1` demonstrates the full failure-detection pipeline live:

1. Enables chaos mode on `payment-service` (`POST http://localhost:8083/chaos/enable`)
2. Sends 5 requests to `checkout-api` (`http://localhost:8081`), which calls downstream through `order-service` → `payment-service`, cascading the induced failure upstream
3. Prints commands to check the results directly against the backend:
   ```powershell
   Invoke-RestMethod http://localhost:5000/v1/alerts
   Invoke-RestMethod http://localhost:5000/v1/graph
   ```
4. Disable chaos afterward:
   ```powershell
   Invoke-RestMethod -Uri http://localhost:8083/chaos/disable -Method Post
   ```

Run this after `run_all_services.ps1` (or `run_harness.ps1`) and `Causa-backend` are both up, to generate a real critical alert and RCA trajectory instead of relying on the frontend's mock data.

---

## Prerequisites
- Java + Maven (via `causa-backend`'s bundled Maven, see repo layout note above)
- `Causa-backend` running on port 5000
