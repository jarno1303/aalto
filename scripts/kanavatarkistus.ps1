<#
.SYNOPSIS
    Aalto kanavatarkistus: etsii kuolleet ja silmukassa pyorivat radioasemat.

.DESCRIPTION
    Hakee Radio Browserista annetun maan asemat ja ottaa jokaisesta kaksi
    nayteta eri aikoina. Elava radio ei koskaan toista tavulleen samaa aanta
    tuntia myohemmin; silmukassa pyoriva ilmoitus ("tama asema on siirtynyt")
    toistaa aina. Vertailu tehdaan etsimalla ensimmaisen naytteen ankkuri
    toisesta naytteesta - sama idea kuin kelauspuskurin saumakohdan
    tunnistuksessa.

    Vaihe 1: jokaisesta asemasta nayte A ja ICY-metadata.
    Vaihe 2: samoista asemista nayte B. Jos A:n ankkuri loytyy B:sta,
             asema pyorii silmukassa. Vaiheiden valiin jaa luonnostaan
             koko vaiheen 1 kesto, eli tyypillisesti 20-40 minuuttia.

    Lopuksi epaselvat asemat verrataan keskenaan: sama ilmoitus jaetaan
    usein monen kuolleen aseman kesken.

    Skripti ei koske puhelimeen eika adb:hen, joten sen voi ajaa akkutestin
    aikana.

.EXAMPLE
    .\scripts\kanavatarkistus.ps1 -Maa FI
    .\scripts\kanavatarkistus.ps1 -Maa FI -Maksimi 20 -AikakatkoS 6
#>
param(
    [string]$Maa = "FI",
    [int]$Maksimi = 0,
    [int]$NayteKt = 128,
    [int]$AnkkuriKt = 16,
    [int]$AikakatkoS = 10,
    [string]$Kansio = "katalogi",
    [string]$Palvelin = ""
)

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$script:enc = [System.Text.Encoding]::GetEncoding(28591)  # Latin-1: tavu <-> merkki 1:1
$script:UA = "Aalto-kanavatarkistus/1.0"

function Lue-Striimi {
    param(
        [string]$Url,
        [int]$TavoiteTavut,
        [int]$Aikakatko,
        [int]$Hyppy = 0
    )

    $tulos = [pscustomobject]@{
        Ok = $false
        Syy = ""
        Audio = $null
        Kappaleet = @()
        Tyyppi = ""
        Bitrate = ""
        Hls = $false
    }

    if ($Hyppy -gt 3) { $tulos.Syy = "liikaa uudelleenohjauksia"; return $tulos }
    if ([string]::IsNullOrWhiteSpace($Url)) { $tulos.Syy = "ei osoitetta"; return $tulos }

    try { $u = [System.Uri]$Url } catch { $tulos.Syy = "virheellinen osoite"; return $tulos }
    if ($u.Scheme -ne "http" -and $u.Scheme -ne "https") { $tulos.Syy = "tuntematon protokolla"; return $tulos }

    $portti = $u.Port
    if ($portti -le 0) {
        if ($u.Scheme -eq "https") { $portti = 443 } else { $portti = 80 }
    }

    $client = $null
    $virta = $null
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $odota = $client.BeginConnect($u.Host, $portti, $null, $null)
        if (-not $odota.AsyncWaitHandle.WaitOne(5000)) {
            $tulos.Syy = "yhteys aikakatkaistiin"
            return $tulos
        }
        $client.EndConnect($odota)
        $virta = $client.GetStream()
        if ($u.Scheme -eq "https") {
            $ssl = New-Object System.Net.Security.SslStream($virta, $false)
            $ssl.AuthenticateAsClient($u.Host)
            $virta = $ssl
        }
        $virta.ReadTimeout = 8000

        # HTTP/1.0 ja Connection: close, koska osa Shoutcast-palvelimista
        # vastaa "ICY 200 OK" eika puhu kunnolla HTTP/1.1:ta.
        $pyynto = "GET " + $u.PathAndQuery + " HTTP/1.0`r`n" +
                  "Host: " + $u.Host + "`r`n" +
                  "User-Agent: " + $script:UA + "`r`n" +
                  "Icy-MetaData: 1`r`n" +
                  "Accept: */*`r`n" +
                  "Connection: close`r`n`r`n"
        $pyyntoTavut = $script:enc.GetBytes($pyynto)
        $virta.Write($pyyntoTavut, 0, $pyyntoTavut.Length)
        $virta.Flush()

        $muisti = New-Object System.IO.MemoryStream
        $puskuri = New-Object byte[] 16384
        $alku = Get-Date
        $otsikkoPituus = -1
        $otsikkoTeksti = ""

        while ($true) {
            if (((Get-Date) - $alku).TotalSeconds -gt $Aikakatko) { break }
            $n = 0
            try { $n = $virta.Read($puskuri, 0, $puskuri.Length) } catch { break }
            if ($n -le 0) { break }
            $muisti.Write($puskuri, 0, $n)

            if ($otsikkoPituus -lt 0 -and $muisti.Length -lt 65536) {
                $kaikki = $script:enc.GetString($muisti.ToArray())
                $i = $kaikki.IndexOf("`r`n`r`n", [System.StringComparison]::Ordinal)
                if ($i -ge 0) {
                    $otsikkoPituus = $i + 4
                    $otsikkoTeksti = $kaikki.Substring(0, $i)
                } else {
                    $i = $kaikki.IndexOf("`n`n", [System.StringComparison]::Ordinal)
                    if ($i -ge 0) {
                        $otsikkoPituus = $i + 2
                        $otsikkoTeksti = $kaikki.Substring(0, $i)
                    }
                }
            }
            if ($otsikkoPituus -ge 0 -and ($muisti.Length - $otsikkoPituus) -ge $TavoiteTavut) { break }
        }

        $kaikkiTavut = $muisti.ToArray()
        $muisti.Dispose()

        if ($otsikkoPituus -lt 0) { $tulos.Syy = "ei vastausta"; return $tulos }

        $rivit = $otsikkoTeksti -split "`r?`n"
        if ($rivit[0] -notmatch '^(?:HTTP/1\.[01]|ICY)\s+(\d{3})') {
            $tulos.Syy = "outo vastaus"
            return $tulos
        }
        $koodi = [int]$Matches[1]

        $otsikot = @{}
        for ($r = 1; $r -lt $rivit.Count; $r++) {
            $kohta = $rivit[$r].IndexOf(":")
            if ($kohta -gt 0) {
                $avain = $rivit[$r].Substring(0, $kohta).Trim().ToLower()
                $otsikot[$avain] = $rivit[$r].Substring($kohta + 1).Trim()
            }
        }

        if ($koodi -ge 300 -and $koodi -lt 400 -and $otsikot.ContainsKey("location")) {
            $kohde = $otsikot["location"]
            if ($kohde -notmatch '^https?://') {
                $kohde = (New-Object System.Uri($u, $kohde)).AbsoluteUri
            }
            return Lue-Striimi -Url $kohde -TavoiteTavut $TavoiteTavut -Aikakatko $Aikakatko -Hyppy ($Hyppy + 1)
        }
        if ($koodi -ne 200) { $tulos.Syy = "HTTP $koodi"; return $tulos }

        $tyyppi = ""
        if ($otsikot.ContainsKey("content-type")) { $tyyppi = $otsikot["content-type"].ToLower() }
        $tulos.Tyyppi = $tyyppi
        if ($otsikot.ContainsKey("icy-br")) { $tulos.Bitrate = $otsikot["icy-br"] }

        $runkoPituus = $kaikkiTavut.Length - $otsikkoPituus
        if ($runkoPituus -le 0) { $tulos.Syy = "tyhja runko"; return $tulos }
        $runko = New-Object byte[] $runkoPituus
        [Array]::Copy($kaikkiTavut, $otsikkoPituus, $runko, 0, $runkoPituus)

        $alkuTeksti = $script:enc.GetString($runko, 0, [Math]::Min(512, $runkoPituus))

        # HLS-manifesti: ei tarkisteta tassa skriptissa.
        if ($alkuTeksti -match '^\s*#EXTM3U' -and ($tyyppi -match 'mpegurl' -or $Url -match '\.m3u8')) {
            $tulos.Hls = $true
            $tulos.Ok = $true
            $tulos.Syy = "HLS"
            return $tulos
        }

        # Soittolista (.pls / .m3u): poimitaan ensimmainen osoite ja jatketaan sinne.
        if ($tyyppi -match 'scpls' -or $tyyppi -match 'mpegurl' -or $Url -match '\.(pls|m3u)(\?|$)') {
            $osoite = $null
            foreach ($rivi in ($script:enc.GetString($runko) -split "`r?`n")) {
                $puhdas = $rivi.Trim()
                if ($puhdas -match '^File\d+\s*=\s*(\S+)') { $osoite = $Matches[1]; break }
                if ($puhdas -match '^(https?://\S+)') { $osoite = $Matches[1]; break }
            }
            if ($osoite) {
                return Lue-Striimi -Url $osoite -TavoiteTavut $TavoiteTavut -Aikakatko $Aikakatko -Hyppy ($Hyppy + 1)
            }
            $tulos.Syy = "tyhja soittolista"
            return $tulos
        }

        # ICY-metadata pois audiotavujen joukosta, jotta vertailu on puhdas.
        $metaint = 0
        if ($otsikot.ContainsKey("icy-metaint")) {
            [int]::TryParse($otsikot["icy-metaint"], [ref]$metaint) | Out-Null
        }

        $kappaleet = New-Object System.Collections.Generic.List[string]
        if ($metaint -gt 0) {
            $audio = New-Object System.IO.MemoryStream
            $p = 0
            while ($p -lt $runkoPituus) {
                $pala = [Math]::Min($metaint, $runkoPituus - $p)
                $audio.Write($runko, $p, $pala)
                $p += $pala
                if ($p -ge $runkoPituus) { break }
                $pituus = [int]$runko[$p] * 16
                $p += 1
                if ($pituus -gt 0) {
                    if (($p + $pituus) -gt $runkoPituus) { break }
                    $meta = $script:enc.GetString($runko, $p, $pituus)
                    if ($meta -match "StreamTitle='([^']*)'") {
                        $nimi = $Matches[1].Trim()
                        if ($nimi -ne "" -and -not $kappaleet.Contains($nimi)) { $kappaleet.Add($nimi) }
                    }
                    $p += $pituus
                }
            }
            $tulos.Audio = $audio.ToArray()
            $audio.Dispose()
        } else {
            $tulos.Audio = $runko
        }
        $tulos.Kappaleet = $kappaleet.ToArray()

        if ($tulos.Audio.Length -lt 4096) {
            $tulos.Syy = "liian vahan dataa"
            return $tulos
        }
        $tulos.Ok = $true
        return $tulos
    }
    catch {
        $tulos.Syy = $_.Exception.Message
        return $tulos
    }
    finally {
        if ($virta) { try { $virta.Dispose() } catch { } }
        if ($client) { try { $client.Close() } catch { } }
    }
}

function Loytyyko-Ankkuri {
    param([byte[]]$A, [byte[]]$B, [int]$AnkkuriTavut)
    if ($null -eq $A -or $null -eq $B) { return $false }
    if ($A.Length -lt ($AnkkuriTavut * 2) -or $B.Length -lt $AnkkuriTavut) { return $false }
    # Ankkuri keskelta: alun purskeessa on usein palvelimen puskuri,
    # joka voi olla sama ilman etta aani toistuu.
    $alkuKohta = [int]($A.Length / 2) - [int]($AnkkuriTavut / 2)
    if ($alkuKohta -lt 0) { $alkuKohta = 0 }
    $ankkuri = $script:enc.GetString($A, $alkuKohta, $AnkkuriTavut)
    $kohde = $script:enc.GetString($B)
    return ($kohde.IndexOf($ankkuri, [System.StringComparison]::Ordinal) -ge 0)
}

# --- Valmistelu ---------------------------------------------------------

if (-not (Test-Path $Kansio)) { New-Item -ItemType Directory -Path $Kansio | Out-Null }
# Absoluuttiseksi: .NET:n tiedostokutsut eivat kayta PowerShellin sijaintia
# vaan prosessin omaa tyohakemistoa, joten suhteellinen polku kirjoittaisi
# naytteet vaaraan paikkaan.
$Kansio = (Resolve-Path $Kansio).Path
$naytteet = Join-Path $Kansio "naytteet"
if (-not (Test-Path $naytteet)) { New-Item -ItemType Directory -Path $naytteet | Out-Null }
$naytteet = (Resolve-Path $naytteet).Path

$tavoite = $NayteKt * 1024
$ankkuriTavut = $AnkkuriKt * 1024

Write-Host "Haetaan asemat Radio Browserista ($Maa)..." -ForegroundColor Cyan

# Radio Browser on joukko vapaaehtoisten palvelimia yhden DNS-nimen takana.
# Peilit tulevat ja menevat, joten nimet haetaan kuten sovelluskin tekee:
# all.api.radio-browser.info -> osoitteet -> kaanteinen nimihaku.
function Etsi-Palvelimet {
    $nimet = New-Object System.Collections.Generic.List[string]
    try {
        foreach ($osoite in [System.Net.Dns]::GetHostAddresses("all.api.radio-browser.info")) {
            try {
                $nimi = [System.Net.Dns]::GetHostEntry($osoite).HostName.Trim().TrimEnd('.').ToLower()
                if ($nimi.EndsWith(".api.radio-browser.info") -and
                    $nimi -ne "all.api.radio-browser.info" -and
                    -not $nimet.Contains($nimi)) {
                    $nimet.Add($nimi)
                }
            } catch { }
        }
    } catch { }
    return $nimet
}

# PowerShell 5.1:n ConvertFrom-Json palauttaa taulukon YHTENA putkiobjektina
# eika pura sita riveiksi, joten -InputObject ja muuttuja, ei putkea.
# Isoissa JSONeissa se kaatuu 2 Mt:n MaxJsonLength-rajaan; varalla
# JavaScriptSerializer, jolle rajan saa nostettua.
function Muunna-Json([string]$teksti) {
    $j = $null
    try { $j = ConvertFrom-Json -InputObject $teksti } catch { $j = $null }
    if ($null -eq $j) {
        try {
            Add-Type -AssemblyName System.Web.Extensions -ErrorAction Stop
            $ser = New-Object System.Web.Script.Serialization.JavaScriptSerializer
            $ser.MaxJsonLength = [int]::MaxValue
            $ser.RecursionLimit = 200
            $j = $ser.DeserializeObject($teksti)
        } catch {
            Write-Host ("  JSON-jasennys epaonnistui: " + $_.Exception.Message) -ForegroundColor DarkGray
            return @()
        }
    }
    if ($null -eq $j) { return @() }
    if ($j -is [System.Array]) { return $j }
    return @($j)
}

$tunnetut = @(
    "de1.api.radio-browser.info",
    "de2.api.radio-browser.info",
    "fi1.api.radio-browser.info",
    "nl1.api.radio-browser.info",
    "at1.api.radio-browser.info"
)
if (-not [string]::IsNullOrWhiteSpace($Palvelin)) {
    $palvelimet = @($Palvelin)
} else {
    $loydetyt = @(Etsi-Palvelimet)
    if ($loydetyt.Count -gt 0) {
        Write-Host ("Nimipalvelu tarjosi: " + ($loydetyt -join ", ")) -ForegroundColor DarkGray
    } else {
        Write-Host "Nimipalvelusta ei loytynyt peileja, kokeillaan tunnettuja." -ForegroundColor DarkGray
    }
    $palvelimet = @($loydetyt + $tunnetut | Select-Object -Unique)
}

$asemat = @()
$kaytetty = ""
foreach ($p in $palvelimet) {
    $api = "https://" + $p + "/json/stations/bycountrycodeexact/" + $Maa +
           "?hidebroken=false&order=clickcount&reverse=true&limit=5000"
    $teksti = ""
    try {
        $teksti = (Invoke-WebRequest -Uri $api -Headers @{ "User-Agent" = $script:UA } `
                     -TimeoutSec 60 -UseBasicParsing).Content
    } catch {
        Write-Host ("  " + $p + ": " + $_.Exception.Message) -ForegroundColor DarkGray
        continue
    }
    $vastaus = @(Muunna-Json $teksti)

    # Kelvollisessa vastauksessa on stationuuid. Virheobjekti ei kelpaa.
    $kelvolliset = @($vastaus | Where-Object { $_.stationuuid })
    if ($kelvolliset.Count -lt 2) {
        $nayte = $teksti
        if ($nayte.Length -gt 160) { $nayte = $nayte.Substring(0, 160) + "..." }
        Write-Host ("  " + $p + ": " + $teksti.Length + " merkkia, " + $vastaus.Count +
                    " jasennettya rivia, " + $kelvolliset.Count + " kelvollista.") -ForegroundColor DarkGray
        Write-Host ("    " + $nayte) -ForegroundColor DarkGray
        continue
    }
    $asemat = $kelvolliset
    $kaytetty = $p
    break
}

if ($asemat.Count -eq 0) {
    Write-Host "Yksikaan Radio Browser -palvelin ei vastannut kelvollisesti." -ForegroundColor Red
    Write-Host "Kokeile: .\scripts\kanavatarkistus.ps1 -Palvelin nl1.api.radio-browser.info" -ForegroundColor Yellow
    exit 1
}
Write-Host ("Palvelin: " + $kaytetty) -ForegroundColor DarkGray

if ($Maksimi -gt 0) { $asemat = @($asemat | Select-Object -First $Maksimi) }

$arvioMin = [math]::Round(($asemat.Count * $AikakatkoS * 2) / 60.0)
Write-Host ("Asemia: " + $asemat.Count + ". Arvioitu kesto noin " + $arvioMin + " min.") -ForegroundColor Cyan

# --- Vaihe 1: nayte A ---------------------------------------------------

$tulokset = New-Object System.Collections.Generic.List[object]
$laskuri = 0
foreach ($asema in $asemat) {
    $laskuri++
    Write-Progress -Activity "Vaihe 1/2: ensimmainen nayte" `
        -Status ("$laskuri / " + $asemat.Count + "  " + $asema.name) `
        -PercentComplete (($laskuri / $asemat.Count) * 100)

    $osoite = $asema.url_resolved
    if ([string]::IsNullOrWhiteSpace($osoite)) { $osoite = $asema.url }
    $r = Lue-Striimi -Url $osoite -TavoiteTavut $tavoite -Aikakatko $AikakatkoS

    if ($r.Ok -and $r.Audio) {
        [System.IO.File]::WriteAllBytes((Join-Path $naytteet ($asema.stationuuid + ".a")), $r.Audio)
    }

    $tulokset.Add([pscustomobject]@{
        Tila        = ""
        Syy         = ""
        Nimi        = $asema.name
        Klikkaukset = $asema.clickcount
        Bitrate     = $r.Bitrate
        Tyyppi      = $r.Tyyppi
        KappaleA    = ($r.Kappaleet -join " | ")
        KappaleB    = ""
        SamaKuin    = ""
        Silmukka    = ""
        Osoite      = $osoite
        Uuid        = $asema.stationuuid
        Vaihe1      = $r.Ok
        Vaihe1Syy   = $r.Syy
        Hls         = $r.Hls
    })
}
Write-Progress -Activity "Vaihe 1/2: ensimmainen nayte" -Completed

$elossa = @($tulokset | Where-Object { $_.Vaihe1 })
Write-Host ("Vaihe 1 valmis: " + $elossa.Count + " / " + $tulokset.Count + " vastasi.") -ForegroundColor Cyan

# --- Vaihe 2: nayte B ja silmukan tunnistus -----------------------------

$vaihe2 = @($tulokset | Where-Object { $_.Vaihe1 -and -not $_.Hls })
$laskuri = 0
foreach ($t in $vaihe2) {
    $laskuri++
    Write-Progress -Activity "Vaihe 2/2: toinen nayte" `
        -Status ("$laskuri / " + $vaihe2.Count + "  " + $t.Nimi) `
        -PercentComplete (($laskuri / $vaihe2.Count) * 100)

    $r = Lue-Striimi -Url $t.Osoite -TavoiteTavut $tavoite -Aikakatko $AikakatkoS
    if (-not $r.Ok) {
        $t.Silmukka = "vaihe 2 epaonnistui: " + $r.Syy
        continue
    }
    $t.KappaleB = ($r.Kappaleet -join " | ")

    $tiedosto = Join-Path $naytteet ($t.Uuid + ".a")
    if (Test-Path $tiedosto) {
        $a = [System.IO.File]::ReadAllBytes($tiedosto)
        if (Loytyyko-Ankkuri -A $a -B $r.Audio -AnkkuriTavut $ankkuriTavut) {
            $t.Silmukka = "kylla"
        } else {
            $t.Silmukka = "ei"
        }
    }
}
Write-Progress -Activity "Vaihe 2/2: toinen nayte" -Completed

# --- Tuomio -------------------------------------------------------------

foreach ($t in $tulokset) {
    if (-not $t.Vaihe1) {
        $t.Tila = "KUOLLUT"
        $t.Syy = $t.Vaihe1Syy
    } elseif ($t.Hls) {
        $t.Tila = "HLS"
        $t.Syy = "ei tarkistettu"
    } elseif ($t.Silmukka -eq "kylla") {
        $t.Tila = "KUOLLUT"
        $t.Syy = "sama aani toistuu - todennakoinen ilmoitussilmukka"
    } elseif ($t.KappaleA -ne "" -and $t.KappaleB -ne "" -and $t.KappaleA -ne $t.KappaleB) {
        $t.Tila = "OK"
        $t.Syy = "kappaleen nimi vaihtui"
    } elseif ($t.Silmukka -eq "ei") {
        $t.Tila = "OK"
        $t.Syy = "aani muuttui"
    } else {
        $t.Tila = "EPASELVA"
        $t.Syy = $t.Silmukka
    }
}

# --- Asemien valinen vertailu: jaettu ilmoitus ---------------------------

$epailyt = @($tulokset | Where-Object { $_.Vaihe1 -and ($_.Tila -eq "EPASELVA" -or $_.Silmukka -eq "kylla") })
if ($epailyt.Count -gt 1) {
    Write-Host ("Verrataan " + $epailyt.Count + " epailyttavaa asemaa keskenaan...") -ForegroundColor Cyan
    $naytteetMuistissa = @{}
    foreach ($e in $epailyt) {
        $f = Join-Path $naytteet ($e.Uuid + ".a")
        if (Test-Path $f) { $naytteetMuistissa[$e.Uuid] = [System.IO.File]::ReadAllBytes($f) }
    }
    for ($i = 0; $i -lt $epailyt.Count; $i++) {
        if (-not $naytteetMuistissa.ContainsKey($epailyt[$i].Uuid)) { continue }
        for ($j = $i + 1; $j -lt $epailyt.Count; $j++) {
            if ($epailyt[$j].SamaKuin -ne "") { continue }
            if (-not $naytteetMuistissa.ContainsKey($epailyt[$j].Uuid)) { continue }
            if (Loytyyko-Ankkuri -A $naytteetMuistissa[$epailyt[$i].Uuid] `
                                 -B $naytteetMuistissa[$epailyt[$j].Uuid] `
                                 -AnkkuriTavut $ankkuriTavut) {
                $epailyt[$j].SamaKuin = $epailyt[$i].Nimi
                if ($epailyt[$j].Tila -eq "EPASELVA") {
                    $epailyt[$j].Tila = "KUOLLUT"
                    $epailyt[$j].Syy = "sama aani kuin " + $epailyt[$i].Nimi
                }
            }
        }
    }
}

# --- Tulokset -----------------------------------------------------------

$csv = Join-Path $Kansio ("kanavatarkistus-" + $Maa + ".csv")
$tulokset |
    Sort-Object Tila, @{ Expression = "Klikkaukset"; Descending = $true } |
    Select-Object Tila, Syy, Nimi, Klikkaukset, Bitrate, Tyyppi, KappaleA, KappaleB, SamaKuin, Osoite, Uuid |
    Export-Csv -Path $csv -NoTypeInformation -Encoding UTF8

$kuunneltavat = @($tulokset | Where-Object { $_.Tila -eq "EPASELVA" -or $_.Tila -eq "HLS" } |
    Sort-Object @{ Expression = "Klikkaukset"; Descending = $true })
$lista = Join-Path $Kansio ("kuunneltavat-" + $Maa + ".txt")
$kuunneltavat | ForEach-Object { $_.Nimi + "  ->  " + $_.Osoite } | Out-File -Encoding utf8 $lista

Write-Host ""
Write-Host "Yhteenveto:" -ForegroundColor Cyan
$tulokset | Group-Object Tila | Sort-Object Name | ForEach-Object {
    Write-Host ("  {0,-10} {1}" -f $_.Name, $_.Count)
}
Write-Host ""
Write-Host "Yleisimmat hylkayksen syyt:" -ForegroundColor Cyan
$tulokset | Where-Object { $_.Tila -eq "KUOLLUT" } | Group-Object Syy |
    Sort-Object Count -Descending | Select-Object -First 8 | ForEach-Object {
        Write-Host ("  {0,-4} {1}" -f $_.Count, $_.Name)
    }
Write-Host ""
Write-Host ("CSV:           " + $csv) -ForegroundColor Green
Write-Host ("Kuunneltavat:  " + $lista + "  (" + $kuunneltavat.Count + " asemaa)") -ForegroundColor Green
Write-Host ("Naytteet:      " + $naytteet + "  (voi poistaa)")
