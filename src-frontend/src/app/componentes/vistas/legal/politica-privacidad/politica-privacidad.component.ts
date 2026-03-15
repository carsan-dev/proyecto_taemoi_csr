import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-politica-privacidad',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './politica-privacidad.component.html',
  styleUrl: '../legal.shared.scss',
})
export class PoliticaPrivacidadComponent {}
