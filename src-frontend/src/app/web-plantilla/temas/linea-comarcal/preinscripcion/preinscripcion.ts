import {
  DIAS_SEMANA,
  EstadoPlazas,
  Salida,
  diasEnTexto,
  edadesEnTexto,
  estadoPlazas,
  importeEnTexto,
  nombreDia,
  proximaClase,
  salidasDeSede,
  telefonoEnlace,
  telefonoVisible,
  textoPlazas,
} from '../../../comun/horario';
import {
  algunDiaConPlaza,
  diasElegibles,
  encajaEdad,
  plazasLibres,
  salidasParaEdad,
  sinPlaza,
  turnoDelDia,
  turnosElegidos,
} from '../../../comun/preinscripcion/preinscripcion';
import { SedeWeb, SitioWeb } from '../../../datos/modelos';
import { TextosTema, claveSede, tarifaPrincipal } from '../contenido';
import { TEXTOS_PREINSCRIPCION_LINEA_COMARCAL as T } from '../textos';

/**
 * Lo que pinta la preinscripción del tema «La línea comarcal» (boceto `fase4-web/boceto-linea-comarcal-preinscripcion.html`)
 * a partir de `GET /api/publico/web`: sedes del paso 1, filas de grupos del paso 2, resumen lateral y confirmación. Sin
 * Angular; la lógica del formulario es la común (`comun/preinscripcion`).
 */

/** Sustituye `{clave}` por su valor. */
export function rellenar(texto: string, valores: Record<string, string | number>): string {
  return texto.replace(/\{(\w+)\}/g, (todo, clave: string) => (clave in valores ? String(valores[clave]) : todo));
}

const mayuscula = (t: string): string => t.charAt(0).toUpperCase() + t.slice(1);

/** El valor de `?sede=` de una sede: su slug, o su id si no tiene. */
export function parametroSede(sede: SedeWeb): string {
  return sede.slug ?? String(sede.id);
}

/** «Lunes y miércoles, 17:00–17:45». */
export function textoHorario(salida: Salida, dias: readonly number[]): string {
  return `${mayuscula(diasEnTexto(dias))}, ${salida.inicio}–${salida.fin}`;
}

/** Si se queda con parte de los días, cuáles faltan («Solo ese día; el grupo también entrena el lunes.»). */
export function textoResto(salida: Salida, dias: readonly number[]): string {
  const resto = salida.dias.filter((d) => !dias.includes(d));
  if (!resto.length) {
    return '';
  }
  return rellenar(dias.length === 1 ? T.soloEseDia : T.soloEsosDias, { x: diasEnTexto(resto) });
}

/** «Peques, 4 a 6 años» (sin edades, el nombre). */
export function textoGrupo(salida: Salida): string {
  const edades = edadesEnTexto(salida.grupo.rangoEdadMin, salida.grupo.rangoEdadMax);
  return edades ? `${salida.grupo.nombre}, ${edades.toLowerCase()}` : salida.grupo.nombre;
}

/** «el lunes» o «los días lunes y miércoles». */
export function textoDiasLista(dias: readonly number[]): string {
  return `${dias.length === 1 ? 'el' : 'los días'} ${diasEnTexto(dias)}`;
}

// ---------------------------------------------------------------------------------------------------------------
// Paso 1
// ---------------------------------------------------------------------------------------------------------------

export interface OpcionSede {
  clave: string;
  parametro: string;
  nombre: string;
  dato: string;
  /** «próxima clase … a las» y la hora aparte (va en cifras tabulares). */
  proxima: { texto: string; hora: string } | null;
}

export function opcionesSede(sitio: SitioWeb, ahora: Date): OpcionSede[] {
  return sitio.sedes.map((sede) => {
    const p = proximaClase(sede, ahora);
    const grupos = sede.grupos.length === 1 ? T.unGrupo : rellenar(T.nGrupos, { n: sede.grupos.length });
    return {
      clave: claveSede(sede),
      parametro: parametroSede(sede),
      nombre: sede.nombre,
      dato: [sede.lugar?.trim(), grupos].filter(Boolean).join(' · '),
      proxima: p
        ? { texto: p.hoy ? T.proximaHoy : rellenar(T.proximaDia, { x: p.cuando.toLowerCase() }), hora: p.hora }
        : null,
    };
  });
}

// ---------------------------------------------------------------------------------------------------------------
// Paso 2
// ---------------------------------------------------------------------------------------------------------------

export interface FilaGrupo {
  salida: Salida;
  clave: string;
  hoy: boolean;
  grupo: string;
  edades: string;
  dias: { letra: string; si: boolean; hoy: boolean }[];
  diasTexto: string;
  plazas: EstadoPlazas | null;
  plazasTexto: string;
  libre: boolean;
  boton: string;
  etiqueta: string;
}

/** Clave de una salida (un grupo a una hora). */
export function claveSalida(salida: Salida): string {
  return `${salida.grupo.id}-${salida.inicio}-${salida.fin}`;
}

export function filasGrupo(sitio: SitioWeb, sede: SedeWeb, edad: number, ahora: Date): FilaGrupo[] {
  const textos = new TextosTema(sitio);
  const textosPlazas = { lleno: textos.t('horarios.lleno'), espera: textos.t('horarios.espera') };
  const hoy = ahora.getDay();
  return salidasParaEdad(sede, edad).map((salida) => {
    const plazas = estadoPlazas(salida.plazas);
    /** Plazas por día (§4.3): basta un día con plaza para elegir el grupo; la lista de espera, sin ninguno. */
    const libre = algunDiaConPlaza(salida);
    const edades = edadesEnTexto(salida.grupo.rangoEdadMin, salida.grupo.rangoEdadMax);
    const boton = libre ? T.elegirGrupo : T.apuntarmeEspera;
    return {
      salida,
      clave: claveSalida(salida),
      hoy: salida.dias.includes(hoy),
      grupo: salida.grupo.nombre,
      edades: [edades, salida.grupo.disciplinaNombre].filter(Boolean).join(' · '),
      dias: DIAS_SEMANA.map((d) => ({ letra: d.letra, si: salida.dias.includes(d.numero), hoy: d.numero === hoy })),
      diasTexto: diasEnTexto(salida.dias),
      plazas,
      plazasTexto: textoPlazas(salida.plazas, textosPlazas),
      libre,
      boton,
      etiqueta: `${boton}: ${textoGrupo(salida)}`,
    };
  });
}

export interface CasillaDia {
  numero: number;
  nombre: string;
  hoy: boolean;
  /** Las plazas de su turno (§4.3, por día), con las marcas y textos del tema. */
  plazas: EstadoPlazas | null;
  plazasTexto: string;
  /** Con algún día con plaza, los llenos no se eligen (`diasElegibles`). */
  elegible: boolean;
}

/** Las casillas de días de la salida elegida: «HOY» en el de hoy y las plazas de cada día. */
export function casillasDia(sitio: SitioWeb, salida: Salida, ahora: Date): CasillaDia[] {
  const textos = new TextosTema(sitio);
  const textosPlazas = { lleno: textos.t('horarios.lleno'), espera: textos.t('horarios.espera') };
  const elegibles = diasElegibles(salida);
  return salida.dias.map((d) => {
    const plazas = turnoDelDia(salida, d)?.plazas;
    return {
      numero: d,
      nombre: mayuscula(nombreDia(d)),
      hoy: d === ahora.getDay(),
      plazas: estadoPlazas(plazas),
      plazasTexto: textoPlazas(plazas, textosPlazas),
      elegible: elegibles.includes(d),
    };
  });
}

export interface VistaVacio {
  titulo: string;
  otras: { parametro: string; nombre: string; dato: string }[];
  texto: string | null;
  telefono: { texto: string; enlace: string; antes: string } | null;
}

/** Sin grupo de su edad en la sede: otras sedes que sí lo tienen, o por qué no hay ninguna; y el teléfono de la sede. */
export function vistaVacio(sitio: SitioWeb, sede: SedeWeb, edad: number): VistaVacio {
  const textos = new TextosTema(sitio);
  const textosPlazas = { lleno: textos.t('horarios.lleno'), espera: textos.t('horarios.espera') };
  const otras = sitio.sedes
    .filter((s) => s.id !== sede.id)
    .map((s) => ({ s, salida: salidasParaEdad(s, edad)[0] ?? null }))
    .filter((o): o is { s: SedeWeb; salida: Salida } => o.salida !== null)
    .map(({ s, salida }) => ({
      parametro: parametroSede(s),
      nombre: s.nombre,
      dato: [textoGrupo(salida), textoHorario(salida, salida.dias), textoPlazas(salida.plazas, textosPlazas)]
        .filter(Boolean)
        .join(' · '),
    }));
  const minimos = sitio.sedes.flatMap((s) => s.grupos.map((g) => g.rangoEdadMin ?? 0));
  const minimo = minimos.length ? Math.min(...minimos) : null;
  const telefono = sede.telefono?.trim();
  return {
    titulo: rellenar(T.vacioTitulo, { x: sede.nombre, n: edad }),
    otras,
    texto: otras.length
      ? null
      : minimo !== null && edad < minimo
        ? rellenar(T.vacioMinimo, { min: minimo, n: edad })
        : rellenar(T.vacioNinguna, { n: edad }),
    telefono: telefono
      ? { texto: telefonoVisible(telefono), enlace: telefonoEnlace(telefono), antes: rellenar(T.vacioLlamar, { x: sede.nombre }) }
      : null,
  };
}

/** «Este año cumple **N años**: hay N grupos de su edad en X.» (sin grupos, solo la edad). */
export function estadoEdad(sede: SedeWeb, edad: number, grupos: number): { anios: string; resto: string } {
  return {
    anios: rellenar(T.anios, { n: edad }),
    resto: grupos === 0 ? '.' : rellenar(grupos === 1 ? T.hayUnGrupo : T.hayNGrupos, { n: grupos, x: sede.nombre }),
  };
}

// ---------------------------------------------------------------------------------------------------------------
// Resumen lateral
// ---------------------------------------------------------------------------------------------------------------

export interface Eleccion {
  sede: SedeWeb | null;
  salida: Salida | null;
  dias: number[];
}

export function textoPlaza(sitio: SitioWeb, eleccion: Eleccion): string | null {
  if (!eleccion.salida) {
    return null;
  }
  const turnos = turnosElegidos(eleccion.salida, eleccion.dias);
  if (sinPlaza(turnos)) {
    return T.plazaEspera;
  }
  const libres = plazasLibres(turnos);
  const textos = new TextosTema(sitio);
  return libres === null
    ? null
    : textoPlazas({ estado: 'QUEDAN', quedan: libres }, { lleno: textos.t('horarios.lleno'), espera: textos.t('horarios.espera') });
}

/** «25 € al mes, igual en todas las sedes.»; null sin tarifa o si alguna sede tiene la suya. */
export function textoTarifa(sitio: SitioWeb, ahora: Date): string | null {
  const tarifa = tarifaPrincipal(sitio, ahora);
  if (!tarifa || !tarifa.igual) {
    return null;
  }
  return rellenar(sitio.sedes.length === 1 ? T.tarifaUna : T.tarifaIgual, { x: importeEnTexto(tarifa.importe) });
}

/** Si la salida elegida sigue siendo de esa edad (paso 3: si no, se avisa). */
export function avisoEdad(salida: Salida | null, edad: number | null): string | null {
  if (!salida || edad === null || encajaEdad(salida.grupo, edad)) {
    return null;
  }
  const edades = edadesEnTexto(salida.grupo.rangoEdadMin, salida.grupo.rangoEdadMax)?.toLowerCase() ?? '';
  return rellenar(T.avisoEdad, { n: edad, grupo: salida.grupo.nombre, edades });
}

/** El valor de `?dias=`: solo si hace falta (parte de los días, o una salida que no es la primera del grupo). */
export function parametroDias(sede: SedeWeb, salida: Salida, dias: readonly number[]): string | null {
  const primera = salidasDeSede(sede).find((s) => s.grupo.id === salida.grupo.id);
  const esPrimera = !!primera && primera.inicio === salida.inicio && primera.fin === salida.fin;
  return esPrimera && dias.length === salida.dias.length ? null : dias.join(',');
}

// ---------------------------------------------------------------------------------------------------------------
// Confirmación
// ---------------------------------------------------------------------------------------------------------------

export interface VistaFin {
  titulo: string;
  referencia: string;
  estado: string;
  pasos: { titulo: string; texto: string }[];
  ficha: { rotulo: string; texto: string; resto?: string }[];
}

export function vistaFin(
  respuesta: { referencia: string; plazaAsignada: boolean; posicionListaEspera: number | null },
  eleccion: { sede: SedeWeb; salida: Salida; dias: number[] },
  datos: { nombre: string; apellidos: string; telefono: string; email: string; tutorNombre: string },
  menor: boolean,
): VistaFin {
  const { sede, salida, dias } = eleccion;
  const plaza = respuesta.plazaAsignada;
  const grupo = salida.grupo.nombre;
  const lugar = sede.lugar?.trim() || sede.nombre;
  const valores = { grupo, sede: sede.nombre, dias: textoDiasLista(dias), n: respuesta.posicionListaEspera ?? '' };
  const estado = plaza
    ? rellenar(T.estadoPlaza, valores)
    : rellenar(respuesta.posicionListaEspera ? T.estadoEsperaPosicion : T.estadoEspera, valores);
  const dobok = salida.grupo.disciplina.toLowerCase() === 'taekwondo';
  const pasos = plaza
    ? [
        { titulo: T.pasoCorreo, texto: T.pasoCorreoPlaza },
        { titulo: T.pasoRevisa, texto: rellenar(T.pasoRevisaTexto, { x: datos.telefono.trim() }) },
        {
          titulo: T.pasoPrimer,
          texto: rellenar(T.pasoPrimerTexto, { horario: textoHorario(salida, dias), lugar }) + (dobok ? T.pasoPrimerDobok : '.'),
        },
      ]
    : [
        { titulo: T.pasoCorreo, texto: T.pasoCorreoEspera },
        { titulo: T.pasoEsperas, texto: T.pasoEsperasTexto },
        { titulo: T.pasoAviso, texto: rellenar(T.pasoAvisoTexto, { grupo }) },
      ];
  const resto = textoResto(salida, dias);
  const ficha: VistaFin['ficha'] = [
    { rotulo: T.fichaEstado, texto: plaza ? T.fichaEstadoPlaza : T.fichaEstadoEspera },
    { rotulo: T.fichaPueblo, texto: [sede.nombre, sede.lugar?.trim()].filter(Boolean).join(' · ') },
    { rotulo: T.fichaGrupo, texto: textoGrupo(salida) },
    { rotulo: T.fichaDias, texto: textoHorario(salida, dias), ...(resto ? { resto } : {}) },
    { rotulo: T.fichaAlumno, texto: `${datos.nombre.trim()} ${datos.apellidos.trim()}`.trim() },
  ];
  if (menor && datos.tutorNombre.trim()) {
    ficha.push({ rotulo: T.fichaResponsable, texto: datos.tutorNombre.trim() });
  }
  ficha.push({ rotulo: T.fichaContacto, texto: datos.email.trim() });
  return { titulo: plaza ? T.finPlaza : T.finEspera, referencia: respuesta.referencia, estado, pasos, ficha };
}
