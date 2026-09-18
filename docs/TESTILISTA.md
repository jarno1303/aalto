# Aalto – testilista (haara ui-ux-polish)

Tämä lista käydään läpi kerralla, kun iso muutospaketti on käännetty.
Merkitse jokaiseen kohtaan OK tai kirjoita, mitä tapahtui.

## Tilanne 18.9.2026

Kuitattu OK: 0 (kaannos + yksikkotestit + asennus), 4a, 7b, 7c, 7d.
Jaljella: 1, 2, 3, 4, 4b, 4c, 5, 6, 7, 8.
7c on todettu oikeaksi negatiivisena (kotona ei palkkia); varsinainen
matkapalkki varmistetaan vasta ulkomailla tai emulaattorin operaattorilla.

## 0. Käännös ja automaattitestit (10 min)

Kaikki tämän osion vaiheet yhdellä komennolla:

```
.\scripts\testaa.ps1 -Asenna
```

Se kääntää, ajaa yksikkötestit, asentaa puhelimeen päivityksenä ja tulostaa
lopuksi tämän listan otsikot. Pysähtyy ensimmäiseen virheeseen.

Jos jokin vaihe kaatuu, koko tuloste tallentuu tiedostoon
`testilokit\viimeisin.log`. Silloin riittää sanoa Claudelle **"testi kaatui"**
— se lukee lokin itse. Onnistunut ajo ei jätä lokia, joten tiedoston olemassaolo
tarkoittaa aina nykyistä ongelmaa. Jos PowerShell
estää skriptin ajamisen, kerran istunnossa:
`Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`

Käsin sama asia:

1. Android Studio: **Build → Rebuild Project**. Ei virheitä.
2. Terminal:
   ```
   .\gradlew.bat :app:testDebugUnitTest
   ```
   Kaikki testit vihreitä (AlarmTimeTest, AlarmRampTest, StreamFallbackTest,
   CuratedStationsTest, sync- ja katalogitestit).
3. **Run ▶** asentaa sovelluksen puhelimeen.

Jos 1 tai 2 epäonnistuu, älä jatka: korjaa ensin.

## 1. Tavallinen kuuntelu (tärkein, 10 min)

- [ ] Sovellus aukeaa alle sekunnissa.
- [ ] Oman aseman napautus aloittaa toiston nopeasti.
- [ ] Tauko ja jatkaminen toimivat.
- [ ] Aseman vaihto toimii sekä ruudukosta että ⏮ ⏭ -painikkeista.
- [ ] Nyt soi -kortti näyttää oikean aseman ja logon.
- [ ] Soivan kappaleen nimi näkyy ainakin YleX:llä tai Suomipopilla.
- [ ] Haku löytää asemia; maan vaihto haussa toimii ja muistuu uudelleenkäynnistyksen jälkeen.
- [ ] Suosikkien järjestely (pitkä painallus ja veto) toimii.
- [ ] Suositut-lista alkaa tutuilla asemilla (Yle Radio Suomi, Suomipop, Nova…).

## 2. Luotettavuus (10 min)

- [ ] Kokeile 5 pientä asemaa haun tuloksista: useampi lähtee soimaan kuin ennen.
- [ ] Laita lentokonetila päälle 5 s toiston aikana ja pois: toisto palautuu tai näyttää "Yhdistetään…".
- [ ] Avaa YouTube toiston aikana: Aalto vaimenee/pysähtyy ja YouTube kuuluu.
- [ ] Soita asemaa 15 min taustalla näyttö sammutettuna: toisto ei katkea.

## 3. Ilmoitus, widget ja lukitusnäyttö (5 min)

- [ ] Ilmoituksessa on ⏮ ⏭, tauko ja sydän; kaikki toimivat.
- [ ] Sydän ilmoituksesta lisää ja poistaa aseman omista asemista.
- [ ] Widget kotinäytölle: näyttää aseman ja logon.
- [ ] Sulje sovellus kokonaan: widgetin ▶ käynnistää viimeisimmän aseman, ⏭ vaihtaa asemaa.

## 4. Uniajastin (20 min, voi tehdä taustalla)

- [ ] Aseta 15 min ajastin: kortissa lukee "Sammuu 14 min".
- [ ] Ajastin toimii myös näyttö sammutettuna.
- [ ] Ääni hiljenee pehmeästi noin 30 s ja toisto pysähtyy; soittimen ilmoitus poistuu.
- [ ] Seuraava kuuntelu alkaa normaalilla äänenvoimakkuudella.

- [ ] Aseta Androidin tekstikoko suurimmaksi: missään näkymässä ei ole sanaa,
      joka katkeaa usealle riville tai kolmeksi kirjaimeksi (asetukset, herätys,
      alapalkki, otsikkorivit).

## 4a. Vaaka-asento

- [x] Käännä puhelin vaaka-asentoon etusivulla: vasemmalla soittonäkymä,
      oikealla asemalista, ei tyhjää tilaa eikä leikkautuvia elementtejä.
- [x] Soittonäkymässä toimivat vaakatasossa samat asiat kuin pystyssä:
      sydän, uniajastin, yönäyttö, edellinen/seuraava ja kappalerivin
      napautus (soitetut kappaleet).
- [x] Aseman poisto pitkällä painalluksella toimii myös vaakatasossa.
- [x] Soiva asema on korostettu ja näkyvissä listassa myös käännön jälkeen.
- [x] Käännä edestakaisin soiton aikana: ääni ei katkea.
- [x] Haku- ja Omat asemat -välilehdet ovat käytettäviä vaakatasossa.

## 4b. Soitetut kappaleet

- [ ] Soita asema, joka lähettää kappaletiedot (esim. Radio Nova, SuomiPop).
      Kun kappale vaihtuu, napauta soittonäkymässä sitä riviä, jolla kappale lukee.
- [ ] Lista näyttää kuunnellut kappaleet uusin ensin, aika ja asema alla.
- [ ] Sama kappale ei toistu listalla, vaikka asema lähettää tiedot uudelleen.
- [ ] Mainoskatkot, aseman nimi ja osoitteet eivät päädy listalle.
- [ ] Kappaleen napautus tarjoaa haun Spotifysta ja YouTubesta, ja linkki avautuu.
- [ ] Tyhjennä-painike tyhjentää listan.
- [ ] Kuuntele hetki sovellus taustalla: kappaleet tallentuvat silti.
- [ ] Päivitys vanhan version päälle: suosikit ja asetukset säilyvät (Room 5 -> 6).

## 4c. Ääni

- [ ] Asetukset -> Ääni avaa paneelin.
- [ ] Aseman äänenvoimakkuuden liuku kuuluu heti soiton aikana.
- [ ] Vaihda toiseen asemaan ja takaisin: säätö muistetaan asemakohtaisesti.
- [ ] Nollaa palauttaa aseman alkuperäiselle tasolle.
- [ ] Taajuuskorjain päälle: esiasetuksen vaihto kuuluu, kaistojen liu'ut kuuluvat.
- [ ] Taajuuskorjain pois: ääni palaa ennalleen.
- [ ] Asetukset säilyvät sovelluksen uudelleenkäynnistyksen yli.
- [ ] Jos laite ei tarjoa taajuuskorjainta, paneeli kertoo sen eikä kaadu.
- [ ] Soitto ei katkea missään yllä olevassa kohdassa.

## 5. Herätys (tärkeä, osa yön yli)

- [ ] Herätyspaneeli aukeaa yläpalkin kellosta.
- [ ] Aika valitaan rullilla; kytkin, päivät, torkku ja äänenvoimakkuus tallentuvat heti.
- [ ] Päälle kytkiessä tulee viesti "Herätys soi X t Y min kuluttua".
- [ ] Jos paneelissa näkyy punainen varoitus ilmoituksista, salli ne: varoituksen
      pitää kadota heti luvan myöntämisen jälkeen, ilman että paneelin sulkee.
- [ ] Sama myös silloin kun luvan antaa järjestelmän asetuksista ja palaa Aaltoon.
- [ ] **Kokeile ääntä**: valittu kanava soi, ääni nousee vähitellen, Torkku ja Lopeta toimivat.
- [ ] Laita puhelimen hälytysääni hiljaiseksi ja kokeile uudelleen: ääni nousee kuuluvaksi ja palautuu jälkeenpäin.
- [ ] Aseta herätys 3 min päähän, lukitse puhelin: lukitusnäytölle aukeaa herätysnäkymä.
- [ ] Lentokonetilassa herätys soittaa puhelimen hälytysäänen.
- [ ] Torkku: herätys soi uudelleen valitun ajan päästä.
- [ ] Jatka kuuntelua (lukitusnäyttö): radio jää soimaan normaalina toistona,
      puhelinta ei tarvitse avata, ja medianäppäimet ohjaavat sitä.
- [ ] Jatka kuuntelua toimii myös ilmoituksen "Jatka"-napista, kun puhelin on käytössä.
- [ ] Ilmoituksen kolme nappia näkyvät kokonaisina sanoina (Torkku · Jatka · Lopeta),
      myös isolla järjestelmän tekstikoolla.
- [ ] Jatka kuuntelua toimii myös silloin kun Aalto on auki (herätysdialogi).
- [ ] Jos median äänenvoimakkuus on nollassa, jatkaminen nostaa sen kuuluvaksi.
- [ ] Tunti ennen herätystä tulee ilmoitus, jossa toimivat "Ohita tämä kerta" ja "Kytke pois".
- [ ] Käynnistä puhelin uudelleen herätys päällä: herätys soi silti.
- [ ] **Yön yli:** aseta oikea herätys ja nuku. Soiko se ajallaan?

## 6. Android Auto (15 min simulaattorilla, myöhemmin autossa)

Käynnistys:
```
cd $env:LOCALAPPDATA\Android\Sdk\platform-tools
.\adb forward tcp:5277 tcp:5277
cd ..\extras\google\auto
.\desktop-head-unit.exe
```
- [ ] Välilehdet: Omat asemat, Viimeisimmät, Suositut, Maat.
- [ ] Aseman napautus alkaa soida; logo ja nimi näkyvät.
- [ ] Viimeisimmät täyttyy kuunnelluista asemista.
- [ ] Maat → Suomi / Saksa: asemat soivat.
- [ ] Haku löytää aseman.
- [ ] Jono näyttää omat asemat; valinta vaihtaa aseman.
- [ ] ⏮ ⏭ vaihtavat omien asemien välillä ja puhelin näyttää saman aseman.
- [ ] Sydän soittonäkymässä lisää ja poistaa aseman.
- [ ] Oikeassa autossa: sama läpikäynti ja ratin napit.

## 7. Synkronointi (10 min, vaatii toisen laitteen)

- [ ] Kirjaudu asetuksista Googlella.
- [ ] Lisää asema toisella laitteella: se ilmestyy tähän laitteeseen.
- [ ] Järjestyksen muutos siirtyy molempiin suuntiin.
- [ ] Radio toimii, vaikka kirjautuisi ulos.

## 7b. Kieli

Nopein tapa: sovelluskohtainen kieli adb:lla, jolloin puhelimen oma kieli ei
muutu. Vaatii Android 13:n tai uudemman.

```
.\scripts\testaa.ps1 -Kieli en      # englanti
.\scripts\testaa.ps1 -Kieli de      # saksa (pitaa nayttaa englantia)
.\scripts\testaa.ps1 -Kieli fi      # suomi
.\scripts\testaa.ps1 -Kieli system  # takaisin puhelimen kieleen
```

- [x] Vaihda kieleksi englanti: sovellus on kokonaan englanniksi, ei yhtään
      suomenkielistä sanaa.
- [x] Kellonajat: englanniksi 7:30, suomeksi 7.30. Sama herätyspaneelissa,
      rullassa, yläpalkissa ja ilmoituksissa.
- [x] Herätyksen päivälyhenteet ovat kielen mukaiset (ma/ti vs. Mon/Tue).
- [x] Vaihda kieleksi esim. saksa: sovellus on englanniksi, ei suomeksi.
- [x] Palauta suomi: kaikki on taas suomeksi eikä mikään ole rikki.
- [x] Maiden nimet ovat kielen mukaiset: haun maavalinnassa Suomi / Finland,
      ei suomea englanninkielisessä käyttöliittymässä.
- [x] Asetuksissa on Kieli-rivi, joka avaa järjestelmän kielivalinnan
      (Android 13+). Kielen vaihto sieltä vaihtaa sovelluksen kielen.

## 7c. Sijainti

- [x] Hakuvälilehti ehdottaa oman maan asemia ilman että maata on valittu.
- [x] Jos matkustat (tai vaihdat SIM-verkkoa), haussa näkyy kerran palkki
      "Näytät olevan maassa X" — ja vasta napautus vaihtaa maan.
- [x] "Ei nyt" piilottaa ehdotuksen pysyvästi sen maan osalta.
- [x] Itse valittu maa ei vaihdu automaattisesti koskaan.

## 7d. Omat maat

- [x] Hakunäkymä näyttää samalta kuin ennen: yksi maarivi, ei sirurivejä.
- [x] Asetuksissa on Maat-rivi, joka avaa monivalinnan.
- [x] Valitut maat näkyvät maarivin valikossa, ja vaihto on yksi napautus.
- [x] Valikon lopussa on "Muokkaa maita…", joka avaa saman monivalinnan.
- [x] Jokainen maa näyttää omat kuunnelluimpansa: pieni maa ei huku isoon.
- [x] Valinnat säilyvät sovelluksen uudelleenkäynnistyksen yli.
- [x] Viimeistä maata ei voi ottaa pois (rivi ei saa jäädä tyhjäksi).
- [x] Autossa valittu maa näkyy sirurivissä, vaikka se ei olisi omissa maissa.

## 8. Ulkoasu (5 min)

- [ ] Vaalea, tumma ja järjestelmän mukainen tila näyttävät oikeilta.
- [ ] Yönäkymä: ohjaimet näkyvät heti, "Poistu" toimii, Takaisin-painike toimii.
- [ ] Vaakanäkymä toimii.
- [ ] Ison tekstikoon asetuksella mikään teksti ei katkea pahasti.

## Jos jokin kaatuu

Android Studio → **Logcat** → hakukenttään `FATAL`, kopioi pino ja liitä keskusteluun.
