import { Component, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-crear-evento',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './crear-evento.component.html',
  styleUrl: './crear-evento.component.scss',
})
export class CrearEventoComponent implements OnInit {
  eventoForm!: FormGroup;
  imagen: File | null = null;

  constructor(
    private endpointsService: EndpointsService,
    private fb: FormBuilder,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.initForm();
  }

  initForm(): void {
    this.eventoForm = this.fb.group({
      titulo: ['', Validators.required],
      descripcion: ['', Validators.required],
    });
  }

  onSubmit(): void {
    const token = localStorage.getItem('token');
    const eventoForm = this.eventoForm.value;

    if (token) {
      this.endpointsService
        .crearEvento(eventoForm, this.imagen, token)
        .subscribe({
          next: (response) => {
            Swal.fire({
              title: 'Perfecto!',
              text: 'Has creado un nuevo evento',
              icon: 'success',
            });
            this.router.navigate(['/eventosListar']);
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
  onFileChange(event: any) {
    const fileList: FileList = event.target.files;
    if (fileList.length > 0) {
      this.imagen = fileList[0];
    }
  }
}
