import { Component, OnInit } from '@angular/core';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';

@Component({
  selector: 'app-crear-turno',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './crear-turno.component.html',
  styleUrl: './crear-turno.component.scss',
})
export class CrearTurnoComponent implements OnInit {
  turnoForm!: FormGroup;
  grupos: any[] = [];

  constructor(
    private readonly fb: FormBuilder,
    private readonly endpointsService: EndpointsService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.obtenerGrupos();
  }

  initForm(): void {
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
        asignarGrupo: [false],
        grupoId: [''],
        tipoGrupo: ['', Validators.required],  // Añadimos tipoGrupo aquí
      },
      { validators: this.horasValidas }
    );

    // Asegurarnos de que tipoGrupo solo es requerido si asignarGrupo es true
    this.turnoForm.get('asignarGrupo')?.valueChanges.subscribe((asignarGrupo) => {
      const tipoGrupoControl = this.turnoForm.get('tipoGrupo');
      if (asignarGrupo) {
        tipoGrupoControl?.setValidators([Validators.required]);
      } else {
        tipoGrupoControl?.clearValidators();
      }
      tipoGrupoControl?.updateValueAndValidity();
    });
  }

  obtenerGrupos(): void {
    this.endpointsService.obtenerTodosLosGrupos().subscribe({
      next: (response) => {
        this.grupos = response;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido conectar con el servidor para obtener los grupos',
          icon: 'error',
        });
      },
    });
  }

  crearTurno(): void {
    const turnoForm = this.turnoForm.value;

    // Si se asigna a un grupo, también debe enviarse el tipo de grupo
    const turnoRequest = turnoForm.asignarGrupo
      ? this.endpointsService.crearTurnoConGrupo({
          ...turnoForm,
          grupoId: turnoForm.grupoId,
          tipoGrupo: turnoForm.tipoGrupo, // Añadimos el tipo de grupo aquí
        })
      : this.endpointsService.crearTurnoSinGrupo(turnoForm);

    turnoRequest.subscribe({
      next: (response) => {
        Swal.fire({
          title: 'Perfecto!',
          text: 'Has creado un nuevo turno!',
          icon: 'success',
        });
        this.router.navigate(['/turnosListar']);
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'No has completado todos los campos requeridos',
          icon: 'error',
        });
      },
      complete: () => {},
    });
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
