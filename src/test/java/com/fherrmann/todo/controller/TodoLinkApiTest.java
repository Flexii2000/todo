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

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Der Link, wie ihn ein anderer Dienst benutzt: ueber HTTP, mit echtem
 * Service und echter Datei. Gegen genau diese Antworten baut der
 * Kalorienzaehler, wenn er Torbens Wuensche als Unteraufgaben ablegt - ein
 * Test mit nachgebautem Service saehe weder die Pruefung noch das JSON.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "todo.data-file=build/tmp/link-api-test/todo.json",
        "todo.push.devices-file=build/tmp/link-api-test/devices.json",
        "todo.security.token=testtoken"})
class TodoLinkApiTest {

    private static final Path DATA = Path.of("build/tmp/link-api-test/todo.json");
    private static final Cookie COOKIE = new Cookie("fh_private", "testtoken");
    private static final String LINK = "https://fherrmann.com/feature-requests/42";
    /** Frisch gesaet: Privat, Uni, Server - Server ist die dritte Kachel. */
    private static final String SERVER = "$.areas[2]";

    @Autowired
    private WebApplicationContext webApplicationContext;

    /** Kein echter Versand im Test - nur: kommt die Benachrichtigung an? */
    @MockitoBean
    private PushNotifier notifier;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        // Ohne Datei beginnt der Dienst neu mit den drei Bereichen.
        Files.deleteIfExists(DATA);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private String serverId() throws Exception {
        String board = mockMvc.perform(get("/api/board").cookie(COOKIE))
                .andExpect(jsonPath(SERVER + ".name").value("Server"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(board, SERVER + ".id");
    }

    private String postTodo(String json) throws Exception {
        return mockMvc.perform(post("/api/todos").cookie(COOKIE)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void wunschAlsUnteraufgabeMitLinkUndDasBrettZeigtIhn() throws Exception {
        String server = serverId();
        String board = postTodo("{\"areaId\":\"" + server + "\",\"title\":\"Healthy\"}");
        String healthy = JsonPath.read(board, SERVER + ".todos[0].id");

        mockMvc.perform(post("/api/todos").cookie(COOKIE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"areaId\":\"" + server + "\",\"parentId\":\"" + healthy
                                + "\",\"title\":\"Wunsch von Torben\",\"link\":\"" + LINK + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(SERVER + ".todos[0].children[0].title").value("Wunsch von Torben"))
                .andExpect(jsonPath(SERVER + ".todos[0].children[0].link").value(LINK))
                // Ohne Link steht das Feld trotzdem da, als null.
                .andExpect(jsonPath(SERVER + ".todos[0]").value(hasKey("link")))
                .andExpect(jsonPath(SERVER + ".todos[0].link").value(nullValue()));

        mockMvc.perform(get("/api/board").cookie(COOKIE))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SERVER + ".todos[0].children[0].link").value(LINK));
    }

    @Test
    void ungueltigerLinkGibt400MitKlartext() throws Exception {
        String server = serverId();
        mockMvc.perform(post("/api/todos").cookie(COOKIE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"areaId\":\"" + server + "\",\"title\":\"Wunsch\",\"link\":\"fherrmann.com/x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Der Link muss mit https:// oder http:// anfangen."));
        mockMvc.perform(post("/api/todos").cookie(COOKIE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"areaId\":\"" + server + "\",\"title\":\"Wunsch\",\"link\":\"https://fherrmann.com/"
                                + "x".repeat(500) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Der Link ist zu lang."));

        mockMvc.perform(get("/api/board").cookie(COOKIE))
                .andExpect(jsonPath(SERVER + ".todos", hasSize(0)));
    }

    @Test
    void leererLinkIstKeinLink() throws Exception {
        postTodo("{\"areaId\":\"" + serverId() + "\",\"title\":\"Healthy\",\"link\":\"\"}");
        mockMvc.perform(get("/api/board").cookie(COOKIE))
                .andExpect(jsonPath(SERVER + ".todos[0].link").value(nullValue()));
    }

    @Test
    void putOhneLinkLaesstIhnStehen() throws Exception {
        String server = serverId();
        String board = postTodo("{\"areaId\":\"" + server + "\",\"title\":\"Wunsch\",\"link\":\"" + LINK + "\"}");
        String id = JsonPath.read(board, SERVER + ".todos[0].id");

        // Genau so schickt es die Fokus-App, die den Link noch nicht kennt.
        mockMvc.perform(put("/api/todos/" + id).cookie(COOKIE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Wunsch, umformuliert\",\"dueAt\":\"2026-10-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SERVER + ".todos[0].title").value("Wunsch, umformuliert"))
                .andExpect(jsonPath(SERVER + ".todos[0].dueAt").value("2026-10-01"))
                .andExpect(jsonPath(SERVER + ".todos[0].link").value(LINK));
    }

    @Test
    void benachrichtigungImRumpfErreichtDenVersandMitDemLink() throws Exception {
        // So schickt es der Kalorienzaehler bei einem Feature Request von Torben.
        postTodo("{\"areaId\":\"" + serverId() + "\",\"title\":\"Wunsch von Torben\",\"link\":\"" + LINK
                + "\",\"notification\":{\"title\":\"Feature Request · Healthy\",\"body\":\"Torben: Wunsch\"}}");
        verify(notifier).announce("Feature Request · Healthy", "Torben: Wunsch", LINK);
    }

    @Test
    void ohneBenachrichtigungImRumpfKeinVersand() throws Exception {
        postTodo("{\"areaId\":\"" + serverId() + "\",\"title\":\"Healthy\"}");
        verify(notifier, never()).announce(any(), any(), any());
    }
}
