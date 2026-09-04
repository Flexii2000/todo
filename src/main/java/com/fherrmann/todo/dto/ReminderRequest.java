package com.fherrmann.todo.dto;

import java.time.Instant;

public record ReminderRequest(Instant at) {
}
