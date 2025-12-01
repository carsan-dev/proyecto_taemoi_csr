import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-configuracion-sistema',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './configuracion-sistema.component.html',
  styleUrls: ['./configuracion-sistema.component.scss']
})
export class ConfiguracionSistemaComponent implements OnInit {
  limiteTurno: number = 36;
  limiteTurnoOriginal: number = 36;
  cargando: boolean = true;

  constructor(private endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.cargarConfiguracion();
  }

  cargarConfiguracion(): void {
    this.cargando = true;
    this.endpointsService.obtenerLimiteTurno().subscribe({
      next: (limite) => {
        this.limiteTurno = limite;
        this.limiteTurnoOriginal = limite;
        this.cargando = false;
      },
      error: (error) => {
        console.error('Error al cargar la configuración:', error);
        Swal.fire({
          icon: 'error',
          title: 'Error',
          text: 'No se pudo cargar la configuración del sistema'
        });
        this.cargando = false;
      }
    });
  }

  guardarConfiguracion(): void {
    if (this.limiteTurno < 1) {
      Swal.fire({
        icon: 'warning',
        title: 'Valor inválido',
        text: 'El límite debe ser al menos 1'
      });
      return;
    }

    Swal.fire({
      title: '¿Confirmar cambio?',
      text: `El límite de alumnos por turno se cambiará de ${this.limiteTurnoOriginal} a ${this.limiteTurno}`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Guardar',
      cancelButtonText: 'Cancelar'
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.actualizarLimiteTurno(this.limiteTurno).subscribe({
          next: () => {
            this.limiteTurnoOriginal = this.limiteTurno;
            Swal.fire({
              icon: 'success',
              title: 'Guardado',
              text: 'La configuración se ha actualizado correctamente',
              timer: 2000,
              showConfirmButton: false
            });
          },
          error: (error) => {
            console.error('Error al guardar la configuración:', error);
            Swal.fire({
              icon: 'error',
              title: 'Error',
              text: 'No se pudo guardar la configuración'
            });
          }
        });
      }
    });
  }

  hayCambios(): boolean {
    return this.limiteTurno !== this.limiteTurnoOriginal;
  }

  restaurarValor(): void {
    this.limiteTurno = this.limiteTurnoOriginal;
  }
}
