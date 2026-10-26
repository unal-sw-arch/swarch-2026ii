package com.parosurvivors.serviya.shared.textfilter.infrastructure.adapters.output;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Filtro de palabras (RNF-006): censura texto libre antes de persistir. */
class ConfigurableWordFilterAdapterTest {

    private ConfigurableWordFilterAdapter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new ConfigurableWordFilterAdapter();
        // La lista viene de application.yaml (@Value); en el test se inyecta directamente.
        Field field = ConfigurableWordFilterAdapter.class.getDeclaredField("blockedWords");
        field.setAccessible(true);
        field.set(filter, List.of("idiota", "estupido"));
        filter.compilePattern();
    }

    @Test
    void censura_las_palabras_bloqueadas() {
        assertThat(filter.filter("eres un idiota")).isEqualTo("eres un ***");
        assertThat(filter.containsBlockedWords("eres un idiota")).isTrue();
    }

    @Test
    void ignora_mayusculas_y_tildes() {
        // "estúpido" con tilde y en mayusculas debe detectarse igual.
        assertThat(filter.containsBlockedWords("ESTÚPIDO")).isTrue();
    }

    @Test
    void no_mutila_palabras_legitimas_que_contienen_la_subcadena() {
        // Solo se censuran palabras COMPLETAS: "idiotas" no es "idiota".
        assertThat(filter.filter("idiotas")).isEqualTo("idiotas");
    }

    @Test
    void deja_intacto_el_texto_limpio_y_es_null_safe() {
        assertThat(filter.filter("un servicio excelente")).isEqualTo("un servicio excelente");
        // null-safe: los PATCH parciales envian null en los campos que no se tocan.
        assertThat(filter.filter(null)).isNull();
        assertThat(filter.containsBlockedWords(null)).isFalse();
    }
}
