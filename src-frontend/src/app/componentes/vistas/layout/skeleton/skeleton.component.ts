import { Component, OnDestroy, OnInit } from '@angular/core';
import { FooterComponent } from '../footer/footer.component';
import { HeaderComponent } from '../header/header.component';
import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [HeaderComponent, FooterComponent, BotonscrollComponent, SidebarComponent, CommonModule],
  templateUrl: './skeleton.component.html',
  styleUrl: './skeleton.component.scss'
})
export class SkeletonComponent implements OnInit, OnDestroy {
  tieneRolAdminOManager: boolean = false;
  sidebarColapsado: boolean = false;
  private unsubscribe$ = new Subject<void>();

  constructor(private authService: AuthenticationService) {}

  ngOnInit(): void {
    this.authService.rolesCambio
      .pipe(takeUntil(this.unsubscribe$))
      .subscribe((roles: string[]) => {
        this.tieneRolAdminOManager = roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER');
      });

    this.authService.usuarioLogueadoCambio
      .pipe(takeUntil(this.unsubscribe$))
      .subscribe((estado: boolean) => {
        if (!estado) {
          this.tieneRolAdminOManager = false;
        }
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
