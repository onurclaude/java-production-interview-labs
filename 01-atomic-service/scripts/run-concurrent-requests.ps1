<#
.SYNOPSIS
  Belirtilen endpoint'e gerçek, eşzamanlı HTTP POST request'leri gönderir ve sonuçları özetler.
  Windows PowerShell 5.1 ve PowerShell 7 ile çalışır; ek araç gerektirmez (.NET HttpClient).

.EXAMPLE
  .\run-concurrent-requests.ps1 -Url http://localhost:8081/api/labs/atomic/plain-int
  .\run-concurrent-requests.ps1 -Url http://localhost:8081/api/labs/atomic/cas -Requests 100 -Concurrency 50
  .\run-concurrent-requests.ps1 -Url "http://localhost:8081/api/labs/atomic/leak/bad?fail=true" -Requests 20 -Concurrency 20
  # Multi-instance: request'ler URL'lere round-robin dağıtılır
  .\run-concurrent-requests.ps1 -Url http://localhost:8081/api/labs/atomic/cas,http://localhost:8082/api/labs/atomic/cas,http://localhost:8083/api/labs/atomic/cas -Requests 90 -Concurrency 90

.NOTES
  Request'ler "concurrency" büyüklüğünde dalgalar halinde gönderilir: bir dalgadaki tüm request'ler
  aynı anda başlatılır, dalga bitince sonraki dalga başlar.
  Script çalışmazsa: powershell -ExecutionPolicy Bypass -File .\run-concurrent-requests.ps1 -Url ...
#>
param(
    [Parameter(Mandatory = $true)][string[]]$Url,
    [int]$Requests = 50,
    [int]$Concurrency = 50
)

Add-Type -AssemblyName System.Net.Http
# .NET Framework varsayılan olarak host başına 2 eşzamanlı bağlantıya izin verir; bu, yükü sessizce seri hale getirirdi.
[System.Net.ServicePointManager]::DefaultConnectionLimit = 1000

$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(60)

Write-Host "==> $Requests POST request, concurrency=$Concurrency"
$Url | ForEach-Object { Write-Host "    $_" }

$codes = New-Object System.Collections.Generic.List[string]
$watch = [System.Diagnostics.Stopwatch]::StartNew()

for ($sent = 0; $sent -lt $Requests; $sent += $Concurrency) {
    $waveSize = [Math]::Min($Concurrency, $Requests - $sent)
    $tasks = @()
    for ($i = 0; $i -lt $waveSize; $i++) {
        $target = $Url[($sent + $i) % $Url.Count]
        $tasks += $client.PostAsync($target, (New-Object System.Net.Http.StringContent("")))
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
$labels = @{
    "200" = "ACCEPTED (provider'a gitti, başarılı)"
    "503" = "REJECTED (limit dolu)"
    "502" = "PROVIDER_FAILED"
    "000" = "CONNECTION ERROR (uygulama çalışıyor mu?)"
}
$codes | Group-Object | Sort-Object Name | ForEach-Object {
    Write-Host ("    {0,5} x {1} {2}" -f $_.Count, $_.Name, $labels[$_.Name])
}

Write-Host ""
Write-Host "==> Stats"
foreach ($u in $Url) {
    $base = $u.Substring(0, $u.IndexOf("/api/"))
    Write-Host "--- $base/api/labs/atomic/stats"
    Write-Host $client.GetStringAsync("$base/api/labs/atomic/stats").GetAwaiter().GetResult()
}
$client.Dispose()
