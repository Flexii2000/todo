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
import java.util.List;
import java.util.Map;
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
        List<Todo> todos = new ArrayList<>();
        boolean changed = false;
        for (Todo todo : data.todos()) {
            List<Reminder> reminders = new ArrayList<>();
            for (Reminder reminder : todo.reminders()) {
                boolean due = reminder.sentAt() == null && !reminder.at().isAfter(now);
                // Erledigte Aufgaben erinnern an nichts mehr - der Eintrag
                // wird als "geschickt" abgehakt, damit er nicht ewig faellig bleibt.
                if (due && (todo.isDone() || now.getEpochSecond() - reminder.at().getEpochSecond() > MAX_LATE_SECONDS)) {
                    reminders.add(reminder.withSentAt(now));
                    changed = true;
                } else if (due) {
                    send(todo, areaNames.getOrDefault(todo.areaId(), ""));
                    reminders.add(reminder.withSentAt(now));
                    changed = true;
                } else {
                    reminders.add(reminder);
                }
            }
            todos.add(todo.withReminders(reminders));
        }
        if (changed) {
            repository.save(new TodoData(data.areas(), todos));
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
