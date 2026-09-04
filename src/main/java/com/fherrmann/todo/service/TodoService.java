package com.fherrmann.todo.service;

import com.fherrmann.todo.dto.AreaRequest;
import com.fherrmann.todo.dto.Board;
import com.fherrmann.todo.dto.TodoRequest;
import com.fherrmann.todo.model.Area;
import com.fherrmann.todo.model.Todo;
import com.fherrmann.todo.model.TodoData;
import com.fherrmann.todo.repository.TodoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Die Regeln: Bereiche, Aufgaben, Unteraufgaben, und was nach dem Abhaken
 * passiert.
 *
 * <p>Die eine Regel, die nicht offensichtlich ist: <b>Erledigtes verschwindet,
 * wird aber nicht geloescht.</b> Eine abgehakte Aufgabe bleibt drei Tage
 * durchgestrichen stehen - lange genug, um ein Versehen zu bemerken - und
 * faellt dann aus dem Brett. In der Datei steht sie weiter; {@code all=true}
 * holt sie zurueck.
 */
@Service
public class TodoService {

    static final int MAX_TITLE = 200;
    static final int MAX_AREA_NAME = 40;

    private final TodoRepository repository;
    private final Clock clock;
    private final Duration doneVisible;

    public TodoService(TodoRepository repository, Clock clock,
                       @Value("${todo.done-visible-days:3}") int doneVisibleDays) {
        this.repository = repository;
        this.clock = clock;
        this.doneVisible = Duration.ofDays(doneVisibleDays);
    }

    // MARK: - Das Brett

    public Board board(boolean includeHidden) {
        return board(repository.load(), includeHidden);
    }

    Board board(TodoData data, boolean includeHidden) {
        Instant now = Instant.now(clock);
        List<Board.AreaView> areas = new ArrayList<>();
        int hiddenTotal = 0;
        List<Area> sorted = new ArrayList<>(data.areas());
        sorted.sort(Comparator.comparingInt(Area::position).thenComparing(Area::createdAt));
        for (Area area : sorted) {
            List<Board.TodoView> tops = new ArrayList<>();
            int open = 0;
            int hidden = 0;
            for (Todo todo : sortedTodos(data, area.id(), null)) {
                // Eine Unteraufgabe haengt am Schicksal ihrer Aufgabe: ist die
                // verschwunden, ist sie es auch - egal ob selbst erledigt.
                if (!isVisible(todo, now) && !includeHidden) {
                    hidden++;
                    continue;
                }
                List<Board.TodoView> children = new ArrayList<>();
                for (Todo child : sortedTodos(data, area.id(), todo.id())) {
                    if (!isVisible(child, now) && !includeHidden) {
                        hidden++;
                        continue;
                    }
                    if (!child.isDone()) {
                        open++;
                    }
                    children.add(view(child, List.of()));
                }
                if (!todo.isDone()) {
                    open++;
                }
                tops.add(view(todo, children));
            }
            hiddenTotal += hidden;
            areas.add(new Board.AreaView(area.id(), area.name(), area.position(), open, hidden, tops));
        }
        return new Board(areas, includeHidden, hiddenTotal, now);
    }

    /** Offene zuerst (aelteste oben), Erledigte dahinter (zuletzt erledigte oben). */
    private static List<Todo> sortedTodos(TodoData data, String areaId, String parentId) {
        List<Todo> todos = new ArrayList<>();
        for (Todo todo : data.todos()) {
            if (todo.areaId().equals(areaId)
                    && (parentId == null ? todo.parentId() == null : parentId.equals(todo.parentId()))) {
                todos.add(todo);
            }
        }
        todos.sort((a, b) -> {
            if (a.isDone() != b.isDone()) {
                return a.isDone() ? 1 : -1;
            }
            return a.isDone()
                    ? b.doneAt().compareTo(a.doneAt())
                    : a.createdAt().compareTo(b.createdAt());
        });
        return todos;
    }

    boolean isVisible(Todo todo, Instant now) {
        return !todo.isDone() || todo.doneAt().plus(doneVisible).isAfter(now);
    }

    private Board.TodoView view(Todo todo, List<Board.TodoView> children) {
        return new Board.TodoView(todo.id(), todo.title(), todo.createdAt(), todo.doneAt(),
                todo.isDone() ? todo.doneAt().plus(doneVisible) : null, children);
    }

    // MARK: - Bereiche

    public Board createArea(AreaRequest request) {
        String name = cleanAreaName(request);
        TodoData data = repository.load();
        for (Area area : data.areas()) {
            if (area.name().equalsIgnoreCase(name)) {
                throw badRequest("Den Bereich „" + area.name() + "“ gibt es schon.");
            }
        }
        int position = data.areas().stream().mapToInt(Area::position).max().orElse(-1) + 1;
        List<Area> areas = new ArrayList<>(data.areas());
        areas.add(new Area(TodoRepository.newId(), name, position, Instant.now(clock)));
        return save(new TodoData(areas, data.todos()));
    }

    public Board renameArea(String id, AreaRequest request) {
        String name = cleanAreaName(request);
        TodoData data = repository.load();
        Area existing = findArea(data, id);
        List<Area> areas = new ArrayList<>();
        for (Area area : data.areas()) {
            areas.add(area.id().equals(id)
                    ? new Area(existing.id(), name, existing.position(), existing.createdAt())
                    : area);
        }
        return save(new TodoData(areas, data.todos()));
    }

    /** Loescht den Bereich mit allen Aufgaben - auch den erledigten. Weg ist weg. */
    public Board deleteArea(String id) {
        TodoData data = repository.load();
        findArea(data, id);
        return save(new TodoData(
                data.areas().stream().filter(a -> !a.id().equals(id)).toList(),
                data.todos().stream().filter(t -> !t.areaId().equals(id)).toList()));
    }

    // MARK: - Aufgaben

    public Board createTodo(TodoRequest request) {
        String title = cleanTitle(request);
        TodoData data = repository.load();
        if (request.areaId() == null) {
            throw badRequest("Zu welchem Bereich gehört die Aufgabe?");
        }
        findArea(data, request.areaId());
        if (request.parentId() != null) {
            Todo parent = findTodo(data, request.parentId());
            if (parent.parentId() != null) {
                throw badRequest("Eine Unteraufgabe kann keine Unteraufgaben haben.");
            }
            if (!parent.areaId().equals(request.areaId())) {
                throw badRequest("Die Aufgabe darüber liegt in einem anderen Bereich.");
            }
        }
        List<Todo> todos = new ArrayList<>(data.todos());
        todos.add(new Todo(TodoRepository.newId(), request.areaId(), request.parentId(),
                title, Instant.now(clock), null));
        return save(new TodoData(data.areas(), todos));
    }

    public Board renameTodo(String id, TodoRequest request) {
        String title = cleanTitle(request);
        TodoData data = repository.load();
        findTodo(data, id);
        return save(new TodoData(data.areas(), data.todos().stream()
                .map(t -> t.id().equals(id) ? t.withTitle(title) : t).toList()));
    }

    /** Abhaken. Noch einmal abhaken aendert nichts - der Zeitpunkt bleibt der erste. */
    public Board done(String id) {
        TodoData data = repository.load();
        findTodo(data, id);
        Instant now = Instant.now(clock);
        return save(new TodoData(data.areas(), data.todos().stream()
                .map(t -> t.id().equals(id) && !t.isDone() ? t.withDoneAt(now) : t).toList()));
    }

    /** Haken zurueck - auch bei einer, die schon aus dem Brett gefallen war. */
    public Board reopen(String id) {
        TodoData data = repository.load();
        findTodo(data, id);
        return save(new TodoData(data.areas(), data.todos().stream()
                .map(t -> t.id().equals(id) ? t.withDoneAt(null) : t).toList()));
    }

    /** Wirklich loeschen, samt Unteraufgaben. Der Ausnahmefall, nicht der Weg. */
    public Board deleteTodo(String id) {
        TodoData data = repository.load();
        findTodo(data, id);
        return save(new TodoData(data.areas(), data.todos().stream()
                .filter(t -> !t.id().equals(id) && !id.equals(t.parentId())).toList()));
    }

    // MARK: - Kleinkram

    private Board save(TodoData data) {
        repository.save(data);
        return board(data, false);
    }

    private static Area findArea(TodoData data, String id) {
        return data.areas().stream().filter(a -> a.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bereich nicht gefunden."));
    }

    private static Todo findTodo(TodoData data, String id) {
        return data.todos().stream().filter(t -> t.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aufgabe nicht gefunden."));
    }

    private static String cleanTitle(TodoRequest request) {
        String title = request == null || request.title() == null ? "" : request.title().trim();
        if (title.isEmpty()) {
            throw badRequest("Eine Aufgabe braucht einen Text.");
        }
        if (title.length() > MAX_TITLE) {
            throw badRequest("Der Text darf höchstens " + MAX_TITLE + " Zeichen haben.");
        }
        return title;
    }

    private static String cleanAreaName(AreaRequest request) {
        String name = request == null || request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw badRequest("Ein Bereich braucht einen Namen.");
        }
        if (name.length() > MAX_AREA_NAME) {
            throw badRequest("Der Name darf höchstens " + MAX_AREA_NAME + " Zeichen haben.");
        }
        return name;
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
