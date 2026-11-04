# Comprehensive Test Suite for CheckoutFlow
$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " 1. SYSTEM HEALTH CHECKS" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$services = @(
    @{ Name = "Gateway"; Url = "http://localhost:8090/actuator/health" },
    @{ Name = "Order Service"; Url = "http://localhost:8081/actuator/health" },
    @{ Name = "Inventory Service"; Url = "http://localhost:8082/actuator/health" },
    @{ Name = "Payment Service"; Url = "http://localhost:8083/actuator/health" },
    @{ Name = "Notification Service"; Url = "http://localhost:8084/actuator/health" }
)

foreach ($svc in $services) {
    try {
        $res = Invoke-RestMethod -Uri $svc.Url -Method GET -TimeoutSec 5
        Write-Host "  [$($res.status)] $($svc.Name) ($($svc.Url))" -ForegroundColor Green
    } catch {
        Write-Host "  [FAIL] $($svc.Name) - $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host " 2. TEST HAPPY PATH SAGA (CONFIRMED)" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$jwt = "demo-token"
$idempotencyKey = [guid]::NewGuid().ToString()
$productId = "11111111-1111-1111-1111-111111111111"
$headers = @{
    "Authorization" = "Bearer $jwt"
    "Idempotency-Key" = $idempotencyKey
    "Content-Type" = "application/json"
    "X-User-Id" = [guid]::NewGuid().ToString()
}
$orderPayload = @{
    items = @( @{ productId = $productId; quantity = 2 } )
} | ConvertTo-Json -Depth 4

$resp1 = Invoke-RestMethod -Uri "http://localhost:8090/api/orders" -Method POST -Headers $headers -Body $orderPayload
$orderId1 = $resp1.orderId
Write-Host "Order created: $orderId1 (Initial Status: $($resp1.status))"

# Poll status
$status = $resp1.status
$elapsed = 0
while ($status -notin @("CONFIRMED", "REJECTED", "CANCELLED") -and $elapsed -lt 30) {
    Start-Sleep -Seconds 1
    $elapsed += 1
    $info = Invoke-RestMethod -Uri "http://localhost:8090/api/orders/$orderId1" -Method GET -Headers @{ "Authorization" = "Bearer $jwt" }
    $status = $info.status
    Write-Host "  [$elapsed s] Order $orderId1 status = $status"
}

if ($status -eq "CONFIRMED") {
    Write-Host ">>> PASS: Happy path saga completed with status CONFIRMED!" -ForegroundColor Green
} else {
    Write-Host ">>> FAIL: Expected CONFIRMED but got $status" -ForegroundColor Red
}

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host " 3. TEST IDEMPOTENCY" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

Write-Host "Resending the exact same POST request with existing Idempotency-Key..."
$respDup = Invoke-RestMethod -Uri "http://localhost:8090/api/orders" -Method POST -Headers $headers -Body $orderPayload
if ($respDup.orderId -eq $orderId1) {
    Write-Host ">>> PASS: Idempotency honored! Returned existing orderId: $($respDup.orderId)" -ForegroundColor Green
} else {
    Write-Host ">>> FAIL: Generated duplicate orderId: $($respDup.orderId)" -ForegroundColor Red
}

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host " 4. TEST INSUFFICIENT STOCK (REJECTED)" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$rejectHeaders = @{
    "Authorization" = "Bearer $jwt"
    "Idempotency-Key" = [guid]::NewGuid().ToString()
    "Content-Type" = "application/json"
    "X-User-Id" = [guid]::NewGuid().ToString()
}
# Request huge quantity (99999) that exceeds available stock (100)
$rejectPayload = @{
    items = @( @{ productId = $productId; quantity = 99999 } )
} | ConvertTo-Json -Depth 4

$resp2 = Invoke-RestMethod -Uri "http://localhost:8090/api/orders" -Method POST -Headers $rejectHeaders -Body $rejectPayload
$orderId2 = $resp2.orderId
Write-Host "Created order with excess quantity: $orderId2"

$status2 = $resp2.status
$elapsed = 0
while ($status2 -notin @("CONFIRMED", "REJECTED", "CANCELLED") -and $elapsed -lt 30) {
    Start-Sleep -Seconds 1
    $elapsed += 1
    $info2 = Invoke-RestMethod -Uri "http://localhost:8090/api/orders/$orderId2" -Method GET -Headers @{ "Authorization" = "Bearer $jwt" }
    $status2 = $info2.status
    Write-Host "  [$elapsed s] Order $orderId2 status = $status2"
}

if ($status2 -eq "REJECTED") {
    Write-Host ">>> PASS: Order rejected as expected due to insufficient stock!" -ForegroundColor Green
} else {
    Write-Host ">>> FAIL: Expected REJECTED but got $status2" -ForegroundColor Red
}

Write-Host "`n=========================================" -ForegroundColor Cyan
Write-Host " 5. NOTIFICATION SERVICE AUDIT" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
docker compose -f "$PSScriptRoot/docker/docker-compose.yml" logs notification-service --tail 10
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " TEST RUN COMPLETE" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
