package com.fherrmann.todo.model;

import java.time.Instant;

/**
 * Ein Bereich - Privat, Uni, Server. Im Browser eine Kachel, in der App eine
 * Seite. Frei anlegbar; die drei vom Anfang legt der Dienst selbst an.
 *
 * @param position Reihenfolge der Kacheln, 0 zuerst
 */
public record Area(String id, String name, int position, Instant createdAt) {
}
