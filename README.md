-WortKarten
+WortKarten 2
# WortKarten 2.0 – German vocabulary trainer (ad-free, offline)

No ads, no account, no internet permission. All data stays on your phone.

## Features
- Card statuses: 🆕 Neu · ✅ Kenne ich schon · 🔁 Üben · 🟢 Gelernt
- Spaced repetition in BOTH directions (Deutsch → Übersetzung and Übersetzung → Deutsch).
  A word becomes 🟢 Gelernt when both directions reach a 14-day interval.
- B1–C1 first: new words come B1 → B2 → C1, with 20 % A1–A2 mixed in (adjustable 0–50 %)
- Every word: level, style (neutral / umgangssprachlich / formell), example sentence + translation, usage note,
  plural for nouns, verb forms and cases where useful
- der / die / das in colour (blue / red / green) + article quiz
- Buttons: Lernen starten · Nur Üben · Nur Neu (choose levels)
- Word list with filters (status, level, subtopic, search); change any word's status by hand
- Topics on/off; "A1–A2 als bekannt markieren" per topic; Technik topics off by default
- Add words from any app: select text → "Zu WortKarten", or Share → WortKarten
- Daily reminder (local notification), streak, 7-day chart, progress per level
- Progress saved after every card; kept when word lists are updated
- Backup / restore (JSON, incl. progress) and CSV import / export

## Build & install
1. Install Android Studio: https://developer.android.com/studio
2. Unzip → Android Studio → File › Open → select the `WortKarten` folder. Wait for Gradle sync.
3. Phone: Settings › About phone › tap "Build number" 7× → Developer options → USB debugging on.
4. Connect via USB, select the phone, press ▶ Run.
   Or: Build › Build APK(s) → copy `app/build/outputs/apk/debug/app-debug.apk` to the phone and install.

Updating later: install the new version over the old one (don't uninstall) – your progress stays.

## Word lists
Files in `app/src/main/assets/words/*.csv` are loaded automatically (sorted by file name).
Format (UTF-8, semicolon-separated, header line):
```
german;plural;grammar;translation;category;subtopic;level;register;example;example_en;note
der Termin;die Termine;;appointment;Alltag & Zeit;Planen;A1;;Ich habe morgen einen Termin.;I have an appointment tomorrow.;
```
- `level`: A1, A2, B1, B2, C1  ·  `register`: empty (neutral), `umg` or `formell`
- A word is identified by category + German word. Changing the translation/example in a
  new version updates the card but keeps your progress.
- Categories starting with "Technik" are switched off by default.

### Included so far
| File | Category | Words |
|---|---|---|
| 01_alltagsgespraeche.csv | Alltagsgespräche (10 subtopics) | 326 |
| 02_verben.csv | Verben (10 subtopics) | 302 |
| 03_arbeit_beruf.csv | Arbeit & Beruf (12 subtopics) | 300 |
| 04_behoerden_recht.csv | Behörden & Recht (9 subtopics) | 197 |
| 05_bank_steuern_versicherung.csv | Bank, Steuern & Versicherung (7 subtopics) | 150 |
| 06_wohnung_haushalt.csv | Wohnung & Haushalt (11 subtopics) | 250 |
| 07_essen_trinken.csv | Essen & Trinken (11 subtopics) | 300 |
| 08_koerper_gesundheit.csv | Körper & Gesundheit (9 subtopics) | 250 |
| 09_einkaufen_kleidung.csv | Einkaufen & Kleidung (8 subtopics) | 200 |
| 10_stadt_verkehr.csv | Stadt & Verkehr (8 subtopics) | 200 |
| 11_reisen_urlaub.csv | Reisen & Urlaub (7 subtopics) | 200 |
| 12_auto_mobilitaet.csv | Auto & Mobilität (5 subtopics) | 150 |
| 13_gefuehle_charakter.csv | Gefühle & Charakter (7 subtopics) | 200 |
| 14_menschen_familie.csv | Menschen & Familie (7 subtopics) | 201 |
| 15_alltag_zeit.csv | Alltag & Zeit (9 subtopics) | 250 |
| **Total** | | **3,476** |

## If pronunciation is silent
Android Settings › Text-to-speech output → Google engine → install German voice data.
