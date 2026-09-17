# Aalto – testilista (haara ui-ux-polish)

Tämä lista käydään läpi kerralla, kun iso muutospaketti on käännetty.
Merkitse jokaiseen kohtaan OK tai kirjoita, mitä tapahtui.

## 0. Käännös ja automaattitestit (10 min)

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

## 5. Herätys (tärkeä, osa yön yli)

- [ ] Herätyspaneeli aukeaa yläpalkin kellosta.
- [ ] Aika valitaan rullilla; kytkin, päivät, torkku ja äänenvoimakkuus tallentuvat heti.
- [ ] Päälle kytkiessä tulee viesti "Herätys soi X t Y min kuluttua".
- [ ] Jos paneelissa näkyy varoitus ilmoituksista, salli ne.
- [ ] **Kokeile ääntä**: valittu kanava soi, ääni nousee vähitellen, Torkku ja Lopeta toimivat.
- [ ] Laita puhelimen hälytysääni hiljaiseksi ja kokeile uudelleen: ääni nousee kuuluvaksi ja palautuu jälkeenpäin.
- [ ] Aseta herätys 3 min päähän, lukitse puhelin: lukitusnäytölle aukeaa herätysnäkymä.
- [ ] Lentokonetilassa herätys soittaa puhelimen hälytysäänen.
- [ ] Torkku: herätys soi uudelleen valitun ajan päästä.
- [ ] Jatka kuuntelua: Aalto aukeaa ja soittaa herätyksen kanavaa.
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

## 8. Ulkoasu (5 min)

- [ ] Vaalea, tumma ja järjestelmän mukainen tila näyttävät oikeilta.
- [ ] Yönäkymä: ohjaimet näkyvät heti, "Poistu" toimii, Takaisin-painike toimii.
- [ ] Vaakanäkymä toimii.
- [ ] Ison tekstikoon asetuksella mikään teksti ei katkea pahasti.

## Jos jokin kaatuu

Android Studio → **Logcat** → hakukenttään `FATAL`, kopioi pino ja liitä keskusteluun.
