import { Component, OnDestroy, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs/internal/Subscription';
import { EventosVistaComponent } from './eventos-vista.component';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';

@Component({
  selector: 'app-eventos',
  standalone: true,
  imports: [EventosVistaComponent],
  templateUrl: './eventos.component.html',
  styleUrl: './eventos.component.scss',
})
export class EventosComponent implements OnInit, OnDestroy {
  eventos: any[] = [];
  isLoading: boolean = true;
  showUserBackButton: boolean = false;
  private readonly subscriptions: Subscription = new Subscription();

  constructor(
    public endpointsService: EndpointsService,
    private readonly router: Router,
    private readonly authService: AuthenticationService
  ) {}

  ngOnInit(): void {
    this.actualizarBotonVolverUsuario();

    const authSubscription = this.authService.usuarioLogueadoCambio.subscribe(() => {
      this.actualizarBotonVolverUsuario();
    });
    const rolesSubscription = this.authService.rolesCambio.subscribe(() => {
      this.actualizarBotonVolverUsuario();
    });
    this.subscriptions.add(authSubscription);
    this.subscriptions.add(rolesSubscription);

    // Suscribirse al observable de eventos
    const eventosSubscription = this.endpointsService.eventos$.subscribe({
      next: (eventos) => {
        this.eventos = eventos;
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        Swal.fire({
          title: 'Error',
          text: 'No hemos podido obtener los eventos.',
          icon: 'error',
        });
      },
    });

    this.subscriptions.add(eventosSubscription);

    // Iniciar la carga de eventos
    this.endpointsService.obtenerEventos();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  verDetalle(id: number) {
    this.router.navigate(['/eventos', id]);
  }

  volverAreaUsuario(): void {
    this.router.navigate(['/userpage']);
  }

  private actualizarBotonVolverUsuario(): void {
    this.showUserBackButton =
      this.authService.comprobarLogueado() &&
      this.authService.tieneRolUser();
  }
}
