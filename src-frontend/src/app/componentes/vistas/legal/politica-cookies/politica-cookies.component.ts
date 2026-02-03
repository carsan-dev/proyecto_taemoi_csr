import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-politica-cookies',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './politica-cookies.component.html',
  styleUrl: '../legal.shared.scss',
})
export class PoliticaCookiesComponent {}
