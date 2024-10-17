import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Evento } from '../../../interfaces/evento';
import Swal from 'sweetalert2';
import { CommonModule, Location } from '@angular/common';

@Component({
  selector: 'app-editar-evento',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, CommonModule],
  templateUrl: './editar-evento.component.html',
  styleUrls: ['./editar-evento.component.scss'],
})
export class EditarEventoComponent implements OnInit {
  eventoForm!: FormGroup;
  eventoId!: number;
  imagen: File | null = null;
  @ViewChild('inputFile', { static: false }) inputFile!: ElementRef;
  evento: Evento | null = null;
  imagenPreview: string | null = null;

  constructor(
    private fb: FormBuilder,
    private endpointsService: EndpointsService,
    private route: ActivatedRoute,
    private router: Router,
    private location: Location
  ) {
    this.eventoForm = this.fb.group({
      titulo: ['', [Validators.required, Validators.maxLength(100)]],
      descripcion: ['', [Validators.required, Validators.maxLength(500)]],
      fotoEvento: [null],
    });
  }

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      this.eventoId = +params['id'];
      this.cargarEvento();
    });
  }

  cargarEvento(): void {
    this.endpointsService
      .obtenerEventoPorId(this.eventoId)
      .subscribe((evento: Evento) => {
        this.evento = evento;
        this.eventoForm.patchValue({
          titulo: evento.titulo,
          descripcion: evento.descripcion,
        });

        // Use `ruta` for image preview, and fallback to default image
        this.imagenPreview = evento.fotoEvento?.url
          ? evento.fotoEvento.url
          : '../../../../assets/media/default.webp';
      });
  }

  actualizarEvento(): void {
    if (this.eventoForm.valid) {
      const formData = new FormData();
      formData.append('eventoEditado', JSON.stringify(this.eventoForm.value));

      if (this.imagen) {
        formData.append('file', this.imagen);
      } else {
        formData.append('file', 'null');
      }

      this.endpointsService
        .actualizarEvento(this.eventoId, formData)
        .subscribe({
          next: (response) => {
            Swal.fire({
              title: 'Perfecto!',
              text: 'El evento ha sido actualizado correctamente',
              icon: 'success',
            });
            this.router.navigate(['/eventosListar']);
          },
          error: (error) => {
            Swal.fire({
              title: 'Error!',
              text: 'No se ha podido actualizar el evento',
              icon: 'error',
            });
          },
        });
    }
  }

  // Method to handle file selection and update image preview
  onFileSelected(event: any) {
    const file: File = event.target.files[0];
    this.imagen = file;

    const reader = new FileReader();
    reader.onload = (e: any) => {
      this.imagenPreview = e.target.result;
    };
    reader.readAsDataURL(file);
  }

  // Method to handle image deletion and reset the form image preview
  eliminarFoto(): void {
    this.endpointsService.eliminarImagenEvento(this.eventoId).subscribe({
      next: (response) => {
        this.inputFile.nativeElement.value = '';
        if (this.evento) {
          this.evento.fotoEvento = null;
        }
        this.imagenPreview = '../../../../assets/media/default.webp';
        this.eventoForm.patchValue({ fotoEvento: null });
      },
      error: (error) => {
        Swal.fire({
          title: 'Error!',
          text: 'No se ha podido eliminar la imagen',
          icon: 'error',
        });
      },
    });
  }

  volver() {
    this.location.back();
  }
}
