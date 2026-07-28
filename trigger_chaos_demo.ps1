# PowerShell script to trigger a live chaos demo scenario

Write-Host "Step 1: Enabling chaos on payment-service (port 8083)..."
try {
    $chaosResponse = Invoke-RestMethod -Uri "http://localhost:8083/chaos/enable" -Method Post
    Write-Host "Response: $chaosResponse"
} catch {
    Write-Host "Error enabling chaos: $_"
    exit 1
}

Write-Host "`nStep 2: Sending 5 HTTP requests to checkout-api (port 8081) to propagate cascading failures..."
for ($i = 1; $i -le 5; $i++) {
    Write-Host "Sending request #$i..."
    $start = Get-Date
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:8081" -Method Get -TimeoutSec 10
        $duration = ((Get-Date) - $start).TotalSeconds
        Write-Host "Result #${i}: Success in $duration seconds -> $resp"
    } catch {
        $duration = ((Get-Date) - $start).TotalSeconds
        Write-Host "Result #${i}: FAILED in $duration seconds -> $_"
    }
    Start-Sleep -Seconds 1
}

Write-Host "`nDemo traffic finished!"
Write-Host "To view results in your terminal, run:"
Write-Host "  - Active Alerts: Invoke-RestMethod http://localhost:5000/v1/alerts"
Write-Host "  - Topology Graph: Invoke-RestMethod http://localhost:5000/v1/graph"
Write-Host "`nTo disable chaos later, run:"
Write-Host "  Invoke-RestMethod -Uri http://localhost:8083/chaos/disable -Method Post"
