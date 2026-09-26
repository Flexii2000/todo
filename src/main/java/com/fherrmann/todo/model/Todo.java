package com.fherrmann.todo.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Eine Aufgabe.
 *
 * @param parentId gesetzt bei einer Unteraufgabe - genau eine Ebene tief.
 *                 Eine Unteraufgabe einer Unteraufgabe gibt es nicht; wer das
 *                 braucht, hat eher einen neuen Bereich.
 * @param link     eine Adresse zur Aufgabe (nur http/https), oder {@code null} -
 *                 etwa die Wunsch-Seite, von der eine automatisch angelegte
 *                 Aufgabe stammt. Gesetzt wird er nur beim Anlegen.
 * @param dueAt    Faelligkeit, oder {@code null}. Nur eine Anzeige - ueberfaellig
 *                 heisst rot, nicht mehr.
 * @param reminders beliebig viele Zeitpunkte, zu denen der Dienst eine Push-
 *                 Nachricht schickt - solange die Aufgabe offen ist
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
        String link,
        Instant createdAt,
        Instant doneAt,
        LocalDate dueAt,
        List<Reminder> reminders) {

    public Todo {
        // Aeltere Dateien kennen das Feld nicht - dann eben keine. Fuer den
        // Link gilt dasselbe, er bleibt dann einfach null.
        reminders = reminders == null ? List.of() : List.copyOf(reminders);
    }

    public boolean isDone() {
        return doneAt != null;
    }

    public Todo withDoneAt(Instant doneAt) {
        return new Todo(id, areaId, parentId, title, link, createdAt, doneAt, dueAt, reminders);
    }

    /** Der Link bleibt dabei, wie er ist - siehe {@code TodoService#update}. */
    public Todo withTitleAndDueAt(String title, LocalDate dueAt) {
        return new Todo(id, areaId, parentId, title, link, createdAt, doneAt, dueAt, reminders);
    }

    public Todo withReminders(List<Reminder> reminders) {
        return new Todo(id, areaId, parentId, title, link, createdAt, doneAt, dueAt, reminders);
    }
}
