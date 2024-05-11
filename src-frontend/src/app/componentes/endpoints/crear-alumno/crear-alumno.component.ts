import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import { CommonModule } from '@angular/common';
import { TipoTarifa } from '../../../enums/tipo-tarifa';
import { SidebarComponent } from '../../vistas/layout/sidebar/sidebar.component';
import { Router } from '@angular/router';

@Component({
  selector: 'app-crear-alumno',
  standalone: true,
  imports: [FormsModule, CommonModule, SidebarComponent, ReactiveFormsModule],
  templateUrl: './crear-alumno.component.html',
  styleUrl: './crear-alumno.component.scss',
})

export class CrearAlumnoComponent implements OnInit {
  alumnoData!: FormGroup;
  imagen: File | null = null;
  tiposTarifa = Object.values(TipoTarifa);

  constructor(private fb: FormBuilder, private endpointsService: EndpointsService, private router: Router) { }

  ngOnInit(): void {
    this.initForm();
  }

  initForm(): void {
    this.alumnoData = this.fb.group({
      nombre: ['', Validators.required],
      apellidos: ['', Validators.required],
      fechaNacimiento: ['', Validators.required],
      numeroExpediente: ['', Validators.required],
      nif: ['', Validators.required],
      direccion: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      telefono: ['', Validators.required],
      tipoTarifa: ['', Validators.required],
      fechaAlta: ['', Validators.required],
      fechaBaja: ['']
    });
  }

  onSubmit(): void {
    const token = localStorage.getItem('token');
    const alumnoData = this.alumnoData.value;

    if (token) {
    this.endpointsService.crearAlumno(alumnoData, this.imagen, token).subscribe({
      next: (response) => {
        Swal.fire({
          title: 'Perfecto!',
          text: 'Has creado un nuevo alumno',
          icon: 'success',
        });
        this.router.navigate(['/alumnos']);
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
