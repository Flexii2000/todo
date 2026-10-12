package com.fherrmann.todo.service;

import com.fherrmann.todo.model.Area;
import com.fherrmann.todo.model.Reminder;
import com.fherrmann.todo.model.Todo;
import com.fherrmann.todo.model.TodoData;
import com.fherrmann.todo.push.ApnsClient;
import com.fherrmann.todo.push.DeviceTokens;
import com.fherrmann.todo.repository.TodoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sieht jede Minute nach, welche Erinnerungen faellig sind, und schickt sie.
 *
 * <p>Der Dienst schickt selbst, nicht die App: eine Erinnerung, die im
 * Browser angelegt wurde, muss auch kommen, wenn die App seit Tagen zu ist.
 * Ohne eingerichteten Schluessel passiert nichts, und die Erinnerung bleibt
 * unverschickt stehen - sie geht raus, sobald der Schluessel da ist, sofern
 * sie nicht laenger als eine Stunde her ist (eine Erinnerung von gestern
 * ist keine mehr).
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    /** Aelter als das, und eine ungeschickte Erinnerung gilt als verpasst. */
    static final long MAX_LATE_SECONDS = 3600;

    private final TodoRepository repository;
    private final ApnsClient apns;
    private final DeviceTokens devices;
    private final Clock clock;

    public ReminderScheduler(TodoRepository repository, ApnsClient apns, DeviceTokens devices, Clock clock) {
        this.repository = repository;
        this.apns = apns;
        this.devices = devices;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${todo.push.check-interval:60000}")
    public void sendDueReminders() {
        if (!apns.isConfigured()) {
            return;
        }
        Instant now = Instant.now(clock);
        TodoData data = repository.load();
        Map<String, String> areaNames = data.areas().stream()
                .collect(Collectors.toMap(Area::id, Area::name));
        // Je Aufgabe die Erinnerungen, die als "geschickt" vermerkt werden.
        Map<String, Set<String>> handled = new HashMap<>();
        for (Todo todo : data.todos()) {
            for (Reminder reminder : todo.reminders()) {
                boolean due = reminder.sentAt() == null && !reminder.at().isAfter(now);
                if (!due) {
                    continue;
                }
                // Erledigte Aufgaben erinnern an nichts mehr - der Eintrag
                // wird als "geschickt" abgehakt, damit er nicht ewig faellig bleibt.
                boolean missed = todo.isDone() || now.getEpochSecond() - reminder.at().getEpochSecond() > MAX_LATE_SECONDS;
                if (!missed) {
                    send(todo, areaNames.getOrDefault(todo.areaId(), ""));
                }
                handled.computeIfAbsent(todo.id(), id -> new HashSet<>()).add(reminder.id());
            }
        }
        if (handled.isEmpty()) {
            return;
        }
        // Vermerkt wird unter der Sperre des TodoService und mit frisch gelesenem Stand:
        // waehrend die Pushes unterwegs waren, kann eine Aufgabe dazugekommen oder
        // abgehakt worden sein - der Stand von vorhin wuerde das ueberschreiben.
        synchronized (repository) {
            TodoData fresh = repository.load();
            List<Todo> todos = new ArrayList<>();
            for (Todo todo : fresh.todos()) {
                Set<String> ids = handled.get(todo.id());
                if (ids == null) {
                    todos.add(todo);
                    continue;
                }
                todos.add(todo.withReminders(todo.reminders().stream()
                        .map(r -> ids.contains(r.id()) && r.sentAt() == null ? r.withSentAt(now) : r)
                        .toList()));
            }
            repository.save(new TodoData(fresh.areas(), todos));
        }
    }

    private void send(Todo todo, String area) {
        String body = area;
        if (todo.dueAt() != null) {
            body += (body.isEmpty() ? "" : " · ") + "fällig am "
                    + todo.dueAt().format(DateTimeFormatter.ofPattern("dd.MM."));
        }
        for (String token : devices.all()) {
            if (!apns.send(token, "Erinnerung: " + todo.title(), body)) {
                devices.remove(token);
            }
        }
        log.info("Erinnerung verschickt: {}", todo.title());
    }
}
