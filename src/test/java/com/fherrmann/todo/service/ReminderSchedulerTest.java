package com.fherrmann.todo.service;

import com.fherrmann.todo.model.Area;
import com.fherrmann.todo.model.Reminder;
import com.fherrmann.todo.model.Todo;
import com.fherrmann.todo.model.TodoData;
import com.fherrmann.todo.push.ApnsClient;
import com.fherrmann.todo.push.DeviceTokens;
import com.fherrmann.todo.repository.TodoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    private final AtomicReference<TodoData> stored = new AtomicReference<>();
    private final ApnsClient apns = mock(ApnsClient.class);
    private final DeviceTokens devices = mock(DeviceTokens.class);

    private ReminderScheduler scheduler(TodoData data) {
        stored.set(data);
        TodoRepository repository = mock(TodoRepository.class);
        when(repository.load()).thenAnswer(inv -> stored.get());
        doAnswer(inv -> { stored.set(inv.getArgument(0)); return null; }).when(repository).save(any());
        when(apns.isConfigured()).thenReturn(true);
        when(apns.send(anyString(), anyString(), anyString())).thenReturn(true);
        when(devices.all()).thenReturn(List.of("a".repeat(64)));
        return new ReminderScheduler(repository, apns, devices, Clock.fixed(NOW, ZoneId.of("Europe/Berlin")));
    }

    private static Todo todo(Instant doneAt, LocalDate dueAt, Reminder... reminders) {
        return new Todo("t1", "uni", null, "Hausarbeit", NOW.minus(Duration.ofDays(1)), doneAt, dueAt, List.of(reminders));
    }

    private static TodoData data(Todo todo) {
        return new TodoData(List.of(new Area("uni", "Uni", 0, NOW)), List.of(todo));
    }

    @Test
    void faelligeErinnerungGehtRausUndWirdAbgehakt() {
        ReminderScheduler scheduler = scheduler(data(todo(null, LocalDate.of(2026, 9, 10),
                new Reminder("r1", NOW.minus(Duration.ofSeconds(30)), null),
                new Reminder("r2", NOW.plus(Duration.ofHours(1)), null))));
        scheduler.sendDueReminders();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(apns).send(eq("a".repeat(64)), eq("Erinnerung: Hausarbeit"), body.capture());
        assertEquals("Uni · fällig am 10.09.", body.getValue());
        List<Reminder> after = stored.get().todos().get(0).reminders();
        assertNotNull(after.get(0).sentAt(), "die faellige ist abgehakt");
        assertNull(after.get(1).sentAt(), "die in einer Stunde nicht");

        // Ein zweiter Durchlauf schickt nichts erneut.
        scheduler.sendDueReminders();
        verify(apns).send(anyString(), anyString(), anyString());
    }

    @Test
    void erledigteAufgabenErinnernAnNichts() {
        ReminderScheduler scheduler = scheduler(data(todo(NOW.minus(Duration.ofHours(1)), null,
                new Reminder("r1", NOW.minus(Duration.ofSeconds(30)), null))));
        scheduler.sendDueReminders();
        verify(apns, never()).send(anyString(), anyString(), anyString());
        assertNotNull(stored.get().todos().get(0).reminders().get(0).sentAt(), "trotzdem abgehakt, sonst bliebe sie ewig faellig");
    }

    @Test
    void eineStundeVerspaetetIstVerpasst() {
        ReminderScheduler scheduler = scheduler(data(todo(null, null,
                new Reminder("r1", NOW.minus(Duration.ofHours(2)), null))));
        scheduler.sendDueReminders();
        verify(apns, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void abgelehnteKennungFliegtRaus() {
        ReminderScheduler scheduler = scheduler(data(todo(null, null,
                new Reminder("r1", NOW.minus(Duration.ofSeconds(1)), null))));
        when(apns.send(anyString(), anyString(), anyString())).thenReturn(false);
        scheduler.sendDueReminders();
        verify(devices).remove("a".repeat(64));
    }
}
