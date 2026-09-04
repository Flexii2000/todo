package com.fherrmann.todo.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Das ganze Brett, fertig sortiert - jede Antwort des Dienstes ist eins.
 *
 * <p>Eine Form fuer alles, statt je Aenderung ein anderes Stueck: die
 * Weboberflaeche zeichnet daraus neu, die App ersetzt ihren Stand. Beide
 * muessen nichts zusammensetzen.
 *
 * @param hiddenDoneCount erledigte Aufgaben, die aelter als die Sichtfrist
 *                        sind und deshalb nicht mitkommen - damit die
 *                        Oberflaeche „12 ältere erledigte" anbieten kann
 */
public record Board(List<AreaView> areas, boolean includesHidden, int hiddenDoneCount, Instant now) {

    public record AreaView(String id, String name, int position, int openCount,
                           int hiddenDoneCount, List<TodoView> todos) {
    }

    /**
     * @param visibleUntil bei erledigten: bis wann sie noch zu sehen ist.
     *                     Damit kann die Oberflaeche „verschwindet morgen" sagen.
     */
    public record TodoView(String id, String title, Instant createdAt, Instant doneAt,
                           Instant visibleUntil, LocalDate dueAt, List<ReminderView> reminders,
                           List<TodoView> children) {
    }

    public record ReminderView(String id, Instant at, Instant sentAt) {
    }
}
