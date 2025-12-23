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
import { showSuccessToast, showErrorToast } from '../../../utils/toast.util';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { finalize } from 'rxjs/operators';

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
  cargando: boolean = true;
  grupos: any[] = [];
  turno: any = null;

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
        grupoId: [''],
      },
      { validators: this.horasValidas }
    );
  }

  ngOnInit(): void {
    this.turnoId = +this.route.snapshot.paramMap.get('id')!;
    this.cargarTurno();
    this.obtenerGrupos();
  }

  obtenerGrupos(): void {
    this.endpointsService.obtenerTodosLosGrupos().subscribe({
      next: (response) => {
        this.grupos = response;
      },
      error: () => {
        showErrorToast('Error al cargar los grupos');
      },
    });
  }

  cargarTurno(): void {
    this.cargando = true;
    this.endpointsService.obtenerTurnoPorId(this.turnoId)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (turno) => {
          this.turno = turno;
          this.turnoForm.patchValue({
            diaSemana: turno.diaSemana,
            horaInicio: turno.horaInicio,
            horaFin: turno.horaFin,
            grupoId: turno.grupoId || '',
          });
        },
        error: () => {
          this.cargando = false;
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
    const { diaSemana, horaInicio, horaFin, grupoId } = this.turnoForm.value;
    const turnoActualizado = { diaSemana, horaInicio, horaFin };

    this.endpointsService
      .actualizarTurno(this.turnoId, turnoActualizado)
      .subscribe({
        next: () => {
          // Check if grupo has changed
          const newGrupoId = grupoId ? +grupoId : null;
          const oldGrupoId = this.turno?.grupoId || null;

          if (newGrupoId !== oldGrupoId) {
            this.actualizarGrupoDelTurno(newGrupoId, oldGrupoId);
          } else {
            showSuccessToast('Turno actualizado correctamente');
            this.router.navigate(['/gruposListar']);
          }
        },
        error: () => {
          showErrorToast('No hemos podido actualizar el turno');
        },
      });
  }

  private actualizarGrupoDelTurno(newGrupoId: number | null, oldGrupoId: number | null): void {
    if (newGrupoId) {
      // Assigning to a new grupo (or changing grupo)
      this.endpointsService.agregarTurnoAGrupo(newGrupoId, this.turnoId).subscribe({
        next: () => {
          showSuccessToast('Turno actualizado y asignado al grupo');
          this.router.navigate(['/gruposListar']);
        },
        error: () => {
          showErrorToast('Turno actualizado pero error al asignar grupo');
          this.router.navigate(['/gruposListar']);
        },
      });
    } else if (oldGrupoId) {
      // Removing from grupo (newGrupoId is null but had an old grupo)
      this.endpointsService.eliminarTurnoDeGrupo(oldGrupoId, this.turnoId).subscribe({
        next: () => {
          showSuccessToast('Turno actualizado y desvinculado del grupo');
          this.router.navigate(['/gruposListar']);
        },
        error: () => {
          showErrorToast('Turno actualizado pero error al desvincular grupo');
          this.router.navigate(['/gruposListar']);
        },
      });
    }
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
