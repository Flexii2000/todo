#!/usr/bin/env bash
set -euo pipefail

# Deployt eine neue Version von Todo (fherrmann.com/todo).
#
#     ssh HeimServerLocal '~/services/todo/deploy/update-todo.sh'
#
# Baut auf dem Server aus ~/services/todo und installiert nach /opt/todo.
# Der Build laeuft als flexii, installiert wird ueber das Root-Skript
# update-service.sh - ohne Passwort, also auch fuer Agenten. NICHT mit sudo starten.
#
# /opt/todo/data/ wird NICHT angefasst: todo.json ist der Live-Bestand.

BUILD_DIR="$HOME/services/todo"
JAR="$BUILD_DIR/build/libs/Todo-0.0.1-SNAPSHOT.jar"
JAVA="/opt/java/jdk-25.0.1+8/bin/java"
STAGING="$HOME/staging/todo"
ROOT_SCRIPT="/usr/local/sbin/update-service.sh"

[[ $EUID -ne 0 ]] || { echo "Bitte OHNE sudo starten - das Skript ruft sudo selbst auf." >&2; exit 1; }

echo "[1/4] git pull ..."
git -C "$BUILD_DIR" pull --ff-only

echo "[2/4] Jar bauen ..."
(cd "$BUILD_DIR" && JAVA_HOME="$(dirname "$(dirname "$JAVA")")" ./gradlew bootJar --quiet)
[[ -f "$JAR" ]] || { echo "    FEHLER: $JAR fehlt." >&2; exit 1; }

echo "[3/4] Jar bereitlegen ..."
mkdir -p "$STAGING"
cp "$JAR" "$STAGING/app.jar"

echo "[4/4] Installieren ..."
# Ohne Passwort ueber /usr/local/sbin/update-service.sh und die Regel
# /etc/sudoers.d/23-agent-deploy (~/scripts/setup-agent-deploy.sh, SERVER-CONTEXT.md):
# sichern, installieren, neu starten, pruefen, notfalls zurueckrollen.
sudo "$ROOT_SCRIPT" todo
