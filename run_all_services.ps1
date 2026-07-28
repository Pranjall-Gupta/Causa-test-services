param(
    [switch]$Stop
)

if ($Stop) {
    Write-Host "Stopping all causa-test-services microservices..."
    Get-Job -Name "checkout-api", "order-service", "payment-service", "inventory-service" -ErrorAction SilentlyContinue | Stop-Job -ErrorAction SilentlyContinue
    Get-Job -Name "checkout-api", "order-service", "payment-service", "inventory-service" -ErrorAction SilentlyContinue | Remove-Job -Force -ErrorAction SilentlyContinue
    
    # Fallback: Port cleanup in case they were started differently
    $ports = @(8081, 8082, 8083, 8084)
    foreach ($port in $ports) {
        $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
        if ($connections) {
            foreach ($conn in $connections) {
                $procId = $conn.OwningProcess
                if ($procId -gt 0) {
                    Write-Host "Killing process $procId listening on port $port..."
                    Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
                }
            }
        }
    }
    Write-Host "All services stopped."
    exit
}

# Auto-cleanup before starting
Write-Host "Stopping any existing background jobs or port bindings..."
Get-Job -Name "checkout-api", "order-service", "payment-service", "inventory-service" -ErrorAction SilentlyContinue | Stop-Job -ErrorAction SilentlyContinue
Get-Job -Name "checkout-api", "order-service", "payment-service", "inventory-service" -ErrorAction SilentlyContinue | Remove-Job -Force -ErrorAction SilentlyContinue

$ports = @(8081, 8082, 8083, 8084)
foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($connections) {
        foreach ($conn in $connections) {
            $procId = $conn.OwningProcess
            if ($procId -gt 0) {
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

Write-Host "Building causa-test-services project..."
& "..\causa-backend\tools\apache-maven-3.9.6\bin\mvn.cmd" clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven build failed!"
    exit $LASTEXITCODE
}

Write-Host "Starting 4 microservices concurrently in background (PowerShell Jobs)..."
Start-Job -Name "checkout-api" -ScriptBlock { cd $using:PSScriptRoot; java -jar .\target\causa-test-services-0.0.1-SNAPSHOT.jar --server.port=8081 --service.name=checkout-api } > $null
Start-Job -Name "order-service" -ScriptBlock { cd $using:PSScriptRoot; java -jar .\target\causa-test-services-0.0.1-SNAPSHOT.jar --server.port=8082 --service.name=order-service } > $null
Start-Job -Name "payment-service" -ScriptBlock { cd $using:PSScriptRoot; java -jar .\target\causa-test-services-0.0.1-SNAPSHOT.jar --server.port=8083 --service.name=payment-service } > $null
Start-Job -Name "inventory-service" -ScriptBlock { cd $using:PSScriptRoot; java -jar .\target\causa-test-services-0.0.1-SNAPSHOT.jar --server.port=8084 --service.name=inventory-service } > $null

Write-Host "`nAll 4 services successfully started!"
Write-Host "  - checkout-api    : http://localhost:8081"
Write-Host "  - order-service   : http://localhost:8082"
Write-Host "  - payment-service : http://localhost:8083"
Write-Host "  - inventory-service: http://localhost:8084"
Write-Host "`nTo stop all services later, run: .\run_all_services.ps1 -Stop"
