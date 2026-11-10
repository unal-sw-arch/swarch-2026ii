package com.parosurvivors.serviya;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        // Claves EC P-256 de prueba (ES256) para que JwtKeys arranque el contexto (Fase 1).
        "JWT_PRIVATE_KEY=MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgQ34gtsVTWWij47x09RZCxR7naS3dlGJMdrHr/lVITJ2hRANCAAQK/Q+kf2OU2hnY1hV7bJELtnwBx+D8LgVmFF81c1eAkY0vt/YVQ200C0aKy6Gpd9XnWVqPBerl7uRIUcm8EM0N",
        "JWT_PUBLIC_KEY=MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECv0PpH9jlNoZ2NYVe2yRC7Z8Acfg/C4FZhRfNXNXgJGNL7f2FUNtNAtGisuhqXfV51lajwXq5e7kSFHJvBDNDQ==",
        "JWT_KEY_ID=test-key-1",
        "ENCRYPTION_KEY=serviya-test-pii-key-for-context-load-strong-32chars-min",
        "NOTIFICATIONS_PAYLOAD_KEY=serviya-test-payload-key-0123456789abcdef-strong",
        "serviya.notifications.outbox.relay-enabled=false",
        "serviya.events.outbox.relay-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class ServiyaApplicationTests {

	@Test
	void contextLoads() {
	}

}
