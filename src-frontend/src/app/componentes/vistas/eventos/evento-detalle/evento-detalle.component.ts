import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import Swal from 'sweetalert2';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import { Documento, Evento } from '../../../../interfaces/evento';
import { CommonModule } from '@angular/common';
import { NgxSpinnerModule, NgxSpinnerService } from 'ngx-spinner';
import { SeoService } from '../../../../servicios/generales/seo.service';

@Component({
  selector: 'app-evento-detalle',
  standalone: true,
  imports: [CommonModule, NgxSpinnerModule, RouterLink],
  templateUrl: './evento-detalle.component.html',
  styleUrl: './evento-detalle.component.scss',
})
export class EventoDetalleComponent implements OnInit, OnDestroy {
  evento: Evento | null = null;
  eventoId!: number;
  modalImagenAbierto: boolean = false;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly endpointsService: EndpointsService,
    private readonly spinner: NgxSpinnerService,
    private readonly seoService: SeoService
  ) {}

  ngOnInit(): void {
    this.eventoId = +this.route.snapshot.paramMap.get('eventoId')!;
    this.obtenerEvento(this.eventoId);
  }

  obtenerEvento(id: number): void {
    this.spinner.show();

    this.endpointsService.obtenerEventoPorId(id).subscribe({
      next: (response: Evento) => {
        this.evento = response;
        this.actualizarSeo(response);
        this.spinner.hide();
      },
      error: () => {
        this.spinner.hide();
        Swal.fire({
          title: 'Error',
          text: 'No hemos podido obtener los detalles del evento.',
          icon: 'error',
        });
      },
    });
  }

  private actualizarSeo(evento: Evento): void {
    const descripcionCorta =
      evento.descripcion.length > 155
        ? evento.descripcion.substring(0, 152) + '...'
        : evento.descripcion;

    this.seoService.updateDynamicSeo({
      title: `${evento.titulo} | Eventos Moiskimdo - Taekwondo en Umbrete`,
      description: descripcionCorta,
      keywords: `${evento.titulo}, evento taekwondo, competicion artes marciales, moiskimdo, umbrete, sevilla`,
      ogImage: evento.fotoEvento?.url ?? undefined,
      canonical: `https://moiskimdo.es/eventos/${evento.id}`,
      breadcrumbs: [
        { name: 'Inicio', url: '/' },
        { name: 'Eventos', url: '/eventos' },
        { name: evento.titulo, url: `/eventos/${evento.id}` },
      ],
    });

    // Agregar Event Schema para SEO
    this.seoService.setEventSchema({
      name: evento.titulo,
      description: evento.descripcion,
      image: evento.fotoEvento?.url ?? undefined,
      url: `/eventos/${evento.id}`,
    });
  }

  abrirModalImagen(): void {
    this.modalImagenAbierto = true;
    document.body.style.overflow = 'hidden'; // Deshabilitar scroll del body
  }

  cerrarModalImagen(): void {
    this.modalImagenAbierto = false;
    document.body.style.overflow = '';
  }

  ngOnDestroy(): void {
    // Ensure scroll is restored if the modal was left open on navigation.
    document.body.style.overflow = '';
    // Limpiar Event Schema al salir de la página
    this.seoService.removeEventSchema();
  }

  getFileIcon(tipo: string): string {
    if (tipo.includes('pdf')) return 'bi-file-earmark-pdf';
    if (tipo.includes('word') || tipo.includes('document')) return 'bi-file-earmark-word';
    if (tipo.includes('excel') || tipo.includes('spreadsheet')) return 'bi-file-earmark-excel';
    if (tipo.includes('image')) return 'bi-file-earmark-image';
    return 'bi-file-earmark';
  }

  descargarDocumento(documento: Documento): void {
    if (!documento?.id || !this.eventoId) {
      Swal.fire({
        title: 'Error',
        text: 'No se pudo descargar el documento del evento.',
        icon: 'error',
      });
      return;
    }

    this.endpointsService.descargarDocumentoEvento(this.eventoId, documento.id).subscribe({
      next: (blob) => {
        const url = globalThis.URL.createObjectURL(blob);
        globalThis.window?.open(url, '_blank', 'noopener');
        setTimeout(() => globalThis.URL.revokeObjectURL(url), 60_000);
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo descargar el documento del evento.',
          icon: 'error',
        });
      },
    });
  }
}
