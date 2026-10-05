<#
.SYNOPSIS
  Belirtilen endpoint'e gerçek, eşzamanlı HTTP (POST veya GET) request'leri gönderir ve sonuçları özetler.
  Windows PowerShell 5.1 ve PowerShell 7 ile çalışır; ek araç gerektirmez (.NET HttpClient).
  02-concurrency-service'teki lab'lar genelde TEK bir HTTP çağrısı içinde kendi concurrency'sini
  (Platform/Virtual/Executor thread'leri) yönetir; bu script asıl olarak Lab 10/11 (ReadWriteLock/
  StampedLock) gibi "many HTTP client isteği aynı anda cache'e çarpsın" senaryoları için kullanışlıdır.

.EXAMPLE
  .\run-concurrent-requests.ps1 -Url http://localhost:8082/api/labs/locks/read-write/price/P1 -Method GET -Requests 30 -Concurrency 30
  .\run-concurrent-requests.ps1 -Url http://localhost:8082/api/labs/aba/bad -Method POST -Requests 10 -Concurrency 10

.NOTES
  Request'ler "concurrency" büyüklüğünde dalgalar halinde gönderilir.
  Script çalışmazsa: powershell -ExecutionPolicy Bypass -File .\run-concurrent-requests.ps1 -Url ...
#>
param(
    [Parameter(Mandatory = $true)][string]$Url,
    [ValidateSet("GET", "POST")][string]$Method = "POST",
    [int]$Requests = 30,
    [int]$Concurrency = 30
)

Add-Type -AssemblyName System.Net.Http
[System.Net.ServicePointManager]::DefaultConnectionLimit = 1000

$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(60)

Write-Host "==> $Requests $Method request, concurrency=$Concurrency -> $Url"

$codes = New-Object System.Collections.Generic.List[string]
$watch = [System.Diagnostics.Stopwatch]::StartNew()

for ($sent = 0; $sent -lt $Requests; $sent += $Concurrency) {
    $waveSize = [Math]::Min($Concurrency, $Requests - $sent)
    $tasks = @()
    for ($i = 0; $i -lt $waveSize; $i++) {
        if ($Method -eq "GET") {
            $tasks += $client.GetAsync($Url)
        } else {
            $tasks += $client.PostAsync($Url, (New-Object System.Net.Http.StringContent("{}", [System.Text.Encoding]::UTF8, "application/json")))
        }
    }
    foreach ($task in $tasks) {
        try {
            $codes.Add([string][int]$task.GetAwaiter().GetResult().StatusCode)
        } catch {
            $codes.Add("000")
        }
    }
}
$watch.Stop()

Write-Host ""
Write-Host "==> HTTP status dağılımı ($($watch.ElapsedMilliseconds) ms)"
$codes | Group-Object | Sort-Object Name | ForEach-Object {
    Write-Host ("    {0,5} x {1}" -f $_.Count, $_.Name)
}
$client.Dispose()
