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
    canonical: `${BASE_URL}/inicio`,
  },
  taekwondo: {
    title: 'Taekwondo en Umbrete (Sevilla) | Clases para ninos y adultos - Moiskimdo',
    description:
      'Clases de taekwondo en Umbrete y Sevilla (Aljarafe). Iniciacion, tecnificacion y competicion con instructores certificados.',
    keywords:
      "taekwondo umbrete, taekwondo sevilla, escuela de taekwondo, clases de taekwondo, taekwondo aljarafe, taekwondo andalucia, taekwondo ninos, taekwondo adultos, club taekwondo sevilla, moiskimdo, club mois kim do, club moi's kim do",
    canonical: `${BASE_URL}/eltaekwondo`,
    ogImage: `${BASE_URL}/assets/media/taekwondo_banner.webp`,
  },
  kickboxing: {
    title: 'Kickboxing Light en Umbrete (Sevilla) | Moiskimdo',
    description:
      'Kickboxing light en Umbrete y Sevilla. Entrenamientos seguros para todas las edades con tecnica, cardio y defensa personal.',
    keywords:
      "kickboxing light, kickboxing umbrete, kickboxing sevilla, kickboxing aljarafe, clases de kickboxing, kickboxing andalucia, kickboxing mujeres, moiskimdo, club mois kim do, club moi's kim do",
    canonical: `${BASE_URL}/kickboxing`,
    ogImage: `${BASE_URL}/assets/media/kickboxing_banner.webp`,
  },
  pilates: {
    title: 'Pilates Balance en Umbrete (Sevilla) | Moiskimdo',
    description:
      'Clases de pilates balance en Umbrete y Sevilla. Postura, fuerza y flexibilidad con grupos reducidos.',
    keywords:
      "pilates umbrete, pilates sevilla, pilates aljarafe, pilates balance, clases de pilates, pilates para adultos, pilates para mujeres, pilates andalucia, moiskimdo, club mois kim do, club moi's kim do",
    canonical: `${BASE_URL}/pilates`,
    ogImage: `${BASE_URL}/assets/media/pilates_suelo.webp`,
  },
  defensaPersonal: {
    title: 'Defensa Personal Femenina en Umbrete (Sevilla) | Moiskimdo',
    description:
      'Defensa personal femenina en Umbrete y Sevilla. Tecnicas practicas de autodefensa, seguridad y confianza.',
    keywords:
      "defensa personal femenina, defensa personal mujeres, autodefensa femenina, defensa personal sevilla, defensa personal umbrete, defensa personal aljarafe, defensa personal andalucia, moiskimdo, club mois kim do, club moi's kim do",
    canonical: `${BASE_URL}/defensapersonalfemenina`,
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
  noIndex: {
    noIndex: true,
  },
} satisfies Record<string, SeoMeta>;
