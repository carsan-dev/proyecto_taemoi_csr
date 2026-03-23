export interface SeoMeta {
  title?: string;
  description?: string;
  canonical?: string;
  ogImage?: string;
  noIndex?: boolean;
}

const BASE_URL = 'https://moiskimdo.es';

 export const SEO_DEFAULTS: Required<Pick<SeoMeta, 'title' | 'description' | 'ogImage'>> = {
  title: "Moi's Kim Do Umbrete | Taekwondo, Kickboxing, Pilates y Defensa Personal",
  description:
    "Moi's Kim Do es tu escuela de artes marciales en Umbrete. Clases de taekwondo, kickboxing, pilates y defensa personal para niños, jóvenes y adultos.",
  ogImage: `${BASE_URL}/assets/media/fachada_escuela.webp`,
};

export const SEO_ROUTES = {
  home: {
    ...SEO_DEFAULTS,
    canonical: `${BASE_URL}/`,
  },
  aljarafe: {
    title: "Moi's Kim Do Aljarafe | Escuela de Taekwondo y Kickboxing en Umbrete",
    description:
      "Si buscas Moi's Kim Do en el Aljarafe, entrenamos en Umbrete. Taekwondo, kickboxing, pilates y defensa personal con grupos por edad y nivel.",
    canonical: `${BASE_URL}/aljarafe`,
    ogImage: `${BASE_URL}/assets/media/fachada_escuela.webp`,
  },
  sevilla: {
    title: "Moi's Kim Do Sevilla | Escuela en Umbrete de Taekwondo y Kickboxing",
    description:
      "Moi's Kim Do entrena en Umbrete, cerca de Sevilla y del Aljarafe. Escuela de taekwondo, kickboxing, pilates y defensa personal para todas las edades.",
    canonical: `${BASE_URL}/sevilla`,
    ogImage: `${BASE_URL}/assets/media/fachada_escuela.webp`,
  },
  taekwondo: {
    title: "Taekwondo Moi's Kim Do Umbrete | Clases para niños y adultos",
    description:
      "Clases de taekwondo en Umbrete con Moi's Kim Do. Iniciación y niveles avanzados para niños, jóvenes y adultos en el Aljarafe sevillano.",
    canonical: `${BASE_URL}/taekwondo`,
    ogImage: `${BASE_URL}/assets/media/taekwondo_banner.webp`,
  },
  kickboxing: {
    title: "Kickboxing Moi's Kim Do Umbrete | Clases en el Aljarafe",
    description:
      "Entrena kickboxing en Umbrete con Moi's Kim Do. Clases seguras y dinámicas para mejorar técnica, forma física y confianza personal.",
    canonical: `${BASE_URL}/kickboxing`,
    ogImage: `${BASE_URL}/assets/media/kickboxing_banner.webp`,
  },
  pilates: {
    title: "Pilates Moi's Kim Do Umbrete | Clases en el Aljarafe sevillano",
    description:
      "Clases de pilates en Umbrete con Moi's Kim Do. Mejora postura, fuerza y movilidad en grupos adaptados a diferentes niveles.",
    canonical: `${BASE_URL}/pilates`,
    ogImage: `${BASE_URL}/assets/media/pilates_suelo.webp`,
  },
  defensaPersonal: {
    title: "Defensa Personal Femenina Moi's Kim Do | Umbrete y Aljarafe",
    description:
      "Clases de defensa personal femenina en Umbrete con Moi's Kim Do. Trabajo práctico, técnica útil y mejora de la confianza en un entorno seguro.",
    canonical: `${BASE_URL}/defensa-personal-femenina`,
    ogImage: `${BASE_URL}/assets/media/defensa_personal_banner.webp`,
  },
  horarios: {
    title: 'Horarios de Deporte en Umbrete | Taekwondo, Kickboxing, Pilates y Defensa',
    description:
      'Consulta horarios de clases en Umbrete (Sevilla y Aljarafe) para taekwondo, kickboxing light, pilates balance y defensa personal femenina.',
    canonical: `${BASE_URL}/horarios`,
    ogImage: `${BASE_URL}/assets/media/interior_escuela.webp`,
  },
  eventos: {
    title: "Eventos de Taekwondo, Kickboxing y mas en Umbrete y Sevilla | Moi's Kim Do",
    description:
      'Eventos, competiciones y actividades de taekwondo y kickboxing en Umbrete, Aljarafe y Sevilla.',
    canonical: `${BASE_URL}/eventos`,
    ogImage: `${BASE_URL}/assets/media/campeonato_andalucia_cadete_2024-04-27.webp`,
  },
  contacto: {
    title: "Contacto Moi's Kim Do | Escuela de Artes Marciales en Umbrete (Sevilla)",
    description:
      "Contacta con Moi's Kim Do en Umbrete (Sevilla y Aljarafe) para informacion de taekwondo, kickboxing, pilates y defensa personal femenina.",
    canonical: `${BASE_URL}/contacto`,
    ogImage: `${BASE_URL}/assets/media/recepcion_escuela.webp`,
  },
  privacidad: {
    title: "Politica de privacidad | Moi's Kim Do",
    description:
      "Informacion sobre el tratamiento de datos personales y derechos de los usuarios en Moi's Kim Do.",
    canonical: `${BASE_URL}/politica-privacidad`,
    ogImage: `${BASE_URL}/assets/media/fachada_escuela.webp`,
  },
  cookies: {
    title: "Politica de cookies | Moi's Kim Do",
    description:
      "Informacion sobre el uso de cookies y analitica en la web de Moi's Kim Do.",
    canonical: `${BASE_URL}/politica-cookies`,
    ogImage: `${BASE_URL}/assets/media/fachada_escuela.webp`,
  },
  avisoLegal: {
    title: "Aviso legal | Moi's Kim Do",
    description:
      "Informacion legal y condiciones de uso del sitio web de Moi's Kim Do.",
    canonical: `${BASE_URL}/aviso-legal`,
    ogImage: `${BASE_URL}/assets/media/fachada_escuela.webp`,
  },
  tarifas: {
    title: "Tarifas y Precios | Clases de Artes Marciales en Umbrete (Sevilla) - Moi's Kim Do",
    description:
      'Consulta las tarifas de taekwondo, kickboxing, pilates y defensa personal femenina en Umbrete (Sevilla). Descuentos familiares y primera clase gratuita.',
    canonical: `${BASE_URL}/tarifas`,
    ogImage: `${BASE_URL}/assets/media/fachada_escuela.webp`,
  },
  noIndex: {
    noIndex: true,
  },
} satisfies Record<string, SeoMeta>;
