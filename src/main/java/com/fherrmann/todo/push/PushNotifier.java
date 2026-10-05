package com.fherrmann.todo.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Benachrichtigungen, die beim Anlegen einer Aufgabe mitkommen - nicht die
 * Erinnerungen, die schickt der {@code ReminderScheduler} selbst.
 *
 * <p>Im Hintergrund: wer die Aufgabe anlegt, soll nicht auf Apple warten. Der
 * Kalorienzaehler gibt nach fuenf Sekunden auf, und APNs darf sich bis zu
 * fuenfzehn nehmen - die Aufgabe stuende dann, der Kalorienzaehler hielte sie
 * aber fuer gescheitert.
 */
@Component
public class PushNotifier {

    private static final Logger log = LoggerFactory.getLogger(PushNotifier.class);

    private final ApnsClient apns;
    private final DeviceTokens devices;
    private final Executor background;

    @Autowired
    public PushNotifier(ApnsClient apns, DeviceTokens devices) {
        this(apns, devices, task -> Thread.ofVirtual().name("push").start(task));
    }

    PushNotifier(ApnsClient apns, DeviceTokens devices, Executor background) {
        this.apns = apns;
        this.devices = devices;
        this.background = background;
    }

    /** An jedes angemeldete Geraet; ein Tipp oeffnet {@code link}, falls es einen gibt. */
    public void announce(String title, String body, String link) {
        if (!apns.isConfigured()) {
            return;
        }
        background.execute(() -> {
            for (String token : devices.all()) {
                if (!apns.send(token, title, body, link)) {
                    devices.remove(token);
                }
            }
            log.info("Benachrichtigung verschickt: {}", title);
        });
    }
}
