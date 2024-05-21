import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { Router } from '@angular/router';

@Component({
  selector: 'app-crear-grupo',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './crear-grupo.component.html',
  styleUrl: './crear-grupo.component.scss'
})
export class CrearGrupoComponent implements OnInit {
  grupoForm!: FormGroup;

  constructor(private fb: FormBuilder, private endpointsService: EndpointsService, private router: Router) { }

  ngOnInit(): void {
    this.initForm();
  }

  initForm(): void {
    this.grupoForm = this.fb.group({
      nombre: ['', Validators.required]
    });
  }

  crearGrupo(): void {
    const token = localStorage.getItem('token');
    const grupoForm = this.grupoForm.value;

    if (token) {
    this.endpointsService.crearGrupo(grupoForm, token).subscribe({
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
          title: 'Error en la petición',
          text: 'No has completado todos los campos requeridos',
          icon: 'error',
        });
      },
      complete: () => {},
    });
  }
}
}
