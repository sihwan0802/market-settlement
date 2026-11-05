# market-settlement

소규모 마켓플레이스의 **주문·결제·환불·판매자 정산** 백엔드. 기획부터 배포·운영까지 1인으로 진행하는 프로젝트입니다.

> 상태: 기획 단계 (진행 기록은 아래 로드맵과 GitHub Issues 참고)

## 왜 만드나
- 판매자가 여러 명인 마켓에서 주문 → 수수료 → 일/월 정산까지 정합성 있게 처리
- 인기 상품 선착순 구매에서 재고 정합성과 트래픽 제어
- 직접 구축한 배포·모니터링·부하 테스트 파이프라인으로 운영 경험 확보

## 기술 스택 (계획)
| 영역 | 선택 |
| --- | --- |
| Backend | Java 21, Spring Boot, MyBatis |
| DB / Cache | PostgreSQL, Redis |
| 결제 | PortOne (테스트 모드) |
| Infra | Docker, GitHub Actions, AWS EC2 + RDS, HTTPS |
| 모니터링 | Prometheus, Grafana |
| 부하 테스트 | k6 |
| Frontend | React + TypeScript (최소 화면) |

## 실행 방법
```bash
docker compose up   # 1주차 완료 후 동작
```

## 문서
- [AS-IS 분석](docs/AS-IS.md)
- [PRD](docs/PRD.md)
- [ERD와 도메인 결정](docs/ERD.md)
- [이슈 목록 초안](docs/issues.md)
- (예정) TO-BE, ADR, 성능 측정 결과(`docs/performance.md`), 자동화 플로우(`docs/automation.md`)

## 로드맵
| 주차 | 목표 |
| --- | --- |
| 1 | 도메인·ERD·핵심 API·Docker Compose |
| 2 | 결제·정산 배치·Redis·최소 프론트 |
| 3 | AWS 배포·CI/CD·모니터링 |
| 4 | k6 전후 측정·README·노션 정리 |
