# PowerShell Keep-Alive Harness for causa-test-services

Write-Host "Starting microservices test harness background jobs..."
$job1 = Start-Job -Name "checkout-api" -ScriptBlock { cd $using:PSScriptRoot; java -jar .\target\causa-test-services-0.0.1-SNAPSHOT.jar --server.port=8081 --service.name=checkout-api }
$job2 = Start-Job -Name "order-service" -ScriptBlock { cd $using:PSScriptRoot; java -jar .\target\causa-test-services-0.0.1-SNAPSHOT.jar --server.port=8082 --service.name=order-service }
$job3 = Start-Job -Name "payment-service" -ScriptBlock { cd $using:PSScriptRoot; java -jar .\target\causa-test-services-0.0.1-SNAPSHOT.jar --server.port=8083 --service.name=payment-service }
$job4 = Start-Job -Name "inventory-service" -ScriptBlock { cd $using:PSScriptRoot; java -jar .\target\causa-test-services-0.0.1-SNAPSHOT.jar --server.port=8084 --service.name=inventory-service }

Write-Host "Jobs launched:"
Get-Job

# Enter keep-alive loop
try {
    while ($true) {
        # Check for any failed jobs and retrieve output
        $jobs = Get-Job
        foreach ($j in $jobs) {
            if ($j.State -eq "Failed" -or $j.State -eq "Stopped") {
                Write-Host "Job $($j.Name) changed state to $($j.State). Output logs:"
                Receive-Job -Job $j
            }
        }
        Start-Sleep -Seconds 2
    }
} finally {
    Write-Host "Intercepted shutdown signal. Stopping all background jobs..."
    Get-Job | Stop-Job -ErrorAction SilentlyContinue
    Get-Job | Remove-Job -Force -ErrorAction SilentlyContinue
    Write-Host "Cleanup completed."
}
