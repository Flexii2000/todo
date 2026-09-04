package com.fherrmann.todo.model;

import java.time.Instant;

/**
 * Eine Aufgabe.
 *
 * @param parentId gesetzt bei einer Unteraufgabe - genau eine Ebene tief.
 *                 Eine Unteraufgabe einer Unteraufgabe gibt es nicht; wer das
 *                 braucht, hat eher einen neuen Bereich.
 * @param doneAt   wann sie abgehakt wurde, oder {@code null}. Sie bleibt
 *                 danach drei Tage durchgestrichen sichtbar und verschwindet
 *                 dann - geloescht wird sie dabei nicht, sie steht weiter in
 *                 der Datei und im Archiv.
 */
public record Todo(
        String id,
        String areaId,
        String parentId,
        String title,
        Instant createdAt,
        Instant doneAt) {

    public boolean isDone() {
        return doneAt != null;
    }

    public Todo withDoneAt(Instant doneAt) {
        return new Todo(id, areaId, parentId, title, createdAt, doneAt);
    }

    public Todo withTitle(String title) {
        return new Todo(id, areaId, parentId, title, createdAt, doneAt);
    }
}
