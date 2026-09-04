# Arbeitsregeln für `todo`

Ein Spring-Boot-Dienst für Felix' To-Do-Listen: Bereiche (Privat, Uni,
Server, …) mit Aufgaben und Unteraufgaben. Läuft als `fherrmann.com/todo` im
privaten Bereich — **mit** Weboberfläche (Kacheln nebeneinander) und als
Tab in der iOS-App Fokus (`~/Server-Projects/cockpit-ios`).

## Vor dem ersten Handgriff

1. `README.md` lesen — Regeln und API stehen dort.
2. `../SERVER-CONTEXT.md` für Deploy, nginx, Port.
3. Die App liest `Board` so, wie er hier serialisiert wird. Wer ein Feld
   umbenennt, zieht `../cockpit-ios/Shared/TodoModels.swift` mit.

## Die Regeln, die nicht offensichtlich sind

- **Erledigtes verschwindet, wird aber nicht gelöscht.** Drei Tage
  durchgestrichen sichtbar (`todo.done-visible-days`), dann fällt es aus dem
  Brett. `GET /api/board?all=true` holt es zurück; `DELETE /api/todos/{id}`
  ist der einzige Weg, wirklich zu löschen.
- **Eine Ebene Unteraufgaben**, nicht mehr. Und eine Unteraufgabe hängt am
  Schicksal ihrer Aufgabe: ist die vom Brett, ist sie es auch.
- **Jede Antwort ist das ganze Brett.** Keine Teilantworten, die ein Client
  zusammensetzen müsste.
- **Erinnerungen schickt der Dienst**, nicht die App (`ReminderScheduler`,
  jede Minute). Eine erledigte Aufgabe erinnert an nichts mehr; eine um mehr
  als eine Stunde verpasste Erinnerung ist verpasst. Beides wird als
  „geschickt" abgehakt, sonst bliebe sie ewig fällig.

## Bauen und prüfen

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew test
./gradlew bootRun     # lokal: http://127.0.0.1:48210/todo/, Cookie fh_private=changeme-local-token
```

**Nie behaupten, etwas baue, ohne `./gradlew test` gelaufen zu haben.**

## Konventionen

Wie bei `habits`: Bezeichner englisch, Kommentare deutsch; kein Token im
Repo (`/etc/todo.env`, vom Setup-Skript aus der nginx-Map geschrieben);
Fehler als Klartext; committen **und** pushen — der Server baut aus dem Repo.
Die Weboberfläche ist Vanilla-JS ohne Framework (`static/app.js`); sie
zeichnet nur, was der Dienst liefert.
