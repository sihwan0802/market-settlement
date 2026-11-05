# Commands (Windows; run in `backend/` unless noted)
- Infra (repo root): `docker compose up -d` (postgres + redis required for app and tests).
- Run app: `.\gradlew.bat bootRun` (PowerShell) / `./gradlew bootRun` (Git Bash).
- Tests: `.\gradlew.bat test`; single class: `.\gradlew.bat test --tests "com.sihwan.market.auth.AuthIntegrationTest"`.
- Build: `.\gradlew.bat build`.
- Shell is PowerShell by default; Bash tool is Git Bash (use `/c/Users/...` paths).
