param(
    [int]$Port = 8765,
    [string]$Package = "fi.aalto.radio"
)

$ErrorActionPreference = "Stop"

function Section([string]$Text) {
    Write-Host ""
    Write-Host "=== $Text ===" -ForegroundColor Cyan
}

function Require([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Komentoa '$Name' ei loydy PATHista."
    }
}

Section "Aalto deterministic fallback test"
Require adb
Require git

$branch = (git branch --show-current).Trim()
Write-Host "Branch: $branch"
if ($branch -ne "stream-reliability-persistence") {
    throw "Vaihda ensin branchille stream-reliability-persistence."
}

$devices = @(adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" })
if ($devices.Count -ne 1) {
    throw "Tarvitaan tasan yksi ADB-laite. Tarkista: adb devices"
}
Write-Host "ADB: $($devices[0])" -ForegroundColor Green

$pythonExe = $null
$pythonArgs = @()
if (Get-Command py -ErrorAction SilentlyContinue) {
    $pythonExe = "py"
    $pythonArgs = @("-3")
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    $pythonExe = "python"
} else {
    throw "Pythonia ei loydy (py/python)."
}

$testDir = Join-Path $env:TEMP "aalto-fallback-test"
New-Item -ItemType Directory -Force -Path $testDir | Out-Null

$playlistPath = Join-Path $testDir "fallback.m3u"
@"
#EXTM3U
#EXTINF:-1,Aalto broken primary
http://127.0.0.1:$Port/definitely-dead-stream
#EXTINF:-1,Aalto working fallback
https://icecast.live.yle.fi/radio/YleX/icecast.audio
"@ | Set-Content -Path $playlistPath -Encoding utf8

Section "Local playlist server"
$args = @()
$args += $pythonArgs
$args += @("-m","http.server","$Port","--bind","127.0.0.1","--directory",$testDir)
$server = Start-Process -FilePath $pythonExe -ArgumentList $args -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 1

try {
    Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$Port/fallback.m3u" | Out-Null
} catch {
    Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
    throw "Paikallinen testipalvelin ei kaynnistynyt."
}

adb reverse "tcp:$Port" "tcp:$Port" | Out-Null
$url = "http://127.0.0.1:$Port/fallback.m3u"
Set-Clipboard $url
adb logcat -c
adb shell monkey -p $Package -c android.intent.category.LAUNCHER 1 | Out-Null

Section "TEST 1 - stream fallback"
Write-Host "Testiosoite kopioitu leikepoydalle:"
Write-Host $url -ForegroundColor Yellow
Write-Host ""
Write-Host "Puhelimessa:"
Write-Host "  1. Aalto -> Haku -> lisaa oma asema"
Write-Host "  2. Liita stream-osoite"
Write-Host "  3. Nimeksi FALLBACK TEST"
Write-Host "  4. Tallenna ja kaynnista asema"
Write-Host ""
Write-Host "ODOTETTU: rikkinainen ensimmainen URL ohitetaan ja YleX alkaa soida automaattisesti."
Read-Host "Kun testi on tehty, paina Enter"

$logFile = Join-Path ([Environment]::GetFolderPath("Desktop")) "aalto-fallback-log.txt"
adb logcat -d | Set-Content -Path $logFile -Encoding utf8
Write-Host "Logi: $logFile"

Section "TEST 2 - network recovery"
$answer = Read-Host "Tehdaanko 20 s lentotilatesti? (k/e)"
if ($answer -match '^[kKyY]') {
    adb shell cmd connectivity airplane-mode enable | Out-Null
    Start-Sleep -Seconds 20
    adb shell cmd connectivity airplane-mode disable | Out-Null
    Write-Host "ODOTETTU: radio palautuu ilman uutta aseman valintaa."
    Read-Host "Kun tarkistus on tehty, paina Enter"
}

Section "TEST 3 - last station cold start"
Write-Host "Valitse Aallossa tavallinen asema ja laita se soimaan."
Read-Host "Kun asema soi, paina Enter"
adb shell am force-stop $Package
Start-Sleep -Seconds 2
adb shell monkey -p $Package -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "ODOTETTU: Aalto avautuu samalle viimeksi kuunnellulle asemalle."
Read-Host "Kun tarkistus on tehty, paina Enter"

adb reverse --remove "tcp:$Port" 2>$null
if ($server -and -not $server.HasExited) {
    Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
}

Section "Valmis"
Write-Host "TEST 1: YleX automaattisesti kuuluviin = fallback PHYSICAL PASS"
Write-Host "TEST 2: verkon palattua radio palautui = recovery PHYSICAL PASS"
Write-Host "TEST 3: viimeinen asema avautui = restore PHYSICAL PASS"
