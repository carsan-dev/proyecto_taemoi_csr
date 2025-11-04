import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { PaginacionComponent } from '../paginacion/paginacion.component';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

@Component({
  selector: 'app-grupo-alumnos-modal',
  standalone: true,
  imports: [CommonModule, PaginacionComponent, FormsModule],
  templateUrl: './grupo-alumnos-modal.component.html',
  styleUrl: './grupo-alumnos-modal.component.scss'
})
export class GrupoAlumnosModalComponent implements OnInit, OnDestroy {
  @Input() grupoNombre!: string;
  @Input() alumnos!: any[];
  @Output() cerrar = new EventEmitter<void>();

  modalVisible = false;

  // Pagination properties
  paginaActual = 1;
  tamanoPagina = 8;
  totalPaginas = 0;

  // Search filter
  nombreFiltro: string = '';
  alumnosFiltrados: any[] = [];
  private searchSubject = new Subject<string>();

  constructor(private readonly router: Router) {}

  ngOnInit(): void {
    setTimeout(() => {
      this.modalVisible = true;
    }, 0);

    // Initialize filtered list with all alumnos
    this.alumnosFiltrados = this.alumnos || [];
    this.calcularTotalPaginas();

    // Setup debounced search
    this.searchSubject.pipe(
      debounceTime(300), // Wait 300ms after user stops typing
      distinctUntilChanged() // Only trigger if value actually changed
    ).subscribe(() => {
      this.filtrarAlumnos();
    });
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  calcularTotalPaginas(): void {
    if (this.alumnosFiltrados && this.alumnosFiltrados.length > 0) {
      this.totalPaginas = Math.ceil(this.alumnosFiltrados.length / this.tamanoPagina);
    } else {
      this.totalPaginas = 0;
    }
  }

  get alumnosPaginados(): any[] {
    if (!this.alumnosFiltrados || this.alumnosFiltrados.length === 0) {
      return [];
    }
    const start = (this.paginaActual - 1) * this.tamanoPagina;
    const end = start + this.tamanoPagina;
    return this.alumnosFiltrados.slice(start, end);
  }

  filtrarAlumnos(): void {
    if (!this.nombreFiltro || this.nombreFiltro.trim() === '') {
      this.alumnosFiltrados = this.alumnos || [];
    } else {
      const filtroLowerCase = this.nombreFiltro.toLowerCase().trim();
      this.alumnosFiltrados = (this.alumnos || []).filter(alumno =>
        `${alumno.nombre} ${alumno.apellidos}`.toLowerCase().includes(filtroLowerCase)
      );
    }
    this.paginaActual = 1;
    this.calcularTotalPaginas();
  }

  onSearchChange(): void {
    this.searchSubject.next(this.nombreFiltro);
  }

  navegarAAlumno(alumno: any): void {
    // Navigate to alumno edit page
    this.router.navigate(['/alumnosEditar', alumno.id]);

    // Close modal after navigation
    this.cerrarModal();
  }

  cambiarPagina(pageNumber: number): void {
    if (pageNumber >= 1 && pageNumber <= this.totalPaginas) {
      this.paginaActual = pageNumber;
    }
  }

  cerrarModal() {
    this.modalVisible = false;
    setTimeout(() => {
      this.cerrar.emit();
    }, 300);
  }
}
