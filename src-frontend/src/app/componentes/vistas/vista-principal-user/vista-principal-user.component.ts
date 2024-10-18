import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs/internal/Subscription';

@Component({
  selector: 'app-vista-principal-user',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './vista-principal-user.component.html',
  styleUrl: './vista-principal-user.component.scss',
})
export class VistaPrincipalUserComponent implements OnInit, OnDestroy {
  grupos: any[] = [];
  private subscriptions: Subscription = new Subscription();

  constructor(
    public endpointsService: EndpointsService,
    private readonly authService: AuthenticationService
  ) {}

  ngOnInit() {
    this.authService.obtenerUsuarioAutenticado().subscribe({
      next: (usuario) => {
        if (usuario && usuario.alumnoDTO) {
          const alumnoId = usuario.alumnoDTO.id;
          this.cargarGruposDelAlumno(alumnoId);
        } else {
          Swal.fire({
            title: 'Error',
            text: 'El usuario no tiene alumno asociado.',
            icon: 'error',
          });
        }
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'Error al obtener el usuario.',
          icon: 'error',
        });
      },
    });
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  cargarGruposDelAlumno(alumnoId: number): void {
    // Suscribirse al Observable expuesto por el BehaviorSubject
    const gruposSubscription = this.endpointsService.gruposDelAlumno$.subscribe({
      next: (grupos) => {
        this.grupos = grupos;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'Error al obtener los grupos del alumno.',
          icon: 'error',
        });
      },
    });

    this.subscriptions.add(gruposSubscription);

    // Llamar al método que inicia la carga de datos
    this.endpointsService.obtenerGruposDelAlumno(alumnoId);
  }
}
