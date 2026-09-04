package com.fherrmann.todo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
@EnableScheduling
public class TodoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TodoApplication.class, args);
    }

    /** Injizierbar, damit Tests "jetzt" festnageln koennen. */
    @Bean
    public Clock clock(@Value("${todo.zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
