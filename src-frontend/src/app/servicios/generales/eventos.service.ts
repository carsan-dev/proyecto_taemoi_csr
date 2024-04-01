import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class EventosService {
  eventos = [
    { nombre: 'Conferencia Angular', fecha: '2024-04-10', imagenUrl: '', descripcion: ''},
    { nombre: 'Taller de Desarrollo Web', fecha: '2024-04-15', imagenUrl: '', descripcion: ''},
    { nombre: 'Reunión de Networking', fecha: '2024-04-20', imagenUrl: '', descripcion: ''}
  ];

  constructor() { }

  obtenerEventos() {
    return this.eventos;
  }
}
