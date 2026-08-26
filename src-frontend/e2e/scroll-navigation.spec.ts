import { expect, Page, test } from '@playwright/test';

const apiUrl = process.env['E2E_API_URL'] ?? 'http://localhost:8080/api';
const adminEmail = process.env['E2E_ADMIN_EMAIL'] ?? 'e2e-admin@taemoi.test';
const adminPassword = process.env['E2E_ADMIN_PASSWORD'] ?? 'E2e-Scroll-Only-2026!';

async function expectScrollNear(page: Page, expected: number, tolerance = 12): Promise<void> {
  await expect.poll(
    () => page.evaluate((target) => Math.abs(window.scrollY - target), expected),
    { timeout: 5_000 }
  ).toBeLessThanOrEqual(tolerance);
}

async function expectAnchorVisibleBelowFixedHeader(page: Page, selector: string): Promise<void> {
  await expect.poll(async () => page.locator(selector).evaluate((element) => {
    const targetTop = element.getBoundingClientRect().top;
    const headers = Array.from(document.querySelectorAll<HTMLElement>([
      '.header-anonimo.fixed-header',
      '.header-user.fixed-header',
      '.admin-top-navbar',
      'header.fixed-header',
      '.navbar.fixed-top',
      '.navbar.sticky-top',
    ].join(','))).filter((header) => {
      const style = getComputedStyle(header);
      return style.display !== 'none' && style.visibility !== 'hidden';
    });
    const visibleHeaderBottom = headers
      .filter((header) => !header.classList.contains('is-hidden'))
      .reduce((bottom, header) => Math.max(bottom, header.getBoundingClientRect().bottom), 0);
    const reservedHeaderHeight = headers
      .reduce((height, header) => Math.max(height, header.getBoundingClientRect().height), 0);

    return targetTop >= Math.max(0, visibleHeaderBottom) - 1
      && targetTop <= Math.max(visibleHeaderBottom, reservedHeaderHeight) + 16;
  }), { timeout: 5_000 }).toBe(true);
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem('cookieConsent', 'rejected');
  });
});

async function loginAsAdmin(page: Page): Promise<void> {
  const response = await page.request.post(`${apiUrl}/auth/signin`, {
    data: { email: adminEmail, contrasena: adminPassword, rememberMe: false },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
}

async function createStudent(page: Page, suffix: string): Promise<{ id: number; nombre: string }> {
  const today = new Date().toISOString().slice(0, 10);
  const nombre = `Scroll${suffix.replace(/[^a-z0-9]/gi, '').slice(-16)}`;
  const student = {
    nombre,
    apellidos: 'Playwright',
    fechaNacimiento: '2000-01-01',
    nif: null,
    direccion: 'Calle E2E 1',
    telefono: 612345678,
    telefono2: null,
    email: `${suffix.replace(/[^a-z0-9]/gi, '').toLowerCase()}@e2e.taemoi.test`,
    autorizacionWeb: true,
    tieneDiscapacidad: false,
    aptoParaExamen: false,
    deportesInicial: [{
      deporte: 'TAEKWONDO',
      grado: 'BLANCO',
      fechaGrado: today,
      fechaAlta: today,
      fechaAltaInicial: today,
      tipoTarifa: 'ADULTO',
      cuantiaTarifa: 35,
      competidor: false,
      tieneLicencia: false,
    }],
  };
  const response = await page.request.post(`${apiUrl}/alumnos/crear`, {
    multipart: { nuevo: JSON.stringify(student) },
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  const created = await response.json();
  return { id: Number(created.id), nombre };
}

test('una ruta nueva empieza arriba y Atrás restaura el fondo de la página', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('footer')).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
  const previousY = await page.evaluate(() => window.scrollY);
  expect(previousY).toBeGreaterThan(500);

  await page.locator('footer a[href="/contacto"]').click();
  await expect(page).toHaveURL(/\/contacto$/);
  await expectScrollNear(page, 0);

  await page.goBack();
  await expect(page).toHaveURL(/\/$/);
  await expectScrollNear(page, previousY, 120);
});

test('la navegación a anclas descuenta la cabecera fija', async ({ page }) => {
  await page.goto('/');
  await page.locator('.schedule-btn').filter({ hasText: /Dónde estamos/i }).click();

  await expectAnchorVisibleBelowFixedHeader(page, '#map-section');
});

test('un cambio exclusivo de query params conserva el viewport', async ({ page }) => {
  await loginAsAdmin(page);
  await page.goto('/alumnosListar');
  await expect(page.getByRole('heading', { name: /Gestión de Alumnos/i })).toBeVisible();
  await page.evaluate(() => {
    const spacer = document.createElement('div');
    spacer.style.height = '1800px';
    spacer.dataset['e2eSpacer'] = 'true';
    document.querySelector('.admin-page-container')?.append(spacer);
    window.scrollTo(0, 640);
  });
  const previousY = await page.evaluate(() => window.scrollY);

  await page.getByRole('button', { name: 'Filtrar por alumnos aptos para examen' })
    .evaluate((button: HTMLElement) => button.click());
  await expect(page).toHaveURL(/aptoParaExamen=true/);
  await expectScrollNear(page, previousY);
});

test('guardar un alumno restaura su sección mientras el toast sigue visible', async ({ page }, testInfo) => {
  await loginAsAdmin(page);
  const suffix = `${Date.now()}-${testInfo.project.name}-${testInfo.workerIndex}`;
  const student = await createStudent(page, suffix);

  try {
    await page.goto(`/alumnosEditar/${student.id}`);
    const section = page.locator('#alumno-informacion-personal');
    await expect(section).toBeVisible();
    await section.scrollIntoViewIfNeeded();
    await section.getByRole('button', { name: 'Editar', exact: true }).click();
    await page.locator('#nombre').fill(`${student.nombre}Editado`);
    const previousTop = await section.evaluate((element) => element.getBoundingClientRect().top);

    const update = page.waitForResponse((response) =>
      response.request().method() === 'PUT' && response.url() === `${apiUrl}/alumnos/${student.id}`
    );
    await page.getByRole('button', { name: 'Guardar Cambios', exact: true }).click();
    expect((await update).ok()).toBeTruthy();

    await expect(page.locator('.swal2-toast')).toBeVisible();
    await expect.poll(() => section.evaluate((element) =>
      Math.abs(element.getBoundingClientRect().top - previousTop)
    )).toBeLessThanOrEqual(16);
    await expect(section).toContainText(`${student.nombre}Editado`);
  } finally {
    await page.request.delete(`${apiUrl}/alumnos/${student.id}`);
  }
});

test('la confirmación de preinscripción sube al resultado tras el render', async ({ page }) => {
  await page.route('**/api/preinscripciones/public/temporada', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ temporada: '2026-2027' }),
  }));
  await page.route('**/api/preinscripciones/public/grupos/TAEKWONDO', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify([{
      id: 1,
      rangoEdadMin: 0,
      rangoEdadMax: 99,
      turnos: [{ id: 11, diaSemana: 'Lunes', horaInicio: '18:00', horaFin: '19:00', completo: false }],
    }]),
  }));
  await page.route('**/api/preinscripciones/public/configuracion/TAEKWONDO', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      deporte: 'TAEKWONDO',
      version: 1,
      contenido: { importes: 'Cuota de prueba', normas: Array.from({ length: 12 }, (_, i) => `Norma ${i + 1}`) },
    }),
  }));
  await page.route('**/api/preinscripciones', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ plazaAsignada: true, referencia: 'E2E-SCROLL', tokenDocumento: 'token-e2e' }),
    });
  });

  await page.goto('/preinscripcion');
  await page.locator('[formcontrolname="nombre"]').fill('Persona');
  await page.locator('[formcontrolname="apellidos"]').fill('E2E');
  await page.locator('[formcontrolname="dni"]').fill('12345678Z');
  await page.locator('[formcontrolname="fechaNacimiento"]').fill('1990-01-01');
  await page.locator('[formcontrolname="direccion"]').fill('Calle E2E 1');
  await page.locator('[formcontrolname="telefono"]').fill('612345678');
  await page.locator('[formcontrolname="email"]').fill('preinscripcion@e2e.taemoi.test');
  await page.getByRole('radio', { name: 'No', exact: true }).check();
  await page.locator('[formcontrolname="deporte"]').selectOption('TAEKWONDO');
  await page.locator('.group-card input[type="checkbox"]').check();

  const rules = page.locator('.rules-content');
  await expect(rules).toBeVisible();
  await rules.evaluate((element) => {
    element.scrollTop = element.scrollHeight;
    element.dispatchEvent(new Event('scroll'));
  });
  await page.locator('[formcontrolname="aceptacionNormas"]').check();
  await page.locator('[formcontrolname="firmanteNombre"]').fill('Persona E2E');

  const canvas = page.locator('canvas');
  const box = await canvas.boundingBox();
  expect(box).not.toBeNull();
  await page.mouse.move(box!.x + 20, box!.y + 30);
  await page.mouse.down();
  await page.mouse.move(box!.x + 120, box!.y + 70, { steps: 5 });
  await page.mouse.up();

  await page.getByRole('button', { name: 'Enviar preinscripción' }).click();
  await expect(page.getByRole('heading', { name: 'Plaza asignada' })).toBeVisible();
  await expectScrollNear(page, 0);
  await expect(page.locator('.success')).toBeFocused();
});

test('abrir y cerrar overlays bloquea y restaura el viewport real', async ({ page }) => {
  await page.goto('/horarios');
  const classCard = page.locator(
    '.desktop-view .day-cell.has-class:visible, .mobile-view .mobile-class-card:visible'
  ).first();
  await expect(classCard).toBeVisible();
  await classCard.scrollIntoViewIfNeeded();
  const previousY = await page.evaluate(() => window.scrollY);
  await classCard.click();

  await expect(page.locator('.modal-overlay')).toBeVisible();
  await expect.poll(() => page.evaluate(() => getComputedStyle(document.body).position)).toBe('fixed');
  await page.locator('.modal-overlay .close-btn').click();
  await expect(page.locator('.modal-overlay')).toBeHidden();
  await expect.poll(() => page.evaluate(() => getComputedStyle(document.body).position)).not.toBe('fixed');
  await expectScrollNear(page, previousY);
});

test('el menú móvil usa un bloqueo contabilizado y devuelve el scroll', async ({ page }, testInfo) => {
  test.skip(!testInfo.project.name.includes('mobile'), 'Solo aplica al menú móvil');
  await page.goto('/');
  await page.evaluate(() => window.scrollTo(0, 500));
  await page.evaluate(() => window.scrollTo(0, 450));
  const openMenu = page.getByRole('button', { name: 'Abrir menú de navegación' });
  await expect(openMenu).toBeVisible();
  const previousY = await page.evaluate(() => window.scrollY);
  expect(previousY).toBeGreaterThan(100);
  await openMenu.click();
  await expect(page.locator('.mobile-header-overlay')).toBeVisible();
  await expect.poll(() => page.evaluate(() => getComputedStyle(document.body).position)).toBe('fixed');

  await page.getByRole('button', { name: 'Cerrar menú' }).click();
  await expect(page.locator('.mobile-header-overlay')).toBeHidden();
  await expect.poll(() => page.evaluate(() => getComputedStyle(document.body).position)).not.toBe('fixed');
  await expectScrollNear(page, previousY);
});
