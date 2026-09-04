package com.fherrmann.todo.controller;

import com.fherrmann.todo.dto.AreaRequest;
import com.fherrmann.todo.dto.DeviceRegistration;
import com.fherrmann.todo.dto.ReminderRequest;
import com.fherrmann.todo.push.DeviceTokens;
import com.fherrmann.todo.dto.Board;
import com.fherrmann.todo.dto.TodoRequest;
import com.fherrmann.todo.service.TodoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Jede Antwort ist das ganze Brett ({@link Board}); die Clients setzen nichts zusammen. */
@RestController
@RequestMapping("/api")
public class TodoController {

    private final TodoService service;
    private final DeviceTokens devices;

    public TodoController(TodoService service, DeviceTokens devices) {
        this.service = service;
        this.devices = devices;
    }

    /** @param all auch erledigte Aufgaben, die aelter als die Sichtfrist sind */
    @GetMapping("/board")
    public Board board(@RequestParam(defaultValue = "false") boolean all) {
        return service.board(all);
    }

    @PostMapping("/areas")
    public ResponseEntity<Board> createArea(@RequestBody AreaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createArea(request));
    }

    @PutMapping("/areas/{id}")
    public Board renameArea(@PathVariable String id, @RequestBody AreaRequest request) {
        return service.renameArea(id, request);
    }

    @DeleteMapping("/areas/{id}")
    public Board deleteArea(@PathVariable String id) {
        return service.deleteArea(id);
    }

    @PostMapping("/todos")
    public ResponseEntity<Board> createTodo(@RequestBody TodoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTodo(request));
    }

    /** Text und Faelligkeit. Ohne {@code dueAt} im Rumpf gibt es keine mehr. */
    @PutMapping("/todos/{id}")
    public Board updateTodo(@PathVariable String id, @RequestBody TodoRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/todos/{id}/reminders")
    public ResponseEntity<Board> addReminder(@PathVariable String id, @RequestBody ReminderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addReminder(id, request));
    }

    @DeleteMapping("/todos/{id}/reminders/{reminderId}")
    public Board deleteReminder(@PathVariable String id, @PathVariable String reminderId) {
        return service.deleteReminder(id, reminderId);
    }

    /** Die Fokus-App meldet ihre Push-Kennung an - bei jedem Start, iOS tauscht sie gelegentlich. */
    @PostMapping("/devices")
    public ResponseEntity<Void> registerDevice(@RequestBody DeviceRegistration request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        devices.add(request.token().trim());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/todos/{id}/done")
    public Board done(@PathVariable String id) {
        return service.done(id);
    }

    @DeleteMapping("/todos/{id}/done")
    public Board reopen(@PathVariable String id) {
        return service.reopen(id);
    }

    @DeleteMapping("/todos/{id}")
    public Board deleteTodo(@PathVariable String id) {
        return service.deleteTodo(id);
    }
}
