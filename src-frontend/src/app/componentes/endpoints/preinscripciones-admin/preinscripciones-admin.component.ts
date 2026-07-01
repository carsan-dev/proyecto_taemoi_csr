import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PreinscripcionService } from '../../../features/preinscripcion/preinscripcion.service';

@Component({
  selector: 'app-preinscripciones-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './preinscripciones-admin.component.html',
  styleUrl: './preinscripciones-admin.component.scss'
})
export class PreinscripcionesAdminComponent implements OnInit, OnDestroy {
  items: any[] = [];
  cargando = false;
  filtros = { temporada: '', deporte: '', estado: '', q: '' };
  seleccion: any;
  alumnoId?: number;
  mensaje = '';
  firmaUrl?: string;

  constructor(private readonly api: PreinscripcionService) {}

  ngOnInit() { this.buscar(); }

  ngOnDestroy() { this.liberarFirma(); }

  buscar() {
    this.cargando = true;
    this.api.listar(this.filtros).subscribe({
      next: items => { this.items = items; this.cargando = false; },
      error: () => this.cargando = false
    });
  }

  seleccionar(item: any) {
    this.liberarFirma();
    this.seleccion = item;
    this.alumnoId = item.alumnoCoincidente?.id;
    this.mensaje = '';
    this.api.firma(item.referencia).subscribe({
      next: firma => this.firmaUrl = URL.createObjectURL(firma),
      error: () => this.mensaje = 'No se pudo cargar la firma.'
    });
  }

  cerrarDetalle() {
    this.liberarFirma();
    this.seleccion = undefined;
    this.alumnoId = undefined;
    this.mensaje = '';
  }

  descargarDocumento() {
    if (!this.seleccion) return;
    this.api.documentoFirmado(this.seleccion.referencia).subscribe(documento => {
      const url = URL.createObjectURL(documento);
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = `preinscripcion-${this.seleccion.referencia}.pdf`;
      enlace.click();
      URL.revokeObjectURL(url);
    });
  }

  private liberarFirma() {
    if (this.firmaUrl) URL.revokeObjectURL(this.firmaUrl);
    this.firmaUrl = undefined;
  }

  cancelar() {
    if (!this.seleccion || !confirm('¿Cancelar esta solicitud?')) return;
    this.api.cancelar(this.seleccion.referencia).subscribe(() => { this.cerrarDetalle(); this.buscar(); });
  }

  reenviar() {
    if (!this.seleccion) return;
    this.api.reenviar(this.seleccion.referencia).subscribe(() => this.mensaje = 'Correo reenviado.');
  }

  finalizar() {
    if (!this.seleccion || !this.alumnoId) return;
    this.api.finalizar(this.seleccion.referencia, this.alumnoId).subscribe({
      next: () => { this.cerrarDetalle(); this.buscar(); },
      error: error => this.mensaje = error?.error?.mensaje || 'No se pudo finalizar.'
    });
  }
}
