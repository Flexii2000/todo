package com.fherrmann.todo.service;

import com.fherrmann.todo.dto.AreaRequest;
import com.fherrmann.todo.dto.Board;
import com.fherrmann.todo.dto.ReminderRequest;
import com.fherrmann.todo.dto.TodoRequest;
import com.fherrmann.todo.model.Area;
import com.fherrmann.todo.model.Todo;
import com.fherrmann.todo.model.TodoData;
import com.fherrmann.todo.push.PushNotifier;
import com.fherrmann.todo.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TodoServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final AtomicReference<TodoData> stored = new AtomicReference<>();
    private final PushNotifier notifier = mock(PushNotifier.class);
    private TodoService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        TodoRepository repository = mock(TodoRepository.class);
        when(repository.load()).thenAnswer(inv -> stored.get());
        doAnswer(inv -> { stored.set(inv.getArgument(0)); return null; })
                .when(repository).save(any());
        clock = Clock.fixed(NOW, BERLIN);
        service = new TodoService(repository, notifier, clock, 3);
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
        Board board = service.createTodo(new TodoRequest("privat", null, "Rasen mähen", null, null));
        assertEquals(1, board.areas().get(0).openCount());
        String id = firstTodoId(board, 0);

        board = service.done(id);
        Board.TodoView done = board.areas().get(0).todos().get(0);
        assertNotNull(done.doneAt());
        assertEquals(NOW.plus(Duration.ofDays(3)), done.visibleUntil());
        assertEquals(0, board.areas().get(0).openCount());

        // Drei Tage spaeter: weg vom Brett, aber nicht aus der Datei.
        TodoService later = new TodoService(mockRepo(), notifier, Clock.fixed(NOW.plus(Duration.ofDays(3)), BERLIN), 3);
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
        Board board = service.createTodo(new TodoRequest("privat", null, "Steuer", null, null));
        String id = firstTodoId(board, 0);
        service.done(id);
        TodoService later = new TodoService(mockRepo(), notifier, Clock.fixed(NOW.plus(Duration.ofDays(10)), BERLIN), 3);
        Board reopened = later.reopen(id);
        assertNull(reopened.areas().get(0).todos().get(0).doneAt());
        assertEquals(1, reopened.areas().get(0).openCount());
    }

    @Test
    void unteraufgabenHaengenAnIhrerAufgabe() {
        Board board = service.createTodo(new TodoRequest("uni", null, "Hausarbeit", null, null));
        String parent = firstTodoId(board, 1);
        board = service.createTodo(new TodoRequest("uni", parent, "Gliederung", null, null));
        board = service.createTodo(new TodoRequest("uni", parent, "Quellen", null, null));
        Board.TodoView top = board.areas().get(1).todos().get(0);
        assertEquals(2, top.children().size());
        assertEquals(3, board.areas().get(1).openCount());

        // Keine Unteraufgabe der Unteraufgabe.
        String child = top.children().get(0).id();
        assertThrows(ResponseStatusException.class,
                () -> service.createTodo(new TodoRequest("uni", child, "zu tief", null, null)));
        // Und nicht in einem anderen Bereich.
        assertThrows(ResponseStatusException.class,
                () -> service.createTodo(new TodoRequest("privat", parent, "falscher Bereich", null, null)));

        // Loeschen nimmt die Kinder mit.
        board = service.deleteTodo(parent);
        assertTrue(board.areas().get(1).todos().isEmpty());
        assertTrue(stored.get().todos().isEmpty());
    }

    @Test
    void erledigteAufgabeNimmtIhreUnteraufgabenMitVomBrett() {
        Board board = service.createTodo(new TodoRequest("uni", null, "Hausarbeit", null, null));
        String parent = firstTodoId(board, 1);
        service.createTodo(new TodoRequest("uni", parent, "Gliederung", null, null));
        service.done(parent);
        TodoService later = new TodoService(mockRepo(), notifier, Clock.fixed(NOW.plus(Duration.ofDays(4)), BERLIN), 3);
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
        Board board = service.createTodo(new TodoRequest("privat", null, "A", null, null));
        board = service.createTodo(new TodoRequest("privat", null, "B", null, null));
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
        service.createTodo(new TodoRequest(id, null, "nginx", null, null));
        board = service.renameArea(id, new AreaRequest("Heimserver"));
        assertEquals("Heimserver", board.areas().get(2).name());
        board = service.deleteArea(id);
        assertEquals(2, board.areas().size());
        assertTrue(stored.get().todos().isEmpty(), "die Aufgaben des Bereichs gehen mit");
    }

    @Test
    void faelligkeitSetzenUndWiederNehmen() {
        Board board = service.createTodo(new TodoRequest("privat", null, "Steuer", LocalDate.of(2026, 9, 30), null));
        String id = firstTodoId(board, 0);
        assertEquals(LocalDate.of(2026, 9, 30), board.areas().get(0).todos().get(0).dueAt());
        board = service.update(id, new TodoRequest(null, null, "Steuererklärung", null, null));
        Board.TodoView view = board.areas().get(0).todos().get(0);
        assertEquals("Steuererklärung", view.title());
        assertNull(view.dueAt(), "ohne dueAt im Request gibt es keine Faelligkeit mehr");
    }

    @Test
    void erinnerungenBeliebigVieleNurInDerZukunft() {
        Board board = service.createTodo(new TodoRequest("privat", null, "Anrufen", null, null));
        String id = firstTodoId(board, 0);
        board = service.addReminder(id, new ReminderRequest(NOW.plus(Duration.ofHours(2))));
        board = service.addReminder(id, new ReminderRequest(NOW.plus(Duration.ofHours(1))));
        List<Board.ReminderView> reminders = board.areas().get(0).todos().get(0).reminders();
        assertEquals(2, reminders.size());
        assertEquals(NOW.plus(Duration.ofHours(1)), reminders.get(0).at(), "sortiert, die naechste zuerst");
        assertThrows(ResponseStatusException.class,
                () -> service.addReminder(id, new ReminderRequest(NOW.minus(Duration.ofMinutes(1)))));
        board = service.deleteReminder(id, reminders.get(0).id());
        assertEquals(1, board.areas().get(0).todos().get(0).reminders().size());
        assertThrows(ResponseStatusException.class, () -> service.deleteReminder(id, "gibtsnicht"));
    }

    @Test
    void leererTextWirdAbgelehnt() {
        assertThrows(ResponseStatusException.class,
                () -> service.createTodo(new TodoRequest("privat", null, "   ", null, null)));
        assertThrows(ResponseStatusException.class,
                () -> service.createTodo(new TodoRequest("gibtsnicht", null, "x", null, null)));
    }

    // MARK: - Links

    private static final String LINK = "https://fherrmann.com/feature-requests/42";

    private static Board.TodoView byTitle(Board board, int areaIndex, String title) {
        return board.areas().get(areaIndex).todos().stream()
                .filter(t -> t.title().equals(title)).findFirst().orElseThrow();
    }

    @Test
    void anlegenMitLinkGetrimmtUndLeerHeisstKeiner() {
        Board board = service.createTodo(new TodoRequest("privat", null, "Wunsch", null, "  " + LINK + "\n"));
        assertEquals(LINK, byTitle(board, 0, "Wunsch").link());
        assertEquals(LINK, stored.get().todos().get(0).link(), "so steht er auch in der Datei");

        board = service.createTodo(new TodoRequest("privat", null, "Leer", null, "   "));
        assertNull(byTitle(board, 0, "Leer").link(), "leer heisst kein Link");
        board = service.createTodo(new TodoRequest("privat", null, "Ohne", null, null));
        assertNull(byTitle(board, 0, "Ohne").link());

        board = service.createTodo(new TodoRequest("privat", null, "Gross", null, "HTTP://Example.org/A"));
        assertEquals("HTTP://Example.org/A", byTitle(board, 0, "Gross").link(), "Gross- und Kleinschreibung im Schema zaehlt nicht");
    }

    @Test
    void ungueltigerLinkIst400UndLegtNichtsAn() {
        String scheme = "Der Link muss mit https:// oder http:// anfangen.";
        String invalid = "Der Link ist keine gültige Adresse.";
        assertRejected("fherrmann.com/feature-requests/42", scheme);
        assertRejected("javascript:alert(1)", scheme);
        assertRejected("ftp://fherrmann.com/datei", scheme);
        assertRejected("https:/fherrmann.com", scheme);
        assertRejected("https://", invalid);
        assertRejected("https://fherrmann.com/mit leerzeichen", invalid);

        String base = "https://fherrmann.com/";
        assertRejected(base + "x".repeat(TodoService.MAX_LINK + 1 - base.length()), "Der Link ist zu lang.");
        assertTrue(stored.get().todos().isEmpty(), "abgelehnt heisst: nichts angelegt");

        String longest = base + "x".repeat(TodoService.MAX_LINK - base.length());
        Board board = service.createTodo(new TodoRequest("privat", null, "Lang", null, longest));
        assertEquals(longest, byTitle(board, 0, "Lang").link(), "genau 500 Zeichen gehen noch");
    }

    private void assertRejected(String link, String reason) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.createTodo(new TodoRequest("privat", null, "Wunsch", null, link)), link);
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode(), link);
        assertEquals(reason, e.getReason(), link);
    }

    @Test
    void aendernLaesstDenLinkStehen() {
        Board board = service.createTodo(new TodoRequest("privat", null, "Wunsch", null, LINK));
        String id = firstTodoId(board, 0);
        // So schicken es Fokus und die Weboberflaeche: Text und Faelligkeit, kein Link.
        board = service.update(id, new TodoRequest(null, null, "Wunsch, umformuliert", LocalDate.of(2026, 10, 1), null));
        Board.TodoView view = board.areas().get(0).todos().get(0);
        assertEquals("Wunsch, umformuliert", view.title());
        assertEquals(LocalDate.of(2026, 10, 1), view.dueAt());
        assertEquals(LINK, view.link());
        // Auch Abhaken, Zuruecknehmen und Erinnerungen fassen ihn nicht an.
        service.done(id);
        service.reopen(id);
        board = service.addReminder(id, new ReminderRequest(NOW.plus(Duration.ofHours(1))));
        assertEquals(LINK, board.areas().get(0).todos().get(0).link());
    }

    @Test
    void unteraufgabeBringtIhrenLinkImBrettMit() {
        // Der Fall, fuer den es die Links gibt: ein Wunsch als Unteraufgabe unter „Healthy".
        Board board = service.createArea(new AreaRequest("Server"));
        String server = board.areas().get(2).id();
        board = service.createTodo(new TodoRequest(server, null, "Healthy", null, null));
        String healthy = firstTodoId(board, 2);
        board = service.createTodo(new TodoRequest(server, healthy, "Wunsch von Torben", null, LINK));
        Board.TodoView top = board.areas().get(2).todos().get(0);
        assertNull(top.link());
        assertEquals(LINK, top.children().get(0).link());
        assertEquals(LINK, service.board(false).areas().get(2).todos().get(0).children().get(0).link(),
                "auch beim naechsten Laden");
    }

    // MARK: - Benachrichtigung beim Anlegen

    @Test
    void benachrichtigungGehtMitDemLinkDerAufgabeRaus() {
        service.createTodo(new TodoRequest("privat", null, "Wunsch", null, LINK,
                new TodoRequest.Notification(" Feature Request · coHabit ", "Torben:\n  Wunsch")));
        verify(notifier).announce("Feature Request · coHabit", "Torben: Wunsch", LINK);
    }

    @Test
    void ohneBenachrichtigungOderOhneTitelBleibtEsStill() {
        service.createTodo(new TodoRequest("privat", null, "Selbst getippt", null, null));
        service.createTodo(new TodoRequest("privat", null, "Leerer Titel", null, LINK,
                new TodoRequest.Notification("  ", "Text")));
        service.createTodo(new TodoRequest("privat", null, "Kein Titel", null, LINK,
                new TodoRequest.Notification(null, "Text")));
        verify(notifier, never()).announce(any(), any(), any());
        assertEquals(3, stored.get().todos().size(), "angelegt sind sie trotzdem");
    }

    @Test
    void zuLangeBenachrichtigungWirdGekuerztStattAbgelehnt() {
        service.createTodo(new TodoRequest("privat", null, "Wunsch", null, null,
                new TodoRequest.Notification("T".repeat(500), "B".repeat(500))));
        verify(notifier).announce("T".repeat(TodoService.MAX_NOTIFICATION_TITLE - 1) + "…",
                "B".repeat(TodoService.MAX_NOTIFICATION_BODY - 1) + "…", null);
    }

    @Test
    void abgelehnteAufgabeMeldetNichts() {
        TodoRequest.Notification notification = new TodoRequest.Notification("Feature Request · Healthy", "Torben: x");
        assertThrows(ResponseStatusException.class,
                () -> service.createTodo(new TodoRequest("gibtsnicht", null, "x", null, LINK, notification)));
        assertThrows(ResponseStatusException.class,
                () -> service.createTodo(new TodoRequest("privat", null, "x", null, "kein-link", notification)));
        verify(notifier, never()).announce(anyString(), anyString(), any());
    }
}
