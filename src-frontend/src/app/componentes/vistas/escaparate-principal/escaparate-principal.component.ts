import {
  AfterViewInit,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
} from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { CommonModule } from '@angular/common';
import { SliderTocableComponent } from '../../generales/carousel/slider-tocable/slider-tocable.component';
import { HammerModule } from '@angular/platform-browser';
import { MapaComponent } from '../../generales/mapa/mapa.component';
import { Router } from '@angular/router';

@Component({
  selector: 'app-escaparate-principal',
  standalone: true,
  imports: [CommonModule, SliderTocableComponent, HammerModule, MapaComponent],
  templateUrl: './escaparate-principal.component.html',
  styleUrl: './escaparate-principal.component.scss',
})
export class EscaparatePrincipalComponent implements OnInit, AfterViewInit {
  @ViewChild('videoPresentacion')
  videoPresentacion!: ElementRef<HTMLVideoElement>;
  usuarioLogueado: boolean = false;
  eventoActualIndex: number = 0;
  idIntervalo: any;

  imagenesSlider = [
    {
      imgSrc: 'assets/media/dia_madre.webp',
      imgAlt:
        'Imagen que muestra un entrenamiento especial con motivo del día de la madre en el que se ven a los hijos y sus madres entrenando juntos.',
      captionTexto:
        'Celebra el día de la madre con un entrenamiento especial para fortalecer vínculos familiares',
    },
    {
      imgSrc: 'assets/media/dia_padre.webp',
      imgAlt:
        'Imagen que muestra un entrenamiento especial con motivo del día del padre en el que se ven a los hijos y sus padres entrenando juntos.',
      captionTexto:
        'Únete a nuestro entrenamiento especial por el día del padre y crea recuerdos inolvidables.',
    },
    {
      imgSrc: 'assets/media/estiramientos.webp',
      imgAlt:
        'Imagen que muestra una dinámica de estiramientos en un día de clase normal.',
      captionTexto:
        'Relaja tu cuerpo y fortalece tu mente con nuestra sesión de estiramientos al final de cada clase.',
    },
    {
      imgSrc: 'assets/media/reunion_tatami.webp',
      imgAlt:
        'Imagen que muestra a todos los alumnos haciendo un círculo y colaborando juntos.',
      captionTexto:
        'Experimenta la fuerza de la comunidad en nuestro club participando en las dinámicas de grupo.',
    },
    {
      imgSrc: 'assets/media/sara_competicion.webp',
      imgAlt:
        'Imagen que muestra a una de nuestras competidoras en una competición con motivo del día de la mujer.',
      captionTexto:
        'Apúntate al equipo de competición para llegar a lo más alto y aprender por el camino.',
    },
  ];

  constructor(
    private authService: AuthenticationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.usuarioLogueado = this.authService.comprobarLogueado();
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
  }

  ngAfterViewInit(): void {
    const video: HTMLVideoElement = this.videoPresentacion.nativeElement;

    video.addEventListener('canplaythrough', () => {
      video.muted = true;
      video.play();
    });
  }

  irARuta(ruta: string) {
    this.router.navigate([ruta]);
  }
}
