import { Injectable } from '@angular/core';
import { EventosInteface } from '../../interfaces/eventos-inteface';

@Injectable({
  providedIn: 'root'
})
export class EventosService {
  eventos: EventosInteface[] = [
    { titulo: 'Campeonato Andalucía Cadete', fecha: '27 de Abril de 2024', imagenUrl: 'assets/media/campeonato_andalucia_cadete_2024-04-27.webp'},
    { titulo: 'Campeonato Andalucía Junior Pesos Olímpicos', fecha: '04 de Mayo de 2024', imagenUrl: ''},
    { titulo: 'Campeonato Andalucía Combate Deporte Base', fecha: '08 de Junio de 2024', imagenUrl: ''},
    { titulo: 'Campeonato de Andalucía Nocturno Campeón de Campeones Precadete y Cadete', fecha: '29 de Junio de 2024', imagenUrl: ''},
    { titulo: 'Programa Jóvenes Talentos Cadete', fecha: '21 de Septiembre de 2024', imagenUrl: ''},
    { titulo: 'Programa Jóvenes Talentos Junior y Entrenamiento Abierto', fecha: '22 de Septiembre de 2024', imagenUrl: ''},
    { titulo: 'Supercopa Andalucía Junior', fecha: '05 de Octubre de 2024', imagenUrl: ''},
    { titulo: 'Supercopa Andalucía Cadete', fecha: '26 de Octubre de 2024', imagenUrl: ''},
    { titulo: 'Campeonato Andalucía Junior 2025', fecha: '02 de Noviembre de 2024', imagenUrl: ''},
  ];

  constructor() { }

  obtenerEventos(): EventosInteface[] {
    return this.eventos;
  }
}
