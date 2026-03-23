import { Component, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { SeoService } from '../../../servicios/generales/seo.service';

@Component({
  selector: 'app-no-encontrado',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './no-encontrado.html',
  styleUrl: './no-encontrado.scss',
})
export class NoEncontrado implements OnInit {
  constructor(
    private readonly router: Router,
    private readonly seoService: SeoService
  ) {}

  ngOnInit(): void {
    this.seoService.updateDynamicSeo({
      title: 'Página no encontrada (404) | Moiskimdo',
      description: 'La página que buscas no existe. Vuelve al inicio para explorar nuestras clases de Taekwondo, Kickboxing y Pilates en Umbrete.',
      canonical: 'https://moiskimdo.es/',
      breadcrumbs: [
        { name: 'Inicio', url: '/' },
      ],
      noIndex: true,
    });
  }

  volver(): void {
    this.router.navigate(['/']);
  }
}
