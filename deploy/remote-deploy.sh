#!/bin/bash
# EC2에서 SSM(root)으로 실행된다. 워크플로가 이 앞에 compose, Caddyfile을 쓰는 줄을 붙여 보낸다.
set -e
trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
cd /home/ubuntu/market   # 폴더 이름이 compose 프로젝트 이름이라 바꾸면 볼륨이 새로 생긴다
# 모니터링을 추가하기 전에 만든 서버의 .env에는 Grafana 비밀번호가 없으므로 서버에서 만든다 (출력하지 않음)
grep -q "^GRAFANA_PASSWORD=" .env || echo "GRAFANA_PASSWORD=$(openssl rand -base64 18 | tr -d "/+=")" >> .env
docker compose pull app
docker compose up -d
# 설정 파일을 새 파일로 교체하면 실행 중인 Caddy는 이전 파일을 계속 쓰므로 다시 만든다
docker compose up -d --force-recreate caddy
docker image prune -f >/dev/null
for i in $(seq 1 30); do
  if curl -sf localhost/actuator/health | grep -q '"UP"'; then echo "health OK"; exit 0; fi
  sleep 3
done
docker compose logs --tail 50 app
echo "health check failed"; exit 1
