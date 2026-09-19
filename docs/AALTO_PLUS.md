# Aalto Plus – rajanveto

Tämä on päätösdokumentti, ei toteutussuunnitelma. Se vastaa yhteen
kysymykseen: **mikä on ilmaista ja mikä ei, ja miten raja näkyy käyttäjälle
ilman että ilmaisversio tuntuu rammalta.**

Kirjoitettu ennen ensimmäistä riviä maksutoteutusta, koska rajanveto on halpa
suunnitella ja kallis korjata jälkikäteen: hinnoittelun voi muuttaa, mutta
ominaisuuden ottaminen pois ilmaisversiosta ei onnistu koskaan.

## Periaate

Maksullinen ominaisuus ei koskaan synny siitä, että ilmaisversiosta
poistetaan jotain. Kaikki mikä on tänään ilmaista pysyy ilmaisena
ikuisesti. Plus on aina päälle tulevaa.

Tästä seuraa käytännön sääntö, joka ratkaisee useimmat rajatapaukset:

> Jos ominaisuus korjaa puutteen, se on ilmainen.
> Jos se tuo jotain lisää, se voi olla maksullinen.

Asemakohtainen äänenvoimakkuus korjaa puutteen (asemat on masteroitu eri
tavoin) → ilmainen. Taajuuskorjain tuo lisää → Plus.

## Mikä on ilmaista, ikuisesti

Nämä eivät ole neuvoteltavissa. Niitä ei rajoiteta määrällä, ajalla eikä
mainoksilla.

- Soitto, haku, asemien selaus, omat asemat ja niiden järjestys. **Suosikkeja
  ei rajoiteta määrällä**: raja osuisi käyttäjän omiin tietoihin, ei
  lisäominaisuuteen.
- Aalto Sync (palvelinkulu on olematon, ks. CURRENT_STATE.md)
- Android Auto ja widget. Auto on Aallon kärki (Car-pilari), eikä autossa voi
  eikä saa näyttää ostokehotusta: maksumuurin takana Auto näyttäisi
  ilmaiskäyttäjälle vain rikkinäiseltä. Plus näkyy autossa välillisesti
  (listat, kelaus), mutta ilmaisen käyttäjän auto ei ole koskaan rikki.
- Yksi herätys, uniajastin, yönäyttö
- Asemakohtainen äänenvoimakkuus
- Soitetut kappaleet: kuluva päivä, ja hakulinkit Spotifyyn ja YouTubeen
- Kelaus taaksepäin 60 sekuntia (kun ominaisuus on rakennettu)

## Mikä on Plussaa

| Ominaisuus | Ilmaiseksi | Plus |
|---|---|---|
| Asemalistat | yksi, rajaton määrä asemia | useita omia listoja (Aamu, Auto, Työ) |
| Oma striimiosoite (URL) | – | asemien lisäys omalla osoitteella |
| Soitetut kappaleet | kuluva päivä | koko historia, haku, vienti |
| Kelaus | 60 s | 30 min + hyppy ohjelman alkuun |
| Taajuuskorjain | – | esiasetukset ja kaistat |
| Automaattinen äänentasaus | – | kyllä |
| Herätykset | yksi | useita, eri kanava eri päiville |
| Teemat ja kuvakkeet | perus | kaikki |

Huomaa mitä taulukossa ei ole: **mikään rivi ei ole "poista mainokset"**,
eikä yksikään ilmaissarake ole tyhjä siellä missä ominaisuus jo on olemassa.

Tarkennukset:

- **Oma URL:** jo lisätyt omat asemat soivat ja synkronoituvat myös Plussan
  päätyttyä. Vain uuden oman aseman lisääminen vaatii Plussan.
- **Asemalistat:** kun Plus päättyy, ylimääräiset listat eivät katoa eivätkä
  lukitu. Ne jäävät näkyviin ja soitettaviksi, vain uuden listan luominen
  vaatii Plussan.

## Toteutuksen tila (2026-09-19)

Taulukko on suunnitelma. Koodissa on tänään:

| Ominaisuus | Tila |
|---|---|
| Asemalistat | yksi lista, useita ei ole |
| Oma URL | toteutettu (Suosikit → "Lisää oma asema"), Plussan takana. Betassa ei näy testaajille, koska maksua ei vielä ole; debug-kytkimellä testattavissa |
| Soitetut kappaleet | 300 viimeisintä tallessa; ilman Plussaa näkyy kuluva päivä, Plussalla kaikki. Haku Spotifysta/YouTubesta. Historian haku ja vienti puuttuvat |
| Kelaus | ei toteutettu |
| Taajuuskorjain | toteutettu, Plussan takana |
| Automaattinen äänentasaus | ei toteutettu |
| Herätykset | yksi |
| Teemat ja kuvakkeet | vaalea/tumma/järjestelmä, yksi kuvake |
| Maksu | `PlusAccess` olemassa (`fi.aalto.radio.plus`), vastaa aina "ei" ilman Play Billingiä; debug-kytkin asetuksissa. Play Billing ei toteutettu |

**Ennen betaa korjattavat** (koska "mikä on tänään ilmaista, pysyy ilmaisena"
alkaa sitoa ensimmäisestä testaajasta) on tehty koodissa haarassa
`plus-boundary`: kappalehistorian ilmaisnäkymä on kuluva päivä, ja
taajuuskorjain on Plussan takana.

Ilmaisversio julkaistaan ensin. Plus lanseerataan vasta, kun siinä on valmiina
ominaisuuksia, joista kannattaa maksaa.

## Kokeilu ja hinta

- 14 päivää kaikki auki, **ilman korttia**
- Kokeilu alkaa siitä, kun käyttäjä ensimmäisen kerran koskee Plus-ominaisuuteen,
  ei asennuksesta. Radiota kaksi viikkoa kuunnellut ei ole kuluttanut kokeiluaan.
- Kokeilun lopussa ominaisuudet palaavat rajattuun muotoonsa **yhdellä
  selkeällä selityksellä**, eivät hiljaa rikkoutumalla
- Hinta: tilaus ja elinikäinen rinnakkain, molemmat asettavat saman lipun

## Missä kysytään – ja missä ei

Kolme paikkaa, ei muita:

1. **Rajalla.** Kun käyttäjä osuu rajaan (yrittää kelata yli minuutin, selata
   eilistä historiaa), kapea palkki näkymän alalaidassa: mitä saisi ja mitä se
   maksaa. Yksi napautus ostoon, yksi ohitukseen. Ei ponnahdusikkunaa.
2. **Asetuksissa.** Oma rivi, jossa lukee mitä Plus sisältää. Tämä on se
   paikka, josta maksuhaluinen löytää tarjouksen itse.
3. **Kokeilun päättyessä.** Kerran.

Kolmen ohituksen jälkeen kohtaa 1 ei näytetä enää automaattisesti. Asetusrivi
jää.

**Ei koskaan:** käynnistysponnahdus, piste välilehtipalkkiin, kruunuikonit
ominaisuuksien vieressä, riippulukot, laskuri jäljellä olevista päivistä
etusivulla, ilmoitus tarjouksesta.

## Tekninen rajanveto

Yksi paikka tietää vastauksen:

```kotlin
package fi.aalto.radio.plus

interface PlusAccess {
    /** Onko käyttäjällä voimassa oleva oikeus juuri nyt. */
    fun isActive(): Boolean
}
```

Kaksi sääntöä:

1. **Tarkistus tehdään ominaisuuden rajalla, ei sen sisällä.** Yksi
   `if (plus.isActive())` per ominaisuus, sen sisääntulokohdassa. Jos
   tarkistuksia ilmestyy sinne tänne, jokainen uusi ominaisuus joutuu
   miettimään maksumuurin uudelleen.
2. **Tarkistus epäonnistuu auki.** Jos Play ei vastaa tai verkkoa ei ole,
   maksanut käyttäjä pitää oikeutensa. Välimuisti kestää vähintään 14
   vuorokautta offline. Maksavan asiakkaan lukitseminen ulos lentokoneessa on
   pahempi virhe kuin muutaman ilmaiskäyttäjän päästäminen sisään.

Testattavuus: debug-buildissa oikeuden voi kääntää päälle ja pois ilman
ostoa, ja Play Consolen lisenssitestaajat ostavat oikeasti mutta ilmaiseksi.

## Mitä maksumuuri ei saa koskaan koskettaa

Nämä ovat samalla tasolla kuin AGENTS.md:n suojatut alijärjestelmät:

- **Soittopolku.** RadioPlayer, PlaybackService, AaltoSessionPlayer. Radio ei
  saa koskaan pysähtyä tai kieltäytyä soittamasta maksuoikeuden takia, ei
  edes silloin kun oikeuden tarkistus epäonnistuu.
- **Herätys.** Herätyksen pitää soida ja sen pitää olla sammutettavissa
  riippumatta maksuoikeuden tilasta. Herätys on lupaus, ei ominaisuus.
- **Android Auto ja widget.** Autossa ei näytetä ostokehotuksia. Ei koskaan.
- **Sync.** Omat asemat eivät saa jäädä laitteelle lukkojen taakse.

## Avoimet kysymykset

Nämä päätetään vasta kun ensimmäinen Plus-versio on julkaistu ja
konversiosta on oikeaa tietoa:

- Kuinka paljon pilvitallennus (jos se joskus tulee) hinnoitellaan
  tallennustilan mukaan, phonostarin malliin
- Tuleeko elinikäiselle hinnalle korotus kun paketti kasvaa, ja pitävätkö
  varhaiset ostajat oman hintansa (suositus: pitävät)
