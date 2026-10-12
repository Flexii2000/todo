package com.fherrmann.todo.dto;

import java.time.LocalDate;

/**
 * Beim Anlegen: Bereich, optional die Aufgabe darueber, Text, optional die
 * Faelligkeit, ein Link und eine Benachrichtigung. Beim Aendern ({@code PUT})
 * zaehlen Text und Faelligkeit - und eine fehlende Faelligkeit heisst dort:
 * keine mehr.
 *
 * <p>Den Link liest nur das Anlegen. Beim Aendern bleibt er, wie er ist:
 * aeltere Clients (Fokus auf dem Handy, ein offener Browser-Tab) kennen das
 * Feld nicht, schicken es nie mit und wuerden den Link sonst loeschen.
 *
 * <p>Die Benachrichtigung ebenso nur beim Anlegen: ein anderer Dienst, der
 * eine Aufgabe ablegt, kann Felix damit Bescheid geben (der Kalorienzaehler
 * bei Feature Requests von anderen).
 *
 * <p>{@code id} nur beim Anlegen und nur, wenn der Client die Kennung selbst
 * vergibt (Fokus, seit 12.10.): ohne Netz legt die App die Aufgabe an, zeigt
 * sie sofort und schickt sie spaeter - Haken und Aenderungen dazwischen
 * brauchen schon eine Kennung, und ein zweites Senden derselben Aufgabe darf
 * keine zweite anlegen. Fehlt sie, vergibt der Dienst eine.
 */
public record TodoRequest(String areaId, String parentId, String title, LocalDate dueAt, String link,
                          Notification notification, String id) {

    public TodoRequest(String areaId, String parentId, String title, LocalDate dueAt, String link) {
        this(areaId, parentId, title, dueAt, link, null, null);
    }

    public TodoRequest(String areaId, String parentId, String title, LocalDate dueAt, String link,
                       Notification notification) {
        this(areaId, parentId, title, dueAt, link, notification, null);
    }

    /** Was als Push in der Fokus-App erscheint; ein Tipp oeffnet den Link der Aufgabe. */
    public record Notification(String title, String body) {
    }
}
