-- 부하 테스트용 시드 데이터: 판매자 100명, 구매자 1,000명, 상품 2,000개, 주문 10만 건(최근 30일)
-- 실행: docker compose exec -T postgres psql -U market -d market -v ON_ERROR_STOP=1 < scripts/seed.sql
-- 삭제: scripts/seed-clean.sql
-- 로그인 비밀번호는 모두 password123 (seed-seller-1@seed.test, seed-buyer-1@seed.test, 운영자 seed-admin@seed.test)
\set ON_ERROR_STOP on
BEGIN;
SELECT setseed(0.42);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM member WHERE email LIKE 'seed-%@seed.test') THEN
        RAISE EXCEPTION '시드 데이터가 이미 있습니다. scripts/seed-clean.sql을 먼저 실행하세요';
    END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TEMP TABLE seed_hash AS SELECT crypt('password123', gen_salt('bf', 10)) AS h;

INSERT INTO member (email, password_hash, name, role)
SELECT 'seed-seller-' || g || '@seed.test', h, 'seller' || g, 'SELLER'
FROM generate_series(1, 100) g, seed_hash;

INSERT INTO member (email, password_hash, name, role)
SELECT 'seed-buyer-' || g || '@seed.test', h, 'buyer' || g, 'BUYER'
FROM generate_series(1, 1000) g, seed_hash;

-- 운영자 계정 (관리자 API 호출용)
INSERT INTO member (email, password_hash, name, role)
SELECT 'seed-admin@seed.test', h, 'admin', 'ADMIN' FROM seed_hash;

INSERT INTO seller (member_id, store_name, fee_rate)
SELECT id, 'seed-store-' || n, (ARRAY[0.05, 0.08, 0.10])[1 + n % 3]
FROM (SELECT id, row_number() OVER (ORDER BY id) AS n
      FROM member WHERE email LIKE 'seed-seller-%@seed.test') m;

-- 판매자마다 상품 20개. 첫 번째 상품은 선착순(flash_sale) 상품
INSERT INTO product (seller_id, name, price, flash_sale)
SELECT s.id, 'seed 상품 ' || s.n || '-' || p, (1 + floor(random() * 500))::bigint * 100, p = 1
FROM (SELECT id, row_number() OVER (ORDER BY id) AS n
      FROM seller WHERE store_name LIKE 'seed-store-%') s, generate_series(1, 20) p;

INSERT INTO inventory (product_id, stock)
SELECT p.id, CASE WHEN p.flash_sale THEN 1000 ELSE 100000 END
FROM product p JOIN seller s ON s.id = p.seller_id WHERE s.store_name LIKE 'seed-store-%';

CREATE TEMP TABLE seed_products AS
SELECT p.id, p.seller_id, p.price, row_number() OVER (ORDER BY p.id) AS rn
FROM product p JOIN seller s ON s.id = p.seller_id WHERE s.store_name LIKE 'seed-store-%';

CREATE TEMP TABLE seed_buyers AS
SELECT id, row_number() OVER (ORDER BY id) AS rn FROM member WHERE email LIKE 'seed-buyer-%@seed.test';

-- 주문 10만 건: 1~3개 항목, 모두 결제 완료, 최근 30일에 분산
CREATE TEMP TABLE seed_orders AS
SELECT x.n, b.id AS buyer_id, x.ordered_at, x.ordered_at + interval '2 minutes' AS paid_at, x.nitems
FROM (SELECT g AS n,
             1 + floor(random() * 1000)::int AS brn,
             now() - interval '1 hour' - random() * interval '29 days' AS ordered_at,
             1 + floor(random() * 3)::int AS nitems
      FROM generate_series(1, 100000) g OFFSET 0) x
JOIN seed_buyers b ON b.rn = x.brn;

CREATE TEMP TABLE seed_items AS
SELECT row_number() OVER () AS n, o.n AS order_n, p.id AS product_id, p.seller_id,
       p.price AS unit_price, o.q AS quantity, p.price * o.q AS amount
FROM (SELECT so.n, k, 1 + floor(random() * 2000)::int AS prn, 1 + floor(random() * 3)::int AS q
      FROM seed_orders so, generate_series(1, 3) k WHERE k <= so.nitems OFFSET 0) o
JOIN seed_products p ON p.rn = o.prn;

-- 항목의 약 5%는 결제 후 3일 안에 환불
CREATE TEMP TABLE seed_refunds AS
SELECT row_number() OVER () AS n, i.n AS item_n, i.order_n, i.amount,
       LEAST(now(), o.paid_at + interval '1 hour' + random() * interval '3 days') AS refunded_at
FROM (SELECT * FROM seed_items WHERE random() < 0.05 OFFSET 0) i
JOIN seed_orders o ON o.n = i.order_n;

CREATE TEMP TABLE seed_refund_cnt AS SELECT order_n, count(*) AS c FROM seed_refunds GROUP BY order_n;

SELECT COALESCE(MAX(id), 0) AS ob FROM orders \gset
SELECT COALESCE(MAX(id), 0) AS oib FROM order_item \gset
SELECT COALESCE(MAX(id), 0) AS pb FROM payment \gset
SELECT COALESCE(MAX(id), 0) AS rb FROM refund \gset

INSERT INTO orders (id, buyer_id, status, total_amount, ordered_at) OVERRIDING SYSTEM VALUE
SELECT :ob + o.n, o.buyer_id,
       CASE WHEN c.c IS NULL THEN 'PAID' WHEN c.c = o.nitems THEN 'REFUNDED' ELSE 'PARTIAL_REFUNDED' END,
       t.total, o.ordered_at
FROM seed_orders o
JOIN (SELECT order_n, sum(amount) AS total FROM seed_items GROUP BY order_n) t ON t.order_n = o.n
LEFT JOIN seed_refund_cnt c ON c.order_n = o.n;

INSERT INTO order_item (id, order_id, product_id, seller_id, unit_price, quantity, amount) OVERRIDING SYSTEM VALUE
SELECT :oib + n, :ob + order_n, product_id, seller_id, unit_price, quantity, amount FROM seed_items;

INSERT INTO payment (id, order_id, portone_payment_id, idempotency_key, amount, status, paid_at) OVERRIDING SYSTEM VALUE
SELECT :pb + o.n, :ob + o.n, 'seed-pay-' || o.n, 'seed-pay-' || o.n, t.total,
       CASE WHEN c.c = o.nitems THEN 'CANCELED' ELSE 'PAID' END, o.paid_at
FROM seed_orders o
JOIN (SELECT order_n, sum(amount) AS total FROM seed_items GROUP BY order_n) t ON t.order_n = o.n
LEFT JOIN seed_refund_cnt c ON c.order_n = o.n;

INSERT INTO refund (id, payment_id, order_item_id, amount, reason, status, refunded_at) OVERRIDING SYSTEM VALUE
SELECT :rb + n, :pb + order_n, :oib + item_n, amount, 'seed', 'COMPLETED', refunded_at FROM seed_refunds;

SELECT setval(pg_get_serial_sequence('orders', 'id'), (SELECT max(id) FROM orders));
SELECT setval(pg_get_serial_sequence('order_item', 'id'), (SELECT max(id) FROM order_item));
SELECT setval(pg_get_serial_sequence('payment', 'id'), (SELECT max(id) FROM payment));
SELECT setval(pg_get_serial_sequence('refund', 'id'), (SELECT max(id) FROM refund));

COMMIT;
ANALYZE;

SELECT (SELECT count(*) FROM seller WHERE store_name LIKE 'seed-store-%') AS sellers,
       (SELECT count(*) FROM orders WHERE id > :ob) AS orders,
       (SELECT count(*) FROM order_item WHERE id > :oib) AS items,
       (SELECT count(*) FROM payment WHERE id > :pb) AS payments,
       (SELECT count(*) FROM refund WHERE id > :rb) AS refunds;
