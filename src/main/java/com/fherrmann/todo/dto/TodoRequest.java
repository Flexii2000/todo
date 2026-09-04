package com.fherrmann.todo.dto;

import java.time.LocalDate;

/**
 * Beim Anlegen: Bereich, optional die Aufgabe darueber, Text, optional die
 * Faelligkeit. Beim Aendern ({@code PUT}) zaehlen Text und Faelligkeit - und
 * eine fehlende Faelligkeit heisst dort: keine mehr.
 */
public record TodoRequest(String areaId, String parentId, String title, LocalDate dueAt) {
}
