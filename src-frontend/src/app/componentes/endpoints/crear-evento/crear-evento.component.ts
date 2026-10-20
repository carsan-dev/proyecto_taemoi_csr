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
import {
  SUBIDA_DOCUMENTO,
  SUBIDA_IMAGEN,
  mensajeErrorSubida,
  prepararFicheroParaSubida,
  reiniciarSelectorArchivo,
} from '../../../utils/subida-archivos.util';

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
      error: (error) => {
        this.finalizarGuardadoEvento();
        Swal.fire({
          title: 'Error en la petición',
          text: mensajeErrorSubida(error, 'No hemos podido crear el evento.'),
          icon: 'error',
        });
      },
    });
  }

  private subirDocumentos(eventoId: number): void {
    let subidos = 0;
    let errores = 0;
    const motivos: string[] = [];

    this.documentos.forEach((doc) => {
      this.endpointsService.subirDocumentoEvento(eventoId, doc).subscribe({
        next: () => {
          subidos++;
          if (subidos + errores === this.documentos.length) {
            this.finalizarGuardadoEvento();
            this.mostrarExitoYRedirigir(errores, motivos);
          }
        },
        error: (error) => {
          errores++;
          const motivo = mensajeErrorSubida(error, '');
          if (motivo && !motivos.includes(motivo)) {
            motivos.push(motivo);
          }
          if (subidos + errores === this.documentos.length) {
            this.finalizarGuardadoEvento();
            this.mostrarExitoYRedirigir(errores, motivos);
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

  private mostrarExitoYRedirigir(erroresDocumentos: number = 0, motivos: string[] = []): void {
    if (erroresDocumentos > 0) {
      Swal.fire({
        title: 'Evento creado',
        text: [`El evento se ha creado, pero ${erroresDocumentos} documento(s) no se pudieron subir.`, ...motivos].join(' '),
        icon: 'warning',
        // Con motivos que leer el aviso no se cierra solo.
        ...(motivos.length > 0 ? {} : { timer: 3000 }),
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
  async onFileChange(event: Event): Promise<void> {
    const entrada = event.target as HTMLInputElement;
    const elegido = entrada?.files?.[0];
    if (elegido) {
      // Copia en memoria al elegirla: en Android leer el content:// al enviar falla.
      const preparado = await prepararFicheroParaSubida(elegido, SUBIDA_IMAGEN);
      reiniciarSelectorArchivo(entrada);
      if (!preparado.ok) {
        Swal.fire({
          title: 'No se pudo usar la imagen',
          text: preparado.motivo,
          icon: 'error',
        });
        return;
      }
      this.imagen = preparado.file;

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

  async onDocumentosChange(event: Event): Promise<void> {
    const entrada = event.target as HTMLInputElement;
    const elegidos = Array.from(entrada?.files ?? []);
    const rechazados: string[] = [];
    // Copia en memoria al elegirlos: en Android leer el content:// al enviar falla.
    for (const elegido of elegidos) {
      const preparado = await prepararFicheroParaSubida(elegido, SUBIDA_DOCUMENTO);
      if (preparado.ok) {
        this.documentos.push(preparado.file);
      } else {
        rechazados.push(`«${elegido.name}»: ${preparado.motivo}`);
      }
    }
    reiniciarSelectorArchivo(entrada);
    if (rechazados.length > 0) {
      Swal.fire({
        title: rechazados.length === 1 ? 'No se pudo añadir el documento' : 'Algunos documentos no se añadieron',
        text: rechazados.join(' '),
        icon: 'error',
      });
    }
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
