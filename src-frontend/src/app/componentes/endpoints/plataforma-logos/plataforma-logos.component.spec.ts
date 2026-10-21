import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, defer, of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { EscuelaLogo } from '../../../interfaces/escuela-logo';
import { LogoEscuelaService } from '../../../servicios/plataforma/logo-escuela.service';
import { LOGO_MAX_BYTES, PlataformaLogosComponent } from './plataforma-logos.component';

describe('PlataformaLogosComponent', () => {
  let fixture: ComponentFixture<PlataformaLogosComponent>;
  let componente: PlataformaLogosComponent;
  let servicio: jasmine.SpyObj<LogoEscuelaService>;
  let creadas: string[];
  let revocadas: string[];

  const con: EscuelaLogo = { id: 2, nombre: 'Escuela Norte', tieneLogo: true, marcaHeredada: false, colorPrimario: null };
  const sin: EscuelaLogo = { id: 3, nombre: 'Escuela Sur', tieneLogo: false, marcaHeredada: false, colorPrimario: null };
  const heredada: EscuelaLogo = { id: 1, nombre: 'Moiskimdo', tieneLogo: false, marcaHeredada: true, colorPrimario: null };
  const heredadaConLogo: EscuelaLogo = { ...heredada, tieneLogo: true };

  const texto = (): string => fixture.nativeElement.textContent.replace(/\s+/g, ' ');
  const filas = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('li.escuela'));
  const boton = (fila: HTMLElement, etiqueta: string): HTMLButtonElement | undefined =>
    Array.from(fila.querySelectorAll('button')).find((b) => b.textContent!.includes(etiqueta)) as HTMLButtonElement | undefined;

  /** El componente copia el archivo en memoria antes de enviarlo: se espera a que termine. */
  async function elegir(fila: HTMLElement, archivo: File): Promise<void> {
    const espia = jasmine.isSpy(componente.elegido)
      ? (componente.elegido as jasmine.Spy)
      : spyOn(componente, 'elegido').and.callThrough();
    const entrada = fila.querySelector('input[type=file]') as HTMLInputElement;
    const lista = new DataTransfer();
    lista.items.add(archivo);
    entrada.files = lista.files;
    entrada.dispatchEvent(new Event('change'));
    await espia.calls.mostRecent().returnValue;
    fixture.detectChanges();
  }

  const png = (bytes = 10) => new File([new Uint8Array(bytes)], 'logo.png', { type: 'image/png' });

  beforeEach(async () => {
    creadas = [];
    revocadas = [];
    let n = 0;
    spyOn(URL, 'createObjectURL').and.callFake(() => {
      const url = `blob:prueba/${++n}`;
      creadas.push(url);
      return url;
    });
    spyOn(URL, 'revokeObjectURL').and.callFake((url: string) => {
      revocadas.push(url);
    });

    servicio = jasmine.createSpyObj<LogoEscuelaService>('LogoEscuelaService', ['escuelas', 'subir', 'quitar', 'logo', 'guardarColor']);
    servicio.escuelas.and.returnValue(of([heredada, con, sin]));
    servicio.logo.and.callFake(() => of(new Blob(['x'], { type: 'image/png' })));

    await TestBed.configureTestingModule({
      imports: [PlataformaLogosComponent],
      providers: [{ provide: LogoEscuelaService, useValue: servicio }],
    }).compileComponents();

    fixture = TestBed.createComponent(PlataformaLogosComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('lista las escuelas, con miniatura la que tiene logo y «Sin logo» la que no', () => {
    expect(filas().length).toBe(3);
    expect(servicio.logo).toHaveBeenCalledWith(2);
    expect(servicio.logo).toHaveBeenCalledWith(1);
    expect(servicio.logo).not.toHaveBeenCalledWith(3);
    expect(filas()[1].querySelector('img')).not.toBeNull();
    expect(filas()[2].querySelector('img')).toBeNull();
    expect(filas()[2].textContent).toContain('Sin logo');
    expect(texto()).toContain('PNG, JPG o SVG, hasta 1 MB');
  });

  it('la heredada sin logo subido pide su miniatura, ofrece «Cambiar logo» y no «Volver al de siempre»', () => {
    const fila = filas()[0];
    expect(fila.textContent).toContain('Sin logo subido, sale el de siempre');
    expect(servicio.logo).toHaveBeenCalledWith(1);
    expect(fila.querySelector('img')).not.toBeNull();
    expect(boton(fila, 'Cambiar logo')).toBeDefined();
    expect(boton(fila, 'Volver al de siempre')).toBeUndefined();
    expect(boton(fila, 'Quitar logo')).toBeUndefined();
    expect(fila.querySelector('input[type=file]')).not.toBeNull();
  });

  it('si el GET de la heredada da 404, muestra el marcador «Logo de siempre» sin aviso', () => {
    servicio.logo.and.callFake((id: number) =>
      id === 1 ? throwError(() => new HttpErrorResponse({ status: 404 })) : of(new Blob(['x'], { type: 'image/png' }))
    );
    componente.cargar();
    fixture.detectChanges();
    const fila = filas()[0];
    expect(fila.querySelector('img')).toBeNull();
    expect(fila.querySelector('.miniatura')!.textContent).toContain('Logo de siempre');
  });

  it('la heredada muestra «Cargando…» mientras llega la miniatura y el marcador solo tras el 404', () => {
    const descarga = new Subject<Blob>();
    servicio.logo.and.returnValue(descarga);
    servicio.escuelas.and.returnValue(of([heredada]));
    componente.cargar();
    fixture.detectChanges();
    expect(filas()[0].querySelector('.miniatura')!.textContent).toContain('Cargando…');
    expect(filas()[0].querySelector('.miniatura')!.textContent).not.toContain('Logo de siempre');
    descarga.error(new HttpErrorResponse({ status: 404 }));
    fixture.detectChanges();
    expect(filas()[0].querySelector('.miniatura')!.textContent).toContain('Logo de siempre');
  });

  it('en la heredada, un GET obsoleto del logo subido no pisa al de siempre tras «Volver al de siempre»', async () => {
    const viejo = new Subject<Blob>();
    const nuevo = new Subject<Blob>();
    servicio.logo.and.returnValues(viejo, nuevo);
    servicio.escuelas.and.returnValue(of([heredadaConLogo]));
    componente.cargar();
    fixture.detectChanges();
    spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: true } as any);
    servicio.quitar.and.returnValue(of(heredada));

    boton(filas()[0], 'Volver al de siempre')!.click();
    await fixture.whenStable();
    viejo.next(new Blob(['viejo'], { type: 'image/png' }));
    fixture.detectChanges();
    expect(componente.filas[0].miniatura).toBeNull();

    nuevo.next(new Blob(['siempre'], { type: 'image/png' }));
    fixture.detectChanges();
    expect(componente.filas[0].miniatura).toBe(creadas[creadas.length - 1]);
    expect(creadas.length).toBe(2 + 1);
  });

  it('la heredada con logo subido ofrece «Volver al de siempre»; confirma, hace DELETE y recupera la miniatura de siempre', async () => {
    servicio.escuelas.and.returnValue(of([heredadaConLogo]));
    componente.cargar();
    fixture.detectChanges();
    const aviso = spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: true } as any);
    servicio.quitar.and.returnValue(of(heredada));
    servicio.logo.calls.reset();

    expect(filas()[0].textContent).not.toContain('Sin logo subido');
    boton(filas()[0], 'Volver al de siempre')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    const opciones = aviso.calls.mostRecent().args[0] as any;
    expect(opciones.title).toBe('¿Volver al logo de siempre de Moiskimdo?');
    expect(opciones.text).toBe('Sus PDF y correos llevarán el logo anterior.');
    expect(servicio.quitar).toHaveBeenCalledWith(1);
    expect(servicio.logo).toHaveBeenCalledWith(1);
    expect(boton(filas()[0], 'Volver al de siempre')).toBeUndefined();
    expect(filas()[0].textContent).toContain('Sin logo subido, sale el de siempre');
  });

  it('el selector admite PNG, JPG y SVG', () => {
    const accept = filas()[1].querySelector('input[type=file]')!.getAttribute('accept')!;
    expect(accept).toBe('.png,.jpg,.jpeg,.svg,image/png,image/jpeg,image/svg+xml');
  });

  it('sube el archivo, desactiva el botón mientras tanto y refresca la miniatura revocando la anterior', async () => {
    const envio = new Subject<EscuelaLogo>();
    servicio.subir.and.returnValue(envio);

    const antes = creadas.length;
    const anterior = componente.filas[1].miniatura!;
    const archivo = png();
    await elegir(filas()[1], archivo);

    expect(servicio.subir).toHaveBeenCalledWith(2, jasmine.any(File));
    const enviado = servicio.subir.calls.mostRecent().args[1];
    expect(enviado).not.toBe(archivo);
    expect(enviado.name).toBe('logo.png');
    expect(enviado.size).toBe(archivo.size);
    expect(boton(filas()[1], 'Cambiar logo')!.disabled).toBeTrue();
    expect(filas()[1].textContent).toContain('Guardando');

    envio.next(con);
    envio.complete();
    fixture.detectChanges();

    expect(boton(filas()[1], 'Cambiar logo')!.disabled).toBeFalse();
    expect(creadas.length).toBe(antes + 1);
    expect(revocadas).toContain(anterior);
    expect(componente.filas[1].miniatura).toBe(creadas[creadas.length - 1]);
  });

  it('subir a una escuela sin logo muestra la miniatura nueva', async () => {
    servicio.subir.and.returnValue(of({ ...sin, tieneLogo: true }));
    await elegir(filas()[2], png());
    expect(servicio.logo).toHaveBeenCalledWith(3);
    expect(filas()[2].querySelector('img')).not.toBeNull();
    expect(boton(filas()[2], 'Quitar logo')).toBeDefined();
  });

  it('un 400 muestra el mensaje del backend en la fila y no toca el logo', async () => {
    servicio.subir.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'El SVG contiene contenido no permitido' } }))
    );
    await elegir(filas()[2], png());
    expect(filas()[2].querySelector('[role=alert]')!.textContent).toContain('El SVG contiene contenido no permitido');
    expect(boton(filas()[2], 'Subir logo')!.disabled).toBeFalse();
    expect(filas()[2].querySelector('img')).toBeNull();
  });

  it('un 409 muestra el mensaje del backend', async () => {
    servicio.subir.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Esta escuela tiene la marca de la plataforma' } }))
    );
    await elegir(filas()[1], png());
    expect(filas()[1].querySelector('[role=alert]')!.textContent).toContain('Esta escuela tiene la marca de la plataforma');
  });

  it('un 404 sin cuerpo usa un texto por defecto', async () => {
    servicio.subir.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    await elegir(filas()[1], png());
    expect(filas()[1].querySelector('[role=alert]')!.textContent).toContain('No se pudo subir el logo');
  });

  it('un archivo que no se puede leer no se envía y avisa del motivo en la fila', async () => {
    const archivo = png();
    spyOn(archivo, 'arrayBuffer').and.rejectWith(new DOMException('no legible', 'NotReadableError'));
    await elegir(filas()[2], archivo);
    expect(servicio.subir).not.toHaveBeenCalled();
    expect(filas()[2].querySelector('[role=alert]')!.textContent).toContain('No se pudo leer el archivo');
  });

  it('un archivo de más de 1 MB no se envía y avisa', async () => {
    await elegir(filas()[2], png(LOGO_MAX_BYTES + 1));
    expect(servicio.subir).not.toHaveBeenCalled();
    expect(filas()[2].querySelector('[role=alert]')!.textContent).toContain('más de 1 MB');
  });

  it('quitar pide confirmación y, si se acepta, llama a DELETE y retira la miniatura', async () => {
    const aviso = spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: true } as any);
    servicio.quitar.and.returnValue(of({ ...con, tieneLogo: false }));
    const anterior = componente.filas[1].miniatura!;

    boton(filas()[1], 'Quitar logo')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    const opciones = aviso.calls.mostRecent().args[0] as any;
    expect(opciones.title).toBe('¿Quitar el logo de Escuela Norte?');
    expect(opciones.text).toBe('Sus PDF y correos saldrán solo con el nombre.');
    expect(servicio.quitar).toHaveBeenCalledWith(2);
    expect(revocadas).toContain(anterior);
    expect(filas()[1].querySelector('img')).toBeNull();
    expect(filas()[1].textContent).toContain('Sin logo');
  });

  it('si se quita el logo con la descarga en vuelo, la miniatura no reaparece', async () => {
    const descarga = new Subject<Blob>();
    servicio.logo.and.returnValue(descarga);
    servicio.escuelas.and.returnValue(of([con]));
    componente.cargar();
    fixture.detectChanges();
    spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: true } as any);
    servicio.quitar.and.returnValue(of({ ...con, tieneLogo: false }));

    boton(filas()[0], 'Quitar logo')!.click();
    await fixture.whenStable();
    descarga.next(new Blob(['x'], { type: 'image/png' }));
    fixture.detectChanges();

    expect(componente.filas[0].miniatura).toBeNull();
    expect(filas()[0].querySelector('img')).toBeNull();
  });

  it('si se cancela la confirmación no se borra nada', async () => {
    spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: false } as any);
    boton(filas()[1], 'Quitar logo')!.click();
    await fixture.whenStable();
    expect(servicio.quitar).not.toHaveBeenCalled();
  });

  it('al destruirse revoca todas las URL de objeto', () => {
    const vivas = creadas.filter((url) => !revocadas.includes(url));
    expect(vivas.length).toBe(2);
    fixture.destroy();
    vivas.forEach((url) => expect(revocadas).toContain(url));
  });

  it('si falla la lista permite reintentar', () => {
    servicio.escuelas.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    componente.cargar();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]')!.textContent).toContain('No se pudo cargar la lista');
    servicio.escuelas.and.returnValue(of([sin]));
    boton(fixture.nativeElement, 'Reintentar')!.click();
    fixture.detectChanges();
    expect(filas().length).toBe(1);
  });

  describe('miniatura tras subir', () => {
    const blob = () => new Blob(['x'], { type: 'image/png' });
    const caida = (estado: number) => new HttpErrorResponse({ status: estado });
    /** Cada suscripción consume un resultado: Error falla, Blob responde. */
    const secuencia = (...pasos: Array<number | Blob>) => {
      let i = 0;
      return defer(() => {
        const paso = pasos[Math.min(i++, pasos.length - 1)];
        return paso instanceof Blob ? of(paso) : throwError(() => caida(paso));
      });
    };
    /** Texto del aviso de arriba (toast) si hay uno visible. */
    const aviso = (): string => (Swal.isVisible() ? Swal.getTitle()?.textContent ?? '' : '');
    const subirEn2 = async (): Promise<void> => {
      servicio.subir.and.returnValue(of(con));
      await elegir(filas()[1], png());
    };

    const limpiarAvisos = (): void => {
      Swal.close();
      document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    };
    /** Reloj simulado: la espera del reintento se salta con tick, sin esperar de verdad. */
    const tick = (ms = 1500): void => {
      jasmine.clock().tick(ms);
      fixture.detectChanges();
    };
    beforeEach(() => {
      limpiarAvisos();
      jasmine.clock().install();
    });
    afterEach(() => {
      jasmine.clock().uninstall();
      limpiarAvisos();
    });

    it('una caída de red (estado 0) en la miniatura se reintenta sola y no avisa de error', async () => {
      let suscripciones = 0;
      const base = secuencia(0, blob());
      servicio.logo.and.returnValue(defer(() => { suscripciones++; return base; }));
      await subirEn2();
      expect(suscripciones).toBe(1); // sin tick no hay segunda petición
      tick(1499);
      expect(suscripciones).toBe(1);
      tick(1);
      expect(suscripciones).toBe(2);
      expect(aviso()).not.toContain('No se pudo');
      expect(filas()[1].querySelector('img')).not.toBeNull();
      expect(texto()).not.toContain('no se pudo mostrar la vista previa');
    });

    it('si falla dos veces, la fila dice que el logo está guardado, sin aviso de arriba, y «Reintentar» pinta la miniatura', async () => {
      servicio.logo.and.returnValue(secuencia(0, 0));
      await subirEn2();
      tick();
      expect(aviso()).not.toContain('No se pudo');
      expect(filas()[1].textContent).toContain('Logo guardado; no se pudo mostrar la vista previa');
      expect(filas()[1].querySelector('img')).toBeNull();

      servicio.logo.and.returnValue(secuencia(blob()));
      boton(filas()[1], 'Reintentar')!.click();
      fixture.detectChanges();
      tick();
      expect(filas()[1].querySelector('img')).not.toBeNull();
      expect(filas()[1].textContent).not.toContain('no se pudo mostrar la vista previa');
      expect(boton(filas()[1], 'Reintentar')).toBeUndefined();
    });

    it('si la fila está sin vista previa y falla otra subida, ambos avisos existen a la vez', async () => {
      servicio.logo.and.returnValue(secuencia(0, 0));
      await subirEn2();
      tick();
      servicio.subir.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'Formato no permitido' } })));
      await elegir(filas()[1], png());
      expect(filas()[1].querySelector('.vista-previa-fila')).not.toBeNull();
      expect(filas()[1].querySelector('.error-fila')).not.toBeNull();
    });

    it('si se destruye durante la espera del reintento no se hace la segunda petición', async () => {
      let suscripciones = 0;
      servicio.logo.and.returnValue(defer(() => { suscripciones++; return throwError(() => caida(0)); }));
      await subirEn2();
      expect(suscripciones).toBe(1);
      fixture.destroy();
      tick(1500);
      expect(suscripciones).toBe(1);
    });

    it('un 5xx también se reintenta una vez', async () => {
      servicio.logo.and.returnValue(secuencia(503, blob()));
      await subirEn2();
      tick();
      expect(filas()[1].querySelector('img')).not.toBeNull();
    });

    it('un 4xx no se reintenta', async () => {
      let suscripciones = 0;
      servicio.logo.and.returnValue(defer(() => { suscripciones++; return throwError(() => caida(403)); }));
      await subirEn2();
      expect(suscripciones).toBe(1);
    });

    it('en la carga inicial, si falla dos veces conserva el aviso de arriba', () => {
      servicio.logo.and.returnValue(secuencia(0, 0));
      componente.cargar();
      fixture.detectChanges();
      tick();
      expect(aviso()).toBe('No se pudo cargar el logo de Escuela Norte');
      expect(texto()).not.toContain('Logo guardado');
    });
  });
  describe('color de la escuela (8b-T)', () => {
    const verde: EscuelaLogo = { id: 4, nombre: 'Escuela Ficticia Verde', tieneLogo: false, marcaHeredada: false, colorPrimario: '#3E9B4F' };
    const fila = (id: number): HTMLElement => filas().find((f) => f.getAttribute('data-escuela') === String(id))!;
    const campoTexto = (f: HTMLElement) => f.querySelector('input.color-texto') as HTMLInputElement;
    const selector = (f: HTMLElement) => f.querySelector('input[type=color]') as HTMLInputElement;
    const cabecera = (f: HTMLElement) => f.querySelector('.vista-cabecera') as HTMLElement;
    const escribir = (f: HTMLElement, valor: string) => {
      campoTexto(f).value = valor;
      campoTexto(f).dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    const respuesta = (escuela: EscuelaLogo, colorPrimario: string | null) => ({ ...escuela, colorPrimario });

    beforeEach(() => {
      servicio.guardarColor.and.callFake((id: number, color: string | null) =>
        of(respuesta(id === 4 ? verde : sin, color))
      );
      servicio.escuelas.and.returnValue(of([verde, sin]));
      componente.cargar();
      fixture.detectChanges();
    });

    it('cada escuela tiene el campo de color con etiqueta y la alternativa de texto #RRGGBB', () => {
      const f = fila(4);
      expect(selector(f)).not.toBeNull();
      expect(campoTexto(f)).not.toBeNull();
      const etiqueta = f.querySelector(`label[for="${campoTexto(f).id}"]`);
      expect(etiqueta?.textContent).toContain('#RRGGBB');
      expect(selector(f).getAttribute('aria-label')).toContain('Escuela Ficticia Verde');
      expect(campoTexto(f).value).toBe('#3E9B4F');
      expect(selector(f).value).toBe('#3e9b4f');
    });

    it('la vista previa tiene la etiqueta visible «Vista previa» y la región la referencia con aria-labelledby', () => {
      const f = fila(4);
      const region = f.querySelector('figure.vista-color') as HTMLElement;
      const id = region.getAttribute('aria-labelledby');
      expect(id).toBeTruthy();
      const etiqueta = f.querySelector('#' + id) as HTMLElement;
      expect(etiqueta.textContent?.trim()).toBe('Vista previa');
      expect(etiqueta.classList).toContain('etiqueta-color');
      expect(etiqueta.tagName).not.toBe('LABEL');
      expect(etiqueta.classList).not.toContain('visually-hidden');
    });

    it('la vista previa usa el color y el texto calculado, con las razones de contraste', () => {
      const f = fila(4);
      expect(cabecera(f).style.backgroundColor).toBe('rgb(62, 155, 79)');
      expect(cabecera(f).style.color).toBe('rgb(26, 34, 48)'); // tinta: el blanco no llega en este verde
      expect(cabecera(f).textContent).toContain('Escuela Ficticia Verde');
      expect(f.textContent).toContain('4,56:1');
      expect(f.textContent).toContain('Texto oscuro');
    });

    it('sin color guardado, la vista previa usa #1A2230 y lo dice', () => {
      const f = fila(3);
      expect(campoTexto(f).value).toBe('');
      expect(cabecera(f).style.backgroundColor).toBe('rgb(26, 34, 48)');
      expect(cabecera(f).style.color).toBe('rgb(255, 255, 255)');
      expect(f.textContent).toContain('Sin color');
      expect(f.textContent).toContain('#1A2230');
      expect(boton(f, 'Quitar color')).toBeUndefined();
    });

    it('escribir un color cambia la vista previa al momento, sin guardar', () => {
      const f = fila(3);
      escribir(f, '#1b2b2e');
      expect(cabecera(f).style.backgroundColor).toBe('rgb(27, 43, 46)');
      expect(cabecera(f).style.color).toBe('rgb(255, 255, 255)');
      expect(f.textContent).toContain('14,66:1');
      expect(servicio.guardarColor).not.toHaveBeenCalled();
    });

    it('el selector nativo también cambia el texto', () => {
      const f = fila(3);
      selector(f).value = '#1b2b2e';
      selector(f).dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(campoTexto(f).value).toBe('#1B2B2E');
    });

    it('«Guardar color» hace una sola petición con el color y deja la fila con el guardado', () => {
      const f = fila(3);
      escribir(f, '#1b2b2e');
      boton(f, 'Guardar color')!.click();
      fixture.detectChanges();
      expect(servicio.guardarColor).toHaveBeenCalledOnceWith(3, '#1B2B2E');
      expect(componente.filas.find((x) => x.escuela.id === 3)!.escuela.colorPrimario).toBe('#1B2B2E');
      expect(boton(fila(3), 'Quitar color')).toBeDefined();
      expect(boton(fila(3), 'Guardar color')!.disabled).toBeTrue(); // ya guardado: nada que guardar
    });

    it('mientras guarda, los botones del color están desactivados y un segundo clic no repite', () => {
      const pendiente = new Subject<EscuelaLogo>();
      servicio.guardarColor.and.returnValue(pendiente);
      const f = fila(3);
      escribir(f, '#1B2B2E');
      boton(f, 'Guardar color')!.click();
      boton(f, 'Guardar color')!.click();
      fixture.detectChanges();
      expect(servicio.guardarColor).toHaveBeenCalledTimes(1);
      expect(boton(fila(3), 'Guardar color')!.disabled).toBeTrue();
      pendiente.next(respuesta(sin, '#1B2B2E'));
      pendiente.complete();
      fixture.detectChanges();
      expect(f.textContent).not.toContain('Guardando');
    });

    it('«Quitar color» envía null y vuelve al de por defecto', () => {
      const f = fila(4);
      boton(f, 'Quitar color')!.click();
      fixture.detectChanges();
      expect(servicio.guardarColor).toHaveBeenCalledOnceWith(4, null);
      expect(campoTexto(fila(4)).value).toBe('');
      expect(cabecera(fila(4)).style.backgroundColor).toBe('rgb(26, 34, 48)');
    });

    it('un formato inválido no se envía: error en la fila y el campo marcado', () => {
      const f = fila(3);
      escribir(f, '#12345');
      boton(f, 'Guardar color')!.click();
      fixture.detectChanges();
      expect(servicio.guardarColor).not.toHaveBeenCalled();
      const error = f.querySelector('.error-color[role=alert]');
      expect(error?.textContent).toContain('#RRGGBB');
      expect(campoTexto(f).getAttribute('aria-invalid')).toBe('true');
      expect(campoTexto(f).getAttribute('aria-describedby')).toContain(error!.id);
    });

    it('el error del servidor sale en la fila del color, sin tocar el logo', () => {
      servicio.guardarColor.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'El color debe tener el formato #RRGGBB.' } }))
      );
      const f = fila(4);
      escribir(f, '#1B2B2E');
      boton(f, 'Guardar color')!.click();
      fixture.detectChanges();
      expect(f.querySelector('.error-color')!.textContent).toContain('El color debe tener el formato #RRGGBB.');
      expect(f.querySelector('.error-fila')).toBeNull();
      expect(componente.filas.find((x) => x.escuela.id === 4)!.escuela.colorPrimario).toBe('#3E9B4F');
    });

    it('un gris medio avisa de que la cabecera usará el color oscurecido', () => {
      const f = fila(3);
      escribir(f, '#808080');
      expect(cabecera(f).style.backgroundColor).toBe('rgb(116, 116, 116)');
      expect(f.textContent).toContain('#747474');
    });
  });
});
