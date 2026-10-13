package com.parosurvivors.serviya.shared.textfilter.infrastructure.adapters.output;

import com.parosurvivors.serviya.shared.textfilter.application.ports.output.WordFilterPort;
import jakarta.annotation.PostConstruct;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador del filtro de palabras (RNF-006) basado en una lista configurable.
 *
 * <p>La lista vive en {@code application.yaml} bajo {@code serviya.word-filter.blocked-words}, de
 * modo que ampliarla no requiere recompilar. La comparación ignora mayúsculas y tildes (se
 * normaliza el texto) y solo censura palabras completas, para no mutilar palabras legítimas que
 * contengan una subcadena bloqueada.</p>
 */
@Component
public class ConfigurableWordFilterAdapter implements WordFilterPort {

    private static final String MASK = "***";

    @Value("${serviya.word-filter.blocked-words:}")
    private List<String> blockedWords;

    /** Patrón compilado una sola vez: alternancia de palabras completas, sin distinguir mayúsculas. */
    private Pattern blockedPattern;

    @PostConstruct
    void compilePattern() {
        if (blockedWords == null || blockedWords.isEmpty()) {
            blockedPattern = null;
            return;
        }
        String alternation = blockedWords.stream()
                .filter(word -> word != null && !word.isBlank())
                .map(word -> Pattern.quote(normalize(word.trim())))
                .reduce((a, b) -> a + "|" + b)
                .orElse(null);
        blockedPattern = alternation == null
                ? null
                : Pattern.compile("\\b(" + alternation + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public String filter(String text) {
        if (text == null || blockedPattern == null) {
            return text;
        }
        // Se busca sobre el texto normalizado (sin tildes) pero se reemplaza sobre el original,
        // preservando longitud de indices al normalizar sin recomponer.
        Matcher matcher = blockedPattern.matcher(normalize(text));
        StringBuilder result = new StringBuilder(text);
        int offset = 0;
        while (matcher.find()) {
            int start = matcher.start() + offset;
            int end = matcher.end() + offset;
            result.replace(start, end, MASK);
            offset += MASK.length() - (matcher.end() - matcher.start());
        }
        return result.toString();
    }

    @Override
    public boolean containsBlockedWords(String text) {
        if (text == null || blockedPattern == null) {
            return false;
        }
        return blockedPattern.matcher(normalize(text)).find();
    }

    /**
     * Quita tildes/diacríticos conservando la longitud (cada carácter base se mantiene en su
     * posición), para que los índices del Matcher sigan siendo válidos sobre el texto original.
     */
    private String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "");
    }
}
