export interface SeoMeta {
  title?: string;
  description?: string;
  keywords?: string;
  canonical?: string;
  ogImage?: string;
  noIndex?: boolean;
}

const BASE_URL = 'https://moiskimdo.es';

export const SEO_DEFAULTS: Required<Pick<SeoMeta, 'title' | 'description' | 'keywords' | 'ogImage'>> = {
  title: 'Moiskimdo | Taekwondo, Kickboxing, Pilates y Defensa Personal Femenina en Umbrete (Sevilla)',
  description:
    'Escuela de artes marciales en Umbrete (Sevilla). Clases de taekwondo, kickboxing light, pilates balance y defensa personal femenina para ninos y adultos en el Aljarafe y Andalucia.',
  keywords:
    "moiskimdo, club mois kim do, club moi's kim do, taekwondo umbrete, taekwondo sevilla, taekwondo andalucia, kickboxing umbrete, kickboxing sevilla, pilates umbrete, pilates sevilla, defensa personal femenina sevilla, defensa personal mujeres, artes marciales umbrete, escuela de taekwondo, clases taekwondo ninos, clases taekwondo adultos, kickboxing light, pilates balance, aljarafe sevilla",
  ogImage: `${BASE_URL}/assets/media/fachada_escuela.webp`,
};

export const SEO_ROUTES = {
  home: {
    ...SEO_DEFAULTS,
    canonical: `${BASE_URL}/`,
  },
  taekwondo: {
    title: "Taekwondo en Umbrete y Aljarafe (Sevilla) | Club Moi's Kim Do",
    description:
      'Escuela de artes marciales en Umbrete (Aljarafe, Sevilla). Clases de taekwondo para ninos y adultos con instructores certificados.',
    keywords:
      "taekwondo umbrete, taekwondo aljarafe, taekwondo sevilla, escuela de taekwondo, clases de taekwondo, escuela artes marciales sevilla, taekwondo andalucia, taekwondo ninos, taekwondo adultos, club taekwondo sevilla, moiskimdo, club mois kim do, club moi's kim do",
    canonical: `${BASE_URL}/taekwondo`,
    ogImage: `${BASE_URL}/assets/media/taekwondo_banner.webp`,
  },
  kickboxing: {
    title: "Kickboxing Light en Umbrete y Aljarafe (Sevilla) | Club Moi's Kim Do",
    description:
      'Escuela de artes marciales en Umbrete (Aljarafe, Sevilla). Kickboxing light seguro para todas las edades con tecnica, cardio y defensa personal.',
    keywords:
      "kickboxing light, kickboxing umbrete, kickboxing aljarafe, kickboxing sevilla, clases de kickboxing, escuela artes marciales sevilla, kickboxing andalucia, kickboxing mujeres, moiskimdo, club mois kim do, club moi's kim do",
    canonical: `${BASE_URL}/kickboxing`,
    ogImage: `${BASE_URL}/assets/media/kickboxing_banner.webp`,
  },
  pilates: {
    title: "Pilates Balance en Umbrete y Aljarafe (Sevilla) | Club Moi's Kim Do",
    description:
      'Clases de pilates balance en Umbrete (Aljarafe, Sevilla). Postura, fuerza y flexibilidad en una escuela de artes marciales.',
    keywords:
      "pilates umbrete, pilates aljarafe, pilates sevilla, pilates balance, clases de pilates, escuela artes marciales sevilla, pilates para adultos, pilates para mujeres, pilates andalucia, moiskimdo, club mois kim do, club moi's kim do",
    canonical: `${BASE_URL}/pilates`,
    ogImage: `${BASE_URL}/assets/media/pilates_suelo.webp`,
  },
  defensaPersonal: {
    title: "Defensa Personal Femenina en Umbrete y Aljarafe (Sevilla) | Club Moi's Kim Do",
    description:
      'Escuela de artes marciales en Umbrete (Aljarafe, Sevilla). Defensa personal femenina con tecnicas practicas, seguridad y confianza.',
    keywords:
      "defensa personal femenina, defensa personal mujeres, autodefensa femenina, defensa personal umbrete, defensa personal aljarafe, defensa personal sevilla, escuela artes marciales sevilla, defensa personal andalucia, moiskimdo, club mois kim do, club moi's kim do",
    canonical: `${BASE_URL}/defensa-personal-femenina`,
    ogImage: `${BASE_URL}/assets/media/defensa_personal_banner.webp`,
  },
  horarios: {
    title: 'Horarios de Taekwondo, Kickboxing y Pilates en Umbrete | Moiskimdo',
    description:
      'Consulta horarios de clases en Umbrete (Sevilla) para taekwondo, kickboxing light, pilates balance y defensa personal femenina.',
    keywords:
      "horarios taekwondo umbrete, horarios kickboxing sevilla, horarios pilates umbrete, horarios defensa personal femenina, horarios moiskimdo, club mois kim do, club moi's kim do",
    canonical: `${BASE_URL}/horarios`,
    ogImage: `${BASE_URL}/assets/media/interior_escuela.webp`,
  },
  eventos: {
    title: 'Eventos y competiciones en Sevilla | Taekwondo y Kickboxing - Moiskimdo',
    description:
      'Eventos, competiciones y actividades de taekwondo y kickboxing en Umbrete y Sevilla.',
    keywords:
      "eventos taekwondo sevilla, competiciones taekwondo andalucia, eventos kickboxing, actividades artes marciales, torneos taekwondo, moiskimdo, club mois kim do, club moi's kim do",
    canonical: `${BASE_URL}/eventos`,
    ogImage: `${BASE_URL}/assets/media/campeonato_andalucia_cadete_2024-04-27.webp`,
  },
  contacto: {
    title: 'Contacto Moiskimdo | Escuela de Artes Marciales en Umbrete (Sevilla)',
    description:
      'Contacta con Moiskimdo en Umbrete (Sevilla) para informacion de taekwondo, kickboxing, pilates y defensa personal femenina.',
    keywords:
      "contacto taekwondo umbrete, escuela artes marciales sevilla, contacto kickboxing, contacto pilates, contacto defensa personal femenina, moiskimdo, club mois kim do, club moi's kim do",
    canonical: `${BASE_URL}/contacto`,
    ogImage: `${BASE_URL}/assets/media/recepcion_escuela.webp`,
  },
  privacidad: {
    title: 'PolÃ­tica de privacidad | Moiskimdo',
    description:
      'InformaciÃ³n sobre el tratamiento de datos personales y derechos de los usuarios en Moiskimdo.',
    keywords: 'politica de privacidad moiskimdo, datos personales, proteccion de datos',
    canonical: `${BASE_URL}/politica-privacidad`,
    ogImage: `${BASE_URL}/assets/media/fachada_escuela.webp`,
  },
  cookies: {
    title: 'PolÃ­tica de cookies | Moiskimdo',
    description:
      'InformaciÃ³n sobre el uso de cookies y analÃ­tica en la web de Moiskimdo.',
    keywords: 'politica de cookies moiskimdo, cookies, analitica',
    canonical: `${BASE_URL}/politica-cookies`,
    ogImage: `${BASE_URL}/assets/media/fachada_escuela.webp`,
  },
  avisoLegal: {
    title: 'Aviso legal | Moiskimdo',
    description:
      'InformaciÃ³n legal y condiciones de uso del sitio web de Moiskimdo.',
    keywords: 'aviso legal moiskimdo, condiciones de uso, informacion legal',
    canonical: `${BASE_URL}/aviso-legal`,
    ogImage: `${BASE_URL}/assets/media/fachada_escuela.webp`,
  },
  tarifas: {
    title: 'Tarifas y Precios | Clases de Artes Marciales en Umbrete (Sevilla) - Moiskimdo',
    description:
      'Consulta las tarifas de taekwondo, kickboxing, pilates y defensa personal femenina en Umbrete (Sevilla). Descuentos familiares y primera clase gratuita.',
    keywords:
      "precios taekwondo umbrete, tarifas artes marciales sevilla, cuanto cuesta taekwondo, precios kickboxing, precios pilates umbrete, descuentos familiares, moiskimdo, club mois kim do, club moi's kim do",
    canonical: `${BASE_URL}/tarifas`,
    ogImage: `${BASE_URL}/assets/media/fachada_escuela.webp`,
  },
  noIndex: {
    noIndex: true,
  },
} satisfies Record<string, SeoMeta>;

