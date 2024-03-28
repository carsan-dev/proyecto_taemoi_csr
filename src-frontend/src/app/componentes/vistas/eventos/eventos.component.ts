import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';

@Component({
  selector: 'app-eventos',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './eventos.component.html',
  styleUrl: './eventos.component.scss'
})
export class EventosComponent {
  eventos = [
    { nombre: 'Conferencia Angular', fecha: '2024-04-10' },
    { nombre: 'Taller de Desarrollo Web', fecha: '2024-04-15' },
    { nombre: 'Reunión de Networking', fecha: '2024-04-20' }
    // Puedes añadir más eventos aquí según sea necesario
  ];
}
