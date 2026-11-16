// 선착순 구매: 재고 100개 상품에 1,000명이 동시에 1개씩 주문
// 사전: UPDATE inventory SET stock = 100 WHERE product_id = <FLASH_PRODUCT_ID>;
// 실행: k6 run -e FLASH_PRODUCT_ID=<id> perf/flash-sale.js
//   -e MODE=spread 를 주면 1,000건을 한꺼번에 보내지 않고 2초에 걸쳐 보낸다
// 사후: 주문 항목 수가 재고(100)와 같고 재고가 0인지 DB에서 확인
import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { BASE, login, authHeaders } from './common.js';

const BUYERS = 50;
const sold = new Counter('orders_created');
const soldOut = new Counter('orders_rejected_409');
const other = new Counter('orders_other');

const burst = { executor: 'per-vu-iterations', vus: 1000, iterations: 1, maxDuration: '120s' };
const spread = { executor: 'constant-arrival-rate', rate: 500, timeUnit: '1s', duration: '2s', preAllocatedVUs: 1000, maxVUs: 1000 };

export const options = {
  scenarios: { sale: __ENV.MODE === 'spread' ? spread : burst },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const tokens = [];
  for (let i = 1; i <= BUYERS; i++) {
    tokens.push(login(`seed-buyer-${i}@seed.test`));
  }
  return { tokens };
}

export default function (data) {
  const token = data.tokens[__VU % BUYERS];
  const body = JSON.stringify({ items: [{ productId: Number(__ENV.FLASH_PRODUCT_ID), quantity: 1 }] });
  const res = http.post(`${BASE}/api/orders`, body, authHeaders(token));
  if (res.status === 201) sold.add(1);
  else if (res.status === 409) soldOut.add(1);
  else other.add(1);
}
