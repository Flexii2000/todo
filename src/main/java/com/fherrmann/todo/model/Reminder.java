package com.fherrmann.todo.model;

import java.time.Instant;

/**
 * Eine Erinnerung zu einer Aufgabe - beliebig viele je Aufgabe.
 *
 * @param at     wann sie kommen soll
 * @param sentAt wann sie rausging, oder {@code null}. Bleibt stehen, damit
 *               dieselbe Erinnerung nicht bei jedem Durchlauf erneut geht.
 */
public record Reminder(String id, Instant at, Instant sentAt) {

    public Reminder withSentAt(Instant sentAt) {
        return new Reminder(id, at, sentAt);
    }
}
