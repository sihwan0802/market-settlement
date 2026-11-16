// 정산 배치: 최근 30일을 하루씩 순서대로 정산하고 마지막에 정합성 검증
// 사전: DELETE FROM settlement_item; DELETE FROM settlement;  (시드 정산 행 정리)
// 실행: k6 run perf/settlement.js
import http from 'k6/http';
import { Trend, Counter } from 'k6/metrics';
import { BASE, login, authHeaders } from './common.js';

const settleMs = new Trend('settle_ms', true);
const settlements = new Counter('settlements_created');
const checkMs = new Trend('consistency_check_ms', true);

export const options = {
  scenarios: { run: { executor: 'shared-iterations', vus: 1, iterations: 1, maxDuration: '30m' } },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
};

function fmt(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function setup() {
  return { token: login('seed-admin@seed.test') };
}

export default function (data) {
  const params = { ...authHeaders(data.token), timeout: '600s' };
  for (let back = 30; back >= 1; back--) {
    const date = fmt(new Date(Date.now() - back * 86400000));
    const res = http.post(`${BASE}/api/admin/settlements?date=${date}`, null, params);
    settleMs.add(res.timings.duration);
    if (res.status === 200) settlements.add(res.json('created'));
    console.log(`${date} status=${res.status} created=${res.json('created')} elapsedMs=${res.json('elapsedMs')}`);
  }
  const check = http.get(`${BASE}/api/admin/settlements/inconsistent`, params);
  checkMs.add(check.timings.duration);
  console.log(`inconsistent=${JSON.stringify(check.json('settlementIds'))} elapsedMs=${check.json('elapsedMs')}`);
}
