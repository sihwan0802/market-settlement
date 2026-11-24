#!/bin/bash
# EC2에서 SSM(root)으로 실행된다. 워크플로가 이 앞에 compose, Caddyfile을 쓰는 줄을 붙여 보낸다.
set -e
trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT
cd /home/ubuntu/market   # 폴더 이름이 compose 프로젝트 이름이라 바꾸면 볼륨이 새로 생긴다
docker compose pull app
docker compose up -d
docker image prune -f >/dev/null
for i in $(seq 1 30); do
  if curl -sf localhost/actuator/health | grep -q '"UP"'; then echo "health OK"; exit 0; fi
  sleep 3
done
docker compose logs --tail 50 app
echo "health check failed"; exit 1
