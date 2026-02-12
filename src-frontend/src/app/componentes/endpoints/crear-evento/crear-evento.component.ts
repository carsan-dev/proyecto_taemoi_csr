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
import { LoadingService } from '../../../servicios/generales/loading.service';

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
  imagePreview: string | null = null;
  documentos: File[] = [];
  guardandoEvento: boolean = false;

  constructor(
    private readonly endpointsService: EndpointsService,
    private readonly fb: FormBuilder,
    private readonly router: Router,
    private readonly loadingService: LoadingService
  ) {}

  ngOnInit(): void {
    this.initForm();
  }

  initForm(): void {
    this.eventoForm = this.fb.group({
      titulo: ['', [Validators.required, Validators.maxLength(100)]],
      descripcion: ['', [Validators.required, Validators.maxLength(500)]],
      fechaEvento: [''],
      visible: [true],
    });
  }

  onSubmit(): void {
    if (this.guardandoEvento) {
      return;
    }

    if (this.eventoForm.invalid) {
      Swal.fire({
        title: 'Formulario inválido',
        text: 'Por favor, completa todos los campos requeridos.',
        icon: 'warning',
        timer: 2000,
      });
      return;
    }

    const eventoForm = {
      ...this.eventoForm.value,
      fechaEvento: this.eventoForm.value.fechaEvento || null,
    };

    this.iniciarGuardadoEvento();
    this.endpointsService.crearEvento(eventoForm, this.imagen).subscribe({
      next: (eventoCreado: any) => {
        if (this.documentos.length > 0) {
          this.subirDocumentos(eventoCreado.id);
        } else {
          this.finalizarGuardadoEvento();
          this.mostrarExitoYRedirigir();
        }
      },
      error: () => {
        this.finalizarGuardadoEvento();
        Swal.fire({
          title: 'Error en la petición',
          text: 'No hemos podido crear el evento.',
          icon: 'error',
        });
      },
    });
  }

  private subirDocumentos(eventoId: number): void {
    let subidos = 0;
    let errores = 0;

    this.documentos.forEach((doc) => {
      this.endpointsService.subirDocumentoEvento(eventoId, doc).subscribe({
        next: () => {
          subidos++;
          if (subidos + errores === this.documentos.length) {
            this.finalizarGuardadoEvento();
            this.mostrarExitoYRedirigir(errores);
          }
        },
        error: () => {
          errores++;
          if (subidos + errores === this.documentos.length) {
            this.finalizarGuardadoEvento();
            this.mostrarExitoYRedirigir(errores);
          }
        },
      });
    });
  }

  private iniciarGuardadoEvento(): void {
    this.guardandoEvento = true;
    this.loadingService.show();
  }

  private finalizarGuardadoEvento(): void {
    this.guardandoEvento = false;
    this.loadingService.hide();
  }

  private mostrarExitoYRedirigir(erroresDocumentos: number = 0): void {
    if (erroresDocumentos > 0) {
      Swal.fire({
        title: 'Evento creado',
        text: `El evento se ha creado, pero ${erroresDocumentos} documento(s) no se pudieron subir.`,
        icon: 'warning',
        timer: 3000,
      });
    } else {
      Swal.fire({
        title: '¡Perfecto!',
        text: 'Has creado un nuevo evento',
        icon: 'success',
        timer: 2000,
      });
    }
    this.router.navigate(['/eventosListar']);
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
      fileInput.value = '';
    }
  }

  onDocumentosChange(event: any): void {
    const files: FileList = event.target.files;
    for (const file of Array.from(files)) {
      this.documentos.push(file);
    }
    event.target.value = '';
  }

  removeDocumento(index: number): void {
    this.documentos.splice(index, 1);
  }

  getFileIcon(tipo: string): string {
    if (tipo.includes('pdf')) return 'bi-file-earmark-pdf';
    if (tipo.includes('word') || tipo.includes('document')) return 'bi-file-earmark-word';
    if (tipo.includes('excel') || tipo.includes('spreadsheet')) return 'bi-file-earmark-excel';
    if (tipo.includes('image')) return 'bi-file-earmark-image';
    return 'bi-file-earmark';
  }

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Number.parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }
}
