# Conventions
- Feature packages under `com.sihwan.market` (`auth`, `member`, ...): Controller / Service / DTOs (grouped in one `*Dtos.java`) / Mapper / entity together.
- MyBatis mappers: annotation SQL in `@Mapper` interface, `@Param` on every arg, `Optional<T>` for single lookups.
- Integration tests use `@SpringBootTest` + `MockMvc` against the real docker-compose PostgreSQL (no Testcontainers yet); use unique data (e.g. `UUID` emails) so tests are re-runnable.
- Deliberate shortcuts are marked with `// ponytail:` comments (naming the ceiling / upgrade path).
- Commits: conventional prefix (`feat:`, `docs:`, `chore:`) with issue number `(#N)`; messages in Korean.
