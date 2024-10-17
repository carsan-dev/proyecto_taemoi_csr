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
  styleUrls: ['./crear-evento.component.scss'],
})
export class CrearEventoComponent implements OnInit {
  eventoForm!: FormGroup;
  imagen: File | null = null;
  imagePreview: string | null = null; // To store the image preview URL

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
      titulo: ['', [Validators.required, Validators.maxLength(100)]],
      descripcion: ['', [Validators.required, Validators.maxLength(500)]],
    });
  }

  onSubmit(): void {
    const eventoForm = this.eventoForm.value;

    this.endpointsService.crearEvento(eventoForm, this.imagen).subscribe({
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

  // Method to handle image selection and preview
  onFileChange(event: any): void {
    const fileList: FileList = event.target.files;
    if (fileList.length > 0) {
      this.imagen = fileList[0];

      // Create a preview of the image
      const reader = new FileReader();
      reader.onload = () => {
        this.imagePreview = reader.result as string;
      };
      reader.readAsDataURL(this.imagen); // Read the file as a data URL to display the preview
    }
  }

  adjustTextareaHeight(event: Event): void {
    const textarea = event.target as HTMLTextAreaElement;
    textarea.style.height = 'auto';
    textarea.style.height = `${textarea.scrollHeight}px`;
  }

  removeImage() {
    this.imagen = null;
    this.imagePreview = null;
    const fileInput = document.getElementById('fotoEvento') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = ''; // Limpiar el valor del input
    }
  }
}
