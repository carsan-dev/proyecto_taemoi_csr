import { CommonModule, Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';
import { Router } from '@angular/router';

@Component({
  selector: 'app-crear-grupo',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './crear-grupo.component.html',
  styleUrl: './crear-grupo.component.scss',
})
export class CrearGrupoComponent implements OnInit {
  grupoForm!: FormGroup;

  constructor(
    private readonly fb: FormBuilder,
    private readonly endpointsService: EndpointsService,
    private readonly router: Router,
    private readonly location: Location
  ) {}

  ngOnInit(): void {
    this.initForm();
  }

  initForm(): void {
    this.grupoForm = this.fb.group({
      nombre: ['', [Validators.required, Validators.maxLength(50)]],
    });
  }

  crearGrupo(): void {
    const grupoForm = this.grupoForm.value;

    this.endpointsService.crearGrupo(grupoForm).subscribe({
      next: (response) => {
        showSuccessToast('Has creado un nuevo grupo');
        this.router.navigate(['/gruposListar']);
      },
      error: (error) => {
        showErrorToast('No has completado todos los campos requeridos');
      },
      complete: () => {},
    });
  }

  volver() {
    this.location.back();
  }
}
