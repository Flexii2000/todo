package com.fherrmann.todo.controller;

import com.fherrmann.todo.push.PushNotifier;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wie Fokus ohne Netz arbeitet: die App vergibt die Kennung, zeigt die Aufgabe sofort
 * und schickt spaeter - Anlegen, Haken und Loeschen ueber HTTP, wie aus dem Postausgang.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "todo.data-file=build/tmp/client-id-api-test/todo.json",
        "todo.push.devices-file=build/tmp/client-id-api-test/devices.json",
        "todo.security.token=testtoken"})
class TodoClientIdApiTest {

    private static final Path DATA = Path.of("build/tmp/client-id-api-test/todo.json");
    private static final Cookie COOKIE = new Cookie("fh_private", "testtoken");
    private static final String ID = "0b7c3a52-4e0f-4c8e-9f1d-2a6b5c4d3e21";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private PushNotifier notifier;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        Files.deleteIfExists(DATA);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private String privatId() throws Exception {
        String board = mockMvc.perform(get("/api/board").cookie(COOKIE)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(board, "$.areas[0].id");
    }

    @Test
    void anlegenZweimalSendenAbhakenLoeschen() throws Exception {
        String body = "{\"areaId\":\"" + privatId() + "\",\"title\":\"Rasen mähen\",\"id\":\"" + ID + "\"}";
        mockMvc.perform(post("/api/todos").cookie(COOKIE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.areas[0].todos[0].id").value(ID));
        // Die Antwort ging unterwegs verloren, der Postausgang schickt noch einmal.
        mockMvc.perform(post("/api/todos").cookie(COOKIE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.areas[0].todos", hasSize(1)));

        mockMvc.perform(post("/api/todos/" + ID + "/done").cookie(COOKIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areas[0].todos[0].doneAt").isNotEmpty());
        mockMvc.perform(delete("/api/todos/" + ID).cookie(COOKIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areas[0].todos", hasSize(0)));
        // Schon weg: das zweite Loeschen ist ein 404 - die App nimmt es als erledigt.
        mockMvc.perform(delete("/api/todos/" + ID).cookie(COOKIE)).andExpect(status().isNotFound());
    }

    @Test
    void eineKennungAusGrossbuchstabenIstKeine() throws Exception {
        String body = "{\"areaId\":\"" + privatId() + "\",\"title\":\"A\",\"id\":\"" + ID.toUpperCase() + "\"}";
        mockMvc.perform(post("/api/todos").cookie(COOKIE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
