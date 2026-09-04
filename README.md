# To-Do

Aufgaben in Bereichen — Privat, Uni, Server, und was sonst noch kommt. Ein
Spring-Boot-Dienst unter `fherrmann.com/todo` im privaten Bereich: im Browser
stehen die Bereiche als **Kacheln nebeneinander** (auf dem Handy
untereinander), in der iOS-App Fokus (`~/Server-Projects/cockpit-ios`) als
Seiten im selben Tab.

## Was es kann

- **Bereiche** anlegen, umbenennen, löschen (mit allen Aufgaben). Die drei vom
  Anfang legt der Dienst beim ersten Start an.
- **Aufgaben** mit **Unteraufgaben** — genau eine Ebene tief.
- **Abhaken.** Eine erledigte Aufgabe bleibt **drei Tage durchgestrichen**
  stehen und verschwindet dann von selbst. Gelöscht wird sie dabei nicht:
  sie steht weiter in der Datei, „ältere erledigte anzeigen" holt sie zurück,
  und der Haken lässt sich jederzeit wieder lösen — auch danach.
- Offene Aufgaben oben (älteste zuerst), erledigte darunter (zuletzt
  erledigte zuerst).

## REST-API

Alles unter `/todo/api`, hinter dem `fh_private`-Cookie (sonst 403). **Jede
Antwort ist das ganze Brett** (`Board`).

| Methode | Pfad | Was |
|---|---|---|
| GET | `/api/board?all=false` | alle Bereiche mit sichtbaren Aufgaben; `all=true` auch die älteren erledigten |
| POST | `/api/areas` | `{name}` → 201 |
| PUT | `/api/areas/{id}` | `{name}` |
| DELETE | `/api/areas/{id}` | samt Aufgaben |
| POST | `/api/todos` | `{areaId, parentId?, title}` → 201 |
| PUT | `/api/todos/{id}` | `{title}` |
| POST | `/api/todos/{id}/done` | abhaken (idempotent, der erste Zeitpunkt bleibt) |
| DELETE | `/api/todos/{id}/done` | Haken zurück |
| DELETE | `/api/todos/{id}` | wirklich löschen, samt Unteraufgaben |

```
Board     areas[], includesHidden, hiddenDoneCount, now
AreaView  id, name, position, openCount, hiddenDoneCount, todos[]
TodoView  id, title, createdAt, doneAt | null, visibleUntil | null, children[]
```

Fehler kommen als Klartext (`Eine Aufgabe braucht einen Text.`).

## Daten

`data/todo.json` — Bereiche und Aufgaben. Geschrieben wird erst daneben, dann
umbenannt. Kein Archiv-Mechanismus: „verschwunden" ist eine Frage der
Sichtfrist beim Lesen, nicht des Speicherns.

## Betrieb

`todo.service` (User `todo`, `/opt/todo`) auf `127.0.0.1:48210`, nginx
`location /todo/` unter `fherrmann.com` mit Privat-Gate — ein Pfad, keine
Subdomain. Die Karte auf der Landing Page steht im privaten Block.

```bash
# einmalig
ssh HeimServerRemote 'git clone git@github.com:Flexii2000/todo.git ~/services/todo'
ssh -t HeimServerRemote '~/services/todo/deploy/setup-todo.sh'
# später
ssh -t HeimServerRemote '~/services/todo/deploy/update-todo.sh'
```

## Bauen

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew test
```

Java 25, Spring Boot 4, keine Datenbank — wie `habits`.
