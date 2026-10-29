/**
 * Lo que daban las pantallas de gestión anteriores al rediseño, grabado con ellas justo antes de retirarlas (8b-Z,
 * decisión 332). Las pruebas de paridad del panel nuevo comparaban con la pantalla de hoy en vivo (dinero, permisos y
 * cuerpos de las peticiones); desde 8b-Z comparan con los valores que daba, en el mismo orden en que los pedían.
 *
 * Cada prueba lee sus valores con `valorDeHoy(GRABADOS)`: el siguiente valor grabado para el nombre completo de la
 * prueba en curso. Si falta o sobra alguno, la prueba falla (el orden de las comparaciones es parte del contrato).
 */
export type ValoresDeHoy = Readonly<Record<string, readonly unknown[]>>;

/** `undefined` no cabe en JSON: así se graba. */
export const SIN_VALOR = { sinValor: true } as const;

let pruebaActual = '';
let indice = 0;
let usados: ValoresDeHoy | null = null;

jasmine.getEnv().addReporter({
  specStarted: (resultado) => {
    pruebaActual = resultado.fullName;
    indice = 0;
    usados = null;
  },
});

// Una prueba que leyó valores grabados tiene que usarlos todos: si sobra alguno, una comparación se perdió.
afterEach(() => {
  if (usados && !todosLosValoresDeHoyUsados(usados)) {
    fail(`«${pruebaActual}» usó ${indice} de ${usados[pruebaActual]?.length ?? 0} valores grabados de la pantalla anterior`);
  }
});

/** El siguiente valor grabado de la pantalla anterior para la prueba en curso (una copia: se puede mutar). */
export function valorDeHoy<T = any>(grabados: ValoresDeHoy): T {
  usados = grabados;
  const valores = grabados[pruebaActual];
  if (!valores) {
    throw new Error(`Sin valores grabados de la pantalla anterior para «${pruebaActual}»`);
  }
  if (indice >= valores.length) {
    throw new Error(`«${pruebaActual}» pide el valor ${indice + 1} y solo hay ${valores.length} grabados`);
  }
  const valor = valores[indice++];
  return (isSinValor(valor) ? undefined : structuredClone(valor)) as T;
}

/** Comprueba que la prueba en curso usó todos sus valores grabados (llamar al final de la prueba). */
export function todosLosValoresDeHoyUsados(grabados: ValoresDeHoy): boolean {
  return (grabados[pruebaActual]?.length ?? 0) === indice;
}

function isSinValor(valor: unknown): boolean {
  return typeof valor === 'object' && valor !== null && (valor as { sinValor?: unknown }).sinValor === true;
}
