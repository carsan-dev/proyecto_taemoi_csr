import { Injectable } from '@angular/core';
import { EventosInteface } from '../../interfaces/eventos-inteface';

@Injectable({
  providedIn: 'root'
})
export class EventosService {
  eventos: EventosInteface[] = [
    { titulo: 'Campeonato de Andalucía Cadete', fecha: '27 de Abril de 2024', imagenUrl: 'assets/media/campeonato_andalucia_cadete.webp', descripcion: 'Pulsar aquí para más información'},
  ];

  constructor() { }

  obtenerEventos(): EventosInteface[] {
    return this.eventos;
  }
}
