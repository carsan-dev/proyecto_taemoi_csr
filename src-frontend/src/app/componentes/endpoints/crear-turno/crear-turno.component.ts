import { Component, OnInit } from '@angular/core';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';
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
import { ActivatedRoute, Router, RouterModule } from '@angular/router';

@Component({
  selector: 'app-crear-turno',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule],
  templateUrl: './crear-turno.component.html',
  styleUrl: './crear-turno.component.scss',
})
export class CrearTurnoComponent implements OnInit {
  turnoForm!: FormGroup;
  grupos: any[] = [];
  preselectedGrupoId: number | null = null;

  constructor(
    private readonly fb: FormBuilder,
    private readonly endpointsService: EndpointsService,
    private readonly router: Router,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    // Check for preselected grupoId from query params
    const grupoIdParam = this.route.snapshot.queryParamMap.get('grupoId');
    if (grupoIdParam) {
      this.preselectedGrupoId = +grupoIdParam;
    }

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
        tipoGrupo: [''],  // Only required when asignarGrupo is true
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

        // If we have a preselected grupoId, enable grupo assignment and select it
        if (this.preselectedGrupoId) {
          const grupo = this.grupos.find(g => g.id === this.preselectedGrupoId);
          if (grupo) {
            this.turnoForm.patchValue({
              asignarGrupo: true,
              grupoId: this.preselectedGrupoId,
              tipoGrupo: grupo.tipo || ''
            });
          }
        }
      },
      error: () => {
        showErrorToast('No hemos podido conectar con el servidor para obtener los grupos');
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
        showSuccessToast('Has creado un nuevo turno');
        this.router.navigate(['/gruposListar']);
      },
      error: (error) => {
        showErrorToast('No has completado todos los campos requeridos');
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
