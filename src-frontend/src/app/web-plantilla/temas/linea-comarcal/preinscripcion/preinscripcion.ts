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
  mejorPlazasDeSalida,
  plazasLibres,
  salidasParaEdad,
  sinPlaza,
  turnoDelDia,
  turnosElegidos,
} from '../../../comun/preinscripcion/preinscripcion';
import { SedeWeb, SitioWeb } from '../../../datos/modelos';
import { TextosTema, claveSede, rellenarHuecos, tarifaPrincipal } from '../contenido';
import { TEXTOS_PREINSCRIPCION_LINEA_COMARCAL as T } from '../textos';

/**
 * Lo que pinta la preinscripción del tema «La línea comarcal» (boceto `fase4-web/boceto-linea-comarcal-preinscripcion.html`)
 * a partir de `GET /api/publico/web`: sedes del paso 1, filas de grupos del paso 2, resumen lateral y confirmación. Sin
 * Angular; la lógica del formulario es la común (`comun/preinscripcion`).
 */

/**
 * Los textos de la preinscripción (W12d-bis): los de la escuela (`pre.*`, con el del tema por defecto) y los fijos sin
 * clave (plurales, errores, campos sin clave). Mismos nombres que antes en la plantilla y en el componente. Solo cambia
 * de dónde sale cada texto: la lógica, las validaciones y el envío del formulario no dependen de ellos.
 */
export function textosPreinscripcion(textos: TextosTema) {
  return {
    ...T,
    atras: textos.t('pre.atras'),
    pasoNdeM: textos.t('pre.paso_n'),
    pasoPueblo: textos.t('pre.paso_pueblo'),
    pasoGrupo: textos.t('pre.paso_grupo'),
    pasoDatos: textos.t('pre.paso_datos'),
    // Resumen lateral
    resumenTitulo: textos.t('pre.resumen_titulo'),
    resumenPueblo: textos.t('pre.resumen_pueblo'),
    resumenGrupo: textos.t('pre.resumen_grupo'),
    resumenDias: textos.t('pre.resumen_dias'),
    sinElegir: textos.t('pre.sin_elegir'),
    // Paso 1
    tituloPueblo: textos.t('pre.titulo_pueblo'),
    pistaPueblo: textos.t('pre.pista_pueblo'),
    continuar: textos.t('pre.continuar'),
    // Paso 2
    tituloGrupo: textos.t('pre.titulo_grupo'),
    cambiarDePueblo: textos.t('pre.cambiar_pueblo'),
    fechaLeyenda: textos.t('pre.fecha_leyenda'),
    fechaPista: textos.t('pre.fecha_pista'),
    dia: textos.t('pre.dia'),
    mes: textos.t('pre.mes'),
    anio: textos.t('pre.anio'),
    verGrupos: textos.t('pre.ver_grupos'),
    esperaFecha: textos.t('pre.espera_fecha'),
    vacioTitulo: textos.t('pre.vacio_titulo'),
    vacioOtras: textos.t('pre.vacio_otras'),
    vacioLlamar: textos.t('pre.vacio_llamar'),
    vacioLlamarFin: textos.t('pre.vacio_llamar_fin'),
    revisarFecha: textos.t('pre.revisar_fecha'),
    // Paso 3
    tituloDatos: textos.t('pre.titulo_datos'),
    avisoEsperaTitulo: textos.t('pre.espera_titulo'),
    avisoEsperaTexto: textos.t('pre.espera_texto'),
    pistaDatos: textos.t('pre.pista_datos'),
    bloqueAlumno: textos.t('pre.bloque_alumno'),
    nombre: textos.t('pre.nombre'),
    apellidos: textos.t('pre.apellidos'),
    bloqueContacto: textos.t('pre.bloque_contacto'),
    telefono: textos.t('pre.telefono'),
    email: textos.t('pre.email'),
    emailPista: textos.t('pre.email_pista'),
    bloqueNormas: textos.t('pre.bloque_normas'),
    aceptoNormas: textos.t('pre.acepto'),
    fotos: textos.t('pre.fotos'),
    bloqueFirma: textos.t('pre.bloque_firma'),
    firmaPista: textos.t('pre.firma_pista'),
    enviar: textos.t('pre.enviar'),
    enviarEspera: textos.t('pre.enviar_espera'),
    // Paso 4
    finPlaza: textos.t('pre.fin_plaza'),
    finEspera: textos.t('pre.fin_espera'),
    referencia: textos.t('pre.referencia'),
    estadoPlaza: textos.t('pre.estado_plaza'),
    estadoEsperaPosicion: textos.t('pre.estado_espera'),
    /** Variante de `pre.estado_espera` sin posición: si la escuela guarda la clave, su texto vale para las dos. */
    estadoEspera: textos.variante('pre.estado_espera', T.estadoEspera),
    queAhora: textos.t('pre.que_ahora'),
    pasoCorreo: textos.t('pre.paso_correo'),
    pasoCorreoPlaza: textos.t('pre.paso_correo_texto'),
    pasoCorreoEspera: textos.t('pre.paso_correo_espera'),
    pasoRevisa: textos.t('pre.paso_revisa'),
    pasoRevisaTexto: textos.t('pre.paso_revisa_texto'),
    descargar: textos.t('pre.descargar'),
    volverInicio: textos.t('pre.volver_inicio'),
  };
}

export type TextosPreinscripcion = ReturnType<typeof textosPreinscripcion>;

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
    const mejor = mejorPlazasDeSalida(salida);
    const plazas = estadoPlazas(mejor);
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
      plazasTexto: textoPlazas(mejor, textosPlazas),
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
  const t = textosPreinscripcion(textos);
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
    titulo: rellenar(t.vacioTitulo, { x: sede.nombre, n: edad }),
    otras,
    texto: otras.length
      ? null
      : minimo !== null && edad < minimo
        ? rellenar(T.vacioMinimo, { min: minimo, n: edad })
        : rellenar(T.vacioNinguna, { n: edad }),
    telefono: telefono
      ? { texto: telefonoVisible(telefono), enlace: telefonoEnlace(telefono), antes: rellenar(t.vacioLlamar, { x: sede.nombre }) }
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

/**
 * «25 € al mes, igual en todas las sedes.»; null sin tarifa o si alguna sede tiene la suya. Con una sede, la variante
 * «…para todos los grupos.»: si la escuela guarda `pre.tarifa_igual`, su texto vale para las dos (vacío, no se pinta).
 */
export function textoTarifa(sitio: SitioWeb, ahora: Date): string | null {
  const tarifa = tarifaPrincipal(sitio, ahora);
  if (!tarifa || !tarifa.igual) {
    return null;
  }
  const textos = new TextosTema(sitio);
  const plantilla = sitio.sedes.length === 1 ? textos.variante('pre.tarifa_igual', T.tarifaUna) : textos.t('pre.tarifa_igual');
  return rellenarHuecos(plantilla, { x: importeEnTexto(tarifa.importe) });
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
  t: TextosPreinscripcion,
): VistaFin {
  const { sede, salida, dias } = eleccion;
  const plaza = respuesta.plazaAsignada;
  const grupo = salida.grupo.nombre;
  const lugar = sede.lugar?.trim() || sede.nombre;
  const posicion = respuesta.posicionListaEspera;
  const valores = { grupo, sede: sede.nombre, dias: textoDiasLista(dias), n: posicion ?? '' };
  // Sin posición, `{n}` no tiene dato: si la escuela lo dejó en su texto, sale vacío y se limpia (`rellenarHuecos`).
  const estado = plaza
    ? rellenar(t.estadoPlaza, valores)
    : posicion
      ? rellenar(t.estadoEsperaPosicion, valores)
      : rellenarHuecos(t.estadoEspera, { ...valores, n: null });
  const dobok = salida.grupo.disciplina.toLowerCase() === 'taekwondo';
  const pasos = plaza
    ? [
        { titulo: t.pasoCorreo, texto: t.pasoCorreoPlaza },
        { titulo: t.pasoRevisa, texto: rellenar(t.pasoRevisaTexto, { x: datos.telefono.trim() }) },
        {
          titulo: t.pasoPrimer,
          texto: rellenar(t.pasoPrimerTexto, { horario: textoHorario(salida, dias), lugar }) + (dobok ? t.pasoPrimerDobok : '.'),
        },
      ]
    : [
        { titulo: t.pasoCorreo, texto: t.pasoCorreoEspera },
        { titulo: t.pasoEsperas, texto: t.pasoEsperasTexto },
        { titulo: t.pasoAviso, texto: rellenar(t.pasoAvisoTexto, { grupo }) },
      ];
  const resto = textoResto(salida, dias);
  const ficha: VistaFin['ficha'] = [
    { rotulo: t.fichaEstado, texto: plaza ? t.fichaEstadoPlaza : t.fichaEstadoEspera },
    { rotulo: t.fichaPueblo, texto: [sede.nombre, sede.lugar?.trim()].filter(Boolean).join(' · ') },
    { rotulo: t.fichaGrupo, texto: textoGrupo(salida) },
    { rotulo: t.fichaDias, texto: textoHorario(salida, dias), ...(resto ? { resto } : {}) },
    { rotulo: t.fichaAlumno, texto: `${datos.nombre.trim()} ${datos.apellidos.trim()}`.trim() },
  ];
  if (menor && datos.tutorNombre.trim()) {
    ficha.push({ rotulo: t.fichaResponsable, texto: datos.tutorNombre.trim() });
  }
  ficha.push({ rotulo: t.fichaContacto, texto: datos.email.trim() });
  return { titulo: plaza ? t.finPlaza : t.finEspera, referencia: respuesta.referencia, estado, pasos, ficha };
}
