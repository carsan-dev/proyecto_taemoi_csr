import { HttpEvent, HttpEventType, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';

import { MOTIVO_LECTURA } from '../../utils/subida-archivos.util';
import { ColaSubidas } from './cola-subidas';
import { MaterialExamenApiService } from './material-examen-api.service';
import { DetalleBloque, SubidaMaterial } from './material-examen.models';

const MB = 1024 * 1024;

function fichero(nombre: string, bytes = 1000): File {
  const archivo = new File(['x'], nombre, { type: 'video/mp4' });
  Object.defineProperty(archivo, 'size', { value: bytes });
  return archivo;
}

const DETALLE: DetalleBloque = { disciplina: 'T', id: 'b', nombre: 'B', grados: [], temario: null, videos: [], documentos: [] };

interface Envio {
  archivo: File;
  salida: Subject<HttpEvent<SubidaMaterial>>;
  sustituye?: string;
  cancelado: boolean;
  terminado: boolean;
}

describe('ColaSubidas', () => {
  let cola: ColaSubidas;
  let envios: Envio[];
  let terminadas: { nombre: string; detalle: DetalleBloque }[];

  const nombres = (): string[] => cola.items().map((i) => i.nombre);

  const terminar = (i: number, cuerpo: unknown): void => {
    envios[i].terminado = true;
    envios[i].salida.next(new HttpResponse({ status: 200, body: cuerpo as SubidaMaterial }));
    envios[i].salida.complete();
  };

  const fallar = (i: number, error: unknown): void => {
    envios[i].terminado = true;
    envios[i].salida.error(error);
  };

  beforeEach(() => {
    envios = [];
    terminadas = [];
    const crear = (archivo: File, sustituye?: string): Observable<HttpEvent<SubidaMaterial>> => {
      const envio: Envio = { archivo, salida: new Subject(), sustituye, cancelado: false, terminado: false };
      envios.push(envio);
      return new Observable((suscriptor) => {
        const s = envio.salida.subscribe(suscriptor);
        return () => {
          envio.cancelado = !envio.terminado;
          s.unsubscribe();
        };
      });
    };

    TestBed.configureTestingModule({
      providers: [
        ColaSubidas,
        {
          provide: MaterialExamenApiService,
          useValue: {
            subirVideo: (_d: string, _b: string, a: File) => crear(a),
            sustituirVideo: (_d: string, _b: string, f: string, a: File) => crear(a, f),
          },
        },
      ],
    });
    cola = TestBed.inject(ColaSubidas);
    cola.iniciar('T', 'b');
    cola.terminada.subscribe((t) => terminadas.push({ nombre: t.nombre, detalle: t.detalle }));
  });

  it('sube de una en una, en orden', async () => {
    await cola.agregar([fichero('a.mp4'), fichero('b.mp4')]);
    expect(envios.length).toBe(1);
    expect(cola.items().map((i) => i.estado)).toEqual(['subiendo', 'espera']);

    terminar(0, { fichero: 'a.mp4', bloque: DETALLE });
    expect(terminadas.map((t) => t.nombre)).toEqual(['a.mp4']);
    expect(terminadas[0].detalle).toBe(DETALLE);
    expect(envios.length).toBe(2);
    expect(nombres()).toEqual(['b.mp4']);
  });

  it('cada subida terminada dice el sello con el que empezó, no el de su final', async () => {
    let sello = 7;
    cola.iniciar('T', 'b', () => sello);
    const inicios: number[] = [];
    cola.terminada.subscribe((t) => inicios.push(t.inicio));

    await cola.agregar([fichero('a.mp4'), fichero('b.mp4')]);
    sello = 8;
    terminar(0, { fichero: 'a.mp4', bloque: DETALLE });
    sello = 9;
    terminar(1, { fichero: 'b.mp4', bloque: DETALLE });
    expect(inicios).toEqual([7, 8]);
  });

  it('prevalida extensión y tamaño: no envía nada y deja la fila solo con Quitar', async () => {
    await cola.agregar([fichero('Examen_completo.mov', 612 * MB)]);
    expect(envios.length).toBe(0);
    const [item] = cola.items();
    expect(item.estado).toBe('fallo');
    expect(item.motivo).toBe('Pesa 612 MB. El máximo es 500 MB por vídeo.');
    expect(item.reintentable).toBeFalse();
  });

  it('un rechazado no para al siguiente', async () => {
    await cola.agregar([fichero('a.avi'), fichero('b.mp4')]);
    expect(envios.map((e) => e.archivo.name)).toEqual(['b.mp4']);
  });

  it('no lee el vídeo entero: solo prueba un byte; si no se puede leer, hay que volver a elegirlo', async () => {
    const roto = fichero('a.mp4');
    const arrayBuffer = spyOn(roto, 'arrayBuffer').and.rejectWith(new Error('entero'));
    const slice = spyOn(roto, 'slice').and.returnValue({ arrayBuffer: () => Promise.reject(new Error('NotReadable')) } as unknown as Blob);
    await cola.agregar([roto]);
    expect(slice).toHaveBeenCalledOnceWith(0, 1);
    expect(arrayBuffer).not.toHaveBeenCalled();
    expect(envios.length).toBe(0);
    expect(cola.items()[0].motivo).toBe(MOTIVO_LECTURA);
  });

  it('el File se envía tal cual, sin copiarlo a memoria', async () => {
    const original = fichero('a.mp4');
    await cola.agregar([original]);
    expect(envios[0].archivo).toBe(original);
  });

  it('informa del progreso', async () => {
    await cola.agregar([fichero('a.mp4', 142 * MB)]);
    envios[0].salida.next({ type: HttpEventType.UploadProgress, loaded: 88 * MB, total: 142 * MB });
    expect(cola.items()[0].cargados).toBe(88 * MB);
    expect(cola.items()[0].total).toBe(142 * MB);
  });

  it('tras un fallo sigue con el siguiente y deja Reintentar', async () => {
    await cola.agregar([fichero('a.mp4'), fichero('b.mp4')]);
    envios[0].salida.next({ type: HttpEventType.UploadProgress, loaded: 88 * MB, total: 142 * MB });
    fallar(0, new HttpErrorResponse({ status: 0 }));

    expect(cola.items()[0].estado).toBe('fallo');
    expect(cola.items()[0].motivo).toBe('Se cortó la conexión a los 88 MB. Vuelve a subirlo.');
    expect(cola.items()[0].reintentable).toBeTrue();
    expect(cola.items()[1].estado).toBe('subiendo');
    expect(envios.length).toBe(2);
  });

  it('el mensaje de la API llega a la fila', async () => {
    await cola.agregar([fichero('a.mp4')]);
    fallar(0, new HttpErrorResponse({ status: 415, error: { mensaje: 'El fichero no es un vídeo válido.' } }));
    expect(cola.items()[0].motivo).toBe('El fichero no es un vídeo válido.');
    expect(cola.items()[0].reintentable).toBeFalse();
  });

  it('reintentar vuelve a enviar el mismo fichero', async () => {
    const a = fichero('a.mp4');
    await cola.agregar([a]);
    fallar(0, new HttpErrorResponse({ status: 0 }));
    cola.reintentar(cola.items()[0].clave);
    expect(envios.length).toBe(2);
    expect(envios[1].archivo).toBe(a);
    expect(cola.items()[0].estado).toBe('subiendo');
    expect(cola.items()[0].motivo).toBeNull();
    expect(cola.items()[0].cargados).toBe(0);
  });

  it('cancelar la activa corta la petición, la quita y sigue', async () => {
    await cola.agregar([fichero('a.mp4'), fichero('b.mp4')]);
    cola.cancelar(cola.items()[0].clave);
    expect(envios[0].cancelado).toBeTrue();
    expect(nombres()).toEqual(['b.mp4']);
    expect(envios.length).toBe(2);
  });

  it('cancelar una en espera solo la quita', async () => {
    await cola.agregar([fichero('a.mp4'), fichero('b.mp4')]);
    cola.cancelar(cola.items()[1].clave);
    expect(envios.length).toBe(1);
    expect(nombres()).toEqual(['a.mp4']);
  });

  it('quitar un fallo lo saca de la lista', async () => {
    await cola.agregar([fichero('a.avi')]);
    cola.cancelar(cola.items()[0].clave);
    expect(cola.items().length).toBe(0);
  });

  it('sustituir usa el PUT y devuelve el detalle', async () => {
    await cola.agregar([fichero('b.mp4')], 'viejo.mp4');
    expect(envios[0].sustituye).toBe('viejo.mp4');
    expect(cola.items()[0].sustituye).toBe('viejo.mp4');
    terminar(0, DETALLE);
    expect(terminadas[0].detalle).toBe(DETALLE);
  });

  it('enCurso: con algo subiendo o en espera, no con solo fallos', async () => {
    expect(cola.enCurso()).toBeFalse();
    await cola.agregar([fichero('a.mp4')]);
    expect(cola.enCurso()).toBeTrue();
    fallar(0, new HttpErrorResponse({ status: 0 }));
    expect(cola.enCurso()).toBeFalse();
  });

  describe('mientras se prepara', () => {
    /** Un fichero cuya lectura previa no acaba hasta que se llame a `soltar`. */
    function lento(nombre: string): { archivo: File; soltar: () => void } {
      const archivo = fichero(nombre);
      let soltar = (): void => undefined;
      const lectura = new Promise<ArrayBuffer>((r) => (soltar = () => r(new ArrayBuffer(1))));
      spyOn(archivo, 'slice').and.returnValue({ arrayBuffer: () => lectura } as unknown as Blob);
      return { archivo, soltar: () => soltar() };
    }

    it('el fichero ya cuenta como pendiente desde la selección, sin enviarse', async () => {
      const { archivo, soltar } = lento('a.mp4');
      const hecho = cola.agregar([archivo]);
      expect(cola.enCurso()).toBeTrue();
      expect(nombres()).toEqual(['a.mp4']);
      expect(envios.length).toBe(0);

      soltar();
      await hecho;
      expect(envios.length).toBe(1);
      expect(cola.items()[0].estado).toBe('subiendo');
    });

    it('toda la selección figura a la vez, aunque el primero aún se esté leyendo', async () => {
      const { archivo, soltar } = lento('a.mp4');
      const hecho = cola.agregar([archivo, fichero('b.mp4')]);
      expect(nombres()).toEqual(['a.mp4', 'b.mp4']);
      soltar();
      await hecho;
      expect(cola.items().map((i) => i.estado)).toEqual(['subiendo', 'espera']);
    });

    it('cancelarlo evita que se envíe', async () => {
      const { archivo, soltar } = lento('a.mp4');
      const hecho = cola.agregar([archivo]);
      cola.cancelar(cola.items()[0].clave);
      expect(cola.enCurso()).toBeFalse();
      soltar();
      await hecho;
      expect(envios.length).toBe(0);
      expect(nombres()).toEqual([]);
    });

    it('si no se puede leer, pasa a fallo y deja de contar como pendiente', async () => {
      const roto = fichero('a.mp4');
      spyOn(roto, 'slice').and.returnValue({ arrayBuffer: () => Promise.reject(new Error('NotReadable')) } as unknown as Blob);
      const hecho = cola.agregar([roto]);
      expect(cola.enCurso()).toBeTrue();
      await hecho;
      expect(cola.enCurso()).toBeFalse();
      expect(cola.items()[0].motivo).toBe(MOTIVO_LECTURA);
    });

    it('al destruirse no envía lo que estaba preparando', async () => {
      const { archivo, soltar } = lento('a.mp4');
      const hecho = cola.agregar([archivo]);
      TestBed.resetTestingModule();
      soltar();
      await hecho;
      expect(envios.length).toBe(0);
    });
  });

  it('al destruirse corta la subida en curso', async () => {
    await cola.agregar([fichero('a.mp4')]);
    TestBed.resetTestingModule();
    expect(envios[0].cancelado).toBeTrue();
  });
});
