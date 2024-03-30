import { Component, OnInit } from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { CommonModule } from '@angular/common';
import { ImagenInterface } from '../../../interfaces/imagen-interface';
import { SliderTocableComponent } from '../../generales/carousel/slider-tocable/slider-tocable.component';
import { HammerModule } from '@angular/platform-browser';

@Component({
  selector: 'app-escaparate-principal',
  standalone: true,
  imports: [CommonModule, SliderTocableComponent, HammerModule],
  templateUrl: './escaparate-principal.component.html',
  styleUrl: './escaparate-principal.component.scss',
})
export class EscaparatePrincipalComponent implements OnInit {
  usuarioLogueado: boolean = false;
  imagenesCarousel: ImagenInterface[] = [
    {
      imgSrc: 'assets/media/fachada_escuela.webp',
      imgAlt: 'Primera imagen del carrousel de la fachada de la escuela de taekwondo con un cartel que representa el nombre de la escuela y otro en el que se muestran los valores que se instruyen.',
      captionTitulo: "¡Bienvenid@ a MOI'S KIM DO!",
      captionTexto: 'Descubre un lugar donde los valores de respeto, disciplina y superación se convierten en tu mejor aliado.',
    },
    {
      imgSrc: 'assets/media/recepcion_escuela.webp',
      imgAlt: 'Segunda imagen del carrousel de la recepcion de la escuela de taekwondo en la que se muestra la mesa de la entrada donde se solicitan todo tipo de gestiones.',
      captionTitulo: '¡Tu paso hacia la grandeza!',
      captionTexto: 'Inicia tu camino hacia la maestría adentrándote en nuestro espacio de bienvenida y asesoramiento.',
    },
    {
      imgSrc: 'assets/media/interior_escuela.webp',
      imgAlt: 'Tercera imagen del carrousel con el tatami de la escuela de taekwondo en la que se muestra cómo es la zona de entrenamiento de la misma.',
      captionTitulo: 'Domina tu destino',
      captionTexto: 'Adéntrate en un entorno de entrenamiento espacioso y agradable diseñado para elevar tu habilidad y alcanzar tus metas.',
    },
    {
      imgSrc: 'assets/media/maquinas_escuela.webp',
      imgAlt: 'Cuarta imagen del carrousel con la zona de la escuela de taekwondo donde están las distintas máquinas para hacer ejercicios.',
      captionTitulo: 'Y por si fuera poco...',
      captionTexto: 'Experimenta el poder del equipo de calidad diseñado para desafiar tus límites y fortalecer tu cuerpo y mente.',
    },
  ];

  imagenesSlider = [
    {
      imgSrc: 'assets/media/dia_madre.webp',
      imgAlt: 'Imagen que muestra un entrenamiento especial con motivo del día de la madre en el que se ven a los hijos y sus madres entrenando juntos.',
      captionTexto: 'Celebra el día de la madre con un entrenamiento especial para fortalecer vínculos familiares',
    },
    {
      imgSrc: 'assets/media/dia_padre.webp',
      imgAlt: 'Imagen que muestra un entrenamiento especial con motivo del día del padre en el que se ven a los hijos y sus padres entrenando juntos.',
      captionTexto: 'Únete a nuestro entrenamiento especial por el día del padre y crea recuerdos inolvidables.',
    },
    {
      imgSrc: 'assets/media/estiramientos.webp',
      imgAlt: 'Imagen que muestra una dinámica de estiramientos en un día de clase normal.',
      captionTexto: 'Relaja tu cuerpo y fortalece tu mente con nuestra sesión de estiramientos al final de cada clase.',
    },
    {
      imgSrc: 'assets/media/reunion_tatami.webp',
      imgAlt: 'Imagen que muestra a todos los alumnos haciendo un círculo y colaborando juntos.',
      captionTexto: 'Experimenta la fuerza de la comunidad en nuestro club participando en las dinámicas de grupo.',
    },
    {
      imgSrc: 'assets/media/sara_competicion.webp',
      imgAlt: 'Imagen que muestra a una de nuestras competidoras en una competición con motivo del día de la mujer.',
      captionTexto: 'Apúntate al equipo de competición para llegar a lo más alto y aprender por el camino.',
    },
  ];

  constructor(private authService: AuthenticationService) {}

  ngOnInit(): void {
    this.usuarioLogueado = this.authService.comprobarLogueado();
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
  }
}
