package com.parosurvivors.serviya.users.infrastructure.adapters.input;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.parosurvivors.serviya.users.infrastructure.security.JwtKeys;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Publica el conjunto de claves públicas (JWKS) con el que gateway y servicios verifican los access tokens
 * ES256 que emite este monolito (Fase 1, {@code guia-implementacion-auth.md}, T1.5). Endpoint estándar y
 * <b>público</b> ({@code GET /.well-known/jwks.json}); solo expone la clave <b>pública</b> (nunca el
 * parámetro privado {@code d}).
 *
 * <p>Se construye con Nimbus a partir de la clave pública y el {@code kid}; está diseñado para publicar
 * varias claves (necesario para la rotación, P1.2). El {@code Cache-Control: max-age=300} permite a los
 * verificadores cachear el JWKS y refrescarlo tras una rotación.
 */
@RestController
public class JwksController {

    private final JwtKeys keys;

    public JwksController(JwtKeys keys) {
        this.keys = keys;
    }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        ECKey jwk = new ECKey.Builder(Curve.P_256, keys.publicKey())
                .keyID(keys.keyId())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.ES256)
                .build();

        // toJSONObject() excluye los parámetros privados por defecto: el JWKS solo lleva x, y, crv, kid, use, alg.
        Map<String, Object> body = new JWKSet(jwk).toJSONObject();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
                .body(body);
    }
}
