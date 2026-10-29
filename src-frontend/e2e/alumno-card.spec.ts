import { expect, test } from '@playwright/test';

const api = process.env['E2E_API_URL'] ?? 'http://localhost:8080/api';

test('perfil compacto con deportes, nombre largo y foto accesible', async ({ page }, info) => {
  await page.addInitScript(() => localStorage.setItem('cookieConsent', 'rejected'));
  const login = await page.request.post(`${api}/auth/signin`, {
    data: {
      email: process.env['E2E_ADMIN_EMAIL'] ?? 'e2e-admin@taemoi.test',
      contrasena: process.env['E2E_ADMIN_PASSWORD'] ?? 'E2e-Scroll-Only-2026!',
      rememberMe: false,
    },
  });
  expect(login.ok()).toBeTruthy();
  const today = new Date().toISOString().slice(0, 10);
  const created = await page.request.post(`${api}/alumnos/crear`, {
    multipart: { nuevo: JSON.stringify({
      nombre: 'María del Carmen Alejandra', apellidos: 'Fernández de la Torre García',
      fechaNacimiento: '2000-01-01', direccion: 'Calle E2E 1', telefono: 612345678,
      email: `card-${Date.now()}@e2e.taemoi.test`, autorizacionWeb: true,
      tieneDiscapacidad: false, aptoParaExamen: false,
      // Como el formulario de alta: Pilates y DPF no tienen grados y el backend rechaza uno indicado.
      deportesInicial: ['TAEKWONDO', 'PILATES', 'KICKBOXING', 'DEFENSA_PERSONAL_FEMENINA'].map(deporte => ({
        deporte,
        ...(deporte === 'TAEKWONDO' || deporte === 'KICKBOXING' ? { grado: 'BLANCO', fechaGrado: today } : {}),
        fechaAlta: today, fechaAltaInicial: today,
        tipoTarifa: 'ADULTO', cuantiaTarifa: 35, competidor: false, tieneLicencia: false,
      })),
    }) },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  const alumno = await created.json();
  try {
    await page.goto(`/alumnosEditar/${alumno.id}`);
    const card = page.getByRole('region', { name: 'María del Carmen Alejandra Fernández de la Torre García' });
    const photo = card.getByRole('button', { name: 'Ampliar la foto de María del Carmen Alejandra' });
    await expect(photo).toBeVisible();
    const disciplines = card.getByRole('list', { name: 'Disciplinas' });
    await expect(disciplines.getByRole('listitem')).toHaveCount(4);
    await expect(disciplines).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
    await expect(photo.locator('img')).toHaveAttribute('src', /default.webp/);
    await expect(photo.locator('img')).toHaveCSS('object-position', '50% 0%');
    await expect(card.getByRole('button', { name: 'Quitar', exact: true })).toBeDisabled();
    const desktop = page.viewportSize()!.width >= 768;
    const bounds = await photo.boundingBox();
    expect(bounds!.width).toBeCloseTo(desktop ? 240 : 184, 1);
    expect(bounds!.height).toBeCloseTo(desktop ? 300 : 230, 1);
    const details = await card.getByRole('heading', { level: 1 }).boundingBox();
    expect(details!.x).toBeGreaterThan(bounds!.x + bounds!.width);
    expect(await card.evaluate(el => el.scrollWidth <= el.clientWidth)).toBeTruthy();
    await card.screenshot({ path: info.outputPath('alumno-card.png') });
    await photo.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('dialog', { name: 'Foto ampliada del alumno' })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog', { name: 'Foto ampliada del alumno' })).not.toBeVisible();
    await expect(photo).toBeFocused();
    await page.keyboard.press('Space');
    await expect(page.getByRole('dialog', { name: 'Foto ampliada del alumno' })).toBeVisible();
    await page.getByRole('button', { name: 'Cerrar foto ampliada' }).click();
    await expect(photo).toBeFocused();
    const upload = page.waitForResponse(response =>
      response.request().method() === 'PUT' && response.url().endsWith(`/alumnos/${alumno.id}`));
    await card.getByLabel('Elegir una foto nueva del alumno').setInputFiles('src/favicon-48x48.png');
    expect((await upload).ok()).toBeTruthy();
    await expect(card.getByRole('button', { name: 'Quitar', exact: true })).toBeEnabled();
    await expect(photo.locator('img')).not.toHaveAttribute('src', /assets\/media\/default.webp/);
    await expect(photo.locator('img')).toHaveJSProperty('complete', true);
    await card.screenshot({ path: info.outputPath('alumno-card-con-foto.png') });
    await card.getByRole('button', { name: 'Quitar', exact: true }).click();
    await expect(card.getByRole('button', { name: 'Quitar', exact: true })).toBeDisabled();
    await expect(photo.locator('img')).toHaveAttribute('src', /assets\/media\/default.webp/);
  } finally {
    const removed = await page.request.delete(`${api}/alumnos/${alumno.id}`);
    expect(removed.ok()).toBeTruthy();
  }
});
