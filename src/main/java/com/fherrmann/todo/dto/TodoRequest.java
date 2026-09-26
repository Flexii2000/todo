package com.fherrmann.todo.dto;

import java.time.LocalDate;

/**
 * Beim Anlegen: Bereich, optional die Aufgabe darueber, Text, optional die
 * Faelligkeit und ein Link. Beim Aendern ({@code PUT}) zaehlen Text und
 * Faelligkeit - und eine fehlende Faelligkeit heisst dort: keine mehr.
 *
 * <p>Den Link liest nur das Anlegen. Beim Aendern bleibt er, wie er ist:
 * aeltere Clients (Fokus auf dem Handy, ein offener Browser-Tab) kennen das
 * Feld nicht, schicken es nie mit und wuerden den Link sonst loeschen.
 */
public record TodoRequest(String areaId, String parentId, String title, LocalDate dueAt, String link) {
}
