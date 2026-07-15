# WeightSonar

Tägliche Ernährung erfassen und gegen den eigenen Energiebedarf bilanzieren —
als Android-App, keine ärztliche Beratung. Fotos statt Tippen: Die KI erkennt
Gerichte oder liest Nährwerttabellen ab; gerechnet wird deterministisch in
der App.

## Was die App macht

- **Profile:** Name, Geschlecht, Geburtsdatum, Größe je Person; das aktuelle
  Gewicht kommt aus dem jüngsten Tagesgewicht. Daraus berechnet die App BMI
  und Grundumsatz (Mifflin-St Jeor). Alle Daten bleiben lokal auf dem Gerät.
- **Essen erfassen:** Gericht fotografieren (KI erkennt Positionen und
  schätzt Mengen in Gramm) oder Nährwerttabelle fotografieren (KI liest die
  Werte pro 100 g ab, die gegessene Menge trägt der Nutzer ein). Unsicher
  gelesene Werte sind mit ⚠️ markiert — nie geraten — und antippbar
  korrigierbar. Manuelles Erfassen geht auch ganz ohne Foto und ohne Key.
- **Tagesbilanz mit Ampel:** Budget = Grundumsatz + gebuchte Aktivitäten
  (Katalog mit editierbaren Richtwerten, freie Einträge, optionale
  KI-Schätzung des Verbrauchs). Ampel: 🟢 im Budget, 🟡 bis 250 kcal
  darüber, 🔴 deutlich darüber.
- **Kalender:** Monatsraster mit Ampel-Quadraten, rotes ✕ für vergangene
  Tage ohne Daten; Tap öffnet den Tag zum Nachpflegen — die Vergangenheit
  bleibt editierbar.
- **Auswertung:** frei wählbare Zeiträume mit eigenen Canvas-Charts
  (Kalorien-Balken gegen das Budget, Gewichtskurve), Summen/Durchschnitte
  aller Nährwerte; Export als Markdown mit eingebetteter Vorschau (Markwon),
  Speichern über SAF und Teilen per Share-Sheet.

## Technik

- Kotlin, ViewBinding, Material Components; gemeinsame Bibliothek
  `common/android` (Theme, Toolbar, komplette Provider-/Key-Verwaltung mit
  Kostenzähler, Update-Erkennung).
- Direkte HTTP-Anbindung (OkHttp) an Anthropic Messages API bzw. OpenAI
  Chat Completions; Kamera ohne eigene Berechtigung über den System-Intent.
- Diagramme und Kalender als eigene `onDraw`-Views — keine Fremd-UI-Lib.
- Datenhaltung als JSON in den App-Dateien (eine Datei je Person für die
  Tage); minSdk 26, targetSdk 34.

## Build

CI baut per `.github/workflows/build-weightsonar.yml` und veröffentlicht
die APKs samt Versions-Manifest im GitHub-Release `weightsonar-latest`;
`deploy-website.yml` synct beides nach [appsonar.de](https://appsonar.de).
Die App prüft beim Start und über „Nach Update suchen“ gegen das Manifest
auf neue Versionen (gemeinsamer `UpdateChecker` aus `common/android`).
