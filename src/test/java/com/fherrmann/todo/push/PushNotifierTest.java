package com.fherrmann.todo.push;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushNotifierTest {

    private static final String LINK = "https://fherrmann.com/feature-requests/42";
    private static final String PHONE = "a".repeat(64);
    private static final String GONE = "b".repeat(64);

    private final ApnsClient apns = mock(ApnsClient.class);
    private final DeviceTokens devices = mock(DeviceTokens.class);
    /** Ohne Hintergrund: was {@code announce} anstoesst, ist mit der Rueckkehr erledigt. */
    private final PushNotifier notifier = new PushNotifier(apns, devices, Runnable::run);

    @Test
    void anJedesGeraetUndAbgelehnteKennungenFliegenRaus() {
        when(apns.isConfigured()).thenReturn(true);
        when(devices.all()).thenReturn(List.of(PHONE, GONE));
        when(apns.send(eq(PHONE), anyString(), anyString(), any())).thenReturn(true);
        when(apns.send(eq(GONE), anyString(), anyString(), any())).thenReturn(false);

        notifier.announce("Feature Request · coHabit", "Torben: Wunsch", LINK);

        verify(apns).send(PHONE, "Feature Request · coHabit", "Torben: Wunsch", LINK);
        verify(devices).remove(GONE);
        verify(devices, never()).remove(PHONE);
    }

    @Test
    void ohneSchluesselPassiertNichts() {
        when(apns.isConfigured()).thenReturn(false);
        notifier.announce("Feature Request · coHabit", "Torben: Wunsch", LINK);
        verify(devices, never()).all();
        verify(apns, never()).send(anyString(), anyString(), anyString(), any());
    }
}
