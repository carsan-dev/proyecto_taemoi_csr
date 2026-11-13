import { HttpErrorResponse } from '@angular/common/http';
import { Location } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { EscuelaLogo } from '../../../interfaces/escuela-logo';
import { ConfirmacionService } from '../../../panel-nuevo/dialogo/confirmacion.service';
import { ImportacionAlumnosService, Incidencia, InformeImportacion } from '../../../servicios/plataforma/importacion-alumnos.service';
import { LogoEscuelaService } from '../../../servicios/plataforma/logo-escuela.service';
import { PlataformaImportarAlumnosComponent, filasDistintas, formatoTamano } from './plataforma-importar-alumnos.component';

const escuela = (id: number, nombre: string): EscuelaLogo => ({ id, nombre, tieneLogo: false, marcaHeredada: false, colorPrimario: null });

const inc = (fila: number, numeroFederacion: number | null, columna: string | null, motivo: string): Incidencia => ({ fila, numeroFederacion, columna, motivo });

const informe = (parcial: Partial<InformeImportacion> = {}): InformeImportacion => ({
  filas: 260,
  listas: 260,
  creados: 0,
  yaCargados: 0,
  errores: [],
  avisos: [],
  yaCargadas: [],
  ...parcial,
});

const fichero = (nombre = 'hoja-sierra.xlsx', bytes = 35 * 1024): File => new File([new Uint8Array(bytes)], nombre);

describe('PlataformaImportarAlumnosComponent', () => {
  let fixture: ComponentFixture<PlataformaImportarAlumnosComponent>;
  let comp: PlataformaImportarAlumnosComponent;
  let servicio: jasmine.SpyObj<ImportacionAlumnosService>;
  let escuelas: jasmine.SpyObj<LogoEscuelaService>;
  let confirmacion: jasmine.SpyObj<ConfirmacionService>;
  let location: jasmine.SpyObj<Location>;
  let router: jasmine.SpyObj<Router>;

  const el = (): HTMLElement => fixture.nativeElement;
  const texto = (): string => el().textContent!.replace(/\s+/g, ' ');
  const boton = (etiqueta: string): HTMLButtonElement | undefined =>
    Array.from(el().querySelectorAll('button')).find((b) => b.textContent!.trim().startsWith(etiqueta)) as HTMLButtonElement | undefined;
  const resumen = (): string => el().querySelector('.barra-resumen')!.textContent!.replace(/\s+/g, ' ').trim();

  function elegirFichero(f: File): void {
    const entrada = { files: [f], value: 'x' } as unknown as HTMLInputElement;
    comp.alElegirFichero({ target: entrada } as unknown as Event);
    fixture.detectChanges();
  }

  function preparar(f = fichero(), id = 1): void {
    comp.alElegirEscuela(id);
    elegirFichero(f);
  }

  async function asentar(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function ensayar(resp: InformeImportacion): Promise<void> {
    servicio.ensayar.and.returnValue(of(resp));
    boton('Ensayar')!.click();
    await asentar();
  }

  function http(status: number, error?: unknown): HttpErrorResponse {
    return new HttpErrorResponse({ status, error });
  }

  beforeEach(async () => {
    servicio = jasmine.createSpyObj<ImportacionAlumnosService>('ImportacionAlumnosService', ['ensayar', 'cargar', 'hojaConErrores']);
    escuelas = jasmine.createSpyObj<LogoEscuelaService>('LogoEscuelaService', ['escuelas']);
    escuelas.escuelas.and.returnValue(of([escuela(1, 'Taekwondo Sierra Sur'), escuela(2, 'Moiskimdo')]));
    confirmacion = jasmine.createSpyObj<ConfirmacionService>('ConfirmacionService', ['confirmar']);
    confirmacion.confirmar.and.resolveTo({ confirmado: true, valor: null });
    location = jasmine.createSpyObj<Location>('Location', ['back']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);

    await TestBed.configureTestingModule({
      imports: [PlataformaImportarAlumnosComponent],
      providers: [
        { provide: ImportacionAlumnosService, useValue: servicio },
        { provide: LogoEscuelaService, useValue: escuelas },
        { provide: ConfirmacionService, useValue: confirmacion },
        { provide: Location, useValue: location },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PlataformaImportarAlumnosComponent);
    comp = fixture.componentInstance;
    fixture.detectChanges();
  });

  describe('helpers', () => {
    it('filasDistintas cuenta filas, no incidencias', () => {
      expect(filasDistintas([inc(3, null, 'a', 'x'), inc(3, null, 'b', 'y'), inc(5, null, null, 'z')])).toBe(2);
    });

    it('formatoTamano', () => {
      expect(formatoTamano(35 * 1024)).toBe('35 KB');
      expect(formatoTamano(10)).toBe('1 KB');
      expect(formatoTamano(1.5 * 1024 * 1024)).toBe('1,5 MB');
    });
  });

  describe('1. inicio', () => {
    it('cabecera, bloques, zona de subida y barra con los textos del boceto', () => {
      expect(el().querySelector('h1')!.textContent).toBe('Importar alumnos');
      expect(texto()).toContain('Carga los alumnos de una escuela desde su hoja de Excel. Primero se ensaya; no se guarda nada hasta que pulses «Cargar».');
      expect(texto()).toContain('Escuela obligatorio');
      expect(texto()).toContain('Elige una escuela');
      expect(texto()).toContain('Un Excel con una fila por alumno.');
      expect(boton('Elegir Excel')).toBeDefined();
      expect(texto()).toContain('Un fichero .xlsx, hasta 5 MB.');
      expect(resumen()).toBe('Elige la escuela y la hoja.');
      expect(boton('Cancelar')).toBeDefined();
      expect(boton('Ensayar')).toBeDefined();
      expect(boton('Cargar')).toBeUndefined();
    });

    it('el selector lista las escuelas de plataforma y elegir una guarda su id', () => {
      const select = el().querySelector<HTMLSelectElement>('#ia-escuela')!;
      const nombres = Array.from(select.options).map((o) => o.textContent!.trim());
      expect(nombres).toEqual(['Elige una escuela', 'Taekwondo Sierra Sur', 'Moiskimdo']);
      select.selectedIndex = 2;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(comp.escuelaId).toBe(2);
    });

    it('error al cargar las escuelas: aviso con Reintentar', () => {
      escuelas.escuelas.and.returnValue(throwError(() => http(500)));
      comp.cargarEscuelas();
      fixture.detectChanges();
      expect(texto()).toContain('No se pudo cargar la lista de escuelas.');
      escuelas.escuelas.and.returnValue(of([escuela(1, 'A')]));
      boton('Reintentar')!.click();
      fixture.detectChanges();
      expect(comp.errorEscuelas).toBeFalse();
      expect(comp.escuelas.length).toBe(1);
    });

    it('Ensayar sin escuela ni hoja no llama al servidor y avisa en cada campo', () => {
      boton('Ensayar')!.click();
      fixture.detectChanges();
      expect(servicio.ensayar).not.toHaveBeenCalled();
      expect(el().querySelector('#ia-escuela-error')!.textContent).toContain('Elige la escuela.');
      expect(el().querySelector('#ia-fichero-error')!.textContent).toContain('Elige la hoja de Excel.');
      expect(el().querySelector('#ia-fichero-error')!.getAttribute('role')).toBe('alert');
    });

    it('validación del fichero en cliente: extensión y 5 MB, con el error de campo', () => {
      elegirFichero(fichero('hoja.xls'));
      expect(comp.archivo).toBeNull();
      expect(el().querySelector('[role="alert"]')!.textContent).toContain('Elige un fichero .xlsx.');
      elegirFichero(fichero('grande.xlsx', 5 * 1024 * 1024 + 1));
      expect(comp.archivo).toBeNull();
      expect(el().querySelector('[role="alert"]')!.textContent).toContain('El fichero pesa más de 5 MB.');
      expect(texto()).toContain('Un fichero .xlsx, hasta 5 MB.');
      elegirFichero(fichero('justo.XLSX', 5 * 1024 * 1024));
      expect(comp.archivo!.name).toBe('justo.XLSX');
      expect(el().querySelector('#ia-fichero-error')).toBeNull();
    });

    it('con fichero elegido, la zona deja paso al fichero con Sustituir', () => {
      preparar();
      expect(el().querySelector('.zona')).toBeNull();
      expect(texto()).toContain('hoja-sierra.xlsx');
      expect(texto()).toContain('35 KB');
      expect(boton('Sustituir')).toBeDefined();
    });
  });

  describe('2. ensayando', () => {
    it('muestra el fichero, el giro, role status y Cancelar deshabilitado; sin doble envío', () => {
      const pendiente = new Subject<InformeImportacion>();
      servicio.ensayar.and.returnValue(pendiente);
      preparar();
      boton('Ensayar')!.click();
      fixture.detectChanges();
      expect(texto()).toContain('35 KB · Taekwondo Sierra Sur');
      const estado = el().querySelector('.barra-resumen')!;
      expect(estado.getAttribute('role')).toBe('status');
      expect(estado.textContent).toContain('Ensayando la hoja…');
      const principal = boton('Ensayando…')!;
      expect(principal.getAttribute('aria-busy')).toBe('true');
      expect(principal.querySelector('.girando')).not.toBeNull();
      expect(boton('Cancelar')!.disabled).toBeTrue();
      principal.click();
      comp.ensayar();
      expect(servicio.ensayar).toHaveBeenCalledTimes(1);
      expect(servicio.ensayar).toHaveBeenCalledWith(1, comp.archivo!);
      pendiente.complete();
    });
  });

  describe('3. ensayo con errores', () => {
    const ERRORES = [
      inc(14, 20341, 'sede', '«Estepa centro» no es una sede de esta escuela.'),
      inc(87, 1932, 'telefono', 'Falta el teléfono.'),
      inc(120, 20877, 'grupo', 'No hay ningún grupo «Infantil L-X 17:00» en Mollina.'),
      inc(121, null, null, 'Cuarta incidencia sin número ni columna.'),
      inc(121, 3, 'grado', 'Misma fila, otra columna.'),
    ];
    const AVISOS = [inc(9, 20112, 'direccion', 'Sin dirección: entrará como «Pendiente».')];

    beforeEach(async () => {
      preparar();
      await ensayar(informe({ listas: 248, errores: ERRORES, avisos: AVISOS }));
    });

    it('recuentos por filas distintas, ayuda, descarga y barra', () => {
      expect(texto()).toContain('Resultado del ensayo');
      const recuentos = el().querySelector('.recuentos')!.textContent!.replace(/\s+/g, ' ');
      expect(recuentos).toContain('248 listas para cargar');
      expect(recuentos).toContain('4 con errores');
      expect(recuentos).toContain('1 con avisos');
      expect(texto()).toContain('No se ha guardado nada. Mientras haya errores no se puede cargar.');
      expect(boton('Descargar hoja con errores')).toBeDefined();
      expect(texto()).toContain('Tu misma hoja, con el motivo de cada fila en la columna revisar.');
      expect(resumen()).toBe('4 filas con errores. Corrige la hoja y vuelve a ensayar.');
      expect(boton('Ensayar otra vez')).toBeDefined();
      expect(boton('Cargar')).toBeUndefined();
      expect(texto()).toContain('35 KB · 260 filas · Taekwondo Sierra Sur');
    });

    it('lista con las 3 primeras, «Ver los 5» despliega el resto y el encabezado cuenta filas', () => {
      const lista = el().querySelector('[data-lista="errores"]')!;
      expect(lista.querySelector('header')!.textContent).toContain('Errores');
      expect(lista.querySelector('header span')!.textContent).toBe('4 filas');
      expect(lista.querySelectorAll('li').length).toBe(3);
      const primera = lista.querySelector('li')!;
      expect(primera.querySelector('.ref')!.textContent).toBe('Fila 14 · n.º 20341');
      expect(primera.querySelector('.pastilla')!.textContent).toBe('sede');
      expect(primera.querySelector('.motivo')!.textContent).toContain('«Estepa centro»');
      // Los avisos (1) no llevan pie.
      expect(el().querySelector('[data-lista="avisos"] .pie-lista')).toBeNull();
      const pie = lista.querySelector<HTMLButtonElement>('.pie-lista')!;
      expect(pie.textContent!.trim()).toBe('Ver los 5');
      pie.click();
      fixture.detectChanges();
      const filas = lista.querySelectorAll('li');
      expect(filas.length).toBe(5);
      expect(lista.querySelector('.pie-lista')).toBeNull();
      // Sin número de federación no hay «n.º»; sin columna no hay pastilla.
      expect(filas[3].querySelector('.ref')!.textContent).toBe('Fila 121');
      expect(filas[3].querySelector('.pastilla')).toBeNull();
    });

    it('«Ver los N» también despliega la lista de avisos', async () => {
      preparar();
      const avisos = [1, 2, 3, 4, 5, 6].map((n) => inc(n + 10, null, 'direccion', 'Sin dirección.'));
      await ensayar(informe({ listas: 3, avisos }));
      const lista = el().querySelector('[data-lista="avisos"]')!;
      expect(lista.querySelectorAll('li').length).toBe(3);
      const pie = lista.querySelector<HTMLButtonElement>('.pie-lista')!;
      expect(pie.textContent!.trim()).toBe('Ver los 6');
      pie.click();
      fixture.detectChanges();
      expect(lista.querySelectorAll('li').length).toBe(6);
      expect(lista.querySelector('.pie-lista')).toBeNull();
    });

    it('«Sustituir» descarta el informe y elegir otro fichero invalida el ensayo', () => {
      boton('Sustituir')!.click();
      fixture.detectChanges();
      expect(comp.informe).toBeNull();
      expect(comp.fase).toBe('inicio');
      expect(el().querySelector('.recuentos')).toBeNull();
    });
  });

  describe('3b. recuentos con ceros', () => {
    it('omite las cifras que valen 0', async () => {
      preparar();
      await ensayar(informe({ listas: 0, errores: [inc(2, null, 'sede', 'x')], avisos: [] }));
      const recuentos = el().querySelector('.recuentos')!.textContent!.replace(/\s+/g, ' ');
      expect(recuentos).toContain('1 con errores');
      expect(recuentos).not.toContain('listas');
      expect(recuentos).not.toContain('avisos');
      expect(resumen()).toBe('1 fila con errores. Corrige la hoja y vuelve a ensayar.');
      expect(el().querySelector('[data-lista="errores"] header span')!.textContent).toBe('1 fila');
    });
  });

  describe('descarga de la hoja con errores', () => {
    it('baja el blob con el nombre del servidor sin guardarlo en otro sitio', async () => {
      preparar();
      await ensayar(informe({ listas: 1, errores: [inc(2, 5, 'sede', 'x')] }));
      const blob = new Blob(['x']);
      servicio.hojaConErrores.and.returnValue(of({ blob, nombre: 'hoja-sierra-revisar.xlsx' }));
      const crear = spyOn(URL, 'createObjectURL').and.returnValue('blob:prueba');
      const revocar = spyOn(URL, 'revokeObjectURL');
      jasmine.clock().install();
      let descargado = '';
      spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
        descargado = this.download;
      });
      const setItem = spyOn(Storage.prototype, 'setItem');
      boton('Descargar hoja con errores')!.click();
      fixture.detectChanges();
      expect(servicio.hojaConErrores).toHaveBeenCalledWith(1, comp.archivo!);
      expect(crear).toHaveBeenCalledWith(blob);
      expect(descargado).toBe('hoja-sierra-revisar.xlsx');
      // La URL se revoca diferida, no al instante.
      expect(revocar).not.toHaveBeenCalled();
      jasmine.clock().tick(60_000);
      jasmine.clock().uninstall();
      expect(revocar).toHaveBeenCalledWith('blob:prueba');
      expect(setItem).not.toHaveBeenCalled();
    });

    it('con la descarga en curso, aria-disabled bloquea un segundo clic', async () => {
      preparar();
      await ensayar(informe({ listas: 1, errores: [inc(2, 5, 'sede', 'x')] }));
      const pendiente = new Subject<{ blob: Blob; nombre: string }>();
      servicio.hojaConErrores.and.returnValue(pendiente);
      const b = boton('Descargar hoja con errores')!;
      b.click();
      fixture.detectChanges();
      expect(b.getAttribute('aria-disabled')).toBe('true');
      b.click();
      comp.descargarHoja();
      expect(servicio.hojaConErrores).toHaveBeenCalledTimes(1);
      pendiente.complete();
    });

    it('si falla, error de campo y se puede reintentar', async () => {
      preparar();
      await ensayar(informe({ listas: 1, errores: [inc(2, 5, 'sede', 'x')] }));
      servicio.hojaConErrores.and.returnValue(throwError(() => http(500)));
      boton('Descargar hoja con errores')!.click();
      fixture.detectChanges();
      expect(comp.descargando).toBeFalse();
      expect(texto()).toContain('No se ha podido descargar la hoja.');
    });
  });

  describe('4. ensayo limpio y regla dura de «Cargar»', () => {
    beforeEach(async () => {
      preparar();
      await ensayar(informe({ listas: 260, avisos: [inc(9, 20112, 'direccion', 'Sin dirección.')] }));
    });

    it('barra con N alumnos listos y «Cargar N alumnos»; sin errores no hay descarga', () => {
      expect(resumen()).toBe('260 alumnos listos para Taekwondo Sierra Sur.');
      expect(boton('Cargar 260 alumnos')).toBeDefined();
      expect(boton('Descargar hoja con errores')).toBeUndefined();
      expect(texto()).toContain('No se ha guardado nada todavía.');
      expect(el().querySelector('[data-lista="errores"]')).toBeNull();
      expect(comp.puedeCargar).toBeTrue();
    });

    it('cambiar de fichero invalida el ensayo y desaparece «Cargar»', () => {
      elegirFichero(fichero('otra.xlsx'));
      expect(comp.puedeCargar).toBeFalse();
      expect(boton('Cargar')).toBeUndefined();
      expect(boton('Ensayar')).toBeDefined();
      expect(comp.informe).toBeNull();
    });

    it('cambiar de escuela invalida el ensayo', () => {
      comp.alElegirEscuela(2);
      expect(comp.puedeCargar).toBeFalse();
      expect(comp.informe).toBeNull();
    });

    it('un ensayo de otro fichero o de otra escuela no habilita cargar aunque el informe esté limpio', async () => {
      // Se fuerza el estado: informe limpio pero el fichero ya no es el ensayado.
      comp.archivo = fichero('intruso.xlsx');
      expect(comp.puedeCargar).toBeFalse();
      await comp.cargar();
      expect(confirmacion.confirmar).not.toHaveBeenCalled();
      expect(servicio.cargar).not.toHaveBeenCalled();
    });

    it('«Cargar» abre la confirmación (alertdialog) con los textos del boceto', async () => {
      confirmacion.confirmar.and.resolveTo({ confirmado: false, valor: null });
      boton('Cargar 260 alumnos')!.click();
      await asentar();
      const opciones = confirmacion.confirmar.calls.mostRecent().args[0];
      expect(opciones.titulo).toBe('¿Cargar 260 alumnos en Taekwondo Sierra Sur?');
      expect(opciones.mensaje).toBe('Se crearán 260 alumnos con su grado, su sede y su grupo. No se puede deshacer desde aquí.');
      expect(opciones.accion).toBe('Cargar 260 alumnos');
      expect(opciones.cancelar).toBe('Cancelar');
      expect(opciones.alerta).toBeTrue();
      expect(opciones.destructivo).toBeFalsy();
      // Cancelada: no se carga nada y «Cargar» sigue ahí.
      expect(servicio.cargar).not.toHaveBeenCalled();
      expect(comp.fase).toBe('informe');
    });

    it('«Cargar» no se dispara dos veces mientras el diálogo está abierto', async () => {
      let resolver!: (v: { confirmado: boolean; valor: null }) => void;
      confirmacion.confirmar.and.returnValue(new Promise((r) => (resolver = r)));
      const a = comp.cargar();
      const b = comp.cargar();
      expect(confirmacion.confirmar).toHaveBeenCalledTimes(1);
      resolver({ confirmado: false, valor: null });
      await Promise.all([a, b]);
    });

    it('si el ensayo se invalida con el diálogo abierto, no se carga', async () => {
      let resolver!: (v: { confirmado: boolean; valor: null }) => void;
      confirmacion.confirmar.and.returnValue(new Promise((r) => (resolver = r)));
      const p = comp.cargar();
      comp.alElegirEscuela(2);
      resolver({ confirmado: true, valor: null });
      await p;
      expect(servicio.cargar).not.toHaveBeenCalled();
    });
  });

  describe('4b. nada que cargar (todo ya cargado)', () => {
    const recuentos = (): string => el().querySelector('.recuentos')!.textContent!.replace(/\s+/g, ' ');

    it('no ofrece «Cargar»: línea de ya cargadas, ayuda y barra propias', async () => {
      preparar();
      await ensayar(informe({ listas: 0, yaCargados: 5 }));
      expect(comp.nadaQueCargar).toBeTrue();
      expect(comp.puedeCargar).toBeFalse();
      expect(boton('Cargar')).toBeUndefined();
      expect(recuentos()).toContain('5 ya cargadas');
      expect(el().querySelector('.recuentos li.gris')).not.toBeNull();
      expect(texto()).toContain('No se ha guardado nada.');
      expect(texto()).not.toContain('No se ha guardado nada todavía.');
      expect(resumen()).toBe('No hay alumnos nuevos que cargar.');
      expect(boton('Ensayar otra vez')).toBeDefined();
    });

    it('singular, y sin lista vacía cuando no hay ninguna cifra', async () => {
      preparar();
      await ensayar(informe({ listas: 0, yaCargados: 1 }));
      expect(recuentos()).toContain('1 ya cargada');
      expect(recuentos()).not.toContain('ya cargadas');
      preparar();
      await ensayar(informe({ listas: 0, yaCargados: 0 }));
      expect(el().querySelector('.recuentos')).toBeNull();
    });

    it('con listas para cargar, la línea de ya cargadas también sale (estado 4)', async () => {
      preparar();
      await ensayar(informe({ listas: 10, yaCargados: 250 }));
      expect(recuentos()).toContain('10 listas para cargar');
      expect(recuentos()).toContain('250 ya cargadas');
      expect(boton('Cargar 10 alumnos')).toBeDefined();
    });

    it('con errores (estado 3) también sale', async () => {
      preparar();
      await ensayar(informe({ listas: 0, yaCargados: 2, errores: [inc(2, null, 'sede', 'x')] }));
      expect(recuentos()).toContain('2 ya cargadas');
    });
  });

  describe('6, 7 y 8. cargando y carga hecha', () => {
    async function llegarALimpio(listas = 260): Promise<void> {
      preparar();
      await ensayar(informe({ listas }));
    }

    it('cargando: giro, role status con el recuento y sin doble envío', async () => {
      await llegarALimpio();
      const pendiente = new Subject<InformeImportacion>();
      servicio.cargar.and.returnValue(pendiente);
      await comp.cargar();
      fixture.detectChanges();
      expect(resumen()).toBe('Cargando 260 alumnos…');
      expect(el().querySelector('.barra-resumen')!.getAttribute('role')).toBe('status');
      expect(boton('Cargando…')!.getAttribute('aria-busy')).toBe('true');
      expect(boton('Cancelar')!.disabled).toBeTrue();
      await comp.cargar();
      comp.ensayar();
      expect(servicio.cargar).toHaveBeenCalledTimes(1);
      expect(servicio.cargar).toHaveBeenCalledWith(1, comp.archivo!);
      pendiente.complete();
    });

    it('7: ninguno estaba ya cargado', async () => {
      await llegarALimpio();
      servicio.cargar.and.returnValue(of(informe({ creados: 260, yaCargados: 0 })));
      await comp.cargar();
      await asentar();
      expect(el().querySelector('h2')!.textContent).toBe('260 alumnos cargados');
      expect(texto()).toContain('En Taekwondo Sierra Sur. Ninguno estaba ya cargado.');
      expect(boton('Importar otra hoja')).toBeDefined();
      expect(el().querySelector('.barra')).toBeNull();
      // No se conserva la hoja.
      expect(comp.archivo).toBeNull();
    });

    it('8: M ya estaban cargados y no se han tocado (con singulares)', async () => {
      await llegarALimpio();
      servicio.cargar.and.returnValue(of(informe({ creados: 12, yaCargados: 248 })));
      await comp.cargar();
      await asentar();
      expect(texto()).toContain('12 alumnos cargados');
      expect(texto()).toContain('En Taekwondo Sierra Sur. 248 ya estaban cargados y no se han tocado.');
    });

    it('singular: 1 alumno cargado y 1 ya estaba cargado', async () => {
      await llegarALimpio(1);
      expect(boton('Cargar 1 alumno')).toBeDefined();
      servicio.cargar.and.returnValue(of(informe({ creados: 1, yaCargados: 1 })));
      await comp.cargar();
      await asentar();
      expect(confirmacion.confirmar.calls.mostRecent().args[0].titulo).toBe('¿Cargar 1 alumno en Taekwondo Sierra Sur?');
      expect(confirmacion.confirmar.calls.mostRecent().args[0].mensaje).toContain('Se creará 1 alumno');
      expect(texto()).toContain('1 alumno cargado');
      expect(texto()).toContain('1 ya estaba cargado y no se ha tocado.');
    });

    it('«Importar otra hoja» vuelve al inicio, sin escuela ni fichero', async () => {
      await llegarALimpio();
      servicio.cargar.and.returnValue(of(informe({ creados: 260 })));
      await comp.cargar();
      await asentar();
      boton('Importar otra hoja')!.click();
      fixture.detectChanges();
      expect(comp.fase).toBe('inicio');
      expect(comp.escuelaId).toBeNull();
      expect(comp.archivo).toBeNull();
      expect(boton('Elegir Excel')).toBeDefined();
      expect(resumen()).toBe('Elige la escuela y la hoja.');
    });
  });

  describe('9. fichero no válido (400 del ensayo)', () => {
    it('vuelve a la zona con el mensaje del servidor y la ayuda del boceto', async () => {
      preparar();
      servicio.ensayar.and.returnValue(
        throwError(() => http(400, { mensaje: 'Este fichero no es la hoja de alumnos: faltan las columnas sede, telefono y grupo.' }))
      );
      boton('Ensayar')!.click();
      await asentar();
      expect(comp.archivo).toBeNull();
      const alerta = el().querySelector('[role="alert"]')!;
      expect(alerta.textContent).toContain('Este fichero no es la hoja de alumnos: faltan las columnas sede, telefono y grupo.');
      expect(texto()).toContain('Usa la hoja maestra de la escuela, sin cambiar los títulos de las columnas.');
      expect(boton('Elegir Excel')).toBeDefined();
      expect(resumen()).toBe('Elige la escuela y la hoja.');
    });

    it('sin mensaje del servidor, texto de reserva', async () => {
      preparar();
      servicio.ensayar.and.returnValue(throwError(() => http(400)));
      boton('Ensayar')!.click();
      await asentar();
      expect(el().querySelector('[role="alert"]')!.textContent).toContain('Este fichero no es la hoja de alumnos.');
    });

    it('red caída (0) o 404: error de campo y el fichero se descarta', async () => {
      preparar();
      servicio.ensayar.and.returnValue(throwError(() => http(0)));
      boton('Ensayar')!.click();
      await asentar();
      expect(el().querySelector('[role="alert"]')!.textContent).toContain('Vuelve a elegirlo.');
      preparar();
      servicio.ensayar.and.returnValue(throwError(() => http(404)));
      boton('Ensayar')!.click();
      await asentar();
      expect(el().querySelector('[role="alert"]')!.textContent).toContain('No se ha podido ensayar la hoja.');
    });
  });

  describe('10. errores al cargar', () => {
    async function llegarACargar(): Promise<void> {
      preparar();
      await ensayar(informe({ listas: 5 }));
    }

    it('500: bloque «No se ha cargado ningún alumno» y «Ensayar otra vez» abre el selector', async () => {
      await llegarACargar();
      servicio.cargar.and.returnValue(throwError(() => http(500, { mensaje: 'La importación ha fallado. No se ha cargado nada.' })));
      await comp.cargar();
      await asentar();
      const bloque = el().querySelector('.bloque--error')!;
      expect(bloque.getAttribute('role')).toBe('alert');
      expect(bloque.textContent).toContain('No se ha cargado ningún alumno');
      expect(bloque.textContent).toContain('La carga se ha deshecho entera. Vuelve a ensayar la hoja antes de intentarlo otra vez.');
      expect(boton('Cargar')).toBeUndefined();
      const abrir = spyOn(HTMLInputElement.prototype, 'click');
      boton('Ensayar otra vez')!.click();
      await asentar();
      expect(abrir).toHaveBeenCalledTimes(1);
      expect(servicio.ensayar).toHaveBeenCalledTimes(1);
      expect(comp.fase).toBe('inicio');
    });

    [0, 502, 503, 504].forEach((status) => {
      it(`${status}: resultado desconocido, no afirma que se deshiciera`, async () => {
        await llegarACargar();
        servicio.cargar.and.returnValue(throwError(() => http(status)));
        await comp.cargar();
        await asentar();
        const bloque = el().querySelector('.bloque--error')!;
        expect(bloque.textContent).toContain('No sabemos si la carga ha terminado');
        expect(bloque.textContent).toContain(
          'Se cortó la conexión antes de recibir la respuesta. Vuelve a ensayar la hoja: los alumnos ya cargados se reconocen y no se duplican.'
        );
        expect(bloque.textContent).not.toContain('No se ha cargado ningún alumno');
        expect(bloque.textContent).not.toContain('deshecho');
        expect(boton('Ensayar otra vez')).toBeDefined();
      });
    });

    it('422: informe con errores como en el estado 3 y «Cargar» invalidado', async () => {
      await llegarACargar();
      servicio.cargar.and.returnValue(
        throwError(() => http(422, informe({ listas: 4, errores: [inc(30, 77, 'grupo', 'No hay ese grupo.')] })))
      );
      await comp.cargar();
      await asentar();
      expect(comp.fase).toBe('informe');
      expect(resumen()).toBe('1 fila con errores. Corrige la hoja y vuelve a ensayar.');
      expect(texto()).toContain('Fila 30 · n.º 77');
      expect(boton('Cargar')).toBeUndefined();
      expect(comp.puedeCargar).toBeFalse();
    });

    it('400 al cargar: vuelve a la zona con el mensaje', async () => {
      await llegarACargar();
      servicio.cargar.and.returnValue(throwError(() => http(400, { mensaje: 'Este fichero no es la hoja de alumnos.' })));
      await comp.cargar();
      await asentar();
      expect(comp.fase).toBe('inicio');
      expect(el().querySelector('[role="alert"]')!.textContent).toContain('Este fichero no es la hoja de alumnos.');
    });
  });

  describe('«Ensayar otra vez» (no reenvía el fichero guardado)', () => {
    const conErrores = (): InformeImportacion => informe({ listas: 1, errores: [inc(2, 5, 'sede', 'x')] });

    it('descarta informe y ensayo, conserva la escuela y abre el selector', async () => {
      preparar(fichero(), 2);
      await ensayar(conErrores());
      const abrir = spyOn(HTMLInputElement.prototype, 'click');
      boton('Ensayar otra vez')!.click();
      fixture.detectChanges();
      expect(abrir).toHaveBeenCalledTimes(1);
      expect(servicio.ensayar).toHaveBeenCalledTimes(1);
      expect(comp.informe).toBeNull();
      expect(comp.archivo).toBeNull();
      expect(comp.escuelaId).toBe(2);
      expect(comp.fase).toBe('inicio');
      expect(el().querySelector('.zona')).not.toBeNull();
    });

    it('si se elige un fichero válido, ensaya directamente', async () => {
      preparar();
      await ensayar(conErrores());
      spyOn(HTMLInputElement.prototype, 'click');
      boton('Ensayar otra vez')!.click();
      servicio.ensayar.and.returnValue(of(informe({ listas: 3 })));
      const nuevo = fichero('corregida.xlsx');
      elegirFichero(nuevo);
      await asentar();
      expect(servicio.ensayar).toHaveBeenCalledTimes(2);
      expect(servicio.ensayar.calls.mostRecent().args).toEqual([1, nuevo]);
      expect(comp.fase).toBe('informe');
    });

    it('si se cancela el selector, queda el estado inicial con la escuela y sin ensayar', async () => {
      preparar();
      await ensayar(conErrores());
      spyOn(HTMLInputElement.prototype, 'click');
      boton('Ensayar otra vez')!.click();
      el().querySelector('#ia-fichero')!.dispatchEvent(new Event('cancel'));
      fixture.detectChanges();
      expect(comp.fase).toBe('inicio');
      expect(comp.escuelaId).toBe(1);
      expect(el().querySelector('.zona')).not.toBeNull();
      // Un fichero elegido más tarde a mano no se ensaya solo.
      elegirFichero(fichero('otra.xlsx'));
      expect(servicio.ensayar).toHaveBeenCalledTimes(1);
      expect(comp.fase).toBe('inicio');
    });

    it('con un fichero no válido en este flujo no ensaya y avisa', async () => {
      preparar();
      await ensayar(conErrores());
      spyOn(HTMLInputElement.prototype, 'click');
      boton('Ensayar otra vez')!.click();
      elegirFichero(fichero('hoja.xls'));
      expect(servicio.ensayar).toHaveBeenCalledTimes(1);
      expect(el().querySelector('[role="alert"]')!.textContent).toContain('Elige un fichero .xlsx.');
    });

    it('con 0 listas, «Ensayar otra vez» también abre el selector', async () => {
      preparar();
      await ensayar(informe({ listas: 0, yaCargados: 5 }));
      const abrir = spyOn(HTMLInputElement.prototype, 'click');
      boton('Ensayar otra vez')!.click();
      expect(abrir).toHaveBeenCalledTimes(1);
      expect(comp.fase).toBe('inicio');
    });
  });

  describe('foco', () => {
    const activo = (): Element | null => document.activeElement;

    it('informe: el título «Resultado del ensayo»', async () => {
      preparar();
      await ensayar(informe({ listas: 1 }));
      expect(activo()).toBe(el().querySelector('[data-foco="resultado"]'));
    });

    it('hecha: el título del resultado', async () => {
      preparar();
      await ensayar(informe({ listas: 1 }));
      servicio.cargar.and.returnValue(of(informe({ creados: 1 })));
      await comp.cargar();
      await asentar();
      expect(activo()).toBe(el().querySelector('[data-foco="hecha"]'));
    });

    it('fallo de la carga: el título del bloque de error', async () => {
      preparar();
      await ensayar(informe({ listas: 1 }));
      servicio.cargar.and.returnValue(throwError(() => http(500)));
      await comp.cargar();
      await asentar();
      expect(activo()).toBe(el().querySelector('[data-foco="fallo"]'));
    });

    [400, 404, 500, 0].forEach((status) => {
      it(`ensayo fallido (${status}): «Elegir Excel»`, async () => {
        preparar();
        servicio.ensayar.and.returnValue(throwError(() => http(status)));
        boton('Ensayar')!.click();
        await asentar();
        expect(activo()).toBe(el().querySelector('#ia-elegir'));
      });
    });
  });

  describe('destrucción del componente', () => {
    it('cancela el ensayo en vuelo y no toca nada si la respuesta llega tarde', () => {
      const pendiente = new Subject<InformeImportacion>();
      servicio.ensayar.and.returnValue(pendiente);
      preparar();
      boton('Ensayar')!.click();
      fixture.destroy();
      expect(pendiente.observed).toBeFalse();
      expect(() => pendiente.next(informe())).not.toThrow();
    });

    it('cancela la carga en vuelo', async () => {
      preparar();
      await ensayar(informe({ listas: 1 }));
      const pendiente = new Subject<InformeImportacion>();
      servicio.cargar.and.returnValue(pendiente);
      await comp.cargar();
      fixture.destroy();
      expect(pendiente.observed).toBeFalse();
    });
  });

  describe('Cancelar', () => {
    it('vuelve atrás si hay historial', () => {
      spyOn(comp, 'hayHistorial').and.returnValue(true);
      boton('Cancelar')!.click();
      expect(location.back).toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('sin historial va a Logo y color', () => {
      spyOn(comp, 'hayHistorial').and.returnValue(false);
      boton('Cancelar')!.click();
      expect(router.navigate).toHaveBeenCalledWith(['/plataformaLogos']);
    });
  });

  describe('privacidad', () => {
    it('no escribe en el almacenamiento ni en la consola', async () => {
      const set = spyOn(Storage.prototype, 'setItem');
      const log = spyOn(console, 'log');
      const aviso = spyOn(console, 'warn');
      preparar();
      await ensayar(informe({ listas: 1, errores: [inc(2, 5, 'sede', 'x')] }));
      expect(set).not.toHaveBeenCalled();
      expect(log).not.toHaveBeenCalled();
      expect(aviso).not.toHaveBeenCalled();
    });
  });
});
