package com.fherrmann.todo.controller;

import com.fherrmann.todo.dto.Board;
import com.fherrmann.todo.security.SecurityConfig;
import com.fherrmann.todo.push.DeviceTokens;
import com.fherrmann.todo.service.TodoService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TodoController.class)
@Import({SecurityConfig.class, PlainTextErrors.class})
@TestPropertySource(properties = "todo.security.token=testtoken")
class TodoControllerTest {

    private static final Cookie COOKIE = new Cookie("fh_private", "testtoken");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private TodoService service;

    @MockitoBean
    private DeviceTokens devices;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private static Board board() {
        Instant now = Instant.parse("2026-09-04T12:00:00Z");
        Board.TodoView child = new Board.TodoView("c1", "Gliederung", now, null, null, null, List.of(), List.of());
        Board.TodoView top = new Board.TodoView("t1", "Hausarbeit", now, null, null, null, List.of(), List.of(child));
        return new Board(List.of(new Board.AreaView("uni", "Uni", 0, 2, 1, List.of(top))), false, 1, now);
    }

    @Test
    void ohneCookieVerboten() throws Exception {
        mockMvc.perform(get("/api/board")).andExpect(status().isForbidden());
    }

    @Test
    void brettMitCookie() throws Exception {
        when(service.board(anyBoolean())).thenReturn(board());
        mockMvc.perform(get("/api/board").cookie(COOKIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areas[0].name").value("Uni"))
                .andExpect(jsonPath("$.areas[0].openCount").value(2))
                .andExpect(jsonPath("$.areas[0].todos[0].children[0].title").value("Gliederung"))
                .andExpect(jsonPath("$.hiddenDoneCount").value(1));
    }

    @Test
    void fehlerKommenAlsKlartext() throws Exception {
        when(service.createTodo(any())).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Eine Aufgabe braucht einen Text."));
        mockMvc.perform(post("/api/todos").cookie(COOKIE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"areaId\":\"uni\",\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Eine Aufgabe braucht einen Text."));
    }

    @Test
    void dieOberflaecheHaengtHinterDemselbenCookie() throws Exception {
        mockMvc.perform(get("/index.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden());
    }
}
