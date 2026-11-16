// 상품 조회 부하: 목록 80%(0~99 페이지 무작위), 상세 20%
// 실행: k6 run perf/product-read.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE } from './common.js';

export const options = {
  scenarios: {
    read: { executor: 'constant-vus', vus: Number(__ENV.VUS || 50), duration: __ENV.DURATION || '30s' },
  },
  // 요청 종류별 지표가 요약에 나오도록 느슨한 기준을 둔다
  thresholds: {
    'http_req_duration{name:list}': ['p(95)<2000'],
    'http_req_duration{name:detail}': ['p(95)<2000'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// 시드 상품 id는 연속이라 가장 큰 id에서 2,000개 범위를 쓴다
export function setup() {
  const res = http.get(`${BASE}/api/products?page=0&size=1`);
  return { maxId: res.json()[0].id };
}

export default function (data) {
  if (Math.random() < 0.8) {
    const page = Math.floor(Math.random() * 100);
    const res = http.get(`${BASE}/api/products?page=${page}&size=20`, { tags: { name: 'list' } });
    check(res, { 'list 200': (r) => r.status === 200 });
  } else {
    const id = data.maxId - Math.floor(Math.random() * 2000);
    const res = http.get(`${BASE}/api/products/${id}`, { tags: { name: 'detail' } });
    check(res, { 'detail 200': (r) => r.status === 200 });
  }
}
