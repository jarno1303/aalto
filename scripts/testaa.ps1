<#
.SYNOPSIS
    Aallon testiajo: puhdas käännös, yksikkötestit ja asennus puhelimeen.

.DESCRIPTION
    Ajaa saman asian samassa järjestyksessä joka kerta, jotta testilistan
    kohta 0 ei ole käsityötä. Pysähtyy ensimmäiseen virheeseen ja kertoo
    mistä lokin tai raportin löytää.

.EXAMPLE
    .\scripts\testaa.ps1
    Kääntää ja ajaa yksikkötestit.

.EXAMPLE
    .\scripts\testaa.ps1 -Asenna
    Kuten yllä, ja asentaa puhelimeen päivityksenä (suosikit säilyvät).

.EXAMPLE
    .\scripts\testaa.ps1 -Puhdas -Asenna
    Siivoaa build-hakemiston ensin. Hitaampi, mutta löytää vanhentuneet tulokset.

.EXAMPLE
    .\scripts\testaa.ps1 -Verkkoraportit
    Ajaa myös ne manuaaliraportit, jotka kutsuvat oikeaa Radio Browseria.

.EXAMPLE
    .\scripts\testaa.ps1 -Kieli de
    Asettaa sovellukselle saksan ja käynnistää sen uudelleen. Ei käännä eikä
    asenna mitään. Puhelimen oma kieli ei muutu. Palautus: -Kieli system
#>
param(
    [switch]$Asenna,
    [switch]$Puhdas,
    [switch]$Verkkoraportit,
    [string]$Kieli
)

$ErrorActionPreference = "Stop"
$juuri = Split-Path -Parent $PSScriptRoot
Set-Location $juuri

$gradle = Join-Path $juuri "gradlew.bat"
if (-not (Test-Path $gradle)) {
    Write-Host "gradlew.bat ei löydy hakemistosta $juuri" -ForegroundColor Red
    exit 1
}

function Invoke-Vaihe {
    [OutputType([bool])]
    param([string]$Nimi, [string[]]$Argumentit)

    Write-Host ""
    Write-Host "== $Nimi ==" -ForegroundColor Cyan
    $alku = Get-Date
    # Out-Host on tarkea: ilman sita Gradlen tuloste menee funktion
    # paluuarvoksi, jolloin kutsuja saa taysinaisen taulukon eika totuusarvoa
    # - ja epaonnistunut vaihe nayttaa onnistuneelta.
    & $gradle @Argumentit --console=plain | Out-Host
    $koodi = $LASTEXITCODE
    $kesto = [int]((Get-Date) - $alku).TotalSeconds
    if ($koodi -ne 0) {
        Write-Host ""
        Write-Host "$Nimi EPÄONNISTUI ($kesto s)" -ForegroundColor Red
        return $false
    }
    Write-Host "$Nimi OK ($kesto s)" -ForegroundColor Green
    return $true
}

# Sovelluskohtainen kieli: nopein tapa kokeilla kaannoksia ilman etta
# puhelimen oma kieli vaihdetaan edestakaisin. Vaatii Android 13:n tai uudemman.
if ($Kieli) {
    $paketti = "fi.aalto.radio"
    $arvo = if ($Kieli -eq "system" -or $Kieli -eq "oletus") { '""' } else { $Kieli }
    Write-Host "Asetetaan sovelluksen kieleksi: $Kieli" -ForegroundColor Cyan
    & adb shell cmd locale set-app-locales $paketti --locales $arvo
    if ($LASTEXITCODE -ne 0) {
        Write-Host "adb epaonnistui. Onko puhelin kiinni ja Android 13 tai uudempi?" -ForegroundColor Red
        exit 1
    }
    & adb shell am force-stop $paketti | Out-Null
    & adb shell monkey -p $paketti -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
    Write-Host "Voimassa nyt: $(& adb shell cmd locale get-app-locales $paketti)" -ForegroundColor Green
    exit 0
}

Write-Host "Aalto – testiajo" -ForegroundColor White
Write-Host "haara: $(git rev-parse --abbrev-ref HEAD 2>$null)  commit: $(git log --oneline -1 2>$null)"

$muutokset = git status --porcelain 2>$null
if ($muutokset) {
    Write-Host "Huom: työpuussa on committoimattomia muutoksia." -ForegroundColor Yellow
}

if ($Puhdas) {
    if (-not (Invoke-Vaihe "Siivous" @("clean"))) { exit 1 }
}

if (-not (Invoke-Vaihe "Käännös" @("assembleDebug"))) {
    Write-Host "Käännösvirheet näkyvät yllä riveinä, jotka alkavat 'e:'." -ForegroundColor Yellow
    exit 1
}

if (-not (Invoke-Vaihe "Yksikkötestit" @("testDebugUnitTest"))) {
    $raportti = Join-Path $juuri "app\build\reports\tests\testDebugUnitTest\index.html"
    if (Test-Path $raportti) {
        Write-Host "Raportti: $raportti" -ForegroundColor Yellow
        Start-Process $raportti
    }
    exit 1
}

if ($Verkkoraportit) {
    # Nämä kutsuvat oikeaa Radio Browseria, siksi ne eivät ole mukana muuten.
    $env:AALTO_MANUAL = "true"
    $ok = Invoke-Vaihe "Verkkoraportit" @("testDebugUnitTest", "--tests", "*ManualReportTest*", "--rerun-tasks")
    Remove-Item Env:AALTO_MANUAL -ErrorAction SilentlyContinue
    if (-not $ok) { exit 1 }
}

if ($Asenna) {
    # Päivityksenä, ei puhtaana asennuksena: se on ainoa tapa testata
    # tietokannan migraatio, ja suosikit sekä historia säilyvät.
    if (-not (Invoke-Vaihe "Asennus puhelimeen" @("installDebug"))) {
        Write-Host "Jos virhe on INSTALL_FAILED_UPDATE_INCOMPATIBLE, allekirjoitus on vaihtunut." -ForegroundColor Yellow
        Write-Host "Vasta silloin: adb uninstall fi.aalto.radio (poistaa suosikit ja historian)." -ForegroundColor Yellow
        exit 1
    }
}

Write-Host ""
Write-Host "Automaattinen osuus valmis." -ForegroundColor Green
Write-Host ""
Write-Host "Seuraavaksi käsin: docs\TESTILISTA.md" -ForegroundColor White
$otsikot = Select-String -Path (Join-Path $juuri "docs\TESTILISTA.md") -Pattern "^## " |
    ForEach-Object { $_.Line -replace "^## ", "  " }
$otsikot | Select-Object -Skip 1 | ForEach-Object { Write-Host $_ }
Write-Host ""
Write-Host "Kun lista on läpi, sano niin merkitään tunnetuksi hyväksi." -ForegroundColor White
