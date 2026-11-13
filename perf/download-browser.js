import { browser } from 'k6/browser';
import { check } from 'k6';

const baseUrl = (__ENV.FRONTEND_URL || 'http://localhost:4200').replace(/\/$/, '');
const token = __ENV.DOWNLOAD_TOKEN;

if (!token) {
  throw new Error('La variable DOWNLOAD_TOKEN est obligatoire.');
}

export const options = {
  scenarios: {
    browser: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 1),
      duration: __ENV.DURATION || '1m',
      options: {
        browser: {
          type: 'chromium',
        },
      },
    },
  },
};

export default async function () {
  const page = await browser.newPage();

  try {
    // Ouverture de la page de téléchargement
    await page.goto(`${baseUrl}/${token}`);

    const downloadCard = page.locator('.download-card');
    await downloadCard.waitFor({ state: 'visible' });

    // Vérification du nom du fichier
    const fileName = await page.locator('.download-card__file-name').textContent();

    check(fileName, {
      'Nom du fichier affiché': (name) => !!name && name.trim().length > 0,
    });

    // Attente de la réponse API avant de cliquer
    const responsePromise = page.waitForResponse(/\/api\/files\/download$/);

    await page.locator('.download-card__button').click();

    const response = await responsePromise;
    const headers = response.headers();

    check(response, {
      'Téléchargement HTTP 200': (r) => r.status() === 200,
      'Content-Type octet-stream': () =>
        (headers['content-type'] || '').includes('application/octet-stream'),
      'Content-Disposition attachment': () =>
        (headers['content-disposition'] || '').toLowerCase().includes('attachment'),
    });

  } finally {
    await page.close();
  }
}