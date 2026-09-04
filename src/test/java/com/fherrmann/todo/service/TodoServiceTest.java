package com.fherrmann.todo.service;

import com.fherrmann.todo.dto.AreaRequest;
import com.fherrmann.todo.dto.Board;
import com.fherrmann.todo.dto.TodoRequest;
import com.fherrmann.todo.model.Area;
import com.fherrmann.todo.model.Todo;
import com.fherrmann.todo.model.TodoData;
import com.fherrmann.todo.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TodoServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final AtomicReference<TodoData> stored = new AtomicReference<>();
    private TodoService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        TodoRepository repository = mock(TodoRepository.class);
        when(repository.load()).thenAnswer(inv -> stored.get());
        doAnswer(inv -> { stored.set(inv.getArgument(0)); return null; })
                .when(repository).save(any());
        clock = Clock.fixed(NOW, BERLIN);
        service = new TodoService(repository, clock, 3);
        stored.set(new TodoData(List.of(
                new Area("privat", "Privat", 0, NOW.minus(Duration.ofDays(30))),
                new Area("uni", "Uni", 1, NOW.minus(Duration.ofDays(30)))),
                List.of()));
    }

    private String firstTodoId(Board board, int areaIndex) {
        return board.areas().get(areaIndex).todos().get(0).id();
    }

    @Test
    void anlegenAbhakenUndDerHakenBleibtDreiTageSichtbar() {
        Board board = service.createTodo(new TodoRequest("privat", null, "Rasen mähen"));
        assertEquals(1, board.areas().get(0).openCount());
        String id = firstTodoId(board, 0);

        board = service.done(id);
        Board.TodoView done = board.areas().get(0).todos().get(0);
        assertNotNull(done.doneAt());
        assertEquals(NOW.plus(Duration.ofDays(3)), done.visibleUntil());
        assertEquals(0, board.areas().get(0).openCount());

        // Drei Tage spaeter: weg vom Brett, aber nicht aus der Datei.
        TodoService later = new TodoService(mockRepo(), Clock.fixed(NOW.plus(Duration.ofDays(3)), BERLIN), 3);
        Board after = later.board(false);
        assertTrue(after.areas().get(0).todos().isEmpty());
        assertEquals(1, after.hiddenDoneCount());
        assertEquals(1, later.board(true).areas().get(0).todos().size(), "mit all=true ist sie wieder da");
        assertEquals(1, stored.get().todos().size(), "geloescht wird nichts");
    }

    private TodoRepository mockRepo() {
        TodoRepository repository = mock(TodoRepository.class);
        when(repository.load()).thenAnswer(inv -> stored.get());
        doAnswer(inv -> { stored.set(inv.getArgument(0)); return null; }).when(repository).save(any());
        return repository;
    }

    @Test
    void hakenZurueckAuchNachDemVerschwinden() {
        Board board = service.createTodo(new TodoRequest("privat", null, "Steuer"));
        String id = firstTodoId(board, 0);
        service.done(id);
        TodoService later = new TodoService(mockRepo(), Clock.fixed(NOW.plus(Duration.ofDays(10)), BERLIN), 3);
        Board reopened = later.reopen(id);
        assertNull(reopened.areas().get(0).todos().get(0).doneAt());
        assertEquals(1, reopened.areas().get(0).openCount());
    }

    @Test
    void unteraufgabenHaengenAnIhrerAufgabe() {
        Board board = service.createTodo(new TodoRequest("uni", null, "Hausarbeit"));
        String parent = firstTodoId(board, 1);
        board = service.createTodo(new TodoRequest("uni", parent, "Gliederung"));
        board = service.createTodo(new TodoRequest("uni", parent, "Quellen"));
        Board.TodoView top = board.areas().get(1).todos().get(0);
        assertEquals(2, top.children().size());
        assertEquals(3, board.areas().get(1).openCount());

        // Keine Unteraufgabe der Unteraufgabe.
        String child = top.children().get(0).id();
        assertThrows(ResponseStatusException.class,
                () -> service.createTodo(new TodoRequest("uni", child, "zu tief")));
        // Und nicht in einem anderen Bereich.
        assertThrows(ResponseStatusException.class,
                () -> service.createTodo(new TodoRequest("privat", parent, "falscher Bereich")));

        // Loeschen nimmt die Kinder mit.
        board = service.deleteTodo(parent);
        assertTrue(board.areas().get(1).todos().isEmpty());
        assertTrue(stored.get().todos().isEmpty());
    }

    @Test
    void erledigteAufgabeNimmtIhreUnteraufgabenMitVomBrett() {
        Board board = service.createTodo(new TodoRequest("uni", null, "Hausarbeit"));
        String parent = firstTodoId(board, 1);
        service.createTodo(new TodoRequest("uni", parent, "Gliederung"));
        service.done(parent);
        TodoService later = new TodoService(mockRepo(), Clock.fixed(NOW.plus(Duration.ofDays(4)), BERLIN), 3);
        Board after = later.board(false);
        assertTrue(after.areas().get(1).todos().isEmpty());
        // Die offene Unteraufgabe zaehlt nicht mehr als offen - sie haengt an
        // einer Aufgabe, die vom Brett ist. Im Archiv steht sie weiter. Als
        // "aeltere erledigte" zaehlt aber nur, was erledigt ist: die eine.
        assertEquals(0, after.areas().get(1).openCount());
        assertEquals(1, after.hiddenDoneCount());
        assertEquals(1, later.board(true).areas().get(1).todos().get(0).children().size());
    }

    @Test
    void offeneZuerstDannErledigte() {
        Board board = service.createTodo(new TodoRequest("privat", null, "A"));
        board = service.createTodo(new TodoRequest("privat", null, "B"));
        String a = board.areas().get(0).todos().get(0).id();
        board = service.done(a);
        List<Board.TodoView> todos = board.areas().get(0).todos();
        assertEquals("B", todos.get(0).title());
        assertEquals("A", todos.get(1).title());
    }

    @Test
    void bereicheAnlegenUmbenennenLoeschen() {
        Board board = service.createArea(new AreaRequest("  Server "));
        assertEquals("Server", board.areas().get(2).name());
        assertEquals(2, board.areas().get(2).position());
        assertThrows(ResponseStatusException.class, () -> service.createArea(new AreaRequest("server")));
        assertThrows(ResponseStatusException.class, () -> service.createArea(new AreaRequest(" ")));

        String id = board.areas().get(2).id();
        service.createTodo(new TodoRequest(id, null, "nginx"));
        board = service.renameArea(id, new AreaRequest("Heimserver"));
        assertEquals("Heimserver", board.areas().get(2).name());
        board = service.deleteArea(id);
        assertEquals(2, board.areas().size());
        assertTrue(stored.get().todos().isEmpty(), "die Aufgaben des Bereichs gehen mit");
    }

    @Test
    void leererTextWirdAbgelehnt() {
        assertThrows(ResponseStatusException.class,
                () -> service.createTodo(new TodoRequest("privat", null, "   ")));
        assertThrows(ResponseStatusException.class,
                () -> service.createTodo(new TodoRequest("gibtsnicht", null, "x")));
    }
}
