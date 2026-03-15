import { Component, OnInit } from '@angular/core';
import {
  FormGroup,
  FormBuilder,
  Validators,
  FormsModule,
  ReactiveFormsModule,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { GrupoDTO } from '../../../interfaces/grupo-dto';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';
import { CommonModule, Location } from '@angular/common';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-editar-grupo',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, CommonModule],
  templateUrl: './editar-grupo.component.html',
  styleUrl: './editar-grupo.component.scss',
})
export class EditarGrupoComponent implements OnInit {
  grupoForm: FormGroup;
  grupoId!: number;
  cargando: boolean = true;

  constructor(
    private readonly fb: FormBuilder,
    private readonly endpointsService: EndpointsService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly location: Location
  ) {
    this.grupoForm = this.fb.group({
      nombre: ['', [Validators.required, Validators.maxLength(50)]],
      rangoEdadMin: [null],
      rangoEdadMax: [null],
    });
  }

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      this.grupoId = +params['id'];
      this.cargarGrupo();
    });
  }

  cargarGrupo(): void {
    this.cargando = true;
    this.endpointsService
      .obtenerGrupoPorId(this.grupoId)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (grupo: GrupoDTO) => {
          this.grupoForm.patchValue({
            nombre: grupo.nombre,
            rangoEdadMin: grupo.rangoEdadMin ?? null,
            rangoEdadMax: grupo.rangoEdadMax ?? null,
          });
        },
        error: () => {
          this.cargando = false;
          showErrorToast('No se pudo cargar el grupo');
        }
      });
  }

  actualizarGrupo(): void {
    if (this.grupoForm.valid) {
      this.endpointsService
        .actualizarGrupo(this.grupoId, this.grupoForm.value)
        .subscribe({
          next: (response) => {
            showSuccessToast('El grupo se ha actualizado correctamente');
            this.router.navigate(['/gruposListar']);
          },
          error: (error) => {
            showErrorToast('Error al actualizar el grupo');
          },
          complete: () => {},
        });
    }
  }

  volver() {
    this.location.back();
  }
}
