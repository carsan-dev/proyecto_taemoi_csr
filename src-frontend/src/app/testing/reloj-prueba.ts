import { fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';

/** Tope de tiempo simulado: más que cualquier espera de un spec. */
const TOPE_MS = 60_000;

/** Los temporizadores simulados pendientes (la zona de `fakeAsync` los expone; Angular no). */
function temporizadoresPendientes(): number {
  const zona = (globalThis as { Zone?: { current: { get(clave: string): unknown } } }).Zone;
  const spec = zona?.current.get('FakeAsyncTestZoneSpec') as { pendingTimers?: unknown[]; pendingPeriodicTimers?: unknown[] } | undefined;
  return (spec?.pendingTimers?.length ?? 0) + (spec?.pendingPeriodicTimers?.length ?? 0);
}

/**
 * Solo para los specs: corre un cuerpo asíncrono (`async () => { … await … }`) con el reloj simulado de `fakeAsync`. Cada
 * vuelta avanza 1 ms hasta que el cuerpo termina, así que sus `setTimeout` (los de esperar a que la pantalla se asiente y
 * los de los componentes) llegan en el mismo orden que con el reloj real, pero sin depender de él.
 *
 * Para Jasmine la prueba es síncrona: no le corre el timeout de 5 s. Con tiempo real, un parón del navegador en CI hacía
 * saltar ese timeout a media prueba y la dejaba viva, y sus `await` pendientes rompían las pruebas siguientes (el montaje
 * compartido, el TestBed y los espías). Aquí, si el cuerpo falla o no termina, la prueba falla y no queda nada vivo.
 *
 * Lo que el navegador hace fuera de la zona (leer un `File`, un `MutationObserver`, el `close` de un `<dialog>`) no llega
 * dentro de la prueba: el spec lo simula o la prueba se queda con `it` y tiempo real.
 */
export function conRelojSimulado(cuerpo: () => Promise<unknown>): () => void {
  return fakeAsync(() => {
    let terminado = false;
    let fallo: { error: unknown } | null = null;
    cuerpo().then(
      () => (terminado = true),
      (error) => (fallo = { error })
    );
    flushMicrotasks();
    for (let ms = 0; !terminado && !fallo; ms++) {
      if (ms >= TOPE_MS || temporizadoresPendientes() === 0) {
        throw new Error(
          `La prueba no terminó (${ms} ms simulados, ${temporizadoresPendientes()} temporizadores pendientes): ` +
            'espera algo que no es un temporizador.'
        );
      }
      tick(1);
    }
    if (fallo) {
      throw (fallo as { error: unknown }).error;
    }
  });
}

/**
 * Con el reloj simulado, leer un `File` (`arrayBuffer`, `FileReader`) lo hace el navegador fuera de la zona y no llega
 * dentro de la prueba. Esto lo sirve desde la zona con los bytes que el spec dio a cada fichero ficticio, por su nombre;
 * un fichero que no esté aquí se lee de verdad (y la prueba, que no termina, lo dice). Son espías: van en la prueba o en
 * su `beforeEach`.
 */
export function leerFicherosEnLaZona(contenidos: Record<string, string | Uint8Array>): void {
  const bytesDe = (blob: Blob): Uint8Array | undefined => {
    const contenido = blob instanceof File ? contenidos[blob.name] : undefined;
    return typeof contenido === 'string' ? new TextEncoder().encode(contenido) : contenido?.slice();
  };
  const arrayBuffer = Blob.prototype.arrayBuffer;
  spyOn(Blob.prototype, 'arrayBuffer').and.callFake(function (this: Blob) {
    const bytes = bytesDe(this);
    return bytes ? Promise.resolve(bytes.buffer as ArrayBuffer) : arrayBuffer.call(this);
  });
  const readAsDataURL = FileReader.prototype.readAsDataURL;
  spyOn(FileReader.prototype, 'readAsDataURL').and.callFake(function (this: FileReader, blob: Blob) {
    const bytes = bytesDe(blob);
    if (!bytes) {
      return readAsDataURL.call(this, blob);
    }
    const url = `data:${blob.type};base64,${btoa(String.fromCharCode(...bytes))}`;
    setTimeout(() => this.onload?.call(this, { target: { result: url } } as unknown as ProgressEvent<FileReader>));
  });
}

/** `it` con su cuerpo asíncrono en reloj simulado (ver `conRelojSimulado`). */
export function itSinReloj(nombre: string, cuerpo: () => Promise<unknown>): void {
  it(nombre, conRelojSimulado(cuerpo));
}
