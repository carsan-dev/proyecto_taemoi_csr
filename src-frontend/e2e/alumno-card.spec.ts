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
      deportesInicial: ['TAEKWONDO', 'PILATES', 'KICKBOXING', 'DEFENSA_PERSONAL_FEMENINA'].map(deporte => ({
        deporte, grado: 'BLANCO', fechaGrado: today, fechaAlta: today, fechaAltaInicial: today,
        tipoTarifa: 'ADULTO', cuantiaTarifa: 35, competidor: false, tieneLicencia: false,
      })),
    }) },
  });
  expect(created.ok(), await created.text()).toBeTruthy();
  const alumno = await created.json();
  try {
    await page.goto(`/alumnosEditar/${alumno.id}`);
    const card = page.locator('.alumno-profile-card');
    const photo = card.getByRole('button', { name: 'Ampliar foto del alumno' });
    await expect(photo).toBeVisible();
    await expect(card.locator('.deporte-banner-badge')).toHaveCount(4);
    await expect(card.locator('.profile-image')).toHaveAttribute('src', /default.webp/);
    await expect(card.locator('.profile-image')).toHaveCSS('object-position', '50% 30%');
    await expect(card.getByRole('button', { name: 'Eliminar imagen del alumno' })).toBeDisabled();
    const desktop = page.viewportSize()!.width >= 768;
    const bounds = await photo.boundingBox();
    expect(bounds!.width).toBeCloseTo(desktop ? 220 : 180, 1);
    expect(bounds!.height).toBeCloseTo(desktop ? 280 : 230, 1);
    await expect(card.locator('.deportes-banner-container')).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
    const details = await card.locator('.profile-details').boundingBox();
    if (desktop) expect(details!.x).toBeGreaterThan(bounds!.x + bounds!.width);
    else expect(details!.y).toBeGreaterThan(bounds!.y + bounds!.height);
    expect(await card.evaluate(el => el.scrollWidth <= el.clientWidth)).toBeTruthy();
    await card.screenshot({ path: info.outputPath('alumno-card.png') });
    await photo.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('dialog', { name: 'Foto ampliada del alumno' })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.locator('#imageModal')).not.toBeVisible();
    await expect(photo).toBeFocused();
    await page.keyboard.press('Space');
    await expect(page.locator('#imageModal')).toBeVisible();
    await page.getByRole('button', { name: 'Cerrar foto ampliada' }).click();
    await expect(photo).toBeFocused();
    const upload = page.waitForResponse(response =>
      response.request().method() === 'PUT' && response.url().endsWith(`/alumnos/${alumno.id}`));
    await card.locator('input[type="file"]').setInputFiles('src/favicon-48x48.png');
    expect((await upload).ok()).toBeTruthy();
    await expect(card.getByRole('button', { name: 'Eliminar imagen del alumno' })).toBeEnabled();
    await expect(card.locator('.profile-image')).not.toHaveAttribute('src', /assets\/media\/default.webp/);
    await expect(card.locator('.profile-image')).toHaveJSProperty('complete', true);
    await card.screenshot({ path: info.outputPath('alumno-card-con-foto.png') });
    await card.getByRole('button', { name: 'Eliminar imagen del alumno' }).click();
    await expect(card.getByRole('button', { name: 'Eliminar imagen del alumno' })).toBeDisabled();
    await expect(card.locator('.profile-image')).toHaveAttribute('src', /assets\/media\/default.webp/);
  } finally {
    const removed = await page.request.delete(`${api}/alumnos/${alumno.id}`);
    expect(removed.ok()).toBeTruthy();
  }
});
