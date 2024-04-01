import { Injectable } from '@angular/core';
import { EventosInteface } from '../../interfaces/eventos-inteface';

@Injectable({
  providedIn: 'root'
})
export class EventosService {
  eventos: EventosInteface[] = [
    { titulo: 'Conferencia Angular', fecha: '2024-04-10', imagenUrl: '', descripcion: ''},
    { titulo: 'Taller de Desarrollo Web', fecha: '2024-04-15', imagenUrl: '', descripcion: ''},
    { titulo: 'Reunión de Networking', fecha: '2024-04-20', imagenUrl: '', descripcion: ''}
  ];

  constructor() { }

  obtenerEventos(): EventosInteface[] {
    return this.eventos;
  }
}
