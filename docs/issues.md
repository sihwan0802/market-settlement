# 이슈 초안

형식: `[마일스톤] 제목 | 라벨` — 한 PR로 끝나는 크기.

## M1. 1주차: 도메인·API·Compose
- 도메인 정의와 ERD 작성 | docs
- AS-IS 리서치 정리, TO-BE 작성 | docs
- Spring Boot + MyBatis + PostgreSQL 프로젝트 초기화 | backend
- Docker Compose (app, PostgreSQL, Redis) | infra
- 회원/판매자 가입·로그인 (JWT) | backend
- 상품·재고 CRUD | backend
- 주문 생성 API | backend
- 주문 조회 API (내 주문, 판매자 주문) | backend
- 공통 에러 응답 형식 정리 | backend
- Testcontainers로 테스트 DB 분리 | backend
- 회원 탈퇴(소프트 삭제)와 비밀번호 변경 | backend

## M2. 2주차: 결제·정산·Redis·성능 측정
- PortOne 테스트 결제 연동 | backend
- 환불 처리 | backend
- 일 정산 배치 (매출 − 환불 − 수수료) | backend
- 정산 정합성 검증 쿼리·테스트 | backend
- 시드 데이터 스크립트 (판매자 100명, 주문 10만 건) | perf
- k6 시나리오: 상품 조회, 선착순 구매, 정산 배치 (Redis 적용 전 기준 측정) | perf
- 상품 조회 Redis 캐시 | backend
- 선착순 구매 Redis 재고 선점 | backend
- 요청 제한 (Redis) | backend
- 환불 REQUESTED 복구 배치 | backend
- 만료된 주문에 들어온 결제 자동 취소 | backend
- 정산 누락 검사와 중복 실행 방지 | backend
- 정산·정합성 쿼리 성능 개선과 전후 측정 | perf
- 공개 전 점검 | docs

## M3. 3주차: 프론트·배포·모니터링·문서
- React 최소 화면 (상품, 주문, 정산 조회) | frontend
- 정산 완료 알림 자동화 (n8n/Apps Script) | automation
- Dockerfile 작성, 이미지 최적화 | infra
- GitHub Actions: 빌드·테스트 | infra
- AWS EC2 + RDS 구성, HTTPS(도메인, 인증서) | infra
- GitHub Actions: EC2 자동 배포 | infra
- Prometheus + Grafana 대시보드 | infra
- 개선 전후 측정과 `docs/performance.md` | perf
- ERD·아키텍처 그림, README 정리 | docs
- 노션 정리, 회고 | docs
- PortOne 웹훅 수신 (결제 상태 동기화) | backend

## 별도 (이 저장소 아님): techconf 빠른 보강
- 커넥션 풀 조정 후 k6 재측정 (전후 비교)
- 조회 API Redis 캐시 후 p95 비교
