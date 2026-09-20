<#
.SYNOPSIS
    Aallon akkutesti: mittaa akun varauksen ennen ja jälkeen sekä kerää
    Androidin akkutilastot.

.DESCRIPTION
    Puhelimen on oltava irti laturista, joten yhdista se langattomalla
    virheenkorjauksella (Kehittajaasetukset -> Langaton virheenkorjaus):
        adb pair <ip>:<parituportti>
        adb connect <ip>:<portti>

    Skripti nollaa tilastot, odottaa annetun ajan ja tallentaa tulokset.
    Kaynnista radio soimaan ja sammuta naytto heti kun skripti kaynnistyy.

.EXAMPLE
    .\scripts\akkutesti.ps1 -Minuutteja 60 -Nimi bluetooth
#>
param(
    [int]$Minuutteja = 60,
    [string]$Nimi = "testi",
    [string]$Paketti = "fi.aalto.radio"
)

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }

function Akku([string]$kentta) {
    (& $adb shell dumpsys battery) -split "`n" |
        Where-Object { $_ -match "^\s*$kentta" } |
        ForEach-Object { ($_ -split ":")[1].Trim() } |
        Select-Object -First 1
}

$laite = (& $adb devices) -split "`n" | Where-Object { $_ -match "\sdevice$" }
if (-not $laite) {
    Write-Host "Puhelinta ei loydy. Yhdista langattomasti: adb connect <ip>:<portti>" -ForegroundColor Red
    exit 1
}

$usb = Akku "USB powered"
$ac = Akku "AC powered"
if ($usb -eq "true" -or $ac -eq "true") {
    Write-Host "Puhelin on kiinni laturissa. Irrota kaapeli, muuten akku ei laske." -ForegroundColor Red
    exit 1
}

$alku = [int](Akku "level")
Write-Host "Akku alussa: $alku %" -ForegroundColor Cyan
& $adb shell dumpsys batterystats --reset | Out-Null
Write-Host "Tilastot nollattu. Kaynnista radio ja sammuta naytto nyt." -ForegroundColor Yellow

$loppuAika = (Get-Date).AddMinutes($Minuutteja)
while ((Get-Date) -lt $loppuAika) {
    $jaljella = [int]($loppuAika - (Get-Date)).TotalMinutes
    Write-Progress -Activity "Akkutesti kaynnissa" -Status "$jaljella min jaljella" -PercentComplete ((($Minuutteja - $jaljella) / $Minuutteja) * 100)
    Start-Sleep -Seconds 30
}

$loppu = [int](Akku "level")
$kulutus = $alku - $loppu
$tiedosto = Join-Path (Get-Location) "akku-$Nimi.txt"
& $adb shell dumpsys batterystats $Paketti | Out-File -Encoding utf8 $tiedosto

$yhteenveto = @(
    "Aalto akkutesti: $Nimi",
    "Kesto: $Minuutteja min",
    "Akku alussa: $alku %",
    "Akku lopussa: $loppu %",
    "Kulutus: $kulutus prosenttiyksikkoa ($([math]::Round($kulutus * 60.0 / $Minuutteja, 1)) %/h)"
)
$yhteenveto | Out-File -Encoding utf8 -Append $tiedosto
$yhteenveto | ForEach-Object { Write-Host $_ -ForegroundColor Green }
Write-Host "Tilastot: $tiedosto"
