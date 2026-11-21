# 배포 (AWS EC2)

서울 리전 EC2(Ubuntu 24.04, t3.small) 한 대에 Caddy(프록시), 앱, PostgreSQL, Redis를 compose로 올린다.
도메인이 생기면 `deploy/Caddyfile`의 `:80`을 도메인으로 바꿔 HTTPS를 켠다. DB를 RDS로 옮길 때는 `.env`에 `DB_URL`만 지정한다.

보안 그룹: 22번은 내 IP만, 80, 443은 전체. 5432, 6379는 열지 않는다.

## 1. 서버 준비 (서버에서, 처음 한 번)
```bash
ssh -i ~/.ssh/market-key.pem ubuntu@<탄력적IP>

# 메모리 여유를 위해 스왑 2GB
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 도커 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu     # 적용하려면 로그아웃 후 다시 접속
mkdir -p ~/market/deploy
```

## 2. 이미지와 설정 올리기 (내 PC의 cmd에서, 배포할 때마다)
PowerShell은 바이너리 파이프를 깨뜨리고 `curl`이 다른 명령으로 바뀌므로 cmd를 쓴다.
```
cd C:UserschasiIdeaProjectsmarket-settlement
docker build -t market-app backend
docker save market-app | ssh -C -i C:Userschasi.sshmarket_key.pem ubuntu@<탄력적IP> "docker load"
scp -i C:Userschasi.sshmarket_key.pem docker-compose.prod.yml ubuntu@<탄력적IP>:~/market/docker-compose.yml
scp -i C:Userschasi.sshmarket_key.pem deployCaddyfile ubuntu@<탄력적IP>:~/market/deploy/
```
`-C`는 전송 압축. 자동 배포(#20)에서 레지스트리를 쓰면 이 단계는 필요 없다.

## 3. 비밀값 (서버에서, 처음 한 번)
```bash
cd ~/market
cat > .env <<EOT
JWT_SECRET=$(openssl rand -base64 48)
DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')
PORTONE_API_SECRET=
EOT
chmod 600 .env
```
PortOne 값은 `nano .env`로 직접 채운다. 이 파일은 서버에만 있고 깃에 올리지 않는다.

## 4. 실행과 확인 (서버에서)
```bash
cd ~/market
docker compose up -d
docker compose ps
curl -s localhost/actuator/health
```
내 PC 브라우저에서 `http://<탄력적IP>/actuator/health`가 `{"status":"UP"...}`이면 성공이다. 이 주소가 열리면 보안 그룹 80번도 확인된 것이다.

서버 안의 `curl localhost/...`는 서버에서만 쓴다. PC의 PowerShell에서는 `curl`이 `Invoke-WebRequest`다.

## 5. 정리
인스턴스를 지울 때는 탄력적 IP도 릴리스한다(연결 안 된 IP는 요금 발생).

## 재배포
앱 코드가 바뀌면 2단계로 이미지를 다시 올리고 서버에서 `cd ~/market && docker compose up -d`. DB 볼륨(`pgdata`)은 유지된다.
