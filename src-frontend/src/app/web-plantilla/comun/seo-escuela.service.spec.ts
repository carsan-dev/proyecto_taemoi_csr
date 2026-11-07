import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { sitioDePrueba } from '../datos/datos-de-prueba';
import { EventoWeb, SedeWeb, SitioWeb } from '../datos/modelos';
import { ContextoSeo, SeoEscuelaService } from './seo-escuela.service';

const ORIGEN = location.origin;

function sede(cambios: Partial<SedeWeb> = {}): SedeWeb {
  return {
    id: 1,
    nombre: 'Estepa',
    direccion: 'Calle Mayor 1',
    localidad: 'Estepa',
    codigoPostal: '41560',
    telefono: '955000000',
    slug: 'estepa',
    lugar: 'Pabellón municipal',
    indicacionesAcceso: null,
    fotoUrl: 'https://club.example/imagenes/estepa.jpg',
    foto2Url: null,
    grupos: [],
    ...cambios,
  };
}

function evento(cambios: Partial<EventoWeb> = {}): EventoWeb {
  return {
    id: 7,
    titulo: 'Exhibición de Navidad',
    fecha: '2026-12-19',
    conImagen: true,
    hora: '18:30',
    lugar: null,
    sedeId: 1,
    sedeNombre: 'Estepa',
    ...cambios,
  };
}

function sitio(cambios: Partial<SitioWeb> = {}): SitioWeb {
  return sitioDePrueba({
    sedes: [sede(), sede({ id: 2, nombre: 'Casariche', slug: 'casariche', direccion: null, fotoUrl: null })],
    proximosEventos: [evento(), evento({ id: 8, titulo: 'Examen', conImagen: false, hora: null, sedeId: null, sedeNombre: null, lugar: 'Polideportivo' })],
    ...cambios,
  });
}

function propias(): Element[] {
  return Array.from(document.head.querySelectorAll('[data-web-plantilla]'));
}

function meta(atributo: 'name' | 'property', valor: string): string | null {
  const encontradas = document.head.querySelectorAll(`meta[${atributo}="${valor}"][data-web-plantilla]`);
  expect(encontradas.length).withContext(valor).toBeLessThanOrEqual(1);
  return encontradas[0]?.getAttribute('content') ?? null;
}

function enlace(rel: string): string | null {
  const encontrados = document.head.querySelectorAll(`link[rel="${rel}"][data-web-plantilla]`);
  expect(encontrados.length).withContext(rel).toBeLessThanOrEqual(1);
  return encontrados[0]?.getAttribute('href') ?? null;
}

function jsonLd(): Record<string, unknown>[] {
  return Array.from(document.head.querySelectorAll('script[type="application/ld+json"][data-web-plantilla]')).map(
    (s) => JSON.parse(s.textContent ?? '') as Record<string, unknown>,
  );
}

describe('SeoEscuelaService', () => {
  let seo: SeoEscuelaService;
  let tituloAnterior: string;
  let ajenas: Element[];

  const logo = new URL(`${environment.apiUrl}/publico/escuela/logo?v=42`, ORIGEN).href;

  function aplicar(contexto: Partial<ContextoSeo> & Pick<ContextoSeo, 'pagina'>): void {
    seo.aplicar({ sitio: sitio(), ruta: '/', vistaPrevia: false, ...contexto });
  }

  beforeEach(() => {
    tituloAnterior = document.title;
    // El título del documento sí lo cambia (Title); el resto de lo ajeno, no.
    ajenas = Array.from(document.head.children).filter((e) => !e.hasAttribute('data-web-plantilla') && e.tagName !== 'TITLE');
    seo = TestBed.inject(SeoEscuelaService);
  });

  afterEach(() => {
    seo.limpiar();
    document.title = tituloAnterior;
  });

  it('portada: título, descripción, canonical, OG, favicon y un SportsActivityLocation por sede', () => {
    aplicar({ pagina: 'portada', ruta: '/' });

    expect(document.title).toBe('Taekwondo Sierra Sur');
    expect(meta('name', 'description')).toContain('Taekwondo Sierra Sur');
    expect(enlace('canonical')).toBe(`${ORIGEN}/`);
    expect(meta('property', 'og:title')).toBe('Taekwondo Sierra Sur');
    expect(meta('property', 'og:description')).toBe(meta('name', 'description'));
    expect(meta('property', 'og:url')).toBe(`${ORIGEN}/`);
    expect(meta('property', 'og:type')).toBe('website');
    expect(meta('property', 'og:site_name')).toBe('Taekwondo Sierra Sur');
    expect(meta('property', 'og:image')).toBe(logo);
    expect(enlace('icon')).toBe(logo);
    expect(meta('name', 'robots')).toBeNull();

    const datos = jsonLd();
    expect(datos.length).toBe(2);
    expect(datos[0]).toEqual({
      '@context': 'https://schema.org',
      '@type': 'SportsActivityLocation',
      name: 'Taekwondo Sierra Sur · Estepa',
      url: `${ORIGEN}/sedes/estepa`,
      telephone: '955000000',
      image: ['https://club.example/imagenes/estepa.jpg'],
      address: {
        '@type': 'PostalAddress',
        streetAddress: 'Calle Mayor 1',
        addressLocality: 'Estepa',
        postalCode: '41560',
      },
      parentOrganization: { '@type': 'SportsOrganization', name: 'Taekwondo Sierra Sur', url: `${ORIGEN}/` },
    });
    // Sin dirección ni foto: esos campos no salen (nada inventado).
    expect(datos[1]['address']).toEqual({ '@type': 'PostalAddress', addressLocality: 'Estepa', postalCode: '41560' });
    expect(datos[1]['image']).toBeUndefined();
  });

  it('la descripción de la portada sale del bloque portada.frase si lo hay', () => {
    aplicar({
      pagina: 'portada',
      sitio: sitio({ bloques: [{ clave: 'portada.frase', texto: '  Taekwondo en seis pueblos.  ', imagenUrl: null, orden: 1 }] }),
    });
    expect(meta('name', 'description')).toBe('Taekwondo en seis pueblos.');
  });

  it('sin logo: ni og:image ni favicon', () => {
    aplicar({ pagina: 'tarifas', ruta: '/tarifas', sitio: sitio({ escuela: { nombre: 'Club', colorPrimario: null, logoVersion: null } }) });
    expect(meta('property', 'og:image')).toBeNull();
    expect(enlace('icon')).toBeNull();
  });

  it('sede: su título, su canonical, su foto y solo su SportsActivityLocation', () => {
    aplicar({ pagina: 'sede', ruta: '/sedes/estepa', slug: 'estepa' });

    expect(document.title).toBe('Estepa · Taekwondo Sierra Sur');
    expect(meta('name', 'description')).toContain('Pabellón municipal');
    expect(enlace('canonical')).toBe(`${ORIGEN}/sedes/estepa`);
    expect(meta('property', 'og:image')).toBe('https://club.example/imagenes/estepa.jpg');
    const datos = jsonLd();
    expect(datos.length).toBe(1);
    expect(datos[0]['name']).toBe('Taekwondo Sierra Sur · Estepa');
  });

  it('sede que no existe: noindex y sin JSON-LD', () => {
    aplicar({ pagina: 'sede', ruta: '/sedes/otra', slug: 'otra' });
    expect(meta('name', 'robots')).toBe('noindex, nofollow');
    expect(jsonLd()).toEqual([]);
  });

  it('tarifas, preinscripción y legales: título de la página y canonical sin parámetros', () => {
    aplicar({ pagina: 'tarifas', ruta: '/tarifas' });
    expect(document.title).toBe('Tarifas · Taekwondo Sierra Sur');
    expect(enlace('canonical')).toBe(`${ORIGEN}/tarifas`);
    expect(jsonLd()).toEqual([]);

    aplicar({ pagina: 'preinscripcion', ruta: '/preinscripcion' });
    expect(document.title).toBe('Preinscripción · Taekwondo Sierra Sur');
    expect(enlace('canonical')).toBe(`${ORIGEN}/preinscripcion`);

    aplicar({ pagina: 'legales', ruta: '/legales' });
    expect(document.title).toBe('Aviso legal y privacidad · Taekwondo Sierra Sur');
    expect(enlace('canonical')).toBe(`${ORIGEN}/legales`);
  });

  it('eventos: un Event por evento, con hora, sede o lugar y foto si la tiene', () => {
    aplicar({ pagina: 'eventos', ruta: '/eventos' });

    expect(document.title).toBe('Eventos · Taekwondo Sierra Sur');
    const datos = jsonLd();
    expect(datos.length).toBe(2);
    expect(datos[0]).toEqual({
      '@context': 'https://schema.org',
      '@type': 'Event',
      name: 'Exhibición de Navidad',
      startDate: '2026-12-19T18:30',
      url: `${ORIGEN}/eventos/7`,
      image: [new URL(`${environment.apiUrl}/eventos/7/imagen`, ORIGEN).href],
      location: {
        '@type': 'Place',
        name: 'Estepa',
        address: {
          '@type': 'PostalAddress',
          streetAddress: 'Calle Mayor 1',
          addressLocality: 'Estepa',
          postalCode: '41560',
        },
      },
      organizer: { '@type': 'SportsOrganization', name: 'Taekwondo Sierra Sur', url: `${ORIGEN}/` },
    });
    expect(datos[1]['startDate']).toBe('2026-12-19');
    expect(datos[1]['image']).toBeUndefined();
    expect(datos[1]['location']).toEqual({ '@type': 'Place', name: 'Polideportivo' });
  });

  it('evento: su título, su foto en OG y solo su Event', () => {
    aplicar({ pagina: 'evento', ruta: '/eventos/7', eventoId: 7 });

    expect(document.title).toBe('Exhibición de Navidad · Taekwondo Sierra Sur');
    expect(enlace('canonical')).toBe(`${ORIGEN}/eventos/7`);
    expect(meta('property', 'og:image')).toBe(new URL(`${environment.apiUrl}/eventos/7/imagen`, ORIGEN).href);
    const datos = jsonLd();
    expect(datos.length).toBe(1);
    expect(datos[0]['@type']).toBe('Event');
    expect(datos[0]['name']).toBe('Exhibición de Navidad');
  });

  it('evento que no está entre los próximos: título de Eventos y sin JSON-LD', () => {
    aplicar({ pagina: 'evento', ruta: '/eventos/99', eventoId: 99 });
    expect(document.title).toBe('Eventos · Taekwondo Sierra Sur');
    expect(enlace('canonical')).toBe(`${ORIGEN}/eventos/99`);
    expect(jsonLd()).toEqual([]);
  });

  it('evento con sus datos (W5): Event con la descripción y el lugar, aunque no esté entre los próximos', () => {
    aplicar({
      pagina: 'evento',
      ruta: '/eventos/40',
      eventoId: 40,
      evento: {
        ...evento({ id: 40, titulo: 'Exhibición de fin de curso', fecha: '2026-06-20', hora: null, conImagen: false }),
        lugar: 'Pabellón municipal',
        descripcion: '  Cerramos el curso con una exhibición.  ',
      },
    });

    expect(document.title).toBe('Exhibición de fin de curso · Taekwondo Sierra Sur');
    expect(meta('property', 'og:image')).toBe(logo);
    const datos = jsonLd();
    expect(datos.length).toBe(1);
    expect(datos[0]['description']).toBe('Cerramos el curso con una exhibición.');
    expect(datos[0]['startDate']).toBe('2026-06-20');
    expect(datos[0]['url']).toBe(`${ORIGEN}/eventos/40`);
    expect(datos[0]['location']).toEqual({
      '@type': 'Place',
      name: 'Pabellón municipal',
      address: { '@type': 'PostalAddress', streetAddress: 'Calle Mayor 1', addressLocality: 'Estepa', postalCode: '41560' },
    });
  });

  it('evento sin descripción: el Event no lleva description', () => {
    aplicar({ pagina: 'evento', ruta: '/eventos/7', eventoId: 7, evento: { ...evento(), descripcion: null } });
    expect(jsonLd()[0]['description']).toBeUndefined();
  });

  it('eventos con la lista de la página: un Event por próximo, con su descripción', () => {
    aplicar({
      pagina: 'eventos',
      ruta: '/eventos',
      eventos: [
        { ...evento({ id: 1 }), descripcion: 'Uno' },
        { ...evento({ id: 2 }), descripcion: null },
        { ...evento({ id: 3 }), descripcion: 'Tres' },
      ],
    });
    expect(jsonLd().map((d) => [d['url'], d['description']])).toEqual([
      [`${ORIGEN}/eventos/1`, 'Uno'],
      [`${ORIGEN}/eventos/2`, undefined],
      [`${ORIGEN}/eventos/3`, 'Tres'],
    ]);
  });

  it('vista previa: robots noindex en todas las páginas', () => {
    aplicar({ pagina: 'portada', vistaPrevia: true });
    expect(meta('name', 'robots')).toBe('noindex, nofollow');
    aplicar({ pagina: 'tarifas', ruta: '/tarifas', vistaPrevia: true });
    expect(meta('name', 'robots')).toBe('noindex, nofollow');
    aplicar({ pagina: 'tarifas', ruta: '/tarifas', vistaPrevia: false });
    expect(meta('name', 'robots')).toBeNull();
  });

  it('web no publicada: solo noindex', () => {
    seo.noPublicada();
    expect(propias().map((e) => e.outerHTML)).toEqual(['<meta name="robots" content="noindex, nofollow" data-web-plantilla="">']);
  });

  it('nada se acumula al navegar: cada página sustituye las etiquetas de la anterior', () => {
    aplicar({ pagina: 'portada' });
    const enPortada = propias().length;
    aplicar({ pagina: 'eventos', ruta: '/eventos' });
    aplicar({ pagina: 'sede', ruta: '/sedes/estepa', slug: 'estepa' });
    aplicar({ pagina: 'portada' });
    expect(propias().length).toBe(enPortada);
    expect(document.head.querySelectorAll('link[rel="canonical"][data-web-plantilla]').length).toBe(1);
  });

  it('solo toca sus etiquetas: las ajenas siguen como estaban y limpiar() quita todas las suyas', () => {
    const antes = ajenas.map((e) => e.outerHTML);
    aplicar({ pagina: 'portada', vistaPrevia: true });
    aplicar({ pagina: 'evento', ruta: '/eventos/7', eventoId: 7 });
    expect(ajenas.every((e) => e.isConnected)).toBeTrue();
    expect(ajenas.map((e) => e.outerHTML)).toEqual(antes);

    seo.limpiar();
    expect(propias()).toEqual([]);
    expect(ajenas.every((e) => e.isConnected)).toBeTrue();
  });
});
