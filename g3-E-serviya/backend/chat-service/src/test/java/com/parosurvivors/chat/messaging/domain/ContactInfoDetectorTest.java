package com.parosurvivors.chat.messaging.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContactInfoDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "llámame al 3001234567",
            "mi cel es 300 123 4567",
            "+57 300-123-4567",
            "(300) 123 45 67 cuando quieras",
            "escríbeme a juan.perez@gmail.com",
            "mira mi perfil https://instagram.com/juan",
            "www.misitio.co",
            "hablemos por wa.me/573001234567",
            "mi número fijo +1 202 555 0143",
    })
    void detecta_telefonos_correos_y_enlaces(String text) {
        assertThat(ContactInfoDetector.looksLikeContactInfo(text)).as(text).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Hola, ¿puedes venir mañana a las 3?",
            "el servicio cuesta $150.000",
            "serían $1.500.000.000 jaja",
            "son 150000 pesos",
            "vivo en la calle 45 # 12-30 apto 301",
            "llego en 20 minutos",
            "son 2 horas, dos técnicos y 3 repuestos",
            "el 12/11/2026 a las 10:30",
            "perfecto, quedamos así. Gracias!",
    })
    void no_marca_precios_horas_ni_direcciones(String text) {
        assertThat(ContactInfoDetector.looksLikeContactInfo(text)).as(text).isFalse();
    }
}
