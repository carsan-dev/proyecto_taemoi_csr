import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';

import { environment } from '../../../environments/environment';
import { EventoListaWeb, EventoWeb, SedeWeb, SitioWeb } from '../datos/modelos';

/** Las siete páginas de la plantilla (fase4-web-plan.md §3.1). */
export type PaginaWeb = 'portada' | 'sede' | 'preinscripcion' | 'tarifas' | 'eventos' | 'evento' | 'legales';

export interface ContextoSeo {
  pagina: PaginaWeb;
  sitio: SitioWeb;
  /** Ruta de la página sin parámetros ni ancla (`/sedes/estepa`): da el `canonical`. */
  ruta: string;
  /** Si la URL pide la vista previa: `noindex`. */
  vistaPrevia: boolean;
  /** Página de sede: su slug. */
  slug?: string | null;
  /** Página de evento: su id. */
  eventoId?: number | null;
  /** Página de evento: sus datos (`GET /api/publico/web/eventos/{id}`); sin ellos, se busca entre los próximos. */
  evento?: EventoListaWeb | null;
  /** Página de eventos: los próximos de `GET /api/publico/web/eventos`; sin ellos, los de la portada. */
  eventos?: EventoListaWeb[] | null;
}

type JsonLd = Record<string, unknown>;

/** Marca de las etiquetas de la plantilla: solo se tocan las que la llevan. */
const MARCA = 'data-web-plantilla';
const NOINDEX = 'noindex, nofollow';

/**
 * SEO por escuela de la web por plantilla (fase4-web-plan.md §6, «SEO por escuela»): título, descripción, `canonical`,
 * Open Graph, favicon con el logo, `noindex` en la vista previa y JSON-LD (`SportsActivityLocation` por sede en la
 * portada y la sede; `Event` por evento en eventos y evento). Solo con lo que devuelve la API.
 *
 * El `SeoService` de Moiskimdo no actúa en estas rutas (§3.2, punto 7). Este servicio solo añade y quita las etiquetas
 * que llevan `data-web-plantilla` (y el título del documento); cada página sustituye las de la anterior.
 *
 * El host del `canonical` es el origen de la página (`location.origin`): la API no expone el dominio principal.
 */
@Injectable({ providedIn: 'root' })
export class SeoEscuelaService {
  private readonly document = inject(DOCUMENT);
  private readonly titulo = inject(Title);

  aplicar(contexto: ContextoSeo): void {
    this.limpiar();
    const { sitio, pagina } = contexto;
    const escuela = sitio.escuela.nombre;
    const logo = sitio.escuela.logoVersion != null
      ? this.absoluta(`${environment.apiUrl}/publico/escuela/logo?v=${sitio.escuela.logoVersion}`)
      : null;

    let titulo = escuela;
    let descripcion = this.textoBloque(sitio, 'portada.frase') ?? `${escuela}: sedes, horarios, tarifas y preinscripción.`;
    let imagen = logo;
    let noindex = contexto.vistaPrevia;
    const datos: JsonLd[] = [];

    switch (pagina) {
      case 'portada':
        datos.push(...sitio.sedes.map((s) => this.lugar(sitio, s)));
        break;
      case 'sede': {
        const sede = sitio.sedes.find((s) => s.slug != null && s.slug === contexto.slug);
        if (!sede) {
          noindex = true;
          break;
        }
        titulo = `${sede.nombre} · ${escuela}`;
        const donde = [sede.lugar, sede.direccion, sede.localidad].filter((v): v is string => !!v?.trim());
        descripcion = donde.length
          ? `${escuela} en ${sede.nombre}: ${donde.join(', ')}.`
          : `${escuela} en ${sede.nombre}.`;
        imagen = sede.fotoUrl ?? logo;
        datos.push(this.lugar(sitio, sede));
        break;
      }
      case 'tarifas':
        titulo = `Tarifas · ${escuela}`;
        descripcion = sitio.temporada
          ? `Tarifas de ${escuela} para la temporada ${sitio.temporada}.`
          : `Tarifas de ${escuela}.`;
        break;
      case 'preinscripcion':
        titulo = `Preinscripción · ${escuela}`;
        descripcion = `Preinscripción en ${escuela}.`;
        break;
      case 'legales':
        titulo = `Aviso legal y privacidad · ${escuela}`;
        descripcion = `Aviso legal, privacidad, cookies y normas de ${escuela}.`;
        break;
      case 'eventos':
        titulo = `Eventos · ${escuela}`;
        descripcion = `Próximos eventos de ${escuela}.`;
        datos.push(...(contexto.eventos ?? sitio.proximosEventos).filter((e) => e.fecha).map((e) => this.evento(sitio, e)));
        break;
      case 'evento': {
        titulo = `Eventos · ${escuela}`;
        descripcion = `Próximos eventos de ${escuela}.`;
        const evento = contexto.evento ?? sitio.proximosEventos.find((e) => e.id === contexto.eventoId);
        if (evento?.fecha) {
          titulo = `${evento.titulo} · ${escuela}`;
          descripcion = `${evento.titulo}: ${this.fechaLarga(evento.fecha)}${evento.hora ? `, ${evento.hora}` : ''}. ${escuela}.`;
          imagen = this.imagenEvento(evento) ?? logo;
          datos.push(this.evento(sitio, evento));
        }
        break;
      }
    }

    const url = this.absoluta(contexto.ruta || '/');
    this.titulo.setTitle(titulo);
    this.meta('name', 'description', descripcion);
    if (noindex) {
      this.meta('name', 'robots', NOINDEX);
    }
    this.enlace('canonical', url);
    this.meta('property', 'og:title', titulo);
    this.meta('property', 'og:description', descripcion);
    this.meta('property', 'og:url', url);
    this.meta('property', 'og:type', 'website');
    this.meta('property', 'og:site_name', escuela);
    if (imagen) {
      this.meta('property', 'og:image', imagen);
    }
    if (logo) {
      this.enlace('icon', logo);
    }
    for (const dato of datos) {
      const script = this.document.createElement('script');
      script.type = 'application/ld+json';
      script.setAttribute(MARCA, '');
      script.textContent = JSON.stringify(dato);
      this.document.head.appendChild(script);
    }
  }

  /** Web no publicada (404 de la API): solo `noindex`. */
  noPublicada(): void {
    this.limpiar();
    this.meta('name', 'robots', NOINDEX);
  }

  /** Página no encontrada (404 del tema): título con la escuela y `noindex`. */
  noEncontrada(sitio: SitioWeb): void {
    this.limpiar();
    this.titulo.setTitle(`No encontramos esta página · ${sitio.escuela.nombre}`);
    this.meta('name', 'robots', NOINDEX);
  }

  /** Quita todas las etiquetas de la plantilla (al salir de sus rutas). */
  limpiar(): void {
    for (const elemento of Array.from(this.document.head.querySelectorAll(`[${MARCA}]`))) {
      elemento.remove();
    }
  }

  private lugar(sitio: SitioWeb, sede: SedeWeb): JsonLd {
    const imagenes = [sede.fotoUrl, sede.foto2Url].filter((v): v is string => !!v);
    return sinVacios({
      '@context': 'https://schema.org',
      '@type': 'SportsActivityLocation',
      name: `${sitio.escuela.nombre} · ${sede.nombre}`,
      url: this.absoluta(sede.slug ? `/sedes/${encodeURIComponent(sede.slug)}` : '/'),
      telephone: sede.telefono,
      image: imagenes.length ? imagenes : null,
      address: this.direccion(sede),
      parentOrganization: this.organizacion(sitio),
    });
  }

  private evento(sitio: SitioWeb, evento: EventoWeb | EventoListaWeb): JsonLd {
    const sede = evento.sedeId != null ? sitio.sedes.find((s) => s.id === evento.sedeId) : undefined;
    const nombreLugar = evento.lugar ?? evento.sedeNombre;
    const imagen = this.imagenEvento(evento);
    return sinVacios({
      '@context': 'https://schema.org',
      '@type': 'Event',
      name: evento.titulo,
      description: 'descripcion' in evento ? evento.descripcion?.trim() : null,
      startDate: evento.hora ? `${evento.fecha}T${evento.hora}` : evento.fecha,
      url: this.absoluta(`/eventos/${evento.id}`),
      image: imagen ? [imagen] : null,
      location: nombreLugar || sede
        ? sinVacios({ '@type': 'Place', name: nombreLugar, address: sede ? this.direccion(sede) : null })
        : null,
      organizer: this.organizacion(sitio),
    });
  }

  private direccion(sede: SedeWeb): JsonLd | null {
    const direccion = sinVacios({
      streetAddress: sede.direccion,
      addressLocality: sede.localidad,
      postalCode: sede.codigoPostal,
    });
    return Object.keys(direccion).length ? { '@type': 'PostalAddress', ...direccion } : null;
  }

  private organizacion(sitio: SitioWeb): JsonLd {
    return { '@type': 'SportsOrganization', name: sitio.escuela.nombre, url: this.absoluta('/') };
  }

  private imagenEvento(evento: EventoWeb): string | null {
    return evento.conImagen ? this.absoluta(`${environment.apiUrl}/eventos/${evento.id}/imagen`) : null;
  }

  private textoBloque(sitio: SitioWeb, clave: string): string | null {
    const texto = sitio.bloques.find((b) => b.clave === clave)?.texto?.trim();
    return texto ? texto : null;
  }

  /** `2026-12-19` → «19 de diciembre de 2026» (sin pasar por la zona horaria). */
  private fechaLarga(fecha: string): string {
    const [anio, mes, dia] = fecha.split('-').map(Number);
    return new Date(anio, mes - 1, dia).toLocaleDateString('es-ES', { day: 'numeric', month: 'long', year: 'numeric' });
  }

  /** Absoluta con el origen de la página (las rutas de la API pueden ser relativas, `/api`, en producción). */
  private absoluta(ruta: string): string {
    return new URL(ruta, this.document.location.origin).href;
  }

  private meta(atributo: 'name' | 'property', valor: string, contenido: string): void {
    const meta = this.document.createElement('meta');
    meta.setAttribute(atributo, valor);
    meta.setAttribute('content', contenido);
    meta.setAttribute(MARCA, '');
    this.document.head.appendChild(meta);
  }

  private enlace(rel: string, href: string): void {
    const enlace = this.document.createElement('link');
    enlace.setAttribute('rel', rel);
    enlace.setAttribute('href', href);
    enlace.setAttribute(MARCA, '');
    this.document.head.appendChild(enlace);
  }
}

/** Sin claves nulas, indefinidas o de texto vacío: el JSON-LD solo lleva lo que hay. */
function sinVacios(objeto: Record<string, unknown>): JsonLd {
  return Object.fromEntries(Object.entries(objeto).filter(([, v]) => v != null && v !== ''));
}
