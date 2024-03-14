import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { SidebarService } from '../../../../servicios/generales/sidebar.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent implements OnInit {
  estaColapsado: boolean = false;

  constructor(private router: Router, private sidebarService: SidebarService) {}

  ngOnInit(): void {
    this.sidebarService.obtenerSubjectAlternable().subscribe(() => {
      this.alternarColapso();
    });
  }
  alternarColapso() {
    this.estaColapsado = !this.estaColapsado;
  }

  irAListado() {
    this.router.navigate(['/alumnos']);
  }

  irACrear() {
    this.router.navigate(['/alumnos/crear']);
  }
}
