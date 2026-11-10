package com.parosurvivors.chat.shared.application.ports;

import java.util.function.Supplier;

/**
 * Frontera transaccional. Todo lo que debe ser atómico (mensaje + secuencia + outbox) va dentro de
 * {@code execute}; lo que debe ocurrir tras el commit (tiempo real) va después de que retorne.
 */
public interface UnitOfWork {

    <T> T execute(Supplier<T> work);

    default void run(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }
}
