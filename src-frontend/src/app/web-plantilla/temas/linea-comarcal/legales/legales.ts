import { telefonoEnlace, telefonoVisible } from '../../../comun/horario';
import { Legal, NormasLegal } from '../../../datos/modelos';
import { TextosTema, enParrafos, rellenarHuecos } from '../contenido';
import { fechaLargaSinDia } from '../eventos/eventos';

/**
 * Los datos de la escuela que rellenan el texto legal del tema «La línea comarcal» (boceto
 * `fase4-web/boceto-linea-comarcal-legales.html`), de `GET /api/publico/web/legal`. El texto es el del boceto; aquí solo
 * los datos: lo que falta no se pinta (fila de la ficha, «con CIF…», «escribe a…»).
 */

export interface VistaContacto {
  texto: string;
  enlace: string;
}

export interface VistaNormas {
  /** El nombre de la disciplina, solo si hay más de una con normas; null con una. */
  disciplina: string | null;
  normas: string[];
}

export interface VistaLegales {
  /** Nombre legal; sin él, el nombre de la escuela. */
  titular: string;
  cif: string | null;
  /** «C/ Ejemplo, 1, 41560 Estepa (Sevilla)»; null sin ningún dato. */
  domicilio: string | null;
  correo: VistaContacto | null;
  telefono: VistaContacto | null;
  /** Null en una vista previa sin publicar: la línea no se pinta. */
  actualizada: { iso: string; texto: string } | null;
  normas: VistaNormas[];
}

const limpio = (valor: string | null | undefined): string | null => valor?.trim() || null;

/**
 * Los textos legales de la escuela (W12d-bis, decisión del propietario del 12/11): solo los títulos, la entrada, los
 * rótulos de la ficha y los párrafos del tema iguales a los del boceto (`legal.*`, con el del tema por defecto). El resto
 * del texto legal sigue fijo en la plantilla. `titular`: el `{x}` de `legal.aviso.p2`.
 */
export function textosLegales(textos: TextosTema, titular: string) {
  return {
    tituloLegal: textos.t('legal.titulo'),
    entradaLegal: textos.t('legal.entrada'),
    actualizada: textos.t('legal.actualizada'),
    enEstaPagina: textos.t('legal.en_esta_pagina'),
    avisoLegal: textos.t('legal.aviso'),
    // Los párrafos, como en Inicio (`enParrafos`): una línea en blanco abre otro <p>; vacíos, ninguno.
    avisoP0: enParrafos(textos.t('legal.aviso.p0')),
    avisoH1: textos.t('legal.aviso.h1'),
    avisoP1: enParrafos(textos.t('legal.aviso.p1')),
    avisoH2: textos.t('legal.aviso.h2'),
    avisoP2: enParrafos(rellenarHuecos(textos.t('legal.aviso.p2'), { x: titular })),
    avisoH3: textos.t('legal.aviso.h3'),
    privacidad: textos.t('legal.privacidad'),
    privP0: enParrafos(textos.t('legal.priv.p0')),
    privH2: textos.t('legal.priv.h2'),
    cookies: textos.t('legal.cookies'),
    normas: textos.t('legal.normas'),
    fichaTitular: textos.t('legal.ficha_titular'),
    fichaCif: textos.t('legal.ficha_cif'),
    fichaDomicilio: textos.t('legal.ficha_domicilio'),
    fichaCorreo: textos.t('legal.ficha_correo'),
  };
}

export function vistaLegales(legal: Legal): VistaLegales {
  const t = legal.titular;
  const correo = limpio(t.email);
  const telefono = limpio(t.telefono);
  const actualizada = fechaLargaSinDia(legal.actualizadaEn);
  const normas = legal.normas
    .map((n) => ({ nombre: limpio(n.disciplinaNombre) ?? n.disciplina, normas: normasDe(n) }))
    .filter((n) => n.normas.length);
  return {
    titular: limpio(t.nombreLegal) ?? t.nombre.trim(),
    cif: limpio(t.cif),
    domicilio: domicilio(t.direccion, t.codigoPostal, t.localidad, t.provincia),
    correo: correo ? { texto: correo, enlace: `mailto:${correo}` } : null,
    telefono: telefono ? { texto: telefonoVisible(telefono), enlace: telefonoEnlace(telefono) } : null,
    actualizada: actualizada ? { iso: legal.actualizadaEn!, texto: actualizada } : null,
    normas: normas.map((n) => ({ disciplina: normas.length > 1 ? n.nombre : null, normas: n.normas })),
  };
}

/** «C/ Ejemplo, 1» + «41560» + «Estepa» + «Sevilla» → «C/ Ejemplo, 1, 41560 Estepa (Sevilla)», con lo que haya. */
function domicilio(
  direccion: string | null,
  codigoPostal: string | null,
  localidad: string | null,
  provincia: string | null,
): string | null {
  const lugar = [limpio(codigoPostal), limpio(localidad)].filter(Boolean).join(' ');
  const partes = [limpio(direccion), lugar].filter(Boolean).join(', ');
  const prov = limpio(provincia);
  if (!partes) {
    return prov;
  }
  return prov ? `${partes} (${prov})` : partes;
}

/** Las normas de la plantilla de preinscripción (`contenido.normas[]`, objeto o JSON en texto), sin vacías. */
export function normasDe(normas: Pick<NormasLegal, 'contenido'>): string[] {
  let contenido = normas.contenido;
  if (typeof contenido === 'string') {
    try {
      contenido = JSON.parse(contenido);
    } catch {
      return [];
    }
  }
  const lista = (contenido as { normas?: unknown } | null)?.normas;
  return Array.isArray(lista)
    ? lista.filter((n): n is string => typeof n === 'string' && n.trim().length > 0).map((n) => n.trim())
    : [];
}
