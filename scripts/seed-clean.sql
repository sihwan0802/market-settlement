-- seed.sql로 넣은 데이터만 지운다 (seed- 로 시작하는 계정과 그 상품·주문)
-- 실행: docker compose exec -T postgres psql -U market -d market -v ON_ERROR_STOP=1 < scripts/seed-clean.sql
BEGIN;
-- order_item.order_id에 인덱스가 없어 주문 삭제 때마다 풀스캔이 되므로 임시 인덱스를 쓴다
CREATE INDEX tmp_seed_clean_order_item ON order_item (order_id);
CREATE TEMP TABLE seed_sellers AS SELECT id FROM seller WHERE store_name LIKE 'seed-store-%';
CREATE TEMP TABLE seed_orders AS
SELECT id FROM orders WHERE buyer_id IN (SELECT id FROM member WHERE email LIKE 'seed-buyer-%@seed.test');

DELETE FROM settlement_item WHERE settlement_id IN (SELECT id FROM settlement WHERE seller_id IN (SELECT id FROM seed_sellers));
DELETE FROM settlement WHERE seller_id IN (SELECT id FROM seed_sellers);
DELETE FROM refund WHERE payment_id IN (SELECT id FROM payment WHERE order_id IN (SELECT id FROM seed_orders));
DELETE FROM payment WHERE order_id IN (SELECT id FROM seed_orders);
DELETE FROM order_item WHERE order_id IN (SELECT id FROM seed_orders);
DELETE FROM orders WHERE id IN (SELECT id FROM seed_orders);
DELETE FROM inventory WHERE product_id IN (SELECT id FROM product WHERE seller_id IN (SELECT id FROM seed_sellers));
DELETE FROM product WHERE seller_id IN (SELECT id FROM seed_sellers);
DELETE FROM seller WHERE id IN (SELECT id FROM seed_sellers);
DELETE FROM member WHERE email LIKE 'seed-%@seed.test';
DROP INDEX tmp_seed_clean_order_item;
COMMIT;
