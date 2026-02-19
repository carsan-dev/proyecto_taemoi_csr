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
import { Documento, Evento } from '../../../interfaces/evento';
import Swal from 'sweetalert2';
import { CommonModule, Location } from '@angular/common';
import { finalize } from 'rxjs/operators';
import { LoadingService } from '../../../servicios/generales/loading.service';

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
  cargando: boolean = true;
  documentosExistentes: Documento[] = [];
  documentosNuevos: File[] = [];
  subiendoDocumento: boolean = false;
  guardandoEvento: boolean = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly endpointsService: EndpointsService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly location: Location,
    private readonly loadingService: LoadingService
  ) {
    this.eventoForm = this.fb.group({
      titulo: ['', [Validators.required, Validators.maxLength(100)]],
      descripcion: ['', [Validators.required, Validators.maxLength(500)]],
      fechaEvento: [''],
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
    this.cargando = true;
    this.endpointsService
      .obtenerEventoPorId(this.eventoId)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (evento: Evento) => {
          this.evento = evento;
          this.eventoForm.patchValue({
            titulo: evento.titulo,
            descripcion: evento.descripcion,
            fechaEvento: this.normalizarFechaEventoInput(evento.fechaEvento),
          });

          this.imagenPreview = this.obtenerUrlPreviewEvento(evento);

          this.documentosExistentes = evento.documentos || [];
        },
        error: () => {
          this.cargando = false;
          Swal.fire({
            title: 'Error',
            text: 'No se pudo cargar el evento',
            icon: 'error',
          });
        }
      });
  }

  actualizarEvento(): void {
    if (!this.eventoForm.valid || this.guardandoEvento) {
      return;
    }

    const formData = new FormData();
    const eventoEditado = {
      ...this.eventoForm.value,
      fechaEvento: this.eventoForm.value.fechaEvento || null,
    };
    formData.append('eventoEditado', JSON.stringify(eventoEditado));

    if (this.imagen) {
      formData.append('file', this.imagen);
    } else {
      formData.append('file', 'null');
    }

    this.guardandoEvento = true;
    this.loadingService.show();

    this.endpointsService
      .actualizarEvento(this.eventoId, formData)
      .pipe(
        finalize(() => {
          this.guardandoEvento = false;
          this.loadingService.hide();
        })
      )
      .subscribe({
        next: () => {
          Swal.fire({
            title: 'Perfecto!',
            text: 'El evento ha sido actualizado correctamente',
            icon: 'success',
            timer: 2000,
          });
          this.router.navigate(['/eventosListar']);
        },
        error: () => {
          Swal.fire({
            title: 'Error!',
            text: 'No se ha podido actualizar el evento',
            icon: 'error',
          });
        },
      });
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

  // ==================== MÉTODOS DE DOCUMENTOS ====================

  onDocumentosChange(event: any): void {
    const files: FileList = event.target.files;
    for (let i = 0; i < files.length; i++) {
      this.documentosNuevos.push(files[i]);
    }
    event.target.value = '';
  }

  removeDocumentoNuevo(index: number): void {
    this.documentosNuevos.splice(index, 1);
  }

  subirDocumentosNuevos(): void {
    if (this.documentosNuevos.length === 0) return;

    this.subiendoDocumento = true;
    let subidos = 0;
    let errores = 0;
    const total = this.documentosNuevos.length;

    this.documentosNuevos.forEach((doc) => {
      this.endpointsService.subirDocumentoEvento(this.eventoId, doc).subscribe({
        next: (documentoGuardado: Documento) => {
          subidos++;
          this.documentosExistentes.push(documentoGuardado);
          if (subidos + errores === total) {
            this.finalizarSubida(subidos, errores);
          }
        },
        error: () => {
          errores++;
          if (subidos + errores === total) {
            this.finalizarSubida(subidos, errores);
          }
        },
      });
    });
  }

  private finalizarSubida(subidos: number, errores: number): void {
    this.subiendoDocumento = false;
    this.documentosNuevos = [];

    if (errores > 0) {
      Swal.fire({
        title: 'Subida parcial',
        text: `${subidos} documento(s) subido(s), ${errores} error(es).`,
        icon: 'warning',
        timer: 3000,
      });
    } else {
      Swal.fire({
        title: 'Documentos subidos',
        text: `${subidos} documento(s) subido(s) correctamente.`,
        icon: 'success',
        timer: 2000,
      });
    }
  }

  eliminarDocumentoExistente(documento: Documento): void {
    Swal.fire({
      title: '¿Eliminar documento?',
      text: `¿Estás seguro de eliminar "${documento.nombre}"?`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#dc2626',
      cancelButtonColor: '#64748b',
      confirmButtonText: 'Sí, eliminar',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.eliminarDocumentoEvento(this.eventoId, documento.id).subscribe({
          next: () => {
            this.documentosExistentes = this.documentosExistentes.filter(
              (d) => d.id !== documento.id
            );
            Swal.fire({
              title: 'Eliminado',
              text: 'El documento ha sido eliminado.',
              icon: 'success',
              timer: 2000,
            });
          },
          error: () => {
            Swal.fire({
              title: 'Error',
              text: 'No se pudo eliminar el documento.',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  descargarDocumento(documento: Documento): void {
    if (!documento?.id || !this.eventoId) {
      Swal.fire({
        title: 'Error',
        text: 'No se pudo descargar el documento seleccionado.',
        icon: 'error',
      });
      return;
    }

    if (this.esDispositivoIOS()) {
      const downloadUrl = this.endpointsService.obtenerUrlDescargaDocumentoEvento(
        this.eventoId,
        documento.id,
        true
      );
      globalThis.window?.open(downloadUrl, '_self');
      return;
    }

    this.endpointsService.descargarDocumentoEvento(this.eventoId, documento.id).subscribe({
      next: (blob) => {
        const url = globalThis.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = documento.nombre || 'documento';
        link.click();
        globalThis.URL.revokeObjectURL(url);
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo descargar el documento.',
          icon: 'error',
        });
      },
    });
  }

  private esDispositivoIOS(): boolean {
    const navigatorRef = globalThis.navigator;
    if (!navigatorRef) {
      return false;
    }

    const userAgent = navigatorRef.userAgent ?? '';
    const esIOSClasico = /iPad|iPhone|iPod/.test(userAgent);
    const esIPadOS = navigatorRef.platform === 'MacIntel' && navigatorRef.maxTouchPoints > 1;
    return esIOSClasico || esIPadOS;
  }

  private obtenerUrlPreviewEvento(evento: Evento): string {
    const fallback = '../../../../assets/media/default.webp';
    const rawUrl = evento?.fotoEvento?.url;
    if (!rawUrl) {
      return fallback;
    }

    const version = String(evento?.fotoEvento?.id ?? evento?.fotoEvento?.nombre ?? '0');
    let url = this.actualizarParametroUrl(rawUrl, 'w', '920');
    url = this.actualizarParametroUrl(url, 'v', version);
    return url;
  }

  private actualizarParametroUrl(url: string, key: string, value: string): string {
    const valueSeguro = encodeURIComponent(value);
    const regex = new RegExp(`([?&])${key}=[^&]*`);
    if (regex.test(url)) {
      return url.replace(regex, `$1${key}=${valueSeguro}`);
    }
    const separador = url.includes('?') ? '&' : '?';
    return `${url}${separador}${key}=${valueSeguro}`;
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
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  private normalizarFechaEventoInput(fechaEvento: unknown): string {
    if (typeof fechaEvento === 'string') {
      return fechaEvento;
    }

    if (Array.isArray(fechaEvento) && fechaEvento.length >= 3) {
      const year = Number(fechaEvento[0]);
      const month = Number(fechaEvento[1]);
      const day = Number(fechaEvento[2]);
      if (!Number.isNaN(year) && !Number.isNaN(month) && !Number.isNaN(day)) {
        return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      }
    }

    return '';
  }
}
