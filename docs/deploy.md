# 배포 (AWS EC2)

서울 리전 EC2(Ubuntu 24.04, t3.small) 한 대에 Caddy(프록시), 앱, PostgreSQL, Redis를 compose로 올린다.
도메인이 생기면 `deploy/Caddyfile`의 `:80`을 도메인으로 바꿔 HTTPS를 켠다. DB를 RDS로 옮길 때는 `.env`에 `DB_URL`만 지정한다.

보안 그룹: 22번은 내 IP만, 80, 443은 전체. 5432, 6379는 열지 않는다.

## 1. 서버 준비 (서버에서, 처음 한 번)
```bash
ssh -i ~/.ssh/market_key.pem ubuntu@<탄력적IP>

# 메모리 여유를 위해 스왑 2GB
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 도커 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu     # 적용하려면 로그아웃 후 다시 접속
mkdir -p ~/market/deploy
```

## 2. 자동 배포 (GitHub Actions, SSM)
main에 머지하면 CI가 통과한 뒤 `.github/workflows/deploy.yml`이 이미지를 빌드해 GHCR(`ghcr.io/sihwan0802/market-settlement`)에 올리고, SSM Run Command로 서버에서 `docker compose pull && up -d`와 health 확인을 한다. 22번 포트도 저장된 서버 키도 필요 없다. compose와 Caddyfile도 이때 같이 갱신된다.

한 번만 하는 설정 (AWS 콘솔):
1. 역할 `market-ec2-ssm` 생성 (EC2용, `AmazonSSMManagedInstanceCore`)해서 인스턴스에 연결. Systems Manager → Fleet Manager에서 Online 확인
2. IAM → ID 공급자: OpenID Connect, URL `https://token.actions.githubusercontent.com`, 대상 `sts.amazonaws.com`
3. 역할 `market-github-deploy` 생성 (웹 ID, 위 공급자). 신뢰 정책은 이 저장소의 main만 허용:
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Federated": "arn:aws:iam::<계정ID>:oidc-provider/token.actions.githubusercontent.com"},
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
        "token.actions.githubusercontent.com:sub": "repo:sihwan0802/market-settlement:ref:refs/heads/main"
      }
    }
  }]
}
```
   권한 정책(인라인): 이 인스턴스에 명령 보내기와 결과 조회만
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {"Effect": "Allow", "Action": "ssm:SendCommand",
     "Resource": ["arn:aws:ec2:ap-northeast-2:<계정ID>:instance/<인스턴스ID>",
                  "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript"]},
    {"Effect": "Allow", "Action": ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"], "Resource": "*"}
  ]
}
```
4. GitHub 저장소 Settings → Secrets → Actions에 `AWS_DEPLOY_ROLE_ARN`(3번 역할 ARN), `EC2_INSTANCE_ID` 등록
5. 첫 배포가 이미지를 올린 뒤, GitHub 프로필 → Packages → 이 이미지 → Settings에서 공개(Public)로 변경. 서버에서 로그인 없이 받기 위해서이고, 이미지에는 비밀값이 없다(비밀값은 서버 `.env`)

배포 실패 시 Actions 로그에 서버 출력과 앱 로그가 남고 GitHub가 실패 메일을 보낸다. 되돌릴 때는 이전 커밋을 다시 머지하거나 서버에서 `ghcr.io/...:<커밋SHA>` 태그로 `docker compose up -d`.

### 수동 배포 (자동 배포가 막혔을 때, 내 PC의 cmd에서)
PowerShell은 바이너리 파이프를 깨뜨리고 `curl`이 다른 명령으로 바뀌므로 cmd를 쓴다.
```
cd C:\Users\chasi\IdeaProjects\market-settlement
docker build -t ghcr.io/sihwan0802/market-settlement:latest backend
docker save ghcr.io/sihwan0802/market-settlement:latest | ssh -C -i C:\Users\chasi\.ssh\market_key.pem ubuntu@<탄력적IP> "docker load"
scp -i C:\Users\chasi\.ssh\market_key.pem docker-compose.prod.yml ubuntu@<탄력적IP>:~/market/docker-compose.yml
scp -i C:\Users\chasi\.ssh\market_key.pem deploy\Caddyfile ubuntu@<탄력적IP>:~/market/deploy/
```
이후 서버에서 `cd ~/market && docker compose up -d --pull never`.

## 3. 비밀값 (서버에서, 처음 한 번)
```bash
cd ~/market
cat > .env <<EOT
JWT_SECRET=$(openssl rand -base64 48)
DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')
PORTONE_API_SECRET=
PORTONE_WEBHOOK_SECRET=
EOT
chmod 600 .env
```
PortOne 값은 `nano .env`로 직접 채운다. 이 파일은 서버에만 있고 깃에 올리지 않는다.

### PortOne 웹훅 등록
프론트 호출 없이도 결제 완료가 반영되도록 PortOne이 `POST /api/webhooks/portone`을 호출한다.
1. PortOne 콘솔 → 결제 연동 → 웹훅에서 URL `http://<탄력적IP>/api/webhooks/portone`(HTTPS가 생기면 https)을 등록하고, 이벤트는 `Transaction.Paid`를 켠다
2. 콘솔의 웹훅 시크릿(`whsec_...`)을 서버 `.env`의 `PORTONE_WEBHOOK_SECRET`에 넣고 `docker compose up -d`로 앱을 다시 띄운다. 비어 있으면 서버는 웹훅을 모두 401로 거부한다
3. 콘솔의 호출 테스트로 200이 오는지 확인한다. 서명 헤더 이름과 형식(`webhook-id`, `webhook-timestamp`, `webhook-signature`)은 Standard Webhooks 규격 기준이라 이때 실제로 맞는지 확인한다

서명이 맞아도 본문의 금액은 믿지 않고 결제 번호로 PortOne을 다시 조회해 주문 금액과 대조한다. 같은 웹훅이 다시 와도 결과는 같다(PortOne 발신 IP는 52.78.5.241).

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
main에 머지하면 2단계의 자동 배포가 한다. DB 볼륨(`pgdata`)은 유지된다.

## 모니터링 (Prometheus, Grafana)
서버 compose에 함께 올라간다. 앱이 `/actuator/prometheus`로 내보내는 지표를 Prometheus가 15초마다 읽고(3일 보관), Grafana 대시보드 `market 개요`가 자동으로 올라온다. 요청 수, 응답 시간 p50/p95/p99, 5xx 오류율, JVM 힙, GC, DB 커넥션 풀, 정산 실행 횟수와 평균 소요(`settlement.run`)를 본다.

- 접속: `http://<탄력적IP>/grafana`, 아이디 `admin`
- 비밀번호는 배포 때 서버에서 자동으로 만들어 `~/market/.env`의 `GRAFANA_PASSWORD`에 있다. 서버에 접속해서 `grep GRAFANA_PASSWORD ~/market/.env`로 확인한다 (화면 공유 중에는 하지 않는다)
- `/actuator/prometheus`는 인증이 없어서 Caddy가 밖에서는 404로 막는다. Prometheus는 compose 내부 네트워크로만 읽는다
- 메모리: t3.small(2GB)에서 Prometheus 256MB, Grafana 300MB 한도를 둔다. 스왑 2GB가 있어도 부하 테스트 때는 `free -m`을 확인한다
- 대시보드 JSON은 `deploy/grafana/dashboards/market.json`이고, 화면에서 고친 것은 서버를 재배포하면 파일 내용으로 돌아간다
