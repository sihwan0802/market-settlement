import http from 'k6/http';

export const BASE = __ENV.BASE_URL || 'http://localhost:8080';
export const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function login(email, password = 'password123') {
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email, password }), { headers: JSON_HEADERS });
  if (res.status !== 200) {
    throw new Error(`login failed for ${email}: ${res.status} ${res.body}`);
  }
  return res.json('accessToken');
}

export function authHeaders(token) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } };
}
