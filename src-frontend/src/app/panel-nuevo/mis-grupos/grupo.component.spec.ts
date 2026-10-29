import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AlumnoDeGrupo } from '../../interfaces/profesor';
import { EstilosPanelService } from '../estilos-panel.service';
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
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } }],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(GrupoPanelComponent);
    fixture.detectChanges();
  });

  afterEach(() => { http.verify(); fixture.destroy(); });

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
});
