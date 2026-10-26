package com.parosurvivors.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API gateway de ServiYa (requisito de la fase de microservicios, ESTADO §7.0/§7.7). Es el ÚNICO punto de
 * entrada del navegador: enruta cada ruta a su dueño (rutas de notificaciones → notifications-service; el
 * resto → monolito) y centraliza CORS. Las rutas viven en application.yaml (sin código): a medida que se
 * extraigan servicios (metrics, feedback, …) se añade una ruta y el monolito pierde esa responsabilidad
 * (Strangler Fig).
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
