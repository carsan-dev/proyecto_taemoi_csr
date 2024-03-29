import { Component, OnInit } from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { CommonModule } from '@angular/common';
import { ImagenInterface } from '../../../interfaces/imagen-interface';

@Component({
  selector: 'app-escaparate-principal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './escaparate-principal.component.html',
  styleUrl: './escaparate-principal.component.scss',
})
export class EscaparatePrincipalComponent implements OnInit {
  usuarioLogueado: boolean = false;
  imagenes: ImagenInterface[] = [
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

  constructor(private authService: AuthenticationService) {}

  ngOnInit(): void {
    this.usuarioLogueado = this.authService.comprobarLogueado();
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
  }
}
