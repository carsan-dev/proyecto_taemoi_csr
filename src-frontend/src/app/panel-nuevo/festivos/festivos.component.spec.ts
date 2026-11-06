import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { Festivo } from './festivos-vista';
import { FestivosComponent } from './festivos.component';

const ESTEPA = { id: 2, nombre: 'Estepa', activa: true };
const HERRERA = { id: 3, nombre: 'Herrera', activa: true };
const MOLLINA = { id: 4, nombre: 'Mollina', activa: true };
const INACTIVA = { id: 9, nombre: 'Cerrada', activa: false };

const permisos = (parcial: Partial<PermisosSesion> = {}): PermisosSesion => ({
  roles: ['ADMINISTRADOR'], esAdminPlataforma: false, sedes: [ESTEPA, HERRERA, MOLLINA, INACTIVA], variasSedes: true, tieneGrupos: false,
  capacidades: [], ...parcial,
});

const festivo = (cambios: Partial<Festivo> = {}): Festivo => ({
  id: 1, tipo: 'DIA', fechaDesde: '2026-11-05', fechaHasta: '2026-11-05', mes: null, dia: null, proximaFecha: '2026-11-05',
  nombre: 'Feria de Estepa', sedes: [{ id: 2, nombre: 'Estepa' }], todasLasSedes: false, editable: true, ...cambios,
});

const NAVIDAD = festivo({
  id: 2, tipo: 'RANGO', fechaDesde: '2026-12-23', fechaHasta: '2027-01-07', nombre: 'Vacaciones de Navidad',
  sedes: [{ id: 2, nombre: 'Estepa' }, { id: 3, nombre: 'Herrera' }, { id: 4, nombre: 'Mollina' }], todasLasSedes: true,
});
const INMACULADA = festivo({ id: 3, tipo: 'ANUAL', fechaDesde: null, fechaHasta: null, mes: 12, dia: 8, nombre: 'Inmaculada Concepción' });
const SIN_NOMBRE = festivo({ id: 4, tipo: 'RANGO', fechaDesde: '2027-03-22', fechaHasta: '2027-03-26', nombre: null });

describe('FestivosComponent (decisión 458)', () => {
  let fixture: ComponentFixture<FestivosComponent>;
  let http: HttpTestingController;
  let avisos: jasmine.SpyObj<AvisosService>;
  let confirmar: jasmine.Spy;
  const url = `${environment.apiUrl}/festivos`;
  const el = () => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined) => n?.textContent?.replace(/\s+/g, ' ').trim();
  const boton = (t: string) =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('button')).find((b) => texto(b) === t);
  const filas = () => Array.from(el().querySelectorAll('.festivos__item'));
  const panel = () => el().querySelector<HTMLDialogElement>('dialog.pn-panel');
  const lista = (pasados = false) => http.expectOne((r) => r.url === url && r.method === 'GET' && r.params.get('pasados') === String(pasados));

  function crear(p: PermisosSesion = permisos()): void {
    avisos = jasmine.createSpyObj<AvisosService>('AvisosService', ['exito', 'error', 'info']);
    confirmar = jasmine.createSpy('confirmar').and.resolveTo({ confirmado: false, valor: null });
    TestBed.configureTestingModule({
      imports: [FestivosComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: AvisosService, useValue: avisos },
        { provide: ConfirmacionService, useValue: { confirmar } },
        { provide: PermisosSesionService, useValue: { obtener: () => of(p) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FestivosComponent);
    fixture.detectChanges();
  }

  function arrancar(festivos: Festivo[] = [festivo(), NAVIDAD, INMACULADA, SIN_NOMBRE], p?: PermisosSesion): void {
    crear(p);
    lista().flush(festivos);
    fixture.detectChanges();
  }

  function escribir(selector: string, valor: string): void {
    const control = el().querySelector<HTMLInputElement | HTMLSelectElement>(selector)!;
    control.value = valor;
    control.dispatchEvent(new Event(control.tagName === 'SELECT' ? 'change' : 'input'));
    fixture.detectChanges();
  }

  function marcarSede(nombre: string): void {
    const label = Array.from(el().querySelectorAll('.festivos__casillas label')).find((l) => texto(l) === nombre)!;
    label.querySelector<HTMLInputElement>('input')!.click();
    fixture.detectChanges();
  }

  afterEach(() => { http.verify(); fixture.destroy(); });

  describe('lista', () => {
    it('próximos: fecha, tipo, nombre y sedes por fila, con total y título', () => {
      arrancar();
      expect(texto(el().querySelector('h1'))).toBe('Festivos');
      expect(texto(el().querySelector('.festivos__total'))).toBe('4 festivos');
      expect(el().querySelector('.festivos__lista')?.getAttribute('aria-label')).toBe('Próximos festivos');
      const f = filas();
      expect(texto(f[0].querySelector('.festivos__fecha'))).toBe('Jueves 5 de noviembre Un día');
      expect(texto(f[0].querySelector('.festivos__nombre'))).toBe('Feria de Estepa');
      expect(Array.from(f[0].querySelectorAll('.festivos__sede')).map(texto)).toEqual(['Estepa']);
      expect(texto(f[1].querySelector('.festivos__fecha'))).toBe('Del 23 dic al 7 ene Varios días');
      expect(Array.from(f[1].querySelectorAll('.festivos__sede')).map(texto)).toEqual(['Todas las sedes']);
      expect(texto(f[2].querySelector('.festivos__fecha'))).toBe('8 de diciembre Cada año');
      expect(texto(f[3].querySelector('.festivos__nombre'))).toBe('Festivo sin nombre');
      expect(f[3].querySelector('.festivos__nombre--vacio')).not.toBeNull();
      expect(f[0].querySelector('.festivos__fila')?.getAttribute('aria-label')).toBe('Editar Feria de Estepa, Jueves 5 de noviembre');
      expect(f[0].querySelector('.festivos__quitar')?.getAttribute('aria-label')).toBe('Quitar Feria de Estepa');
    });

    it('«Ver pasados» pide los pasados y vuelve con «Ver próximos»', () => {
      arrancar();
      boton('Ver pasados')!.click();
      lista(true).flush([]);
      fixture.detectChanges();
      expect(texto(el().querySelector('.festivos__vacio-titulo'))).toBe('No hay festivos pasados');
      expect(el().querySelector('.festivos__vacio button')).toBeNull();
      boton('Ver próximos')!.click();
      lista(false).flush([festivo()]);
      fixture.detectChanges();
      expect(texto(el().querySelector('.festivos__total'))).toBe('1 festivo');
    });

    it('filtro por sede: «Todas las sedes» y las activas; elegir pide con sedeId', () => {
      arrancar();
      const select = el().querySelector<HTMLSelectElement>('#fv-filtro-sede')!;
      expect(Array.from(select.options).map((o) => texto(o))).toEqual(['Todas las sedes', 'Estepa', 'Herrera', 'Mollina']);
      expect(el().querySelector('label[for="fv-filtro-sede"]')?.textContent).toBe('Sede');
      escribir('#fv-filtro-sede', '3');
      const r = http.expectOne((x) => x.url === url && x.params.get('sedeId') === '3');
      expect(r.request.params.get('pasados')).toBe('false');
      r.flush([]);
      fixture.detectChanges();
      expect(select.classList.contains('festivos__filtro--elegido')).toBeTrue();
    });

    it('con una sola sede no hay filtro', () => {
      arrancar([festivo()], permisos({ sedes: [ESTEPA], variasSedes: false }));
      expect(el().querySelector('#fv-filtro-sede')).toBeNull();
    });

    it('vacío: el texto del boceto y «Nuevo festivo», que abre el panel', () => {
      arrancar([]);
      expect(texto(el().querySelector('.festivos__vacio-titulo'))).toBe('No hay festivos próximos');
      expect(texto(el().querySelector('.festivos__vacio-texto'))).toBe(
        'Añade los días sin clase: fiestas locales, vacaciones o puentes. En esos días no se pasa lista y nadie tiene falta.'
      );
      expect(el().querySelector('.festivos__vacio')?.getAttribute('role')).toBe('status');
      expect(texto(el().querySelector('.festivos__total'))).toBe('Ningún festivo');
      el().querySelector<HTMLButtonElement>('.festivos__vacio button')!.click();
      fixture.detectChanges();
      expect(texto(panel()?.querySelector('.pn-panel__titulo'))).toBe('Nuevo festivo');
    });

    it('error al cargar: bloque con «Reintentar»', () => {
      crear();
      lista().flush({}, { status: 500, statusText: 'Error' });
      fixture.detectChanges();
      expect(el().querySelector('pn-bloque-error')).not.toBeNull();
      expect(boton('Nuevo festivo')?.disabled).toBeTrue();
      el().querySelector<HTMLButtonElement>('pn-bloque-error button')!.click();
      lista().flush([]);
    });
  });

  describe('alta', () => {
    it('un día: pastillas del tipo, validación, impacto con espera y POST', fakeAsync(() => {
      arrancar();
      boton('Nuevo festivo')!.click();
      fixture.detectChanges();
      expect(texto(panel()!.querySelector('.pn-panel__descripcion'))).toBe('Días sin clase en las sedes que elijas');
      const radios = Array.from(panel()!.querySelectorAll<HTMLButtonElement>('[role="radio"]'));
      expect(radios.map((r) => [texto(r), r.getAttribute('aria-checked')])).toEqual([
        ['Un día', 'true'], ['De una fecha a otra', 'false'], ['Cada año la misma fecha', 'false'],
      ]);
      expect(panel()!.querySelector('[role="radiogroup"]')?.getAttribute('aria-labelledby')).toBe('fv-tipo-leyenda');
      // Casillas: «Todas las sedes» y las activas.
      expect(Array.from(panel()!.querySelectorAll('.festivos__casillas label')).map(texto)).toEqual(['Todas las sedes', 'Estepa', 'Herrera', 'Mollina']);
      boton('Crear festivo')!.click();
      fixture.detectChanges();
      expect(texto(el().querySelector('#fv-error-fecha'))).toBe('Elige el día.');
      expect(texto(el().querySelector('#fv-error-sedes'))).toBe('Elige al menos una sede.');
      expect(document.activeElement?.id).toBe('fv-fecha');
      http.expectNone(url);
      escribir('#fv-fecha', '2026-11-05');
      marcarSede('Estepa');
      escribir('#fv-nombre', 'Feria de Estepa');
      expect(texto(el().querySelector('#fv-ayuda-nombre'))).toBe('Sale en Pasar lista y en el PDF. 15 / 60');
      tick(400);
      const impacto = http.expectOne(`${url}/impacto`);
      expect(impacto.request.body).toEqual({ tipo: 'DIA', sedeIds: [2], nombre: 'Feria de Estepa', fechaDesde: '2026-11-05', fechaHasta: '2026-11-05' });
      impacto.flush({
        clases: 9, conLista: 2, conListaDetalle: [
          { sedeId: 2, sede: 'Estepa', fecha: '2026-10-30', horaInicio: '17:00', grupo: 'Iniciación' },
          { sedeId: 2, sede: 'Estepa', fecha: '2026-10-30', horaInicio: '19:00', grupo: 'Avanzados' },
        ],
      });
      fixture.detectChanges();
      expect(texto(el().querySelector('.festivos__nota'))).toBe(
        'Afecta a 9 clases; 2 ya tienen lista y se quedan como están (Estepa, viernes 30: Iniciación y Avanzados).'
      );
      expect(el().querySelector('.festivos__nota')?.getAttribute('role')).toBe('status');
      boton('Crear festivo')!.click();
      const post = http.expectOne((r) => r.url === url && r.method === 'POST');
      expect(post.request.body).toEqual({ tipo: 'DIA', sedeIds: [2], nombre: 'Feria de Estepa', fechaDesde: '2026-11-05', fechaHasta: '2026-11-05' });
      post.flush(festivo(), { status: 201, statusText: 'Created' });
      fixture.detectChanges();
      expect(avisos.exito).toHaveBeenCalledWith('Festivo creado.');
      expect(panel()).toBeNull();
      lista().flush([festivo()]);
      tick(400);
    }));

    it('de una fecha a otra: días contados, fin anterior al inicio y «Todas las sedes»', fakeAsync(() => {
      arrancar();
      boton('Nuevo festivo')!.click();
      fixture.detectChanges();
      boton('De una fecha a otra')!.click();
      fixture.detectChanges();
      expect(boton('De una fecha a otra')!.getAttribute('aria-checked')).toBe('true');
      escribir('#fv-desde', '2026-10-30');
      escribir('#fv-hasta', '2026-11-02');
      expect(texto(el().querySelector('#fv-ayuda-rango'))).toBe('Los dos días cuentan: 4 días.');
      marcarSede('Todas las sedes');
      expect(Array.from(el().querySelectorAll<HTMLInputElement>('.festivos__casillas input')).every((i) => i.checked)).toBeTrue();
      tick(400);
      http.expectOne(`${url}/impacto`).flush({ clases: 18, conLista: 0, conListaDetalle: [] });
      fixture.detectChanges();
      expect(texto(el().querySelector('.festivos__nota'))).toBe('Afecta a 18 clases; ninguna tiene lista todavía.');
      escribir('#fv-hasta', '2026-10-01');
      tick(400);
      boton('Crear festivo')!.click();
      fixture.detectChanges();
      expect(texto(el().querySelector('#fv-error-hasta'))).toBe('El último día no puede ser anterior al primero.');
      expect(el().querySelector('#fv-hasta')?.getAttribute('aria-invalid')).toBe('true');
      escribir('#fv-hasta', '2026-11-02');
      tick(400);
      http.expectOne(`${url}/impacto`).flush({ clases: 18, conLista: 0, conListaDetalle: [] });
      boton('Crear festivo')!.click();
      const post = http.expectOne((r) => r.url === url && r.method === 'POST');
      expect(post.request.body).toEqual({ tipo: 'RANGO', sedeIds: [2, 3, 4], fechaDesde: '2026-10-30', fechaHasta: '2026-11-02' });
      post.flush({}, { status: 400, statusText: 'Bad Request' });
      fixture.detectChanges();
      expect(avisos.error).toHaveBeenCalledWith('No se ha podido guardar el festivo. Inténtalo de nuevo.');
      expect(panel()).not.toBeNull();
    }));

    it('cada año: día y mes sin año; 31 de abril no vale', fakeAsync(() => {
      arrancar();
      boton('Nuevo festivo')!.click();
      fixture.detectChanges();
      boton('Cada año la misma fecha')!.click();
      fixture.detectChanges();
      expect(texto(el().querySelector('#fv-ayuda-anual'))).toBe('Sin año: se repite todos los años. Si cae en un día sin clase, no afecta a nada.');
      escribir('#fv-dia', '31');
      escribir('#fv-mes', '4');
      marcarSede('Herrera');
      // Abril tiene 30 días: el 31 ya no se ofrece.
      expect(Array.from(el().querySelector<HTMLSelectElement>('#fv-dia')!.options).length).toBe(31);
      tick(400);
      boton('Crear festivo')!.click();
      fixture.detectChanges();
      expect(texto(el().querySelector('#fv-error-dia'))).toBe('Abril no tiene día 31.');
      escribir('#fv-dia', '8');
      escribir('#fv-mes', '12');
      tick(400);
      http.expectOne(`${url}/impacto`).flush({ clases: 0, conLista: 0, conListaDetalle: [] });
      fixture.detectChanges();
      expect(texto(el().querySelector('.festivos__nota'))).toBe('No afecta a ninguna clase.');
      boton('Crear festivo')!.click();
      const post = http.expectOne((r) => r.url === url && r.method === 'POST');
      expect(post.request.body).toEqual({ tipo: 'ANUAL', sedeIds: [3], mes: 12, dia: 8 });
      post.flush(INMACULADA);
      lista().flush([]);
    }));

    it('teclado en las pastillas: flechas con vuelta, Inicio y Fin', () => {
      arrancar();
      boton('Nuevo festivo')!.click();
      fixture.detectChanges();
      const grupo = el().querySelector('[role="radiogroup"]')!;
      const tecla = (key: string) => {
        grupo.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
        fixture.detectChanges();
      };
      tecla('ArrowRight');
      expect(texto(document.activeElement)).toBe('De una fecha a otra');
      tecla('End');
      expect(texto(document.activeElement)).toBe('Cada año la misma fecha');
      tecla('ArrowRight');
      expect(texto(document.activeElement)).toBe('Un día');
      expect(el().querySelector('[role="radio"][aria-checked="true"]')?.getAttribute('tabindex')).toBe('0');
    });

    it('con una sola sede, viene marcada y no hay «Todas las sedes»', () => {
      arrancar([], permisos({ sedes: [ESTEPA], variasSedes: false }));
      boton('Nuevo festivo')!.click();
      fixture.detectChanges();
      const casillas = Array.from(el().querySelectorAll<HTMLInputElement>('.festivos__casillas input'));
      expect(casillas.length).toBe(1);
      expect(casillas[0].checked).toBeTrue();
    });
  });

  describe('edición', () => {
    it('la fila abre «Editar festivo» con sus datos; el impacto excluye el propio festivo; PUT', fakeAsync(() => {
      arrancar();
      el().querySelector<HTMLButtonElement>('[data-id="2"]')!.click();
      fixture.detectChanges();
      expect(texto(panel()!.querySelector('.pn-panel__titulo'))).toBe('Editar festivo');
      expect(boton('De una fecha a otra')!.getAttribute('aria-checked')).toBe('true');
      expect(el().querySelector<HTMLInputElement>('#fv-desde')!.value).toBe('2026-12-23');
      expect(el().querySelector<HTMLInputElement>('#fv-nombre')!.value).toBe('Vacaciones de Navidad');
      tick(400);
      const impacto = http.expectOne(`${url}/impacto`);
      expect(impacto.request.body.excluirFestivoId).toBe(2);
      impacto.flush({ clases: 46, conLista: 0, conListaDetalle: [] });
      boton('Guardar cambios')!.click();
      const put = http.expectOne(`${url}/2`);
      expect(put.request.method).toBe('PUT');
      expect(put.request.body).toEqual({
        tipo: 'RANGO', sedeIds: [2, 3, 4], nombre: 'Vacaciones de Navidad', fechaDesde: '2026-12-23', fechaHasta: '2027-01-07',
      });
      put.flush(NAVIDAD);
      fixture.detectChanges();
      expect(avisos.exito).toHaveBeenCalledWith('Cambios guardados.');
      lista().flush([NAVIDAD]);
    }));
  });

  describe('quitar', () => {
    it('confirma en rojo con las clases que pedirán lista; DELETE y aviso', async () => {
      arrancar();
      el().querySelectorAll<HTMLButtonElement>('.festivos__quitar')[1].click();
      const impacto = http.expectOne(`${url}/impacto`);
      expect(impacto.request.body.excluirFestivoId).toBe(2);
      confirmar.and.callFake(async (o: { ejecutar: () => { subscribe: (x: object) => void } }) => {
        o.ejecutar().subscribe({});
        return { confirmado: true, valor: null };
      });
      impacto.flush({ clases: 50, conLista: 4, conListaDetalle: [] });
      await fixture.whenStable();
      const opciones = confirmar.calls.mostRecent().args[0];
      expect(opciones.titulo).toBe('¿Quitar «Vacaciones de Navidad»?');
      expect(opciones.mensaje).toEqual([
        'Del 23 de diciembre al 7 de enero volverá a haber clase en ', { fuerte: 'todas las sedes' },
        ': esas 46 clases pedirán lista otra vez. No se borra ninguna lista.',
      ]);
      expect(opciones.destructivo).toBeTrue();
      expect(opciones.accion).toBe('Quitar festivo');
      const borrar = http.expectOne(`${url}/2`);
      expect(borrar.request.method).toBe('DELETE');
      borrar.flush(null, { status: 204, statusText: 'No Content' });
      await fixture.whenStable();
      expect(avisos.exito).toHaveBeenCalledWith('Festivo quitado.');
      lista().flush([festivo()]);
    });

    it('cancelar no borra nada', async () => {
      arrancar();
      el().querySelectorAll<HTMLButtonElement>('.festivos__quitar')[0].click();
      http.expectOne(`${url}/impacto`).flush({ clases: 4, conLista: 0, conListaDetalle: [] });
      await fixture.whenStable();
      expect(confirmar).toHaveBeenCalled();
      http.expectNone(`${url}/1`);
    });
  });

  describe('responsable de sede', () => {
    const RESPONSABLE = permisos({ roles: ['RESPONSABLE_SEDE'], sedes: [ESTEPA, HERRERA], capacidades: ['PANEL_SEDE'] });

    it('los compartidos: «Lo gestiona la escuela», sin editar ni quitar; filtro «Mis sedes»', () => {
      arrancar([festivo(), { ...NAVIDAD, editable: false }], RESPONSABLE);
      const [propio, compartido] = filas();
      expect(propio.querySelector('button.festivos__fila')).not.toBeNull();
      expect(compartido.querySelector('button')).toBeNull();
      expect(texto(compartido.querySelector('.festivos__solo-lectura'))).toBe('Lo gestiona la escuela');
      const select = el().querySelector<HTMLSelectElement>('#fv-filtro-sede')!;
      expect(Array.from(select.options).map((o) => texto(o))).toEqual(['Mis sedes', 'Estepa', 'Herrera']);
    });

    it('casillas solo de sus sedes y sin «Todas las sedes»', () => {
      arrancar([], RESPONSABLE);
      boton('Nuevo festivo')!.click();
      fixture.detectChanges();
      expect(Array.from(el().querySelectorAll('.festivos__casillas label')).map(texto)).toEqual(['Estepa', 'Herrera']);
    });
  });
});
