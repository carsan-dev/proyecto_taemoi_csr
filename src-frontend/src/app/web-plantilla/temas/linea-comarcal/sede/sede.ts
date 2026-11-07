import { enlaceMapa, edadesEnTexto, importeEnTexto, proximaClaseConGrupo, telefonoEnlace, telefonoVisible } from '../../../comun/horario';
import { tarifaPorSede } from '../../../comun/tarifas';
import { SedeWeb, SitioWeb } from '../../../datos/modelos';
import { TextosTema, VistaSalida, claveSede, tarifaPrincipal, vistaSalidas } from '../contenido';
import { TEXTOS_FIJOS_LINEA_COMARCAL as FIJOS, TEXTOS_PAGINAS_LINEA_COMARCAL as P } from '../textos';

/**
 * Lo que pinta la página de una sede del tema «La línea comarcal» (boceto `fase4-web/boceto-linea-comarcal-sede.html`),
 * con los datos de `GET /api/publico/web`: cartel del pueblo con su tramo de línea, tablero de grupos y horas (plazas
 * del §4.3), cómo llegar, tarifa de la sede y la línea entera. Una sede o varias salen de los datos. Sin Angular.
 *
 * Lo que el boceto enseña y la API no da no se pinta: profesores por sede, profesor de cada grupo, horario del
 * teléfono, WhatsApp y créditos de las fotos.
 */

const t = (texto: string, valores: Record<string, string | number>) =>
  texto.replace(/\{(\w+)\}/g, (_, k: string) => String(valores[k] ?? ''));

export interface VecinaTramo {
  nombre: string;
  enlace: string | null;
}

export interface VistaTramo {
  una: boolean;
  /** `left` y `right` del trazo (CSS, como el boceto). */
  izquierda: string;
  derecha: string;
  /** `left` de los terminales (donde acaba la línea). */
  terminales: string[];
  anterior: VecinaTramo | null;
  siguiente: VecinaTramo | null;
  actual: string;
}

export interface FilaFicha {
  rotulo: string;
  texto: string;
  nota: string | null;
  enlace: string | null;
}

export interface VistaPaginaSede {
  nombre: string;
  titularLargo: boolean;
  lugar: string | null;
  direccion: string | null;
  /** `?sede=` de la preinscripción. */
  consulta: Record<string, string>;
  preinscribirme: string;
  preinscribirmeEtiqueta: string;
  foto: { url: string; alt: string } | null;
  tramo: VistaTramo;
  horarios: {
    rotulo: string;
    titulo: string;
    proxima: { rotulo: string; hoy: boolean; cuando: string; hora: string; grupo: string } | null;
    salidas: VistaSalida[];
    leyenda: { lleno: string };
  };
  llegar: { filas: FilaFicha[]; mapa: string | null; foto: { url: string; alt: string } | null } | null;
  tarifa: { importe: string; alMes: string; igual: string | null; puntos: string[]; boton: string } | null;
  ruta: { clave: string; nombre: string; enlace: string | null; detalle: string; actual: boolean }[] | null;
}

/** La sede del slug; null si ninguna lo tiene (la página pinta la 404 del tema). */
export function sedeDelSlug(sitio: SitioWeb, slug: string | null): SedeWeb | null {
  return slug ? (sitio.sedes.find((s) => s.slug != null && s.slug === slug) ?? null) : null;
}

export function vistaPaginaSede(sitio: SitioWeb, sede: SedeWeb, ahora: Date): VistaPaginaSede {
  const textos = new TextosTema(sitio);
  const sedes = sitio.sedes;
  const una = sedes.length === 1;
  const localidad = sede.localidad?.trim() || sede.nombre;
  const lugar = sede.lugar?.trim() || null;
  const direccion = sede.direccion?.trim() ? `${sede.direccion.trim()}, ${localidad}` : null;
  const preinscribirme = textos.t('horarios.boton');
  const proxima = proximaClaseConGrupo(sede, ahora);

  return {
    nombre: sede.nombre,
    titularLargo: Math.max(...sede.nombre.split(/\s+/).map((p) => p.length)) > 7,
    lugar,
    direccion,
    consulta: { sede: sede.slug ?? String(sede.id) },
    preinscribirme,
    preinscribirmeEtiqueta: `${preinscribirme} en ${sede.nombre}`,
    foto: sede.fotoUrl ? { url: sede.fotoUrl, alt: t(P.fotoSede, { x: sede.nombre }) } : null,
    tramo: tramo(sedes, sede),
    horarios: {
      rotulo: sitio.temporada ? t(P.rotuloHorariosCurso, { x: sitio.temporada }) : P.rotuloHorarios,
      titulo: P.tituloHorarios,
      proxima: proxima
        ? {
            rotulo: textos.t('sedes.proxima_rotulo'),
            hoy: proxima.hoy,
            cuando: proxima.cuando,
            hora: proxima.hora,
            grupo: [proxima.grupo.nombre, edadesEnTexto(proxima.grupo.rangoEdadMin, proxima.grupo.rangoEdadMax)?.toLowerCase()]
              .filter(Boolean)
              .join(', '),
          }
        : null,
      salidas: vistaSalidas(sede, textos, ahora),
      leyenda: { lleno: t(P.leyendaLleno, { x: textos.t('horarios.lleno') }) },
    },
    llegar: llegar(sede, lugar, direccion, localidad),
    tarifa: tarifaDeSede(sitio, sede, textos, una, ahora),
    ruta: una
      ? null
      : sedes.map((s) => ({
          clave: claveSede(s),
          nombre: s.nombre,
          enlace: s.slug ? `/sedes/${s.slug}` : null,
          detalle: [s.lugar, s.direccion].map((v) => v?.trim()).filter(Boolean).join(' · '),
          actual: s.id === sede.id,
        })),
  };
}

/** El tramo del boceto: la parada actual entre sus vecinas; el trazo sigue si hay más paradas a cada lado. */
function tramo(sedes: SedeWeb[], sede: SedeWeb): VistaTramo {
  const vecina = (s: SedeWeb | undefined): VecinaTramo | null =>
    s ? { nombre: s.nombre, enlace: s.slug ? `/sedes/${s.slug}` : null } : null;
  if (sedes.length <= 1) {
    const ini = 'calc(50% - 72px)';
    const fin = 'calc(50% + 72px)';
    return { una: true, izquierda: ini, derecha: `calc(100% - ${fin})`, terminales: [ini, fin], anterior: null, siguiente: null, actual: P.unicaSede };
  }
  const pos = sedes.indexOf(sede);
  const ant = sedes[pos - 1];
  const sig = sedes[pos + 1];
  const ini = !ant ? 'calc(50% - 64px)' : pos - 1 === 0 ? 'calc(16.6667% - 40px)' : 'var(--sangra-izq)';
  const fin = !sig ? 'calc(50% + 64px)' : pos + 1 === sedes.length - 1 ? 'calc(83.3333% + 40px)' : 'calc(100% - var(--sangra-der))';
  const terminales = [ini, fin].filter((x) => x.startsWith('calc(') && !x.includes('sangra'));
  return {
    una: false,
    izquierda: ini,
    derecha: `calc(100% - ${fin})`,
    terminales,
    anterior: vecina(ant),
    siguiente: vecina(sig),
    actual: t(P.parada, { n: pos + 1, total: sedes.length }),
  };
}

function llegar(sede: SedeWeb, lugar: string | null, direccion: string | null, localidad: string): VistaPaginaSede['llegar'] {
  const filas: FilaFicha[] = [];
  if (lugar || direccion) {
    filas.push({ rotulo: P.fichaLugar, texto: lugar ?? direccion!, nota: lugar ? direccion : null, enlace: null });
  }
  const acceso = sede.indicacionesAcceso?.trim();
  if (acceso) {
    filas.push({ rotulo: P.fichaEntrada, texto: acceso, nota: null, enlace: null });
  }
  const telefono = sede.telefono?.trim();
  if (telefono) {
    filas.push({ rotulo: P.fichaTelefono, texto: telefonoVisible(telefono), nota: null, enlace: telefonoEnlace(telefono) });
  }
  const mapa = lugar || sede.direccion?.trim() ? enlaceMapa([sede.lugar, sede.direccion, localidad]) : null;
  const foto = sede.foto2Url ? { url: sede.foto2Url, alt: t(P.fotoSede, { x: sede.nombre }) } : null;
  return filas.length || mapa ? { filas, mapa, foto } : null;
}

/**
 * La tarifa de la sede: la mensualidad que se paga hoy en ella. «Igual en todas las sedes» (o «Para todos los grupos»
 * con una sede) solo si ninguna sede paga hoy otra cosa; si no, la línea no se pinta (el boceto no tiene ese estado).
 */
function tarifaDeSede(sitio: SitioWeb, sede: SedeWeb, textos: TextosTema, una: boolean, ahora: Date): VistaPaginaSede['tarifa'] {
  const principal = tarifaPrincipal(sitio, ahora);
  const tarifa = sitio.tarifas.find((x) => x.importe != null);
  if (!principal || !tarifa) {
    return null;
  }
  const enSede = tarifaPorSede(sitio, tarifa, ahora).find((x) => x.sede.id === sede.id);
  const puntos = [textos.t('tarifas.punto1'), textos.t('tarifas.punto2')];
  for (let n = 3; n <= 20; n++) {
    puntos.push(textos.bloques.textoPropio(`tarifas.punto${n}`) ?? '');
  }
  return {
    importe: importeEnTexto(enSede?.importe ?? principal.importe),
    alMes: FIJOS.alMes,
    igual: principal.igual ? (una ? FIJOS.igualUnaSede : FIJOS.igualEnTodasLasSedes) : null,
    puntos: puntos.filter(Boolean),
    boton: textos.t('tarifas.boton_preinscripcion'),
  };
}
