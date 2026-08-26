import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SeoMeta } from '../../../core/constants/seo.constants';

type LocalLandingKey = 'aljarafe' | 'sevilla';

type LocalLandingContent = {
  badge: string;
  introTitle: string;
  introParagraphs: string[];
  reasonsTitle: string;
  reasons: string[];
  routeTitle: string;
  routeParagraph: string;
};

type LocalLandingViewModel = LocalLandingContent & {
  title: string;
  description: string;
};

type ServiceLink = {
  label: string;
  route: string;
  icon: string;
};

const SERVICE_LINKS: ServiceLink[] = [
  { label: 'Taekwondo', route: '/taekwondo', icon: 'bi bi-trophy-fill' },
  { label: 'Kickboxing', route: '/kickboxing', icon: 'bi bi-fire' },
  { label: 'Pilates', route: '/pilates', icon: 'bi bi-heart-pulse-fill' },
  {
    label: 'Defensa Personal Femenina',
    route: '/defensa-personal-femenina',
    icon: 'bi bi-shield-fill-check',
  },
];

const LANDING_CONTENT: Record<LocalLandingKey, LocalLandingContent> = {
  aljarafe: {
    badge: "Moi's Kim Do en el Aljarafe",
    introTitle: 'Escuela física en Umbrete para alumnos del Aljarafe',
    introParagraphs: [
      "Si buscas Moi's Kim Do en el Aljarafe, nuestras clases se imparten en Umbrete. Mantenemos la ubicación física real en Calle Parada de la Cigüeña 36, 41806 Umbrete, Sevilla.",
      'Entrenamos taekwondo, kickboxing, pilates y defensa personal femenina con grupos por edad y nivel para niños, jóvenes y adultos.',
    ],
    reasonsTitle: "Por qué elegir Moi's Kim Do si vienes desde el Aljarafe",
    reasons: [
      'Ubicación real y estable en Umbrete.',
      'Grupos diferenciados por edad y nivel.',
      'Horarios de tarde para varias disciplinas.',
      'Primera clase gratuita bajo disponibilidad.',
    ],
    routeTitle: "Moi's Kim Do sigue en Umbrete",
    routeParagraph:
      'La referencia local de la escuela sigue siendo Umbrete. Desde aquí atendemos a alumnado de Umbrete, del Aljarafe y de Sevilla sin mover la dirección física del centro.',
  },
  sevilla: {
    badge: "Moi's Kim Do cerca de Sevilla",
    introTitle: "Escuela en Umbrete para quienes buscan Moi's Kim Do desde Sevilla",
    introParagraphs: [
      "Moi's Kim Do entrena en Umbrete, cerca de Sevilla y del Aljarafe. La escuela mantiene su ubicación física real en Calle Parada de la Cigüeña 36, 41806 Umbrete, Sevilla.",
      'Trabajamos taekwondo, kickboxing, pilates y defensa personal femenina con grupos por edad y nivel para niños, jóvenes y adultos.',
    ],
    reasonsTitle: "Por qué venir a Moi's Kim Do desde Sevilla",
    reasons: [
      'Escuela especializada con ubicación física en Umbrete.',
      'Clases para iniciación y niveles avanzados.',
      'Entrenamientos seguros, dinámicos y guiados.',
      'Horarios compatibles con la tarde entre semana.',
    ],
    routeTitle: "Umbrete es la sede real de Moi's Kim Do",
    routeParagraph:
      "Aunque muchos usuarios buscan Moi's Kim Do desde Sevilla, la escuela entrena en Umbrete y mantiene ahí su punto de encuentro, su dirección y la experiencia presencial completa.",
  },
};

@Component({
  selector: 'app-localidad-landing',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './localidad-landing.component.html',
  styleUrl: './localidad-landing.component.scss',
})
export class LocalidadLandingComponent implements OnInit {
  landing: LocalLandingViewModel = {
    title: '',
    description: '',
    ...LANDING_CONTENT.aljarafe,
  };
  readonly serviceLinks = SERVICE_LINKS;

  constructor(private readonly route: ActivatedRoute) {}

  ngOnInit(): void {
    const key = (this.route.snapshot.data['landingKey'] as LocalLandingKey | undefined) ?? 'aljarafe';
    const seo = (this.route.snapshot.data['seo'] as SeoMeta | undefined) ?? {};
    const content = LANDING_CONTENT[key];

    this.landing = {
      title: seo.title ?? '',
      description: seo.description ?? '',
      ...content,
    };
  }
}
