import http from 'k6/http';
import { check } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const token = __ENV.DOWNLOAD_TOKEN;

if (!token) {
  throw new Error('La variable DOWNLOAD_TOKEN est obligatoire.');
}

export const options = {
  vus: 10,
  duration: '1m',
  discardResponseBodies: true,
};

export default function () {
  const payload = {
    token: token
  };

  const response = http.post(
    `${baseUrl}/api/files/download`,
    JSON.stringify(payload),
    {
      headers: {
        'Content-Type': 'application/json',
      },
      responseType: 'none', // K6 does not keep the file in memory
      tags: {
        endpoint: 'download',
      },
    }
  );

  check(response, {
    'HTTP 200': (r) => r.status === 200,
    'Content-Type octet-stream': (r) =>
      (r.headers['Content-Type'] || '').includes('application/octet-stream'),
    'Content-Disposition attachment': (r) =>
      (r.headers['Content-Disposition'] || '').toLowerCase().includes('attachment'),
  });
}