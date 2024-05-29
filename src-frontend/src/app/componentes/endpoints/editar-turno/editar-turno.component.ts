import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';

@Component({
  selector: 'app-editar-turno',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './editar-turno.component.html',
  styleUrl: './editar-turno.component.scss',
})
export class ActualizarTurnoComponent implements OnInit {
  turnoForm: FormGroup;
  diasSemana: string[] = [
    'Lunes',
    'Martes',
    'Miércoles',
    'Jueves',
    'Viernes',
    'Sábado',
    'Domingo',
  ];
  turnoId!: number;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private endpointsService: EndpointsService
  ) {
    this.turnoForm = this.fb.group({
      diaSemana: ['', Validators.required],
      horaInicio: ['', Validators.required],
      horaFin: ['', Validators.required],
    });
  }

  ngOnInit(): void {
    this.turnoId = +this.route.snapshot.paramMap.get('id')!;
    this.cargarTurno();
  }

  cargarTurno(): void {
    const token = localStorage.getItem('token');
    if (token) {
      this.endpointsService.obtenerTurnoPorId(this.turnoId, token).subscribe({
        next: (turno) => {
          this.turnoForm.patchValue({
            diaSemana: turno.diaSemana,
            horaInicio: turno.horaInicio,
            horaFin: turno.horaFin,
          });
        },
        error: (error) => {
          Swal.fire({
            title: 'Error en la carga del turno',
            text: 'No hemos podido cargar los detalles del turno',
            icon: 'error',
          });
        },
      });
    }
  }

  onSubmit(): void {
    if (this.turnoForm.invalid) {
      return;
    }
    const token = localStorage.getItem('token');
    const { diaSemana, horaInicio, horaFin } = this.turnoForm.value;
    const turnoActualizado = { diaSemana, horaInicio, horaFin };

    if (token) {
      this.endpointsService
        .actualizarTurno(this.turnoId, turnoActualizado, token)
        .subscribe({
          next: () => {
            Swal.fire({
              title: 'Actualizado',
              text: 'El turno ha sido actualizado',
              icon: 'success',
            }).then(() => {
              this.router.navigate(['/turnosListar']);
            });
          },
          error: (error) => {
            Swal.fire({
              title: 'Error en la actualización',
              text: 'No hemos podido actualizar el turno',
              icon: 'error',
            });
          },
        });
    }
  }
}
