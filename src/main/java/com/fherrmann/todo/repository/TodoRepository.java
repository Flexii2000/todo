package com.fherrmann.todo.repository;

import com.fherrmann.todo.model.Area;
import com.fherrmann.todo.model.TodoData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Liest und schreibt {@code todo.json}.
 *
 * <p>Gibt es die Datei noch nicht, beginnt der Dienst mit den drei Bereichen,
 * mit denen Felix angefangen hat.
 */
@Repository
public class TodoRepository {

    private final Path dataFile;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TodoRepository(
            @Value("${todo.data-file:data/todo.json}") String dataFile,
            ObjectMapper objectMapper,
            Clock clock) {
        this.dataFile = Path.of(dataFile);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public synchronized TodoData load() {
        if (!Files.exists(dataFile)) {
            TodoData seed = seed(Instant.now(clock));
            save(seed);
            return seed;
        }
        try {
            return objectMapper.readValue(Files.readAllBytes(dataFile), TodoData.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read todo file: " + dataFile, e);
        }
    }

    /** Erst daneben schreiben, dann umbenennen - eine halbe Datei waere die ganze Liste. */
    public synchronized void save(TodoData data) {
        try {
            if (dataFile.getParent() != null) {
                Files.createDirectories(dataFile.getParent());
            }
            Path temp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), data);
            Files.move(temp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write todo file: " + dataFile, e);
        }
    }

    static TodoData seed(Instant now) {
        return new TodoData(List.of(
                new Area(newId(), "Privat", 0, now),
                new Area(newId(), "Uni", 1, now),
                new Area(newId(), "Server", 2, now)),
                List.of());
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
