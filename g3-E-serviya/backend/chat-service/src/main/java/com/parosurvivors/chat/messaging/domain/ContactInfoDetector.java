package com.parosurvivors.chat.messaging.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detecta, de forma disuasiva (no infalible), teléfonos, correos y enlaces (DP-06, ADR-013). Prefiere dejar
 * pasar un caso dudoso antes que advertir por un precio, una hora o una dirección.
 */
public final class ContactInfoDetector {

    private static final Pattern EMAIL =
            Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(\\.[\\p{L}\\p{N}-]+)+");

    private static final Pattern URL = Pattern.compile(
            "(?i)(https?://|www\\.|wa\\.me/|t\\.me/|bit\\.ly/)\\S+"
                    + "|\\b[\\p{L}\\p{N}-]+\\.(com|net|org|co|io|me|app)(/\\S*)?\\b");

    /** Celular colombiano: 3XX XXX XXXX, con +57/57 y separadores opcionales (espacio, punto, guion). */
    private static final Pattern CO_MOBILE = Pattern.compile(
            "(?<![\\d.,$])(?:\\+?57[\\s.-]?)?\\(?3\\d{2}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{2}[\\s.-]?\\d{2}(?!\\d)");

    /** Cualquier secuencia de dígitos con separadores que sume 10+ dígitos (internacional, fijos con indicativo). */
    private static final Pattern LONG_DIGITS = Pattern.compile("(?<![\\d$])\\+?\\d(?:[\\s().-]?\\d){9,}(?!\\d)");

    private ContactInfoDetector() {
    }

    public static boolean looksLikeContactInfo(String text) {
        return EMAIL.matcher(text).find()
                || URL.matcher(text).find()
                || CO_MOBILE.matcher(text).find()
                || hasLongPhoneLikeNumber(text);
    }

    private static boolean hasLongPhoneLikeNumber(String text) {
        Matcher m = LONG_DIGITS.matcher(text);
        while (m.find()) {
            String run = m.group();
            // Un precio con separadores de miles ("1.500.000.000") no es un teléfono: los grupos de 3 dígitos
            // separados por punto o coma son cantidades.
            if (run.matches("\\d{1,3}([.,]\\d{3})+")) {
                continue;
            }
            return true;
        }
        return false;
    }
}
