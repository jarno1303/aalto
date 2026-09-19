<#
.SYNOPSIS
    Ottaa Aallosta kuvakaappaukset valituista näkymistä puhelimelta.

.DESCRIPTION
    Puoliautomaattinen: skripti kertoo, mikä näkymä avataan, ja ottaa kuvan
    kun painat Enter. Näin kuvat onnistuvat, vaikka sovelluksen asettelu
    muuttuu, eikä skripti napauta mitään puhelimen puolesta.

    Kuvat tallentuvat kansioon kuvakaappaukset\<päivä_aika>\ projektin
    juureen. Kansio on .gitignoressa.

.PARAMETER Tumma
    Ottaa jokaisesta näkymästä myös tumman teeman kuvan. Aallon teeman pitää
    olla asetuksissa "Järjestelmän mukaan". Puhelimen oma teema palautetaan
    lopuksi.

.PARAMETER Siisti
    Siisti tilapalkki (kello 12.00, täysi akku ja wifi, ei ilmoituksia)
    Androidin demotilalla. Hyvä Play-kaupan kuviin. Palautetaan lopuksi.

.PARAMETER Laite
    Puhelimen tunnus (adb devices), jos koneeseen on kytketty useampi.

.PARAMETER Nakymat
    Vain nämä näkymät, esimerkiksi -Nakymat koti,haku

.EXAMPLE
    .\scripts\kuvakaappaukset.ps1

.EXAMPLE
    .\scripts\kuvakaappaukset.ps1 -Tumma -Siisti
#>
param(
    [switch]$Tumma,
    [switch]$Siisti,
    [string]$Laite,
    [string[]]$Nakymat
)

$ErrorActionPreference = 'Stop'

$naytot = [ordered]@{
    'koti'       = 'Kotinäkymä, asema soi ja kappaleen nimi näkyy'
    'suosikit'   = 'Suosikit-välilehti'
    'haku'       = 'Haku, jossa on hakutuloksia (esim. "rock")'
    'aani'       = 'Ääniasetukset (asemakohtainen äänenvoimakkuus)'
    'historia'   = 'Soitetut kappaleet'
    'herätys'    = 'Herätyspaneeli'
    'asetukset'  = 'Asetukset'
    'yönäyttö'   = 'Yönäyttö'
}
if ($Nakymat) {
    $valitut = [ordered]@{}
    foreach ($n in $Nakymat) {
        if (-not $naytot.Contains($n)) { throw "Tuntematon näkymä '$n'. Vaihtoehdot: $($naytot.Keys -join ', ')" }
        $valitut[$n] = $naytot[$n]
    }
    $naytot = $valitut
}

# ---- adb ----
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
if (-not (Test-Path $adb)) { throw "adb ei löydy. Asenna Android SDK Platform-Tools tai lisää adb PATHiin." }

$kohde = @()
if ($Laite) { $kohde = @('-s', $Laite) }

# Tavallinen funktio ilman param-lohkoa: adb:n omat valitsimet (-a, -e, -p)
# kulkevat $argsissa sellaisenaan eivätkä sekoitu PowerShellin parametreihin.
function Adb {
    & $adb @kohde @args
    if ($LASTEXITCODE -ne 0) { throw "adb $($args -join ' ') epäonnistui" }
}

$laitteet = @(& $adb devices | Select-String "`tdevice$")
if ($laitteet.Count -eq 0) { throw "Puhelinta ei löydy. Tarkista kaapeli, USB-virheenkorjaus ja 'adb devices'." }
if ($laitteet.Count -gt 1 -and -not $Laite) { throw "Useita laitteita kytkettynä. Anna -Laite <tunnus> (ks. adb devices)." }

$juuri = Split-Path -Parent $PSScriptRoot
$kansio = Join-Path $juuri ("kuvakaappaukset\" + (Get-Date -Format 'yyyy-MM-dd_HHmm'))
New-Item -ItemType Directory -Force -Path $kansio | Out-Null

function Ota-Kuva([string]$nimi) {
    $puhelimella = '/sdcard/aalto_kaappaus.png'
    Adb shell screencap -p $puhelimella
    # adb pull, ei ">"-uudelleenohjausta: PowerShell 5 rikkoisi kuvan.
    Adb pull $puhelimella (Join-Path $kansio "$nimi.png") | Out-Null
    Adb shell rm $puhelimella
    Write-Host "  tallennettu $nimi.png" -ForegroundColor Green
}

function Aseta-Tumma([bool]$paalla) {
    Adb shell cmd uimode night ($(if ($paalla) { 'yes' } else { 'no' })) | Out-Null
    Start-Sleep -Milliseconds 800
}

$alkuperainenYo = $null
try {
    if ($Siisti) {
        Adb shell settings put global sysui_demo_allowed 1
        Adb shell am broadcast -a com.android.systemui.demo -e command enter | Out-Null
        Adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 | Out-Null
        Adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false | Out-Null
        Adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile hide | Out-Null
        Adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false | Out-Null
    }
    if ($Tumma) {
        $alkuperainenYo = (Adb shell cmd uimode night) -join ''
        Aseta-Tumma $false
    }

    Write-Host ""
    Write-Host "Kuvat tallentuvat: $kansio"
    Write-Host "Avaa kukin näkymä puhelimessa ja paina Enter. 'o' + Enter ohittaa näkymän."
    foreach ($nimi in $naytot.Keys) {
        Write-Host ""
        $vastaus = Read-Host "[$nimi] $($naytot[$nimi])"
        if ($vastaus -eq 'o') { Write-Host "  ohitettu"; continue }
        if ($Tumma) {
            Ota-Kuva "$nimi-vaalea"
            Aseta-Tumma $true
            Ota-Kuva "$nimi-tumma"
            Aseta-Tumma $false
        } else {
            Ota-Kuva $nimi
        }
    }
    Write-Host ""
    Write-Host "Valmis. Kuvat: $kansio" -ForegroundColor Green
    Invoke-Item $kansio
}
finally {
    if ($Siisti) {
        & $adb @kohde shell am broadcast -a com.android.systemui.demo -e command exit | Out-Null
    }
    if ($Tumma -and $alkuperainenYo) {
        $palautus = if ($alkuperainenYo -match 'yes') { 'yes' } elseif ($alkuperainenYo -match 'auto') { 'auto' } else { 'no' }
        & $adb @kohde shell cmd uimode night $palautus | Out-Null
    }
}
