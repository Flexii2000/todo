package com.fherrmann.todo.model;

import java.util.List;

/** Alles, was in {@code todo.json} steht. */
public record TodoData(List<Area> areas, List<Todo> todos) {

    public TodoData {
        areas = areas == null ? List.of() : List.copyOf(areas);
        todos = todos == null ? List.of() : List.copyOf(todos);
    }
}
