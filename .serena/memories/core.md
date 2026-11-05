# market-settlement

Marketplace order/payment/refund/seller-settlement backend, solo project. Docs in Korean.

## Layout
- `backend/` Gradle Spring Boot app (pkg `com.sihwan.market`), feature-packaged: `auth/`, `member/`, `config/`. Run all gradle commands from `backend/`.
- `docker-compose.yml` (root): postgres:17 + redis:7, creds `market/market`, db `market`.
- `docs/` PRD, ERD (design decisions: daily settlement, BIGINT won, per-seller fee_rate frozen into settlement row, idempotent batch via unique keys), AS-IS, issues. Read `docs/ERD.md` before touching schema/settlement logic.
- Schema owned by Flyway: `backend/src/main/resources/db/migration/V*__*.sql`. Never edit applied migrations; add a new version.

## Invariants
- Money = `BIGINT` won, never floating point.
- Persistence is MyBatis (annotation `@Mapper` interfaces, `@Select` even for `INSERT ... RETURNING id`); no JPA.
- Auth: Spring Security + JWT (oauth2-resource-server), secret from `JWT_SECRET` env (dev default in application.yml).
- Redis planned for first-come purchase stock reservation (not yet implemented).

See `mem:tech_stack`, `mem:suggested_commands`, `mem:conventions`, `mem:task_completion`.
