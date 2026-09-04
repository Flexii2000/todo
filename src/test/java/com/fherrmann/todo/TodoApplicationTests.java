package com.fherrmann.todo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "todo.data-file=build/tmp/context-test/todo.json")
class TodoApplicationTests {

    @Test
    void contextLoads() {
    }
}
