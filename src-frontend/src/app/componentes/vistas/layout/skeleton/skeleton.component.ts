import { Component } from '@angular/core';
import { FooterComponent } from '../footer/footer.component';
import { HeaderComponent } from '../header/header.component';
import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [HeaderComponent, FooterComponent, BotonscrollComponent, SidebarComponent, CommonModule],
  templateUrl: './skeleton.component.html',
  styleUrl: './skeleton.component.scss'
})
export class SkeletonComponent {
  tieneRolAdminOManager: boolean = false;
  sidebarColapsado: boolean = false;

  constructor(private authService: AuthenticationService) {}

  ngOnInit(): void {
    this.authService.rolesCambio.subscribe((roles: string[]) => {
      this.tieneRolAdminOManager = roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER');
    });

    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      if (!estado) {
        this.tieneRolAdminOManager = false;
      }
    });
  }


  onColapsoCambiado(colapsado: boolean) {
    this.sidebarColapsado = colapsado;
  }
}
