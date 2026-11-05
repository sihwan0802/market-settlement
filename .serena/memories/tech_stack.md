# Tech stack
- Java 21 (Gradle toolchain), Spring Boot 4.0.x (Jackson 3: `tools.jackson.*`, not `com.fasterxml`), Gradle wrapper.
- Starters: webmvc, security, oauth2-resource-server, validation, actuator (only `health` exposed), data-redis, flyway (+flyway-database-postgresql).
- MyBatis 4.0.1 starter; `map-underscore-to-camel-case: true`; XML mappers path `classpath:mapper/**/*.xml` (annotation mappers currently used).
- PostgreSQL 17, Redis 7, Lombok.
- Spring Boot 4 test packages are modularized, e.g. `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.
- Planned (not present): PortOne payments, Prometheus/Grafana, k6, React+TS frontend, AWS EC2+RDS, GitHub Actions.
- Env overrides: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `JWT_SECRET`.
