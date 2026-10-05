<#
.SYNOPSIS
  Lab 9 (Singleton) BAD/GOOD race demo. Unit test DEGILDIR.
  Request-A uzun holdMs (2000ms) ile baslar, kisa sure sonra Request-B kisa holdMs (0ms) ile AYNI
  endpoint'e gider. BAD senaryoda Request-A, Request-B'nin field'a yazdigi degeri gozlemler.

.EXAMPLE
  .\run-singleton-race.ps1 -Endpoint bad -BaseUrl http://localhost:8086
  .\run-singleton-race.ps1 -Endpoint good -BaseUrl http://localhost:8086
#>
param(
    [ValidateSet("bad", "good")][string]$Endpoint = "bad",
    [string]$BaseUrl = "http://localhost:8086"
)

Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$url = "$BaseUrl/api/labs/singleton/$Endpoint"

Write-Host "==> [$Endpoint] Request-A (ORDER-A, holdMs=2000) gonderiliyor (arka planda)..."
$bodyA = New-Object System.Net.Http.StringContent('{"orderId":"ORDER-A","amount":100.00,"holdMs":2000}', [System.Text.Encoding]::UTF8, "application/json")
$taskA = $client.PostAsync($url, $bodyA)

Start-Sleep -Milliseconds 300
Write-Host "==> [$Endpoint] Request-B (ORDER-B, holdMs=0) gonderiliyor..."
$bodyB = New-Object System.Net.Http.StringContent('{"orderId":"ORDER-B","amount":200.00,"holdMs":0}', [System.Text.Encoding]::UTF8, "application/json")
$respB = $client.PostAsync($url, $bodyB).GetAwaiter().GetResult().Content.ReadAsStringAsync().GetAwaiter().GetResult()

$respA = $taskA.GetAwaiter().GetResult().Content.ReadAsStringAsync().GetAwaiter().GetResult()

Write-Host ""
Write-Host "==> Request-A response (beklenen requestOrderId=ORDER-A):"
Write-Host $respA
Write-Host ""
Write-Host "==> Request-B response (beklenen requestOrderId=ORDER-B):"
Write-Host $respB
Write-Host ""
if ($respA -match '"corrupted":true') {
    Write-Host "==> SONUC: Request-A'nin gozlemledigi state BOZULDU (corrupted=true) -> race condition reproduklendi."
} else {
    Write-Host "==> SONUC: corrupted=false."
}
$client.Dispose()
