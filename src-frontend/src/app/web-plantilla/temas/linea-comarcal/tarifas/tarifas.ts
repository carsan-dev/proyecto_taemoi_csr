import { importeEnTexto, numeroEnLetras, telefonoEnlace, telefonoVisible } from '../../../comun/horario';
import { mesYAnio, tarifaPorSede } from '../../../comun/tarifas';
import { SitioWeb, TarifaWeb } from '../../../datos/modelos';
import { TextosTema, claveSede, tarifaPrincipal } from '../contenido';
import { TEXTOS_FIJOS_LINEA_COMARCAL as FIJOS, TEXTOS_PAGINAS_LINEA_COMARCAL as P } from '../textos';

/**
 * Lo que pinta la página de tarifas del tema «La línea comarcal» (boceto `fase4-web/boceto-linea-comarcal-tarifas.html`)
 * con los datos de `GET /api/publico/web` (§9 n.º 6, §10 n.º 8 y 9): la banda con la mensualidad, el cuadro de todas
 * las tarifas, la mensualidad parada a parada con los cambios anunciados por sede, y aparte la licencia y el examen
 * («desde» el menor precio). Sin tarifas, el estado «Pregunta la cuota en tu sede». Sin Angular.
 */

const t = (texto: string, valores: Record<string, string | number>) =>
  texto.replace(/\{(\w+)\}/g, (_, k: string) => String(valores[k] ?? ''));

export interface FilaCuadro {
  nombre: string;
  /** Una fila por importe: «Cada alumno», o «Cada adulto» y «Cada menor» (tarifa familiar). */
  importes: { quien: string; importe: string }[];
}

export interface ParadaTarifa {
  clave: string;
  nombre: string;
  enlace: string | null;
  importe: string;
  /** «Desde enero de 2027: 28 € al mes»; null sin cambio anunciado. */
  cambio: string | null;
}

export interface VistaTarifas {
  titulo: string;
  verHorarios: string;
  /** Con al menos una sede: «Ver horarios» lleva a la línea de la portada. */
  conHorarios: boolean;
  /** La banda con la mensualidad; null sin tarifas (entonces, `vacia`). */
  cuota: { importe: string; alMes: string; texto: string; incluye: string[]; boton: string } | null;
  vacia: {
    titular: string;
    texto: string;
    correo: { rotulo: string; texto: string; enlace: string } | null;
    telefono: { rotulo: string; texto: string; enlace: string } | null;
  } | null;
  cuadro: { leyenda: string; filas: FilaCuadro[] } | null;
  porSede: { paradas: ParadaTarifa[]; conCambio: boolean } | null;
  aparte: { concepto: string; precio: string }[];
  boton: string;
}

export function vistaTarifas(sitio: SitioWeb, ahora: Date): VistaTarifas {
  const textos = new TextosTema(sitio);
  const sedes = sitio.sedes;
  const una = sedes.length === 1;
  const principal = tarifaPrincipal(sitio, ahora);
  const tarifa = sitio.tarifas.find((x) => x.importe != null);
  const boton = textos.t('tarifas.boton_preinscripcion');
  const aparte = apartes(sitio);

  let cuota: VistaTarifas['cuota'] = null;
  let vacia: VistaTarifas['vacia'] = null;
  if (principal && tarifa) {
    const otras: string[] = [];
    if (sitio.tarifas.some((x) => x.importeAdulto != null || x.importeMenor != null)) {
      otras.push(P.incluyeFamiliar);
    }
    if (sitio.tarifas.some((x) => esHermanos(x) && x.importe != null)) {
      otras.push(P.incluyeHermanos);
    }
    cuota = {
      importe: importeEnTexto(principal.importe),
      alMes: FIJOS.alMes,
      texto: una
        ? P.cuotaUnaSede
        : principal.igual && sedes.length > 1
          ? t(P.cuotaIgual, { x: numeroEnLetras(sedes.length) })
          : P.cuota,
      incluye: [
        P.incluyeMensualidad,
        ...(otras.length ? [t(P.incluyeTambien, { x: otras.join(' y ') })] : []),
        ...(aparte.length ? [P.incluyeAparte] : []),
      ],
      boton,
    };
  } else {
    const email = sitio.contacto.email?.trim();
    const telefono = sitio.contacto.telefono?.trim();
    vacia = {
      titular: P.vacioTitular,
      texto: email || telefono ? P.vacioTexto : P.vacioTextoSinContacto,
      correo: email ? { rotulo: P.correo, texto: email, enlace: `mailto:${email}` } : null,
      telefono: telefono ? { rotulo: P.telefono, texto: telefonoVisible(telefono), enlace: telefonoEnlace(telefono) } : null,
    };
  }

  const filas = sitio.tarifas.map(filaCuadro).filter((f): f is FilaCuadro => f !== null);
  let porSede: VistaTarifas['porSede'] = null;
  if (tarifa && sedes.length > 1) {
    const paradas = tarifaPorSede(sitio, tarifa, ahora).map(
      (x): ParadaTarifa => ({
        clave: claveSede(x.sede),
        nombre: x.sede.nombre,
        enlace: x.sede.slug ? `/sedes/${x.sede.slug}` : null,
        importe: importeEnTexto(x.importe),
        cambio: x.cambio ? t(P.cambio, { x: mesYAnio(x.cambio.desde), importe: importeEnTexto(x.cambio.importe) }) : null,
      }),
    );
    porSede = { paradas, conCambio: paradas.some((p) => p.cambio) };
  }

  return {
    titulo: textos.t('tarifas.titulo'),
    verHorarios: P.verHorarios,
    conHorarios: sedes.length > 0,
    cuota,
    vacia,
    cuadro:
      principal && filas.length
        ? { leyenda: sitio.temporada ? t(P.leyendaCuadroCurso, { x: sitio.temporada }) : P.leyendaCuadro, filas }
        : null,
    porSede,
    aparte,
    boton,
  };
}

function esHermanos(tarifa: TarifaWeb): boolean {
  return tarifa.codigo?.toUpperCase() === 'HERMANOS';
}

/** Una tarifa en el cuadro; null si no tiene ningún importe. */
function filaCuadro(tarifa: TarifaWeb): FilaCuadro | null {
  const importes: FilaCuadro['importes'] = [];
  if (tarifa.importeAdulto != null || tarifa.importeMenor != null) {
    if (tarifa.importeAdulto != null) {
      importes.push({ quien: P.cadaAdulto, importe: importeEnTexto(tarifa.importeAdulto) });
    }
    if (tarifa.importeMenor != null) {
      importes.push({ quien: P.cadaMenor, importe: importeEnTexto(tarifa.importeMenor) });
    }
  } else if (tarifa.importe != null) {
    importes.push({ quien: esHermanos(tarifa) ? P.hermanos : P.cadaAlumno, importe: importeEnTexto(tarifa.importe) });
  }
  return importes.length ? { nombre: tarifa.nombre, importes } : null;
}

/** Licencia de cada disciplina (con precio) y examen «desde» el menor precio, como los da la API. */
function apartes(sitio: SitioWeb): { concepto: string; precio: string }[] {
  const nombres = new Map(sitio.sedes.flatMap((s) => s.grupos).map((g) => [g.disciplina, g.disciplinaNombre]));
  const lista = sitio.licencias
    .filter((l) => l.precio != null)
    .map((l) => ({
      concepto: l.concepto?.trim() || `${P.licencia} de ${(nombres.get(l.disciplina) ?? l.disciplina).toLowerCase()}`,
      precio: importeEnTexto(l.precio!),
    }));
  if (sitio.examenDesde != null) {
    lista.push({ concepto: P.examen, precio: t(P.examenDesde, { x: importeEnTexto(sitio.examenDesde) }) });
  }
  return lista;
}
