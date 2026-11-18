-- 오래 REQUESTED로 남은 환불을 찾기 위해 요청 시각을 기록한다
ALTER TABLE refund ADD COLUMN requested_at TIMESTAMPTZ NOT NULL DEFAULT now();
