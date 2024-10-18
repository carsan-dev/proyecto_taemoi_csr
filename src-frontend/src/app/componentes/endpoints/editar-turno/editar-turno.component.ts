import { CommonModule, Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  ValidationErrors,
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
export class EditarTurnoComponent implements OnInit {
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
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly endpointsService: EndpointsService,
    private readonly location: Location
  ) {
    this.turnoForm = this.fb.group(
      {
        diaSemana: ['', Validators.required],
        horaInicio: [
          '',
          [
            Validators.required,
            Validators.pattern(/^([01]\d|2[0-3]):([0-5]\d)$/),
          ],
        ],
        horaFin: [
          '',
          [
            Validators.required,
            Validators.pattern(/^([01]\d|2[0-3]):([0-5]\d)$/),
          ],
        ],
      },
      { validators: this.horasValidas }
    );
  }

  ngOnInit(): void {
    this.turnoId = +this.route.snapshot.paramMap.get('id')!;
    this.cargarTurno();
  }

  cargarTurno(): void {
    this.endpointsService.obtenerTurnoPorId(this.turnoId).subscribe({
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

  onSubmit(): void {
    if (this.turnoForm.invalid) {
      return;
    }
    const { diaSemana, horaInicio, horaFin } = this.turnoForm.value;
    const turnoActualizado = { diaSemana, horaInicio, horaFin };

    this.endpointsService
      .actualizarTurno(this.turnoId, turnoActualizado)
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

  volver() {
    this.location.back();
  }

  horasValidas(group: AbstractControl): ValidationErrors | null {
    const horaInicio = group.get('horaInicio')?.value;
    const horaFin = group.get('horaFin')?.value;

    if (horaInicio && horaFin && horaInicio >= horaFin) {
      return { horasInvalidas: true };
    }
    return null;
  }
}
