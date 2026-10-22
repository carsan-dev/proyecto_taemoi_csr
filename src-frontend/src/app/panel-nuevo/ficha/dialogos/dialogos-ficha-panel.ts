import { Injectable, inject } from '@angular/core';
import type { SweetAlertOptions, SweetAlertResult } from 'sweetalert2';

import type { DialogosFichaPort } from '../../../componentes/endpoints/editar-alumno/dialogos-ficha.port';
import { AvisosService } from '../../avisos/avisos.service';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import {
  BloqueMensaje,
  CampoDialogo,
  ErrorCampo,
  OpcionPastilla,
  OpcionesConfirmacion,
  ParteMensaje,
  ResultadoConfirmacion,
  TipoCampoDialogo,
  ValoresDialogo,
} from '../../dialogo/dialogo.component';

/** Hasta tantas opciones, pastillas (`role=radiogroup`); con más, un desplegable. */
export const MAX_PASTILLAS = 6;

/** Id del campo de un `input` de SweetAlert2 en el diálogo del panel. */
export const ID_CAMPO_SWAL = 'pn-ficha-campo';

/** Diálogos cuya acción principal borra o da de baja (por su id en la ficha de hoy). */
const DESTRUCTIVOS = new Set(['baja-o-eliminar', 'eliminar-deporte', 'eliminar-alumno']);

/** Verbos de una acción destructiva (eliminar, dar de baja, quitar, revertir, borrar, cancelar un cargo). */
const VERBO_DESTRUCTIVO = /eliminar|\bbaja\b|quitar|revert|borrar|cancelar cargo/i;

/** Los rojos de SweetAlert2 en la ficha de hoy («#d33» y el de Bootstrap). */
const ROJOS = new Set(['#d33', '#dd3333', '#dc3545']);

/** Títulos que no dicen nada más que el icono: el aviso lleva solo la frase. */
const TITULO_GENERICO = /^(¡?error!?|¡?bien!?|información)$/i;

const TIPOS_CAMPO: Partial<Record<string, TipoCampoDialogo>> = {
  text: 'text',
  number: 'number',
  date: 'date',
  textarea: 'textarea',
  email: 'email',
};

/**
 * Los diálogos de la ficha con las piezas del panel nuevo (8b-D2b, decisión 269). `FichaAlumnoComponent` lo da en sus
 * `providers` como `DIALOGOS_FICHA`: los diálogos que hereda de la ficha de hoy, con las mismas opciones de SweetAlert2,
 * se pintan con `ConfirmacionService` y `AvisosService` y devuelven un resultado igual al de SweetAlert2
 * (`{isConfirmed, isDenied, isDismissed, value, dismiss}`), así que lo que hace cada `.then` no cambia. Nunca abre
 * SweetAlert2: lo que no sabe traducir lo pinta como una confirmación genérica con el mismo título y la misma frase.
 */
@Injectable()
export class DialogosFichaPanel implements DialogosFichaPort {
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);

  /** Lo que un `preConfirm` en curso pidió con `showValidationMessage`. */
  private validacion: string | null = null;

  fire(opciones: SweetAlertOptions, id?: string): Promise<SweetAlertResult<any>> {
    if (esAviso(opciones)) {
      this.avisar(opciones);
      return Promise.resolve({ isConfirmed: true, isDenied: false, isDismissed: false, value: true });
    }
    const traduccion = this.traducir(opciones, id);
    return this.confirmacion.confirmar(traduccion.opciones).then((r) => traduccion.resultado(r));
  }

  /** Sin uso en la ficha (solo un spec de hoy llama a `Swal.close`): los diálogos del panel se cierran solos. */
  close(): void {
    // Nada: no hay SweetAlert2 que cerrar.
  }

  showValidationMessage(mensaje: string): void {
    this.validacion = mensaje;
  }

  toastExito(mensaje: string): void {
    this.avisos.exito(mensaje);
  }

  toastError(mensaje: string): void {
    this.avisos.error(mensaje);
  }

  // ------------------------------------------------------------------ avisos

  private avisar(o: SweetAlertOptions): void {
    const texto = textoDeAviso(o);
    if (o.icon === 'success') {
      this.avisos.exito(texto);
    } else if (o.icon === 'error' || o.icon === 'warning') {
      this.avisos.error(texto);
    } else {
      this.avisos.info(texto);
    }
  }

  // ------------------------------------------------------------------ confirmaciones y campos

  private traducir(
    o: SweetAlertOptions,
    id: string | undefined
  ): { opciones: OpcionesConfirmacion; resultado: (r: ResultadoConfirmacion) => SweetAlertResult } {
    const titulo = textoPlano(o.titleText || o.title) || '';
    const html = typeof o.html === 'string' ? leerHtml(o.html) : null;
    const accion = o.confirmButtonText || 'Aceptar';
    const generico = esGenerico(o, html);
    const campos = generico ? [] : html ? html.campos : campoDeInput(o);
    const [primero, ...resto] = html?.bloques ?? [];
    const mensaje = o.text ? o.text : primero?.tipo === 'parrafo' ? primero.partes : undefined;
    const bloques = o.text ? html?.bloques : primero?.tipo === 'parrafo' ? resto : html?.bloques;

    const opciones: OpcionesConfirmacion = {
      titulo,
      mensaje,
      bloques: bloques?.length ? bloques : undefined,
      accion,
      cancelar: o.cancelButtonText || 'Cancelar',
      destructivo: esDestructivo(id, accion, o.confirmButtonColor),
      nombre: id,
    };
    if (o.showDenyButton) {
      const texto = o.denyButtonText || 'No';
      opciones.accionSecundaria = { texto, destructivo: VERBO_DESTRUCTIVO.test(texto) || esRojo(o.denyButtonColor) };
    }
    if (campos.length) {
      opciones.campos = campos;
    }
    if (generico) {
      opciones.generico = true;
    }

    // Lo que devolverá `value`: lo de `preConfirm` si lo hay; si no, el valor del campo (o `true`, como SweetAlert2).
    let valorFinal: unknown = undefined;
    const campoId = campos[0]?.id;
    const valorDeEntrada = (valores: ValoresDialogo): string | undefined =>
      campoId !== undefined ? valores[campoId] ?? '' : undefined;

    if (!generico && (o.inputValidator || o.preConfirm)) {
      opciones.comprobar = (valores) => {
        const entrada = valorDeEntrada(valores);
        const seguirConPreConfirm = (): ErrorCampo | null | PromiseLike<ErrorCampo | null> => {
          if (!o.preConfirm) {
            return null;
          }
          this.validacion = null;
          const r = desenvolver(o.preConfirm(entrada));
          const tras = (v: unknown): ErrorCampo | null => {
            if (this.validacion !== null || v === false) {
              const error = { campo: campoId, mensaje: this.validacion ?? '' };
              this.validacion = null;
              return error;
            }
            valorFinal = v === undefined || v === true ? undefined : v;
            return null;
          };
          return esPromesa(r) ? Promise.resolve(r).then(tras) : tras(r);
        };
        if (o.inputValidator && !html) {
          const validar = o.inputValidator as (valor: string) => unknown;
          const v = desenvolver(validar(entrada ?? '')) as string | null | false | void | PromiseLike<string | null | false | void>;
          const tras = (mensaje: string | null | false | void): ErrorCampo | null | PromiseLike<ErrorCampo | null> =>
            mensaje ? { campo: campoId, mensaje } : seguirConPreConfirm();
          return esPromesa(v) ? Promise.resolve(v).then(tras) : tras(v);
        }
        return seguirConPreConfirm();
      };
    }

    const resultado = (r: ResultadoConfirmacion): SweetAlertResult => {
      if (r.error !== undefined) {
        throw r.error;
      }
      if (r.confirmado) {
        const entrada = r.valores ? valorDeEntrada(r.valores) : undefined;
        const value = valorFinal !== undefined ? valorFinal : entrada !== undefined && !html ? entrada : true;
        return { isConfirmed: true, isDenied: false, isDismissed: false, value };
      }
      if (r.negado) {
        return { isConfirmed: false, isDenied: true, isDismissed: false, value: false };
      }
      return { isConfirmed: false, isDenied: false, isDismissed: true, dismiss: 'cancel' as SweetAlertResult['dismiss'] };
    };
    return { opciones, resultado };
  }
}

// ---------------------------------------------------------------- ayudantes (sin estado)

/** Un SweetAlert2 sin «Cancelar», sin «Denegar», sin campo y sin `preConfirm`: un aviso. */
function esAviso(o: SweetAlertOptions): boolean {
  return !o.showCancelButton && !o.showDenyButton && !o.input && !o.preConfirm && !(typeof o.html === 'string' && /<(select|input|textarea)\b/i.test(o.html));
}

/**
 * El texto del aviso: la frase; sin frase, el título. En los errores y advertencias, un título que dice algo más que
 * «Error» va delante («No se pudo subir el documento. El archivo supera 10 MB.»).
 */
export function textoDeAviso(o: SweetAlertOptions): string {
  const titulo = textoPlano(o.titleText || o.title).trim();
  const frase = (o.text ?? (typeof o.html === 'string' ? textoPlano(o.html) : '')).trim();
  if (!frase) {
    return titulo;
  }
  const conTitulo = (o.icon === 'error' || o.icon === 'warning') && titulo && !TITULO_GENERICO.test(titulo);
  if (!conTitulo) {
    return frase;
  }
  return /[.!?:]$/.test(titulo) ? `${titulo} ${frase}` : `${titulo}. ${frase}`;
}

function textoPlano(valor: unknown): string {
  if (typeof valor !== 'string') {
    return '';
  }
  if (!/<[a-z]/i.test(valor)) {
    return valor;
  }
  const plantilla = document.createElement('template');
  plantilla.innerHTML = valor;
  return (plantilla.content.textContent ?? '').replace(/\s+/g, ' ').trim();
}

function esRojo(color: unknown): boolean {
  return typeof color === 'string' && ROJOS.has(color.trim().toLowerCase());
}

function esDestructivo(id: string | undefined, accion: string, color: unknown): boolean {
  return (id !== undefined && DESTRUCTIVOS.has(id)) || esRojo(color) || VERBO_DESTRUCTIVO.test(accion);
}

/** Un `input` de un tipo que el diálogo no pinta (file, range, checkbox, url, password…): confirmación genérica. */
function esGenerico(o: SweetAlertOptions, html: HtmlLeido | null): boolean {
  if (html?.desconocido) {
    return true;
  }
  if (!o.input) {
    return false;
  }
  return !(o.input === 'select' || o.input === 'radio' || TIPOS_CAMPO[o.input]);
}

function opcionesDeInput(valor: unknown): OpcionPastilla[] {
  if (valor instanceof Map) {
    return Array.from(valor.entries()).map(([v, t]) => ({ valor: String(v), texto: String(t) }));
  }
  if (valor && typeof valor === 'object') {
    return Object.entries(valor as Record<string, unknown>).map(([v, t]) => ({ valor: v, texto: String(t) }));
  }
  return [];
}

/** El campo de un `input` de SweetAlert2: select o radio (pastillas o desplegable) y los de texto. */
function campoDeInput(o: SweetAlertOptions): CampoDialogo[] {
  if (!o.input) {
    return [];
  }
  const valor = o.inputValue === undefined || o.inputValue === null ? '' : String(o.inputValue);
  const etiqueta = textoPlano(o.inputLabel) || (o.input === 'select' ? textoPlano(o.inputPlaceholder) : '') || undefined;
  const atributos = o.inputAttributes ? { ...(o.inputAttributes as Record<string, string>) } : undefined;
  if (o.input === 'select' || o.input === 'radio') {
    const opciones = opcionesDeInput(o.inputOptions);
    const tipo: TipoCampoDialogo = opciones.length <= MAX_PASTILLAS ? 'pastillas' : 'select';
    return [{ id: ID_CAMPO_SWAL, tipo, etiqueta, opciones, valor, placeholder: textoPlano(o.inputPlaceholder) || undefined }];
  }
  const tipo = TIPOS_CAMPO[o.input]!;
  return [{ id: ID_CAMPO_SWAL, tipo, etiqueta, valor, placeholder: textoPlano(o.inputPlaceholder) || undefined, atributos }];
}

interface HtmlLeido {
  bloques: BloqueMensaje[];
  campos: CampoDialogo[];
  /** Algo que el diálogo no sabe pintar (un control de otro tipo). */
  desconocido: boolean;
}

/**
 * El html de un diálogo de hoy, sin su maquetación (estilos y clases de SweetAlert2): párrafos con su negrita, listas
 * y los controles con id (un `<select>` pasa a pastillas o desplegable con el mismo id). El párrafo justo antes de un
 * control sin etiqueta es su etiqueta («¿Qué deporte deseas activar?»).
 */
function leerHtml(html: string): HtmlLeido {
  const plantilla = document.createElement('template');
  plantilla.innerHTML = html;
  const leido: HtmlLeido = { bloques: [], campos: [], desconocido: false };
  let ultimoParrafo: { bloque: BloqueMensaje; texto: string } | null = null;
  for (const nodo of Array.from(plantilla.content.childNodes)) {
    if (nodo.nodeType === Node.TEXT_NODE) {
      const texto = (nodo.textContent ?? '').replace(/\s+/g, ' ').trim();
      if (texto) {
        const bloque: BloqueMensaje = { tipo: 'parrafo', partes: [texto] };
        leido.bloques.push(bloque);
        ultimoParrafo = { bloque, texto };
      }
      continue;
    }
    if (!(nodo instanceof HTMLElement)) {
      continue;
    }
    const etiqueta = nodo.tagName.toLowerCase();
    if (etiqueta === 'ul' || etiqueta === 'ol') {
      const elementos = Array.from(nodo.querySelectorAll('li')).map((li) => partesDe(li));
      leido.bloques.push({ tipo: 'lista', elementos });
      ultimoParrafo = null;
    } else if (etiqueta === 'select') {
      const opciones = Array.from((nodo as HTMLSelectElement).options).map((op) => ({
        valor: op.value,
        texto: (op.textContent ?? '').replace(/^\s*✓\s*/, '').replace(/\s+/g, ' ').trim(),
      }));
      const vacia = opciones.find((op) => op.valor === '');
      const reales = opciones.filter((op) => op.valor !== '');
      const etiquetaCampo = etiquetaDe(plantilla.content, nodo) ?? ultimoParrafo?.texto;
      if (ultimoParrafo && etiquetaCampo === ultimoParrafo.texto) {
        leido.bloques.splice(leido.bloques.indexOf(ultimoParrafo.bloque), 1);
      }
      leido.campos.push({
        id: nodo.id || `${ID_CAMPO_SWAL}-${leido.campos.length + 1}`,
        tipo: reales.length <= MAX_PASTILLAS ? 'pastillas' : 'select',
        etiqueta: etiquetaCampo,
        opciones: reales,
        valor: (nodo as HTMLSelectElement).value || '',
        placeholder: vacia?.texto,
      });
      ultimoParrafo = null;
    } else if (etiqueta === 'input' || etiqueta === 'textarea') {
      const tipo = etiqueta === 'textarea' ? 'textarea' : TIPOS_CAMPO[(nodo as HTMLInputElement).type || 'text'];
      if (!tipo) {
        leido.desconocido = true;
        continue;
      }
      const etiquetaCampo = etiquetaDe(plantilla.content, nodo) ?? ultimoParrafo?.texto;
      if (ultimoParrafo && etiquetaCampo === ultimoParrafo.texto) {
        leido.bloques.splice(leido.bloques.indexOf(ultimoParrafo.bloque), 1);
      }
      leido.campos.push({
        id: nodo.id || `${ID_CAMPO_SWAL}-${leido.campos.length + 1}`,
        tipo,
        etiqueta: etiquetaCampo,
        valor: (nodo as HTMLInputElement).value || '',
        placeholder: nodo.getAttribute('placeholder') || undefined,
      });
      ultimoParrafo = null;
    } else if (etiqueta === 'label') {
      continue;
    } else if (nodo.querySelector('select, input, textarea')) {
      leido.desconocido = true;
    } else {
      const partes = partesDe(nodo);
      if (partes.length) {
        const bloque: BloqueMensaje = { tipo: 'parrafo', partes };
        leido.bloques.push(bloque);
        ultimoParrafo = { bloque, texto: (nodo.textContent ?? '').replace(/\s+/g, ' ').trim() };
      }
    }
  }
  return leido;
}

function etiquetaDe(raiz: DocumentFragment, control: HTMLElement): string | undefined {
  if (!control.id) {
    return undefined;
  }
  const label = raiz.querySelector(`label[for="${control.id}"]`);
  return label?.textContent?.replace(/\s+/g, ' ').trim() || undefined;
}

/** El texto de un elemento con su negrita (`<strong>`, `<b>`); lo demás, como texto. */
function partesDe(elemento: Element): ParteMensaje[] {
  const partes: ParteMensaje[] = [];
  elemento.childNodes.forEach((n) => {
    const texto = (n.textContent ?? '').replace(/\s+/g, ' ');
    if (!texto.trim() && texto !== ' ') {
      return;
    }
    if (n instanceof HTMLElement && (n.tagName === 'STRONG' || n.tagName === 'B')) {
      partes.push({ fuerte: texto.trim() });
    } else {
      partes.push(texto);
    }
  });
  // Sin espacios sueltos al principio y al final.
  if (typeof partes[0] === 'string') {
    partes[0] = partes[0].replace(/^\s+/, '');
  }
  const ultima = partes.length - 1;
  if (typeof partes[ultima] === 'string') {
    partes[ultima] = (partes[ultima] as string).replace(/\s+$/, '');
  }
  return partes.filter((p) => p !== '');
}

/** `SyncOrAsync` de SweetAlert2: un valor, una promesa o algo con `toPromise()`. */
function desenvolver(valor: unknown): unknown {
  const conToPromise = valor as { toPromise?: () => unknown } | null;
  return conToPromise && typeof conToPromise === 'object' && typeof conToPromise.toPromise === 'function' && !esPromesa(valor)
    ? conToPromise.toPromise()
    : valor;
}

function esPromesa<T>(valor: T | PromiseLike<T>): valor is PromiseLike<T> {
  return !!valor && typeof (valor as PromiseLike<T>).then === 'function';
}
