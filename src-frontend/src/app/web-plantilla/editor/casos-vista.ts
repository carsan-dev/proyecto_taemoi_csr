import { algunDiaConPlaza, errorFecha, inicioTemporada, salidasParaEdad } from '../comun/preinscripcion/preinscripcion';
import type { DatosPreinscripcion, RespuestaPreinscripcion } from '../comun/preinscripcion/preinscripcion';
import { Salida, salidasDeSede } from '../comun/horario';
import type { EventoDetalleWeb, EventosWeb, GrupoWeb, Plazas, SedeWeb, SitioWeb } from '../datos/modelos';
import type { CasoVista, PasoPreinscripcion } from './puente-editor';

/**
 * «Ver el caso» (W12c-bis, d528; `CASOS` del boceto `boceto-panel-contenido-web-comarcal.html`): cada caso poco frecuente
 * se simula cambiando en memoria los datos que ya tiene la vista previa de gestión, para que el tema pinte los textos
 * que solo salen en él. Vive en el chunk de la capa de edición; nunca llama a la API ni cambia lo guardado.
 *
 * W12c-ter (d533): el paso de la preinscripción (selector «Paso») y sus tres casos, y «Sin clases esta semana» de una
 * sede. La preinscripción no se simula cambiando el sitio: el tema recibe un ejemplo (paso, lo elegido, una fecha de
 * nacimiento y, en el paso 4, una respuesta simulada) sacado de los datos de la escuela, y lo pinta sin dirección,
 * sin almacenamiento y sin envío.
 */
export interface SimuladorCaso {
  sitio(sitio: SitioWeb): SitioWeb;
  eventos(eventos: EventosWeb): EventosWeb;
  evento(evento: EventoDetalleWeb): EventoDetalleWeb;
  /** El ejemplo de la preinscripción que se ve; null, lo de la dirección (los datos reales). */
  preinscripcion(sitio: SitioWeb, ahora: Date): EjemploPreinscripcion | null;
  /** Lo que pinta la página de una sede con el caso que se ve («Sin clases esta semana»). */
  vistaSede<T extends VistaConProxima>(vista: T): T;
}

/** Lo justo de la vista de la página de una sede (`VistaPaginaSede` del tema) para «Sin clases esta semana». */
export interface VistaConProxima {
  horarios: { proxima: unknown };
}

/**
 * El paso de la preinscripción que enseña el editor, con datos de ejemplo en memoria. `consulta` sustituye a los
 * parámetros de la dirección (`sede`, `grupo`, `dias`); `datos`, a lo escrito; `respuesta`, a la del envío.
 */
export interface EjemploPreinscripcion {
  paso: PasoPreinscripcion;
  consulta: Partial<Record<'sede' | 'grupo' | 'dias', string>>;
  datos: Partial<Pick<DatosPreinscripcion, 'dia' | 'mes' | 'anio' | 'telefono'>>;
  respuesta: (Pick<RespuestaPreinscripcion, 'referencia' | 'tokenDocumento' | 'plazaAsignada' | 'posicionListaEspera'>) | null;
}

/** La referencia del boceto (`preinscripcionHtml`, paso 4). */
export const REFERENCIA_DE_EJEMPLO = 'TSS-2026-0142';
/** La posición en la lista de espera del boceto («En la lista de espera»). */
const POSICION_DE_EJEMPLO = 3;
/** La edad del boceto en «Sin grupo para su edad». */
const EDAD_SIN_GRUPO = 15;
/** Un grupo sin edades: cualquiera vale; la de un adulto. */
const EDAD_SIN_RANGO = 18;

const igual = <T>(x: T) => x;

/** Todos los grupos (y sus turnos) con el mismo estado de plazas: «Grupo lleno» o «Lista de espera». */
function conPlazas(sitio: SitioWeb, estado: Plazas['estado']): SitioWeb {
  const plazas: Plazas = { estado, quedan: null };
  const grupo = (g: GrupoWeb): GrupoWeb => ({
    ...g,
    plazas: g.plazas ? plazas : null,
    turnos: g.turnos.map((t) => ({ ...t, plazas })),
  });
  return { ...sitio, sedes: sitio.sedes.map((s) => ({ ...s, grupos: s.grupos.map(grupo) })) };
}

function simulador(parcial: Partial<SimuladorCaso>): SimuladorCaso {
  return { sitio: igual, eventos: igual, evento: igual, preinscripcion: () => null, vistaSede: igual, ...parcial };
}

export function simuladorDe(caso: CasoVista): SimuladorCaso {
  switch (caso) {
    case 'lleno':
      return simulador({ sitio: (s) => conPlazas(s, 'LLENO') });
    case 'espera':
      // En la preinscripción es «Grupo completo»: sin plaza, la solicitud va a la lista de espera.
      return simulador({ sitio: (s) => conPlazas(s, 'LISTA_ESPERA') });
    case 'sin-tarifas':
      // «Aún no hay mensualidad en Tesorería»: sin tarifas publicadas.
      return simulador({ sitio: (s) => ({ ...s, tarifas: [], tarifasSede: [] }) });
    case 'sin-proximos':
      return simulador({ eventos: (e) => ({ ...e, proximos: [] }) });
    case 'sin-eventos':
      return simulador({ eventos: () => ({ proximos: [], anteriores: [] }) });
    case 'un-evento':
      return simulador({ eventos: (e) => ({ ...e, proximos: e.proximos.slice(0, 1) }) });
    case 'sin-anteriores':
      return simulador({ eventos: (e) => ({ ...e, anteriores: [] }) });
    case 'una-foto':
      return simulador({ evento: (e) => ({ ...e, fotos: e.fotos.slice(0, 1) }) });
    case 'sin-clases':
      // «Vacaciones o festivos: no hay próxima clase»: el tablero dice «Sin clases esta semana» y las salidas siguen.
      return simulador({ vistaSede: <T extends VistaConProxima>(v: T): T => ({ ...v, horarios: { ...v.horarios, proxima: null } }) });
    case 'sin-grupo':
    case 'fin-espera':
      // Solo cambian el ejemplo de la preinscripción (`simuladorDeLaVista`).
      return simulador({});
  }
}

/**
 * Lo que simula la capa con el caso y el paso que manda el panel: null sin ninguno de los dos (los datos reales). El
 * paso solo cuenta en la preinscripción; los casos de la preinscripción cambian su ejemplo en su paso.
 */
export function simuladorDeLaVista(caso: CasoVista | null, paso: PasoPreinscripcion | null): SimuladorCaso | null {
  if (caso === null && paso === null) {
    return null;
  }
  const base = caso ? simuladorDe(caso) : simulador({});
  return paso === null ? base : { ...base, preinscripcion: (sitio, ahora) => ejemploPreinscripcion(sitio, ahora, paso, caso) };
}

/**
 * La parte de la página donde se ve cada caso (`PARTE_CASO` del boceto), para enseñarla al elegirlo: la primera que
 * exista en la página. En la preinscripción, la del flujo (el `|| 'pr-flujo'` del boceto).
 */
export const PARTE_DEL_CASO: Readonly<Record<CasoVista, readonly string[]>> = {
  lleno: ['horarios', 'sp-horarios'],
  espera: ['horarios', 'sp-horarios', 'pr-flujo'],
  'sin-tarifas': ['tp-banda'],
  'sin-proximos': ['ep-proximos'],
  'sin-eventos': ['ep-proximos'],
  'un-evento': ['ep-proximos'],
  'sin-anteriores': ['ep-anteriores'],
  'una-foto': ['ev-galeria'],
  'sin-clases': ['sp-horarios'],
  'sin-grupo': ['pr-flujo'],
  'fin-espera': ['pr-flujo'],
};

/**
 * Notas del editor que acompañan a un caso (boceto: `tarifasPagHtml` y `eventosPagHtml`, `ed-nota-parte`): van tras la
 * parte que se ve en el caso y solo mientras se ve.
 */
export const NOTA_DEL_CASO: Readonly<Partial<Record<CasoVista, { tras: string; texto: string }>>> = {
  'sin-tarifas': {
    tras: '.p-tarifas > #tarifas',
    texto: 'Sin tarifas publicadas no salen el cuadro, «Por sede» ni «Licencia y exámenes».',
  },
  'sin-eventos': { tras: '.p-eventos > #proximos', texto: 'Sin ningún evento tampoco sale «Anteriores».' },
};

// ------------------------------------------------------------------ ejemplo de la preinscripción

/**
 * El ejemplo de cada paso con los datos de la escuela (el boceto los inventa): la primera sede con grupos; en el paso 3
 * y el 4, su primer grupo (por hora) con plaza o, sin ninguno, el primero, con todos sus días y una fecha de nacimiento
 * de su edad; en «Sin grupo para su edad», una edad sin grupo en esa sede. Sin sede con grupos, el paso 1.
 */
function ejemploPreinscripcion(sitio: SitioWeb, ahora: Date, paso: PasoPreinscripcion, caso: CasoVista | null): EjemploPreinscripcion {
  const vacio: EjemploPreinscripcion = { paso: 1, consulta: {}, datos: {}, respuesta: null };
  const sede = sitio.sedes.find((s) => salidasDeSede(s).length > 0);
  if (paso === 1 || !sede) {
    return vacio;
  }
  const inicio = inicioTemporada(sitio.temporada) ?? ahora.getFullYear();
  const consulta = { sede: sede.slug ?? String(sede.id) };
  if (paso === 2) {
    const edad = caso === 'sin-grupo' ? edadSinGrupo(sede) : null;
    return { paso, consulta, datos: edad === null ? {} : fechaDeEdad(edad, inicio, ahora), respuesta: null };
  }
  const salidas = salidasDeSede(sede);
  const salida: Salida = salidas.find(algunDiaConPlaza) ?? salidas[0];
  const grupo = salida.grupo;
  const datos = fechaDeEdad(grupo.rangoEdadMin ?? grupo.rangoEdadMax ?? EDAD_SIN_RANGO, inicio, ahora);
  const elegido = { ...consulta, grupo: String(grupo.id), dias: salida.dias.join(',') };
  if (paso === 3) {
    return { paso, consulta: elegido, datos, respuesta: null };
  }
  const espera = caso === 'fin-espera';
  return {
    paso,
    consulta: elegido,
    // «Si falta algo, te llaman al {x}»: el boceto pone el teléfono de la sede.
    datos: { ...datos, telefono: sede.telefono?.trim() ?? '' },
    respuesta: {
      referencia: REFERENCIA_DE_EJEMPLO,
      tokenDocumento: '',
      plazaAsignada: !espera,
      posicionListaEspera: espera ? POSICION_DE_EJEMPLO : null,
    },
  };
}

/** 15 años, como el boceto; si la sede tiene grupo de 15, la edad justo por debajo de su menor o por encima de su mayor. */
function edadSinGrupo(sede: SedeWeb): number | null {
  const minimo = Math.min(...sede.grupos.map((g) => g.rangoEdadMin ?? 0));
  const maximo = Math.max(...sede.grupos.map((g) => g.rangoEdadMax ?? 99));
  return [EDAD_SIN_GRUPO, minimo - 1, maximo + 1].find((e) => e >= 0 && e <= 99 && salidasParaEdad(sede, e).length === 0) ?? null;
}

/** El 1 de enero del año en que cumple esa edad en la temporada (la edad de cohorte del tema); sin fecha si no vale. */
function fechaDeEdad(edad: number, inicio: number, ahora: Date): EjemploPreinscripcion['datos'] {
  const fecha = { dia: '1', mes: '1', anio: String(inicio - edad) };
  return errorFecha(fecha, ahora) ? {} : fecha;
}
