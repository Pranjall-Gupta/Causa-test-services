param(
    [switch]$Stop
)

$CausaApiKey = "causa_proj_a7b96665-2b1d-4b89-a3d9-abd23cd22dc5"

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
& "$PSScriptRoot\mvnw.cmd" clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nERROR: Maven build failed with exit code $LASTEXITCODE. Aborting service startup!" -ForegroundColor Red
    Write-Error "Maven build failed!"
    exit $LASTEXITCODE
}

Write-Host "Starting 4 microservices concurrently in background (PowerShell Jobs)..."
Start-Job -Name "checkout-api" -ScriptBlock { cd $using:PSScriptRoot; java -jar .\target\causa-test-services-0.0.1-SNAPSHOT.jar --server.port=8081 --service.name=checkout-api --causa.service-name=checkout-api --causa.api-key=$using:CausaApiKey } > $null
Start-Job -Name "order-service" -ScriptBlock { cd $using:PSScriptRoot; java -jar .\target\causa-test-services-0.0.1-SNAPSHOT.jar --server.port=8082 --service.name=order-service --causa.service-name=order-service --causa.api-key=$using:CausaApiKey } > $null
Start-Job -Name "payment-service" -ScriptBlock { cd $using:PSScriptRoot; java -jar .\target\causa-test-services-0.0.1-SNAPSHOT.jar --server.port=8083 --service.name=payment-service --causa.service-name=payment-service --causa.api-key=$using:CausaApiKey } > $null
Start-Job -Name "inventory-service" -ScriptBlock { cd $using:PSScriptRoot; java -jar .\target\causa-test-services-0.0.1-SNAPSHOT.jar --server.port=8084 --service.name=inventory-service --causa.service-name=inventory-service --causa.api-key=$using:CausaApiKey } > $null

Write-Host "Waiting for microservices to initialize and bind ports..."
$portsToTest = @(8081, 8082, 8083, 8084)
$timeoutSeconds = 20
$startTime = Get-Date
$allConnected = $false
$pendingPorts = @()

while (((Get-Date) - $startTime).TotalSeconds -lt $timeoutSeconds) {
    $pendingPorts = @()
    foreach ($port in $portsToTest) {
        $tcp = New-Object System.Net.Sockets.TcpClient
        try {
            $asyncResult = $tcp.BeginConnect("127.0.0.1", $port, $null, $null)
            $success = $asyncResult.AsyncWaitHandle.WaitOne(200, $false)
            if ($success -and $tcp.Connected) {
                $tcp.EndConnect($asyncResult)
            } else {
                $pendingPorts += $port
            }
        } catch {
            $pendingPorts += $port
        } finally {
            $tcp.Close()
        }
    }

    if ($pendingPorts.Count -eq 0) {
        $allConnected = $true
        break
    }

    Start-Sleep -Seconds 1
}

if ($allConnected) {
    Write-Host "`nAll 4 services successfully started!" -ForegroundColor Green
    Write-Host "  - checkout-api    : http://localhost:8081"
    Write-Host "  - order-service   : http://localhost:8082"
    Write-Host "  - payment-service : http://localhost:8083"
    Write-Host "  - inventory-service: http://localhost:8084"
} else {
    Write-Host "`nWARNING: Timed out after $timeoutSeconds seconds waiting for microservices to respond on port(s): $($pendingPorts -join ', ')" -ForegroundColor Yellow
    Write-Host "Check job logs using 'Receive-Job -Name <service-name>' for details."
}
Write-Host "`nTo stop all services later, run: .\run_all_services.ps1 -Stop"
