package com.fherrmann.todo.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Die ganze Anwendung - Weboberflaeche und API - haengt hinter dem
 * Privat-Modus-Cookie von fherrmann.com, siehe {@link PrivateCookie}.
 *
 * <p>nginx weist Anfragen ohne den Cookie schon ab ({@code deploy/nginx-todo.conf});
 * die zweite Pruefung hier haelt die App vor allem dicht, was
 * {@code 127.0.0.1:48210} direkt erreicht. Kein Login, kein {@code /setup}:
 * der Cookie wird zentral auf fherrmann.com ausgestellt.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PrivateCookieAuthFilter privateCookieAuthFilter(@Value("${todo.security.token}") String token) {
        return new PrivateCookieAuthFilter(token);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, PrivateCookieAuthFilter privateCookieAuthFilter) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(privateCookieAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, ex) -> {
                    String accept = request.getHeader("Accept");
                    if (accept != null && accept.contains("text/html")) {
                        // Wie beim Kalorienzaehler: ein Browser ohne Cookie
                        // wird dorthin geschickt, wo es einen gibt.
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("text/html;charset=UTF-8");
                        response.getWriter().write("""
                                <!doctype html>
                                <html><body>
                                <script>
                                  alert('Nicht autorisiert');
                                  window.location.href = 'https://fherrmann.com';
                                </script>
                                </body></html>
                                """);
                    } else {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    }
                }))
                // Geteiltes Geheimnis in einem SameSite=Lax-Cookie, keine
                // Formulare, keine Sitzungen - kein CSRF obendrauf noetig.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
