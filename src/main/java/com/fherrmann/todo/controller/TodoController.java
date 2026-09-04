package com.fherrmann.todo.controller;

import com.fherrmann.todo.dto.AreaRequest;
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

    public TodoController(TodoService service) {
        this.service = service;
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

    @PutMapping("/todos/{id}")
    public Board renameTodo(@PathVariable String id, @RequestBody TodoRequest request) {
        return service.renameTodo(id, request);
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
