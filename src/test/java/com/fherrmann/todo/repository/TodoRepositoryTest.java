package com.fherrmann.todo.repository;

import com.fherrmann.todo.model.Todo;
import com.fherrmann.todo.model.TodoData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TodoRepositoryTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T10:00:00Z"), ZoneId.of("Europe/Berlin"));

    @TempDir
    Path dir;

    @Test
    void ersteInstallationBeginntMitDreiBereichen() {
        TodoRepository repository = new TodoRepository(dir.resolve("todo.json").toString(), MAPPER, CLOCK);
        TodoData data = repository.load();
        assertEquals(List.of("Privat", "Uni", "Server"), data.areas().stream().map(a -> a.name()).toList());
    }

    @Test
    void speichernUndWiederLesen() {
        TodoRepository repository = new TodoRepository(dir.resolve("todo.json").toString(), MAPPER, CLOCK);
        TodoData data = repository.load();
        String area = data.areas().get(0).id();
        TodoData mitAufgabe = new TodoData(data.areas(), List.of(
                new Todo("t1", area, null, "Rasen", Instant.now(CLOCK), null),
                new Todo("t2", area, "t1", "Kanten", Instant.now(CLOCK), Instant.now(CLOCK))));
        repository.save(mitAufgabe);
        assertEquals(mitAufgabe, repository.load());
    }
}
