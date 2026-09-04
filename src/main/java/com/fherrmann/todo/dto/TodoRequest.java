package com.fherrmann.todo.dto;

/** Beim Anlegen: Bereich, optional die Aufgabe darueber, und der Text. Beim Umbenennen zaehlt nur der Text. */
public record TodoRequest(String areaId, String parentId, String title) {
}
