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
import Swal from 'sweetalert2';
import { Location } from '@angular/common';

@Component({
  selector: 'app-editar-grupo',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule],
  templateUrl: './editar-grupo.component.html',
  styleUrl: './editar-grupo.component.scss',
})
export class EditarGrupoComponent implements OnInit {
  grupoForm: FormGroup;
  grupoId!: number;

  constructor(
    private fb: FormBuilder,
    private endpointsService: EndpointsService,
    private route: ActivatedRoute,
    private router: Router,
    private location: Location
  ) {
    this.grupoForm = this.fb.group({
      nombre: ['', Validators.required],
    });
  }

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      this.grupoId = +params['id'];
      this.cargarGrupo();
    });
  }

  cargarGrupo(): void {
    const token = localStorage.getItem('token');
    if (token) {
      this.endpointsService
        .obtenerGrupoPorId(this.grupoId, token)
        .subscribe((grupo: GrupoDTO) => {
          this.grupoForm.patchValue({ nombre: grupo.nombre });
        });
    }
  }

  actualizarGrupo(): void {
    if (this.grupoForm.valid) {
      const token = localStorage.getItem('token');
      if (token) {
        this.endpointsService
          .actualizarGrupo(this.grupoId, this.grupoForm.value, token)
          .subscribe({
            next: (response) => {
              Swal.fire({
                title: 'Perfecto!',
                text: 'Has creado un nuevo grupo!',
                icon: 'success',
              });
              this.router.navigate(['/gruposListar']);
            },
            error: (error) => {
              Swal.fire({
                title: 'Error',
                text: 'Error al actualizar el grupo',
                icon: 'error',
              });
            },
            complete: () => {},
          });
      }
    }
  }

  volver() {
    this.location.back();
  }
}
