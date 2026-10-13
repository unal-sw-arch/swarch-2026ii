package com.parosurvivors.serviya;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "JWT_SECRET=serviya-test-jwt-secret-for-context-load-strong-32chars-min",
        "ENCRYPTION_KEY=serviya-test-pii-key-for-context-load-strong-32chars-min",
        "NOTIFICATIONS_PAYLOAD_KEY=serviya-test-payload-key-0123456789abcdef-strong",
        "serviya.notifications.outbox.relay-enabled=false",
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
