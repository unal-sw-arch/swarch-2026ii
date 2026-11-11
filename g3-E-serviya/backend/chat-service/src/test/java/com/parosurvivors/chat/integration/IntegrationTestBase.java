package com.parosurvivors.chat.integration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.images.builder.Transferable;

/**
 * Base de las pruebas de integración: la aplicación completa (servidor en puerto aleatorio) contra infraestructura
 * REAL y efímera, un único juego de contenedores por ejecución:
 * <ul>
 *   <li>MySQL 8.4 — Flyway + Hibernate {@code validate}.</li>
 *   <li>Redis «coord» con el ACL REAL del usuario {@code chat} tomado de {@code infra/redis}: si el chat
 *       usara un comando que el ACL no permite, las pruebas fallarían.</li>
 *   <li>Redis «pubsub» (instancia propia del chat) y RabbitMQ.</li>
 *   <li>Un JWKS servido por un stub HTTP con una clave EC P-256: la verificación ES256 por JWKS es la real.</li>
 * </ul>
 * Se SALTA sin Docker. El relay del outbox está apagado: las pruebas lo invocan a mano para controlar cuándo publica.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "serviya.security.jwt.issuer=https://auth.test",
        "serviya.security.jwt.audience=marketplace-api",
        "serviya.chat.outbox.relay-enabled=false",
        "serviya.chat.outbox.confirm-timeout-ms=1500",
        "serviya.chat.rate-limit.max-messages=1000",
        "spring.rabbitmq.listener.simple.retry.max-attempts=2",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "serviya.chat.allowed-ws-origins=http://localhost:5173"
})
public abstract class IntegrationTestBase {

    protected static final String ISSUER = "https://auth.test";
    protected static final String AUDIENCE = "marketplace-api";
    protected static final String CHAT_REDIS_PASSWORD = "chat-test-password-0000000000000000";
    protected static final String CHAT_RABBIT_PASSWORD = "chat-rabbit-test-password-000000000";

    protected static MySQLContainer<?> mysql;
    protected static GenericContainer<?> redisCoord;
    protected static GenericContainer<?> redisPubSub;
    protected static RabbitMQContainer rabbit;
    protected static ECKey signingKey;
    private static HttpServer jwks;

    @BeforeAll
    static void startInfrastructure() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Sin Docker: se omiten las pruebas de integración");
        synchronized (IntegrationTestBase.class) {
            if (mysql != null) {
                return;
            }
            mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("chat_db")
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
            redisCoord = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379)
                    .withCopyToContainer(Transferable.of(coordAcl()), "/data/users.acl")
                    .withCommand("redis-server", "--aclfile", "/data/users.acl");
            redisPubSub = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
            rabbit = new RabbitMQContainer("rabbitmq:4.1-alpine");
            mysql.start();
            redisCoord.start();
            redisPubSub.start();
            rabbit.start();
            prepareBrokerLikeInfra();

            signingKey = new ECKeyGenerator(Curve.P_256).keyID("test-key-1").generate();
            String jwksJson = new JWKSet(signingKey.toPublicJWK()).toString();
            jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwks.createContext("/.well-known/jwks.json", exchange -> {
                byte[] body = jwksJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            jwks.start();
        }
    }

    /** ACL de redis-coord: la línea REAL del usuario {@code chat} de la plantilla del proyecto + un admin para inspeccionar. */
    private static String coordAcl() throws IOException {
        String template = Files.readString(Path.of("../../infra/redis/acl/users.coord.acl.tpl"));
        String line = template.lines().filter(l -> l.startsWith("user chat ")).findFirst().orElseThrow();
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(CHAT_REDIS_PASSWORD.getBytes(StandardCharsets.UTF_8)));
            return "user default off\n" + line.replace("${CHAT_COORD_HASH}", hash) + "\n"
                    + "user admin on nopass ~* &* +@all\n";
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reproduce {@code infra/rabbitmq/setup-topology.sh}: exchanges compartidos, usuario {@code chat} y sus permisos.
     * Las expresiones de permisos se LEEN del script, así que un cambio allí se prueba aquí. {@code serviya.notifications}
     * lo declara notifications-service en producción; aquí lo declara el admin.
     */
    private static void prepareBrokerLikeInfra() throws Exception {
        String script = Files.readString(Path.of("../../infra/rabbitmq/setup-topology.sh"));
        String configure = shellVar(script, "CONFIGURE");
        String write = shellVar(script, "WRITE");
        String read = shellVar(script, "READ");
        exec("rabbitmqctl", "add_user", "chat", CHAT_RABBIT_PASSWORD);
        exec("rabbitmqctl", "set_permissions", "-p", "/", "chat", configure, write, read);
        org.springframework.amqp.rabbit.core.RabbitAdmin admin = adminAmqp();
        for (String topic : new String[] {"serviya.requests", "serviya.users", "serviya.chat", "serviya.notifications"}) {
            admin.declareExchange(new org.springframework.amqp.core.TopicExchange(topic, true, false));
        }
        admin.declareExchange(new org.springframework.amqp.core.DirectExchange("serviya.chat.dlx", true, false));
    }

    private static String shellVar(String script, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^" + name + "='([^']*)'").matcher(script);
        if (!m.find()) {
            throw new IllegalStateException("No se encontró " + name + " en setup-topology.sh");
        }
        return m.group(1);
    }

    private static void exec(String... command) throws Exception {
        var result = rabbit.execInContainer(command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(String.join(" ", command) + " falló: " + result.getStderr());
        }
    }

    private static org.springframework.amqp.rabbit.connection.CachingConnectionFactory adminConnections;

    /** Conexión de ADMINISTRADOR, para lo que en la vida real hacen otros (monolito, infra, inspección de colas). */
    protected static synchronized org.springframework.amqp.rabbit.connection.CachingConnectionFactory adminConnectionFactory() {
        if (adminConnections == null) {
            adminConnections = new org.springframework.amqp.rabbit.connection.CachingConnectionFactory(
                    rabbit.getHost(), rabbit.getAmqpPort());
            adminConnections.setUsername(rabbit.getAdminUsername());
            adminConnections.setPassword(rabbit.getAdminPassword());
        }
        return adminConnections;
    }

    protected static org.springframework.amqp.rabbit.core.RabbitAdmin adminAmqp() {
        return new org.springframework.amqp.rabbit.core.RabbitAdmin(adminConnectionFactory());
    }

    protected static org.springframework.amqp.rabbit.core.RabbitTemplate adminRabbitTemplate() {
        return new org.springframework.amqp.rabbit.core.RabbitTemplate(adminConnectionFactory());
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> mysql.getJdbcUrl() + "?connectionTimeZone=UTC");
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redisCoord::getHost);
        registry.add("spring.data.redis.port", () -> redisCoord.getMappedPort(6379));
        registry.add("spring.data.redis.username", () -> "chat");
        registry.add("spring.data.redis.password", () -> CHAT_REDIS_PASSWORD);
        registry.add("serviya.chat.pubsub.host", redisPubSub::getHost);
        registry.add("serviya.chat.pubsub.port", () -> redisPubSub.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        // La aplicación corre como el usuario `chat` de infra, con sus permisos reales.
        registry.add("spring.rabbitmq.username", () -> "chat");
        registry.add("spring.rabbitmq.password", () -> CHAT_RABBIT_PASSWORD);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + jwks.getAddress().getPort() + "/.well-known/jwks.json");
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        for (String table : new String[] {"messages", "conversation_participants", "conversations",
                "outbox_events", "processed_events", "user_directory"}) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    // ---------- tokens ----------

    /** Access token ES256 válido, firmado con la clave que publica el JWKS de prueba. */
    protected static String token(long userId, String... roles) {
        return tokenWith(signingKey, userId, ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(600), "at+jwt", roles);
    }

    protected static String tokenWith(ECKey key, long userId, String issuer, List<String> audience, Instant expires,
                                      String typ, String... roles) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(Long.toString(userId)).issuer(issuer)
                    .audience(audience).issueTime(Date.from(expires.minusSeconds(3600))).expirationTime(Date.from(expires))
                    .claim("roles", List.of(roles.length == 0 ? new String[] {"CLIENT"} : roles)).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .type(new JOSEObjectType(typ)).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new ECDSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
