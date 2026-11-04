# PowerShell demo script for CheckoutFlow
# ------------------------------------------------------------
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# 1️⃣ Build the whole project
Write-Host "Building Maven project..."
if (Test-Path "./mvnw") {
    ./mvnw clean package -DskipTests
} else {
    mvn clean package -DskipTests
}
Write-Host "Build complete."

# 2️⃣ Start Docker Compose
Write-Host "Resetting any previous containers..."
docker compose -f docker/docker-compose.yml down -v --remove-orphans

Write-Host "Starting Docker Compose stack..."
docker compose -f docker/docker-compose.yml up --build -d
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker Compose failed to build or start containers. Please check the error above."
    exit 1
}
Write-Host "Docker Compose launched. Waiting for services to become healthy..."

function Wait-ForHealth([string]$url, [int]$timeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-RestMethod -Uri $url -Method GET -TimeoutSec 5 -ErrorAction SilentlyContinue
            if ($resp.status -eq "UP") { return $true }
        } catch { }
        Start-Sleep -Seconds 3
    }
    return $false
}

Write-Host "Waiting for Gateway to be healthy..."
$gatewayHealth = "http://localhost:8090/actuator/health"
if (-not (Wait-ForHealth $gatewayHealth 90)) {
    Write-Warning "Gateway health check timed out. Showing docker logs for gateway:"
    docker compose -f docker/docker-compose.yml logs gateway --tail 50
    Write-Error "Gateway health check failed. See container logs above."
    exit 1
}
Write-Host "Gateway is healthy."

Write-Host "Waiting for Order Service to be healthy..."
$orderHealth = "http://localhost:8081/actuator/health"
if (-not (Wait-ForHealth $orderHealth 90)) {
    Write-Warning "Order service health check timed out. Showing docker logs for order-service:"
    docker compose -f docker/docker-compose.yml logs order-service --tail 50
    Write-Error "Order service health check failed. See container logs above."
    exit 1
}
Write-Host "Order service is healthy."

Write-Host "Waiting for Inventory Service to be healthy..."
$inventoryHealth = "http://localhost:8082/actuator/health"
if (-not (Wait-ForHealth $inventoryHealth 90)) {
    Write-Warning "Inventory service health check timed out. Showing docker logs for inventory-service:"
    docker compose -f docker/docker-compose.yml logs inventory-service --tail 50
    Write-Error "Inventory service health check failed. See container logs above."
    exit 1
}
Write-Host "Inventory service is healthy."

Write-Host "Waiting for Payment Service to be healthy..."
$paymentHealth = "http://localhost:8083/actuator/health"
if (-not (Wait-ForHealth $paymentHealth 90)) {
    Write-Warning "Payment service health check timed out. Showing docker logs for payment-service:"
    docker compose -f docker/docker-compose.yml logs payment-service --tail 50
    Write-Error "Payment service health check failed. See container logs above."
    exit 1
}
Write-Host "Payment service is healthy."

# 3️⃣ Create an order (idempotent)
$jwt = "demo-token"
$idempotencyKey = [guid]::NewGuid().ToString()
$productId = "11111111-1111-1111-1111-111111111111"

$orderPayload = @{
    items = @(
        @{ productId = $productId; quantity = 2 }
    )
} | ConvertTo-Json -Depth 4

$createOrderUrl = "http://localhost:8090/api/orders"
Write-Host "Creating order with Idempotency-Key=$idempotencyKey"

$headers = @{
    "Authorization" = "Bearer $jwt"
    "Idempotency-Key" = $idempotencyKey
    "Content-Type" = "application/json"
    "X-User-Id" = [guid]::NewGuid().ToString()
}

$response = Invoke-RestMethod -Uri $createOrderUrl -Method POST -Headers $headers -Body $orderPayload
$orderId = $response.orderId
Write-Host "Order created, ID = $orderId (status: $($response.status))"

# 4️⃣ Poll order status until terminal (CONFIRMED / REJECTED / CANCELLED)
$orderStatusUrl = "http://localhost:8090/api/orders/$orderId"
$terminal = @("CONFIRMED", "REJECTED", "CANCELLED")
$elapsed = 0
$status = $response.status

while ($true) {
    try {
        $orderInfo = Invoke-RestMethod -Uri $orderStatusUrl -Method GET -Headers @{ "Authorization" = "Bearer $jwt" } -TimeoutSec 5
        $status = $orderInfo.status
        Write-Host "[$(Get-Date -Format HH:mm:ss)] Order $orderId status = $status"
        if ($terminal -contains $status) { break }
    } catch {
        Write-Warning "Waiting for order status... ($($_.Exception.Message))"
    }
    Start-Sleep -Seconds 2
    $elapsed += 2
    if ($elapsed -gt 60) {
        Write-Warning "Taking longer than expected; continue polling..."
        break
    }
}

Write-Host "Final order status: $status"

Write-Host "Demo finished. To stop the stack run:"
Write-Host "   docker compose -f docker/docker-compose.yml down -v"
