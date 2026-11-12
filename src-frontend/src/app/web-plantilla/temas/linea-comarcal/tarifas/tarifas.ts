import { importeEnTexto, numeroEnLetras, telefonoEnlace, telefonoVisible } from '../../../comun/horario';
import { mesYAnio, tarifaPorSede } from '../../../comun/tarifas';
import { SitioWeb, TarifaWeb } from '../../../datos/modelos';
import { TextosTema, claveSede, rellenarHuecos, tarifaPrincipal } from '../contenido';
import { TEXTOS_PAGINAS_LINEA_COMARCAL as P } from '../textos';

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
  /**
   * Una fila por importe: «Cada alumno», o «Cada adulto» y «Cada menor» (tarifa familiar). `tipo` va en el cuadro como
   * `data-quien` (no se ve): con él, el editor sabe qué fila es la de hermanos aunque dos digan lo mismo.
   */
  importes: { quien: string; importe: string; tipo: 'alumno' | 'adulto' | 'menor' | 'hermanos' }[];
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
  /** `dato` (`data-dato`, neutro): la capa de edición de «Web · Contenido» pone su candado al precio de la licencia. */
  aparte: { concepto: string; precio: string; dato: 'licencia' | 'examen' }[];
  boton: string;
}

/**
 * Los textos que la plantilla de la página pinta tal cual (W12d-bis): los de la escuela (`tarifas.*`, con el del tema
 * por defecto) y los fijos sin clave. Mismos nombres que antes en la plantilla. `alMes` es `tarifas.al_mes`, el mismo
 * texto que en Inicio y en la página de cada sede.
 */
export function textosTarifas(textos: TextosTema) {
  return {
    ...P,
    alMes: textos.t('tarifas.al_mes'),
    rotuloCuadro: textos.t('tarifas.rotulo_cuadro'),
    tituloCuadro: textos.t('tarifas.titulo_cuadro'),
    columnaTarifa: textos.t('tarifas.col_tarifa'),
    columnaQuien: textos.t('tarifas.col_quien'),
    columnaImporte: textos.t('tarifas.col_importe'),
    pieCuadro: textos.t('tarifas.pie_cuadro'),
    rotuloPorSede: textos.t('tarifas.rotulo_sede'),
    tituloPorSede: textos.t('tarifas.titulo_sede'),
    rotuloAparte: textos.t('tarifas.rotulo_aparte'),
    tituloAparte: textos.t('tarifas.titulo_aparte'),
    notaAparte: textos.t('tarifas.nota_aparte'),
  };
}

export function vistaTarifas(sitio: SitioWeb, ahora: Date): VistaTarifas {
  const textos = new TextosTema(sitio);
  const sedes = sitio.sedes;
  const una = sedes.length === 1;
  const principal = tarifaPrincipal(sitio, ahora);
  const tarifa = sitio.tarifas.find((x) => x.importe != null);
  const boton = textos.t('tarifas.boton_preinscripcion');
  const aparte = apartes(sitio, textos);

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
    // `tarifas.cuota_igual` vale para las tres variantes si la escuela la guarda; `{x}` (el número de sedes) solo lo
    // tiene la de varias sedes con la misma cuota.
    const igual = !una && principal.igual && sedes.length > 1;
    cuota = {
      importe: importeEnTexto(principal.importe),
      alMes: textos.t('tarifas.al_mes'),
      texto: rellenarHuecos(
        igual
          ? textos.t('tarifas.cuota_igual')
          : textos.variante('tarifas.cuota_igual', una ? P.cuotaUnaSede : P.cuota),
        { x: igual ? numeroEnLetras(sedes.length) : null },
      ),
      incluye: [
        // `tarifas.punto1`, el mismo texto que el primer punto de Inicio y de la página de cada sede (vacía, no se pinta).
        textos.t('tarifas.punto1'),
        ...(otras.length ? [rellenarHuecos(textos.t('tarifas.tambien'), { x: otras.join(' y ') })] : []),
        ...(aparte.length ? [textos.t('tarifas.incluye_aparte')] : []),
      ].filter(Boolean),
      boton,
    };
  } else {
    const email = sitio.contacto.email?.trim();
    const telefono = sitio.contacto.telefono?.trim();
    vacia = {
      titular: textos.t('tarifas.vacio_titular'),
      texto:
        email || telefono ? textos.t('tarifas.vacio_texto') : textos.variante('tarifas.vacio_texto', P.vacioTextoSinContacto),
      correo: email ? { rotulo: textos.t('tarifas.vacio_correo'), texto: email, enlace: `mailto:${email}` } : null,
      telefono: telefono
        ? { rotulo: textos.t('tarifas.vacio_telefono'), texto: telefonoVisible(telefono), enlace: telefonoEnlace(telefono) }
        : null,
    };
  }

  const filas = sitio.tarifas.map((x) => filaCuadro(x, textos)).filter((f): f is FilaCuadro => f !== null);
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
    verHorarios: textos.t('tarifas.ver_horarios'),
    conHorarios: sedes.length > 0,
    cuota,
    vacia,
    cuadro:
      principal && filas.length
        ? {
            leyenda: rellenarHuecos(
              sitio.temporada ? textos.t('tarifas.leyenda_cuadro') : textos.variante('tarifas.leyenda_cuadro', P.leyendaCuadro),
              { x: sitio.temporada },
            ),
            filas,
          }
        : null,
    porSede,
    aparte,
    boton,
  };
}

function esHermanos(tarifa: TarifaWeb): boolean {
  return tarifa.codigo?.toUpperCase() === 'HERMANOS';
}

/**
 * Una tarifa en el cuadro; null si no tiene ningún importe. `tarifas.cada_alumno` solo rotula la fila genérica
 * (decisión del propietario): «Cada adulto» y «Cada menor» de la tarifa familiar quedan siempre fijos, para que dos
 * importes distintos no salgan con el mismo rótulo.
 */
function filaCuadro(tarifa: TarifaWeb, textos: TextosTema): FilaCuadro | null {
  const importes: FilaCuadro['importes'] = [];
  if (tarifa.importeAdulto != null || tarifa.importeMenor != null) {
    if (tarifa.importeAdulto != null) {
      importes.push({ quien: P.cadaAdulto, importe: importeEnTexto(tarifa.importeAdulto), tipo: 'adulto' });
    }
    if (tarifa.importeMenor != null) {
      importes.push({ quien: P.cadaMenor, importe: importeEnTexto(tarifa.importeMenor), tipo: 'menor' });
    }
  } else if (tarifa.importe != null) {
    importes.push({
      quien: esHermanos(tarifa) ? textos.t('tarifas.hermanos') : textos.t('tarifas.cada_alumno'),
      importe: importeEnTexto(tarifa.importe),
      tipo: esHermanos(tarifa) ? 'hermanos' : 'alumno',
    });
  }
  return importes.length ? { nombre: tarifa.nombre, importes } : null;
}

/** Licencia de cada disciplina (con precio) y examen «desde» el menor precio, como los da la API. */
function apartes(sitio: SitioWeb, textos: TextosTema): VistaTarifas['aparte'] {
  const nombres = new Map(sitio.sedes.flatMap((s) => s.grupos).map((g) => [g.disciplina, g.disciplinaNombre]));
  const licencia = textos.t('tarifas.licencia');
  const lista: VistaTarifas['aparte'] = sitio.licencias
    .filter((l) => l.precio != null)
    .map((l) => ({
      concepto: l.concepto?.trim() || `${licencia} de ${(nombres.get(l.disciplina) ?? l.disciplina).toLowerCase()}`,
      precio: importeEnTexto(l.precio!),
      dato: 'licencia',
    }));
  if (sitio.examenDesde != null) {
    lista.push({
      concepto: textos.t('tarifas.examen'),
      precio: rellenarHuecos(textos.t('tarifas.examen_desde'), { x: importeEnTexto(sitio.examenDesde) }),
      dato: 'examen',
    });
  }
  return lista;
}
