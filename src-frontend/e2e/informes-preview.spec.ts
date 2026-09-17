import { expect, Page, test } from '@playwright/test';

// Two real PDF pages, generated without dependencies; PDF.js uses its actual worker.
function pdfFixture(): Buffer {
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 7 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    ...[1, 2].map(n => {
      const content = `BT /F1 22 Tf 50 760 Td (Informe de prueba - pagina ${n}) Tj ET`;
      return `<< /Length ${content.length} >>\nstream\n${content}\nendstream`;
    }),
  ];
  let pdf = '%PDF-1.4\n'; const offsets = [0];
  objects.forEach((object, index) => { offsets.push(pdf.length); pdf += `${index + 1} 0 obj\n${object}\nendobj\n`; });
  const xref = pdf.length;
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  pdf += offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('');
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
  return Buffer.from(pdf);
}

async function preparar(page: Page) {
  const requests: string[] = [];
  const pdf = pdfFixture();
  await page.addInitScript(() => localStorage.setItem('cookieConsent', 'rejected'));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/roles')) { await route.fulfill({ json: ['ROLE_ADMIN'] }); }
    else if (url.pathname.endsWith('/user')) { await route.fulfill({ json: { id: 1, nombre: 'Admin', email: 'admin@example.test', roles: ['ROLE_ADMIN'] } }); }
    else if (url.pathname.endsWith('/temporadas')) { await route.fulfill({ json: ['2026/2027'] }); }
    else if (url.pathname.includes('/informes/')) {
      requests.push(url.pathname + url.search);
      await route.fulfill({ contentType: 'application/pdf', body: pdf });
    } else if (url.pathname.endsWith('/alumnos')) { await route.fulfill({ json: { content: [], totalPages: 0, totalElements: 0 } }); }
    else { await route.fulfill({ json: [] }); }
  });
  await page.goto('/alumnosListar');
  await expect(page.getByRole('heading', { name: 'Gestión de Alumnos' })).toBeVisible();
  return requests;
}

async function abrirInforme(page: Page) {
  await page.getByRole('button', { name: 'Generar informe de alumnos' }).click();
  await page.getByText('Informe de Alumnos por Grado General', { exact: true }).click();
  const trigger = page.locator('app-informe-modal').getByRole('button', { name: 'Ver PDF', exact: true });
  await trigger.click();
  const dialog = page.locator('app-informe-pdf-modal dialog');
  await expect(dialog).toBeVisible();
  return { dialog, trigger };
}

test('PDF real: ver, navegar, zoom, expansión, teclado, descargar sin regenerar y restaurar foco/scroll', async ({ page }, testInfo) => {
  const requests = await preparar(page);
  let downloads = 0; page.on('download', () => downloads++);
  const { dialog, trigger } = await abrirInforme(page);
  await expect(dialog.locator('.pdf-canvas-page')).toHaveText('Página 1 / 2');
  await expect(dialog.locator('.pdf-canvas-loading')).toHaveCount(0);
  expect(downloads).toBe(0); expect(requests).toHaveLength(1);
  const canvas = dialog.locator('canvas');
  expect(await canvas.evaluate((el: HTMLCanvasElement) => el.width)).toBeGreaterThan(100);
  await dialog.getByRole('button', { name: 'Siguiente' }).click();
  await expect(dialog.locator('.pdf-canvas-page')).toHaveText('Página 2 / 2');
  await expect(dialog.locator('.pdf-canvas-loading')).toHaveCount(0);
  await dialog.getByRole('button', { name: 'Aumentar zoom' }).click();
  await expect(dialog.locator('.pdf-canvas-zoom')).toHaveText('115%');
  await expect(dialog.locator('.pdf-canvas-loading')).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath('visor.png'), fullPage: true });
  await dialog.getByRole('button', { name: /Expandir/ }).click();
  await expect(dialog.locator('.is-expanded')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dialog.locator('.is-expanded')).toHaveCount(0); await expect(dialog).toBeVisible();
  // Native modal contains keyboard focus even after repeated tab cycles.
  for (let n = 0; n < 14; n++) {
    await page.keyboard.press('Tab');
    expect(await dialog.evaluate(el => el.contains(document.activeElement))).toBe(true);
  }
  const downloadEvent = page.waitForEvent('download');
  await dialog.getByRole('button', { name: 'Descargar PDF', exact: true }).click();
  expect((await downloadEvent).suggestedFilename()).toBe('informe.pdf');
  expect(requests).toHaveLength(1);
  await page.keyboard.press('Escape'); await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await page.locator('app-informe-modal').getByRole('button', { name: 'Cancelar', exact: true }).click();
  await expect(page.locator('app-informe-modal')).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => document.body.style.position)).not.toBe('fixed');
});

test('un PDF inválido conserva descarga y permite cerrar', async ({ page }) => {
  await preparar(page);
  await page.route('**/api/informes/alumnosPorGrado*', route => route.fulfill({ contentType: 'application/pdf', body: 'PDF inválido' }));
  const { dialog } = await abrirInforme(page);
  await expect(dialog.getByRole('alert')).toContainText('No se pudo cargar el visor');
  const download = page.waitForEvent('download');
  await dialog.getByRole('button', { name: 'Descargar PDF', exact: true }).click();
  expect((await download).suggestedFilename()).toBe('informe.pdf');
  await dialog.getByRole('button', { name: 'Cerrar', exact: true }).click();
  await expect(dialog).toHaveCount(0);
});

test('asistencia múltiple: fallos parciales, selector, descarga visible y scroll restaurado', async ({ page }, testInfo) => {
  await preparar(page);
  await page.route('**/api/informes/asistencia*', route => {
    const grupo = new URL(route.request().url()).searchParams.get('grupo');
    return grupo === 'lunes'
      ? route.fulfill({ status: 500, json: { mensaje: 'Error de prueba' } })
      : route.fulfill({ contentType: 'application/pdf', body: pdfFixture() });
  });
  const section = page.locator('details').filter({ has: page.locator('summary', { hasText: 'Listado de Asistencia' }) });
  await section.locator('summary').click();
  await section.locator('#mesAnoAsis-ano').selectOption('2026');
  await section.locator('#mesAnoAsis').selectOption('09');
  for (const dia of ['viernes', 'lunes', 'martes']) { await section.locator('.dia-chip').filter({ hasText: new RegExp(dia, 'i') }).click(); }
  const trigger = section.getByRole('button', { name: /Ver PDF/ });
  await trigger.scrollIntoViewIfNeeded();
  const scroll = await page.evaluate(() => window.scrollY);
  await trigger.click();
  const dialog = page.locator('app-informe-pdf-modal dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('alert')).toContainText('lunes');
  await expect(dialog.locator('option')).toHaveText(['Asistencia viernes — 2026-09', 'Asistencia martes — 2026-09']);
  await expect(dialog.locator('.pdf-canvas-loading')).toHaveCount(0);
  await dialog.getByLabel('Documento', { exact: true }).selectOption('1');
  await expect(dialog.getByRole('heading')).toHaveText('Asistencia martes — 2026-09');
  await expect(dialog.locator('.pdf-canvas-page')).toHaveText('Página 1 / 2');
  await expect(dialog.locator('.pdf-canvas-loading')).toHaveCount(0);
  const download = page.waitForEvent('download');
  await dialog.getByRole('button', { name: 'Descargar PDF', exact: true }).click();
  expect((await download).suggestedFilename()).toBe('Asistencia-martes-2026-09.pdf');
  await page.screenshot({ path: testInfo.outputPath('asistencia-multiple.png'), fullPage: true });
  await dialog.getByRole('button', { name: 'Cerrar', exact: true }).click();
  await expect(dialog).toHaveCount(0); await expect(trigger).toBeFocused();
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeCloseTo(scroll, 0);
});
