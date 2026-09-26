package com.fherrmann.todo.repository;

import com.fherrmann.todo.model.Todo;
import com.fherrmann.todo.model.TodoData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
                new Todo("t1", area, null, "Rasen", null, Instant.now(CLOCK), null, null, List.of()),
                new Todo("t2", area, "t1", "Kanten", "https://fherrmann.com/feature-requests/7",
                        Instant.now(CLOCK), Instant.now(CLOCK), null,
                        List.of(new com.fherrmann.todo.model.Reminder("r1", Instant.now(CLOCK), null)))));
        repository.save(mitAufgabe);
        assertEquals(mitAufgabe, repository.load());
    }

    @Test
    void alteDateiOhneLinkLaedtWeiter() throws IOException {
        // So sah todo.json vor den Links aus - der erste Eintrag noch ohne
        // Erinnerungen, beide ohne Link.
        Path file = dir.resolve("todo.json");
        Files.writeString(file, """
                {
                  "areas" : [ { "id" : "srv", "name" : "Server", "position" : 2, "createdAt" : "2026-09-04T09:00:00Z" } ],
                  "todos" : [ {
                    "id" : "t1", "areaId" : "srv", "parentId" : null, "title" : "Healthy",
                    "createdAt" : "2026-09-04T09:30:00Z", "doneAt" : null, "dueAt" : "2026-09-30"
                  }, {
                    "id" : "t2", "areaId" : "srv", "parentId" : "t1", "title" : "Kacheln",
                    "createdAt" : "2026-09-04T09:31:00Z", "doneAt" : null, "dueAt" : null,
                    "reminders" : [ { "id" : "r1", "at" : "2026-09-05T08:00:00Z", "sentAt" : null } ]
                  } ]
                }
                """);
        TodoData data = new TodoRepository(file.toString(), MAPPER, CLOCK).load();
        assertEquals(2, data.todos().size());
        assertNull(data.todos().get(0).link());
        assertNull(data.todos().get(1).link());
        assertEquals("Healthy", data.todos().get(0).title());
        assertEquals(LocalDate.of(2026, 9, 30), data.todos().get(0).dueAt());
        assertEquals(List.of(), data.todos().get(0).reminders());
        assertEquals(1, data.todos().get(1).reminders().size());
    }
}
