#!/usr/bin/env python3
"""Kokoaa kuratoidun kanavatiedoston lahdeaineistosta.

Lukee katalogi/lahteet/*.json ja yhdistaa ne yhdeksi katalogi/fi.json
-tiedostoksi, jonka sovellus lukee.

Tunnisteet: jos asema loytyy Radio Browserista (tunnistetaan striimin
osoitteesta), se perii sen uuid:n, jotta olemassa olevat suosikit eivat
riko. Muuten asema saa oman tunnisteen muotoa "fi-<lahettaja>-<nimi>".

Alias-kentta radioBrowserIds kirjataan aina, vaikka sovellus ei viela
kayta sita: se on kartoitus tulevaa tunnistemigraatiota varten, ja se on
tarkin juuri nyt kun lahdeaineisto on tuore.

Ajo projektin juuresta:
    python scripts/kokoa_katalogi.py
"""

import csv
import json
import re
import sys
import unicodedata
from datetime import date
from pathlib import Path

JUURI = Path(__file__).resolve().parent.parent
LAHTEET = JUURI / "katalogi" / "lahteet"
RAPORTTI = JUURI / "katalogi" / "kanavatarkistus-FI.csv"
ULOS = JUURI / "katalogi" / "fi.json"

MUOTO_VERSIO = 1

# Lyhyt avain tunnisteeseen. Tunnisteet ovat pysyvia, joten pidetaan ne
# lyhyina ja luettavina: fi-yle-radio-1, ei fi-yle-yle-radio-1.
LAHETTAJA_AVAIN = {
    "Yle": "yle",
    "Bauer Media": "bauer",
    "Nelonen Media": "nelonen",
}


def siisti_tunniste(teksti: str) -> str:
    """'Yle Radio Suomi - Jyvaskyla' -> 'yle-radio-suomi-jyvaskyla'."""
    normaali = unicodedata.normalize("NFKD", teksti)
    ascii_teksti = "".join(c for c in normaali if not unicodedata.combining(c))
    ascii_teksti = ascii_teksti.replace("ä", "a").replace("ö", "o").replace("å", "a")
    pelkistetty = re.sub(r"[^a-zA-Z0-9]+", "-", ascii_teksti).strip("-").lower()
    return re.sub(r"-+", "-", pelkistetty)


def osoiteavain(url: str) -> str:
    """Vertailuavain: protokolla, www ja kyselyosa pois.

    Radio Browserissa sama asema esiintyy http:na ja https:na, eri
    kyselyparametreilla ja joskus www-etuliitteella. Nama ovat sama virta.
    """
    ilman = re.sub(r"^https?://", "", url.strip(), flags=re.I)
    ilman = re.sub(r"^www\.", "", ilman, flags=re.I)
    ilman = ilman.split("?", 1)[0].split("#", 1)[0]
    return ilman.rstrip("/").lower()


def lue_radiobrowser_kartta() -> dict[str, list[tuple[str, str]]]:
    """osoiteavain -> [(uuid, nimi)] kanavatarkistuksen raportista."""
    kartta: dict[str, list[tuple[str, str]]] = {}
    if not RAPORTTI.exists():
        print(f"  huom: {RAPORTTI.name} puuttuu, alias-kartoitus jaa tyhjaksi")
        return kartta
    with RAPORTTI.open(encoding="utf-8-sig", newline="") as f:
        for rivi in csv.DictReader(f):
            osoite = (rivi.get("Osoite") or "").strip()
            uuid = (rivi.get("Uuid") or "").strip()
            if not osoite or not uuid:
                continue
            kartta.setdefault(osoiteavain(osoite), []).append((uuid, rivi.get("Nimi", "")))
    return kartta


def lue_lahde(nimi: str) -> dict:
    polku = LAHTEET / nimi
    if not polku.exists():
        print(f"  huom: {nimi} puuttuu, ohitetaan")
        return {}
    return json.loads(polku.read_text(encoding="utf-8"))


def kerää_asemat(rb_kartta) -> list[dict]:
    asemat: list[dict] = []

    yle = lue_lahde("yle.json")
    for i, a in enumerate(yle.get("asemat", [])):
        asemat.append(
            rakenna(
                nimi=a["nimi"],
                lahettaja="Yle",
                osoitteet=[a["osoite"]],
                jarjestys=100 + i,
                rb_kartta=rb_kartta,
            )
        )

    bauer = lue_lahde("bauer.json")
    for i, a in enumerate(bauer.get("asemat", [])):
        osoitteet = [a["ensisijainen"]] + [
            u for u in a.get("varalla", []) if u != a["ensisijainen"]
        ]
        asemat.append(
            rakenna(
                nimi=a["nimi"],
                lahettaja="Bauer Media",
                osoitteet=osoitteet,
                jarjestys=200 + i,
                rb_kartta=rb_kartta,
            )
        )

    return asemat


def rakenna(nimi: str, lahettaja: str, osoitteet: list[str], jarjestys: int, rb_kartta) -> dict:
    # Kaikki taman aseman osoitteet, joille Radio Browserissa on vastine.
    uuidit: list[str] = []
    for url in osoitteet:
        for uuid, _ in rb_kartta.get(osoiteavain(url), []):
            if uuid not in uuidit:
                uuidit.append(uuid)

    avain = LAHETTAJA_AVAIN.get(lahettaja, siisti_tunniste(lahettaja))
    nimiosa = siisti_tunniste(nimi)
    # "Yle Radio 1" lahettajalla Yle -> "radio-1", ei "yle-radio-1".
    if nimiosa == avain:
        nimiosa = ""
    elif nimiosa.startswith(avain + "-"):
        nimiosa = nimiosa[len(avain) + 1 :]
    oma_tunniste = f"fi-{avain}-{nimiosa}".rstrip("-")
    # Peritty tunniste kun sellainen on: suosikit eivat riko.
    tunniste = uuidit[0] if uuidit else oma_tunniste

    return {
        "id": tunniste,
        "omaId": oma_tunniste,
        "nimi": nimi,
        "lahettaja": lahettaja,
        "osoitteet": osoitteet,
        "radioBrowserIds": uuidit,
        "jarjestys": jarjestys,
    }


def main() -> int:
    if not LAHTEET.exists():
        print(f"Lahdekansiota ei loydy: {LAHTEET}", file=sys.stderr)
        return 1

    print("Luetaan Radio Browser -kartoitus...")
    rb_kartta = lue_radiobrowser_kartta()
    print(f"  {len(rb_kartta)} osoitetta raportissa")

    print("Kootaan asemat...")
    asemat = kerää_asemat(rb_kartta)

    # Tunnisteiden pitaa olla yksiloivia. Jos kaksi asemaa perii saman
    # uuid:n (sama virta kahdella nimella), jalkimmainen saa oman.
    nahty: set[str] = set()
    for a in asemat:
        if a["id"] in nahty:
            print(f"  tunniste toistui, kaytetaan omaa: {a['nimi']}")
            a["id"] = a["omaId"]
        nahty.add(a["id"])

    peritty = sum(1 for a in asemat if a["id"] != a["omaId"])
    print(f"  {len(asemat)} asemaa, {peritty} peri tunnisteen Radio Browserista")

    ULOS.parent.mkdir(parents=True, exist_ok=True)
    ULOS.write_text(
        json.dumps(
            {
                "versio": MUOTO_VERSIO,
                "paivitetty": date.today().isoformat(),
                "maa": "FI",
                "asemat": asemat,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"Kirjoitettu: {ULOS}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
