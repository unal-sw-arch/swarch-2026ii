package com.parosurvivors.chat.shared.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** El tiempo real es best effort: un fallo de difusión nunca debe revertir ni fallar lo ya confirmado. */
public final class RealtimeSafely {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSafely.class);

    private RealtimeSafely() {
    }

    public static void run(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Difusión en tiempo real fallida ({}): {}", what, ex.toString());
        }
    }
}
