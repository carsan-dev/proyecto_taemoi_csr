import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { BehaviorSubject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ClaseAsistencia, ClasesDelGrupo } from '../../interfaces/asistencia';
import { AlumnoDeGrupo } from '../../interfaces/profesor';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { CONSULTA_HOJA } from '../capas/capa-modal';
import { EstilosPanelService } from '../estilos-panel.service';
import { FichaProfesorPanelComponent } from './ficha-profesor-panel.component';
import { GrupoPanelComponent } from './grupo.component';

describe('GrupoPanelComponent', () => {
  let fixture: ComponentFixture<GrupoPanelComponent>;
  let http: HttpTestingController;
  let parametros: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let consulta: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  const api = `${environment.apiUrl}/profesor`;
  const elemento = (): HTMLElement => fixture.nativeElement;
  const alumnos: AlumnoDeGrupo[] = [
    { id: 21, nombre: 'Lucía', apellidos: 'Romero', deportes: [
      { deporte: 'TAEKWONDO', deporteNombre: 'Taekwondo', grado: { codigo: 'AMARILLO', nombre: 'Amarillo' }, fechaGrado: null, apto: true, delGrupo: true },
      { deporte: 'KICKBOXING', deporteNombre: 'Kickboxing', grado: null, fechaGrado: null, apto: true, delGrupo: false },
    ] },
    { id: 22, nombre: 'Hugo', apellidos: 'Martín', deportes: [
      { deporte: 'TAEKWONDO', deporteNombre: 'Taekwondo', grado: null, fechaGrado: null, apto: false, delGrupo: true },
    ] },
  ];

  function cargar(lista: AlumnoDeGrupo[] = alumnos, id = 7): void {
    http.expectOne(`${api}/grupos`).flush([{ id, nombre: 'Infantil', sede: { id: 1, nombre: 'Umbrete' }, rol: 'TITULAR', alumnosActivos: lista.length, disciplina: null, turnos: [] }]);
    http.expectOne(`${api}/grupos/${id}/alumnos`).flush(lista);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    parametros = new BehaviorSubject(convertToParamMap({ grupoId: '7' }));
    consulta = new BehaviorSubject(convertToParamMap({}));
    await TestBed.configureTestingModule({
      imports: [GrupoPanelComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: parametros, queryParamMap: consulta } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
        // La ficha embebida inyecta la sesión: con la real, su constructor deja abierta una petición a auth-status.
        { provide: AuthenticationService, useValue: { getRolesActuales: () => ['ROLE_PROFESOR'] } }],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(GrupoPanelComponent);
    fixture.detectChanges();
  });

  // La tarjeta de hoy y la constancia piden sus datos aparte: lo que un test no contesta se cierra vacío.
  afterEach(() => {
    const abiertas = (coincide: (url: string) => boolean) => http.match(r => coincide(r.url)).filter(r => !r.cancelled);
    abiertas(u => u.endsWith('/clases')).forEach(r => r.flush({ hoy: [], recientes: [], proxima: null }));
    abiertas(u => u.endsWith('/asistencia/resumen')).forEach(r => r.flush([]));
    abiertas(u => /\/alumnos\/\d+\/asistencia$/.test(u)).forEach(r => r.flush({ presentes: 0, clases: 0, dias: [] }));
    http.verify();
    fixture.destroy();
  });

  it('muestra el esqueleto y carga título, sede y papel', () => {
    expect(elemento().querySelector('pn-esqueleto')).not.toBeNull();
    expect(fixture.componentInstance.cargando).toBeTrue();
    cargar();
    expect(elemento().querySelector('h1')?.textContent).toContain('Infantil');
    expect(elemento().textContent).toContain('Umbrete');
    expect(elemento().textContent).toContain('Titular');
  });

  it('migas «Mis grupos /» con enlace en vez de «Volver»', () => {
    cargar();
    const miga = elemento().querySelector('nav.grupo-profesor__migas a')!;
    expect(miga.textContent?.trim()).toBe('Mis grupos');
    expect(miga.getAttribute('href')).toBe('/mis-grupos');
    expect(elemento().textContent).not.toContain('Volver');
  });

  it('la fila lleva el nombre como enlace, el grado formateado con su color, el chip Apto y sin chevron', () => {
    cargar([{ id: 21, nombre: 'Lucía', apellidos: 'Romero', deportes: [
      { deporte: 'TAEKWONDO', deporteNombre: 'Taekwondo', grado: { codigo: 'BLANCO_AMARILLO', nombre: 'BLANCO-AMARILLO' }, fechaGrado: null, apto: true, delGrupo: true },
    ] }]);
    const fila = elemento().querySelector('.grupo-profesor__fila')!;
    expect(fila.querySelector('a.grupo-profesor__alumno')?.textContent?.trim()).toBe('Lucía Romero');
    expect(fila.textContent).not.toContain('BLANCO-AMARILLO');
    expect(fila.querySelector<HTMLElement>('.grupo-profesor__cinto')?.style.background).not.toBe('');
    expect(fila.querySelector('.grupo-profesor__apto')?.textContent?.trim()).toBe('Apto');
    expect(fila.querySelector('svg')).toBeNull();
    expect(fila.querySelector('.grupo-profesor__avatar')?.textContent?.trim()).toBe('LR');
  });

  it('muestra error y permite reintentar las mismas lecturas', () => {
    http.expectOne(`${api}/grupos`).flush([]);
    http.expectOne(`${api}/grupos/7/alumnos`).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(elemento().textContent).toContain('No se ha podido cargar el grupo.');
    elemento().querySelector<HTMLButtonElement>('pn-bloque-error button')!.click();
    cargar();
    expect(elemento().querySelector('pn-bloque-error')).toBeNull();
  });

  it('muestra el vacío sin alumnos activos', () => {
    cargar([]);
    expect(elemento().textContent).toContain('Este grupo no tiene alumnos activos.');
    expect(elemento().querySelector('pn-mascota')).toBeNull();
  });

  it('cuenta alumnos aptos una sola vez aunque tengan dos deportes aptos y filtra', () => {
    cargar();
    const aptos = elemento().querySelector<HTMLButtonElement>('[data-filtro="aptos"]')!;
    expect(aptos.textContent?.replace(/\s+/g, ' ').trim()).toBe('Aptos 1');
    aptos.click();
    fixture.detectChanges();
    expect(elemento().querySelectorAll('.grupo-profesor__alumno').length).toBe(1);
    expect(aptos.getAttribute('aria-checked')).toBe('true');
    expect(elemento().textContent).toContain('Lucía Romero');
    expect(elemento().textContent).not.toContain('Hugo Martín');
    expect(elemento().textContent).toContain('Taekwondo · Amarillo');
    expect(elemento().textContent).toContain('Kickboxing · Sin grado');
  });

  it('permite cambiar de filtro con el teclado y devuelve el foco al segmento', () => {
    cargar();
    elemento().querySelector('[role="radiogroup"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(fixture.componentInstance.filtro).toBe('aptos');
    expect(elemento().querySelector('[data-filtro="aptos"]')?.getAttribute('tabindex')).toBe('0');
  });

  it('muestra el vacío propio del filtro aptos', () => {
    cargar([alumnos[1]]);
    elemento().querySelector<HTMLButtonElement>('[data-filtro="aptos"]')!.click();
    fixture.detectChanges();
    expect(elemento().textContent).toContain('Ningún alumno de este grupo está apto para examen.');
  });

  it('en pantalla estrecha (<1024) enlaza al alumno con el grupo y navega a su pantalla propia', () => {
    cargar();
    spyOn(fixture.componentInstance, 'esEscritorio').and.returnValue(false);
    const navegar = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const enlace = elemento().querySelector<HTMLAnchorElement>('.grupo-profesor__alumno')!;
    expect(enlace.getAttribute('href')).toBe('/mis-grupos/alumnos/21?grupo=7');
    enlace.click();
    expect(navegar).toHaveBeenCalledOnceWith(['/mis-grupos/alumnos', 21], { queryParams: { grupo: 7 } });
    expect(elemento().querySelector('app-navegacion-profesor')).toBeNull();
  });

  describe('ficha del alumno en escritorio (≥1024)', () => {
    const ficha = { id: 21, nombre: 'Lucía', apellidos: 'Romero', edad: 9, grupos: [], contacto: [], deportes: [] };

    // El panel es hoja inferior bajo 768 px: el test no puede depender del tamaño de la ventana de Chrome Headless.
    beforeEach(() => {
      spyOn(window, 'matchMedia').and.callFake((consulta: string) => ({
        matches: consulta !== CONSULTA_HOJA,
        media: consulta,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
      }) as unknown as MediaQueryList);
    });

    it('pulsar un alumno lo refleja en la URL (?alumno=) sin abandonar el grupo', () => {
      cargar();
      spyOn(fixture.componentInstance, 'esEscritorio').and.returnValue(true);
      const navegar = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
      elemento().querySelector<HTMLAnchorElement>('.grupo-profesor__alumno')!.click();
      expect(navegar).toHaveBeenCalledOnceWith([], jasmine.objectContaining({ queryParams: { alumno: 21 }, queryParamsHandling: 'merge' }));
    });

    it('con ?alumno= abre el panel lateral de 420 px con su ficha y la lista sigue en pantalla', () => {
      cargar();
      spyOn(fixture.componentInstance, 'esEscritorio').and.returnValue(true);
      consulta.next(convertToParamMap({ alumno: '21' }));
      fixture.detectChanges();
      const panel = elemento().querySelector<HTMLElement>('pn-panel-lateral');
      expect(panel).not.toBeNull();
      expect(panel!.querySelector<HTMLElement>('dialog')!.style.width).toBe('420px');
      expect(panel!.querySelector('.pn-panel__titulo')?.textContent).toContain('Lucía Romero');
      http.expectOne(`${api}/alumnos/21`).flush(ficha);
      fixture.detectChanges();
      expect(panel!.querySelector('pn-ficha-profesor')).not.toBeNull();
      expect(elemento().querySelectorAll('.grupo-profesor__alumno').length).toBe(2);
    });

    it('cambiar «Apto» en la ficha actualiza el chip de la fila y el contador «Aptos»', () => {
      cargar();
      spyOn(fixture.componentInstance, 'esEscritorio').and.returnValue(true);
      consulta.next(convertToParamMap({ alumno: '22' }));
      fixture.detectChanges();
      http.expectOne(`${api}/alumnos/22`).flush({ ...ficha, id: 22 });
      fixture.detectChanges();
      const aptos = () => elemento().querySelector('[data-filtro="aptos"]')!.textContent!.replace(/\s+/g, ' ').trim();
      const chips = () => elemento().querySelectorAll('.grupo-profesor__apto').length;
      expect(aptos()).toBe('Aptos 1');
      expect(chips()).toBe(1);
      const anterior = fixture.componentInstance.alumnos;
      fixture.debugElement.query(By.directive(FichaProfesorPanelComponent)).componentInstance
        .aptoCambiado.emit({ alumnoId: 22, deporte: 'TAEKWONDO', apto: true });
      fixture.detectChanges();
      expect(aptos()).toBe('Aptos 2');
      expect(chips()).toBe(2);
      expect(fixture.componentInstance.alumnos).not.toBe(anterior);
      expect(anterior[1].deportes[0].apto).toBeFalse();
    });

    it('el panel no es modal: sin velo, la lista sigue activa, la fila abierta queda marcada y el contenido se estrecha', () => {
      cargar();
      spyOn(fixture.componentInstance, 'esEscritorio').and.returnValue(true);
      consulta.next(convertToParamMap({ alumno: '21' }));
      fixture.detectChanges();
      http.expectOne(`${api}/alumnos/21`).flush(ficha);
      fixture.detectChanges();
      const dialogo = elemento().querySelector<HTMLDialogElement>('pn-panel-lateral dialog')!;
      expect(dialogo.open).toBeTrue();
      expect(dialogo.matches(':modal')).toBeFalse();
      expect(dialogo.classList).toContain('pn-panel--sin-velo');
      const filas = elemento().querySelectorAll('.grupo-profesor__fila');
      expect(filas[0].classList).toContain('grupo-profesor__fila--abierta');
      expect(filas[1].classList).not.toContain('grupo-profesor__fila--abierta');
      expect(elemento().querySelector('.grupo-profesor--con-panel')).not.toBeNull();
      // La lista no queda inerte: se puede enfocar y pulsar otro alumno.
      expect(elemento().querySelector('.grupo-profesor__lista')!.closest('[inert]')).toBeNull();
    });

    it('cambiar de alumno con el panel abierto actualiza la ficha y el título sin cerrar el panel', () => {
      cargar();
      spyOn(fixture.componentInstance, 'esEscritorio').and.returnValue(true);
      consulta.next(convertToParamMap({ alumno: '21' }));
      fixture.detectChanges();
      http.expectOne(`${api}/alumnos/21`).flush(ficha);
      const panel = elemento().querySelector('pn-panel-lateral');
      consulta.next(convertToParamMap({ alumno: '22' }));
      fixture.detectChanges();
      expect(elemento().querySelector('pn-panel-lateral')).toBe(panel);
      expect(panel!.querySelector('.pn-panel__titulo')?.textContent).toContain('Hugo Martín');
      http.expectOne(`${api}/alumnos/22`).flush({ ...ficha, id: 22, nombre: 'Hugo', apellidos: 'Martín' });
    });

    it('Esc cierra el panel aunque el foco esté en la lista, y cerrar devuelve el foco a la fila abierta', fakeAsync(() => {
      cargar();
      spyOn(fixture.componentInstance, 'esEscritorio').and.returnValue(true);
      consulta.next(convertToParamMap({ alumno: '22' }));
      fixture.detectChanges();
      http.expectOne(`${api}/alumnos/22`).flush({ ...ficha, id: 22 });
      const navegar = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
      elemento().querySelector<HTMLElement>('.grupo-profesor__lista')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      expect(navegar).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { alumno: null } }));
      consulta.next(convertToParamMap({}));
      fixture.detectChanges();
      tick();
      expect(elemento().querySelector('pn-panel-lateral')).toBeNull();
      expect(document.activeElement).toBe(elemento().querySelector('[data-alumno="22"]'));
    }));

    it('cerrar quita ?alumno= de la URL', () => {
      cargar();
      spyOn(fixture.componentInstance, 'esEscritorio').and.returnValue(true);
      consulta.next(convertToParamMap({ alumno: '21' }));
      fixture.detectChanges();
      http.expectOne(`${api}/alumnos/21`).flush(ficha);
      const navegar = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
      elemento().querySelector<HTMLButtonElement>('.pn-panel__cerrar')!.click();
      expect(navegar).toHaveBeenCalledOnceWith([], jasmine.objectContaining({ queryParams: { alumno: null }, queryParamsHandling: 'merge' }));
    });

    it('bajo 1024 px ?alumno= no abre el panel', () => {
      cargar();
      spyOn(fixture.componentInstance, 'esEscritorio').and.returnValue(false);
      consulta.next(convertToParamMap({ alumno: '21' }));
      fixture.detectChanges();
      expect(elemento().querySelector('pn-panel-lateral')).toBeNull();
    });

    it('un ?alumno= que no es de este grupo se ignora', () => {
      cargar();
      spyOn(fixture.componentInstance, 'esEscritorio').and.returnValue(true);
      consulta.next(convertToParamMap({ alumno: '999' }));
      fixture.detectChanges();
      expect(elemento().querySelector('pn-panel-lateral')).toBeNull();
    });
  });

  it('recarga al cambiar el grupo sin conservar alumnos ni filtro anteriores', () => {
    cargar();
    fixture.componentInstance.filtro = 'aptos';
    parametros.next(convertToParamMap({ grupoId: '8' }));
    expect(fixture.componentInstance.alumnos).toEqual([]);
    cargar([], 8);
    expect(fixture.componentInstance.filtro).toBe('todos');
  });

  it('pide el PDF del mes elegido y conserva documento y foco para el visor', fakeAsync(() => {
    cargar();
    fixture.componentInstance.mes = fixture.componentInstance.meses[1].valor;
    const mes = fixture.componentInstance.meses[1];
    const boton = elemento().querySelector<HTMLButtonElement>('.grupo-profesor__boton')!;
    boton.click();
    const peticion = http.expectOne(r => r.url === `${api}/grupos/7/asistencia`);
    expect(peticion.request.params.get('year')).toBe(String(mes.year));
    expect(peticion.request.params.get('month')).toBe(String(mes.month));
    peticion.flush(new Blob(['pdf'], { type: 'application/pdf' }), { headers: { 'Content-Disposition': 'attachment; filename="lista.pdf"' } });
    tick();
    expect(fixture.componentInstance.documentos[0].nombreArchivo).toBe('lista.pdf');
    expect(fixture.componentInstance.focoRetorno).toBe(boton);
    fixture.componentInstance.cerrarVisor();
    expect(fixture.componentInstance.documentos).toEqual([]);
  }));

  it('presenta un error recuperable si falla la hoja de asistencia', fakeAsync(() => {
    cargar();
    elemento().querySelector<HTMLButtonElement>('.grupo-profesor__boton')!.click();
    http.expectOne(r => r.url === `${api}/grupos/7/asistencia`).flush(null, { status: 500, statusText: 'Error' });
    tick();
    fixture.detectChanges();
    expect(elemento().textContent).toContain('No se ha podido descargar la hoja de asistencia.');
    expect(fixture.componentInstance.descargando).toBeFalse();
  }));

  describe('tarjeta de hoy', () => {
    const clase = (cambios: Partial<ClaseAsistencia> = {}): ClaseAsistencia => ({
      turnoId: 9, fecha: '2026-10-28', horaInicio: '18:00', horaFin: '19:00', pasada: false, presentes: null, total: null, alumnos: 18, ...cambios,
    });
    const tarjeta = () => elemento().querySelector<HTMLElement>('section[aria-label^="Clase"]')!;

    function conClases(clases: Partial<ClasesDelGrupo>): void {
      cargar();
      http.expectOne(`${api}/grupos/7/clases`).flush({ hoy: [], recientes: [], proxima: null, ...clases });
      fixture.detectChanges();
    }

    it('esqueleto mientras carga, sin tumbar el resto', () => {
      cargar();
      expect(elemento().querySelector('pn-esqueleto')).not.toBeNull();
      expect(elemento().textContent).toContain('Lucía Romero');
    });

    it('sin pasar: título con el día, «Sin pasar lista» y botón principal con turno y fecha', () => {
      conClases({ hoy: [clase()] });
      expect(tarjeta().textContent).toContain('Hoy, miércoles · 18:00–19:00');
      expect(tarjeta().textContent).toContain('Sin pasar lista');
      const enlace = tarjeta().querySelector<HTMLAnchorElement>('a')!;
      expect(enlace.textContent?.trim()).toBe('Pasar lista');
      expect(enlace.getAttribute('href')).toBe('/mis-grupos/7/lista?turno=9&fecha=2026-10-28');
    });

    it('con lista: «12 de 18 presentes» y «Corregir lista»; pasada no significa lista', () => {
      conClases({ hoy: [clase({ presentes: 12, total: 18, pasada: true })] });
      expect(tarjeta().textContent).toContain('12 de 18 presentes');
      expect(tarjeta().querySelector('a')?.textContent?.trim()).toBe('Corregir lista');
    });

    it('una clase pasada sin lista sigue siendo «Pasar lista»', () => {
      conClases({ hoy: [clase({ pasada: true })] });
      expect(tarjeta().querySelector('a')?.textContent?.trim()).toBe('Pasar lista');
    });

    it('turno sin alumnos: lo dice y no hay botón', () => {
      conClases({ hoy: [clase({ alumnos: 0 })] });
      expect(tarjeta().textContent).toContain('Este turno no tiene alumnos asignados.');
      expect(tarjeta().querySelector('a, button')).toBeNull();
    });

    it('turno vaciado con lista guardada: muestra «12 de 18 presentes»', () => {
      conClases({ hoy: [clase({ alumnos: 0, presentes: 12, total: 18 })] });
      expect(tarjeta().textContent).toContain('12 de 18 presentes');
      expect(tarjeta().textContent).not.toContain('no tiene alumnos');
    });

    it('turno vaciado con lista guardada, con dos clases hoy: también la cuenta', () => {
      conClases({ hoy: [clase({ alumnos: 0, presentes: 5, total: 8 }), clase({ turnoId: 10, horaInicio: '19:00', horaFin: '20:00' })] });
      const filas = elemento().querySelectorAll('.grupo-profesor__hoy-fila');
      expect(filas[0].textContent).toContain('5 de 8 presentes');
    });

    it('dos clases hoy: una fila por clase con «Corregir» y «Pasar lista»', () => {
      conClases({ hoy: [clase({ presentes: 12, total: 18 }), clase({ turnoId: 10, horaInicio: '19:00', horaFin: '20:00' })] });
      const filas = elemento().querySelectorAll('.grupo-profesor__hoy-fila');
      expect(filas.length).toBe(2);
      expect(filas[0].textContent).toContain('18:00–19:00');
      expect(filas[0].textContent).toContain('12 de 18 presentes');
      expect(filas[0].querySelector('a')?.textContent?.trim()).toBe('Corregir');
      expect(filas[1].querySelector('a')?.textContent?.trim()).toBe('Pasar lista');
      expect(filas[1].querySelector('a')?.getAttribute('href')).toBe('/mis-grupos/7/lista?turno=10&fecha=2026-10-28');
    });

    it('sin clase hoy: próxima y «Pasar otra clase» si hay recientes', () => {
      conClases({ recientes: [clase({ fecha: '2026-10-26' })], proxima: { fecha: '2026-11-02', horaInicio: '18:00' } });
      expect(tarjeta().textContent).toContain('Hoy no hay clase');
      expect(tarjeta().textContent).toContain('Próxima: lunes 2 de noviembre, 18:00');
      const enlace = tarjeta().querySelector<HTMLAnchorElement>('a')!;
      expect(enlace.textContent?.trim()).toBe('Pasar otra clase');
      expect(enlace.getAttribute('href')).toBe('/mis-grupos/7/lista');
    });

    it('sin clase hoy, sin próxima y sin recientes: ni línea ni botón', () => {
      conClases({});
      expect(tarjeta().textContent).toContain('Hoy no hay clase');
      expect(tarjeta().textContent).not.toContain('Próxima');
      expect(tarjeta().querySelector('a, button')).toBeNull();
    });

    it('si falla, error compacto con reintentar y el resto sigue en pie', () => {
      cargar();
      http.expectOne(`${api}/grupos/7/clases`).flush({}, { status: 500, statusText: 'Error' });
      fixture.detectChanges();
      expect(tarjeta().textContent).toContain('No se ha podido cargar la clase de hoy.');
      expect(elemento().textContent).toContain('Lucía Romero');
      tarjeta().querySelector('button')!.click();
      http.expectOne(`${api}/grupos/7/clases`).flush({ hoy: [clase()], recientes: [], proxima: null });
      fixture.detectChanges();
      expect(tarjeta().textContent).toContain('Sin pasar lista');
    });
  });

  describe('constancia del mes', () => {
    it('el botón del PDF se llama «Hoja en PDF»', () => {
      cargar();
      expect(elemento().querySelector('.grupo-profesor__asistencia .grupo-profesor__boton')?.textContent?.trim()).toBe('Hoja en PDF');
    });

    it('«{presentes} de {clases}» por fila, «—» sin datos e ignora a quien ya no está', () => {
      cargar();
      const peticion = http.expectOne(r => r.url === `${api}/grupos/7/asistencia/resumen`);
      const mes = fixture.componentInstance.meses[0];
      expect(peticion.request.params.get('year')).toBe(String(mes.year));
      expect(peticion.request.params.get('month')).toBe(String(mes.month));
      peticion.flush([{ alumnoId: 21, presentes: 3, clases: 4 }, { alumnoId: 999, presentes: 1, clases: 1 }]);
      fixture.detectChanges();
      const filas = elemento().querySelectorAll('.grupo-profesor__fila');
      expect(filas.length).toBe(2);
      expect(filas[0].querySelector('.grupo-profesor__constancia')?.textContent?.trim()).toBe('3 de 4');
      expect(filas[1].querySelector('.grupo-profesor__constancia')?.textContent).toContain('—');
      expect(filas[1].querySelector('.grupo-profesor__constancia .grupo-profesor__oculto')?.textContent).toBe('sin lista este mes');
      expect(elemento().querySelector('.grupo-profesor__cabecera-lista')?.textContent).toContain('Asistencia en ');
    });

    it('al cambiar de mes vuelve a pedir el resumen', () => {
      cargar();
      http.expectOne(r => r.url === `${api}/grupos/7/asistencia/resumen`).flush([]);
      const select = elemento().querySelector<HTMLSelectElement>('select')!;
      const mes = fixture.componentInstance.meses[1];
      select.value = mes.valor;
      select.dispatchEvent(new Event('change'));
      const peticion = http.expectOne(r => r.url === `${api}/grupos/7/asistencia/resumen`);
      expect(peticion.request.params.get('month')).toBe(String(mes.month));
      peticion.flush([]);
    });

    it('si falla el resumen, las filas quedan sin cifra y sin error a pantalla completa', () => {
      cargar();
      http.expectOne(r => r.url === `${api}/grupos/7/asistencia/resumen`).flush({}, { status: 500, statusText: 'Error' });
      fixture.detectChanges();
      expect(elemento().querySelectorAll('.grupo-profesor__fila').length).toBe(2);
      expect(elemento().querySelector('.grupo-profesor__constancia')).toBeNull();
      expect(elemento().querySelector('.grupo-profesor__cabecera-lista')).toBeNull();
      expect(elemento().querySelector('pn-bloque-error')).toBeNull();
    });
  });
});
