import { Component, OnDestroy, OnInit } from '@angular/core';
import { FooterComponent } from '../footer/footer.component';
import { HeaderComponent } from '../header/header.component';
import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { CommonModule } from '@angular/common';
import { Subject } from 'rxjs/internal/Subject';
import { takeUntil } from 'rxjs/internal/operators/takeUntil';
import { switchMap } from 'rxjs/internal/operators/switchMap';
import { of } from 'rxjs/internal/observable/of';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [
    HeaderComponent,
    FooterComponent,
    BotonscrollComponent,
    SidebarComponent,
    CommonModule,
  ],
  templateUrl: './skeleton.component.html',
  styleUrl: './skeleton.component.scss',
})
export class SkeletonComponent implements OnInit, OnDestroy {
  tieneRolAdminOManager: boolean = false;
  sidebarColapsado: boolean = false;
  mostrarLoader: boolean = false;
  private unsubscribe$ = new Subject<void>();

  constructor(private readonly authService: AuthenticationService) {}

  ngOnInit(): void {
    // Solo obtener roles si el usuario está autenticado
    this.authService.usuarioLogueadoCambio
      .pipe(
        takeUntil(this.unsubscribe$),
        switchMap((estado: boolean) => {
          if (estado) {
            this.mostrarLoader = true;  // Mostrar loader
            return this.authService.obtenerRoles();
          }
          return of([]);  // No hacer nada si no está autenticado
        })
      )
      .subscribe((roles: string[]) => {
        this.mostrarLoader = false;  // Ocultar loader cuando roles se obtienen
        this.tieneRolAdminOManager =
          roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER');
      });
  }

  ngOnDestroy(): void {
    this.unsubscribe$.next();
    this.unsubscribe$.complete();
  }

  onColapsoCambiado(colapsado: boolean) {
    this.sidebarColapsado = colapsado;
  }
}
