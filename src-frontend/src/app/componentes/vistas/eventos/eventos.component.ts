import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { EventosInteface } from '../../../interfaces/eventos-inteface';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EventosService } from '../../../servicios/generales/eventos.service';

@Component({
  selector: 'app-eventos',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './eventos.component.html',
  styleUrl: './eventos.component.scss'
})
export class EventosComponent implements OnInit {
  eventos: EventosInteface[] = [];
  usuarioLogueado: boolean = false;

  constructor(private authService: AuthenticationService, private eventosService: EventosService) { }

  ngOnInit(): void {
    this.usuarioLogueado = this.authService.comprobarLogueado();
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
    this.eventos = this.eventosService.obtenerEventos();
  }

}
