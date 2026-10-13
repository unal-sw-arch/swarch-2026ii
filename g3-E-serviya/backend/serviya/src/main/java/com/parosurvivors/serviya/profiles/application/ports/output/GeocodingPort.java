package com.parosurvivors.serviya.profiles.application.ports.output;

import com.parosurvivors.serviya.profiles.application.dto.result.CoordinatesResult;

import java.util.Optional;

/**
 * Puerto de salida para geocodificación de direcciones.
 * Permite intercambiar la implementación (Nominatim, Google Maps, etc.) sin tocar la lógica de aplicación.
 */
public interface GeocodingPort {

    /**
     * Verifica si una dirección es válida y devuelve sus coordenadas.
     *
     * @param addressLine línea de dirección (calle, número, etc.)
     * @param city ciudad
     * @return Optional con las coordenadas si la dirección es válida, empty si no se encuentra
     */
    Optional<CoordinatesResult> geocode(String addressLine, String city);
}