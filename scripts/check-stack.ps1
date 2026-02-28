param(
    [int]$LogTail = 120
)

$ErrorActionPreference = "SilentlyContinue"

Write-Host "== Docker Compose Service Status =="
docker compose ps -a
Write-Host ""

$rows = docker compose ps -a --format json | ForEach-Object { $_ | ConvertFrom-Json }
$failedServices = @($rows | Where-Object { $_.State -ne "running" } | Select-Object -ExpandProperty Service -Unique)

$serviceUrls = @(
    @{ Name = "storefront"; Url = "http://localhost:5173/" },
    @{ Name = "gateway-service"; Url = "http://localhost:8080/actuator/health" },
    @{ Name = "discovery-service"; Url = "http://localhost:8761/actuator/health" },
    @{ Name = "product-service"; Url = "http://localhost:8081/actuator/health" },
    @{ Name = "inventory-service"; Url = "http://localhost:8082/actuator/health" },
    @{ Name = "order-service"; Url = "http://localhost:8083/actuator/health" },
    @{ Name = "notification-service"; Url = "http://localhost:8084/actuator/health" },
    @{ Name = "prometheus"; Url = "http://localhost:9090/-/healthy" },
    @{ Name = "grafana"; Url = "http://localhost:3000/api/health" },
    @{ Name = "kafka-ui"; Url = "http://localhost:8090/" }
)

Write-Host "== HTTP Reachability =="
foreach ($svc in $serviceUrls) {
    if ($failedServices -contains $svc.Name) {
        Write-Host ("[SKIP] {0} -> {1} (container not running)" -f $svc.Name, $svc.Url)
        continue
    }

    try {
        $response = Invoke-WebRequest -Uri $svc.Url -Method Get -TimeoutSec 5
        if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
            Write-Host ("[OK]   {0} -> {1}" -f $svc.Name, $svc.Url)
        } else {
            Write-Host ("[FAIL] {0} -> {1} (HTTP {2})" -f $svc.Name, $svc.Url, $response.StatusCode)
        }
    } catch {
        Write-Host ("[FAIL] {0} -> {1}" -f $svc.Name, $svc.Url)
    }
}

if ($failedServices.Count -eq 0) {
    Write-Host ""
    Write-Host "All containers are running."
    exit 0
}

Write-Host ""
Write-Host "== Failed Containers (logs tail) =="
foreach ($name in $failedServices) {
    Write-Host ""
    Write-Host ("--- {0} ---" -f $name)
    docker compose logs $name --tail=$LogTail
}

exit 1
