package com.portfolio.eventbooking.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that need a real MySQL instance.
 *
 * IMPORTANT — environment note: this project originally used Testcontainers
 * to programmatically start/stop a MySQL container per test run. On this
 * development machine, Testcontainers' Docker API client (docker-java)
 * consistently received empty/malformed responses from Docker Desktop's API
 * (confirmed independently: the daemon itself was verified healthy via
 * direct curl and raw Java NIO socket calls to the same endpoint, both of
 * which returned correct, fully-populated responses — the fault was
 * isolated specifically to docker-java's HTTP-over-Unix-socket transport
 * layer against this Docker Desktop version). Multiple fixes were
 * attempted (forcing the Testcontainers version, forcing the
 * httpclient5 transport, disabling Enhanced Container Isolation) without
 * resolving it.
 *
 * Pragmatic decision: rather than keep fighting a local environment
 * incompatibility, MySQL for integration tests is run via plain
 * docker-compose (see docker-compose.test.yml) and the test connects to a
 * fixed, well-known port. This still exercises the only thing the
 * concurrency test actually needs — real InnoDB row-locking behavior under
 * SELECT ... FOR UPDATE — without depending on Testcontainers' Docker API
 * client working correctly on every machine. The tradeoff: the developer
 * must run `docker compose -f docker-compose.test.yml up -d` once before
 * running tests, rather than tests managing the container lifecycle
 * automatically. This is documented in the README.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3307/event_booking_test";
    private static final String USERNAME = "test";
    private static final String PASSWORD = "test";

    @DynamicPropertySource
    static void registerMysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
    }
}
