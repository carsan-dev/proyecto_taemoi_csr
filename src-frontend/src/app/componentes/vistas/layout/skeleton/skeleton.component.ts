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
  usuarioLogueado: boolean = false;

  constructor(private authService: AuthenticationService) {}

  ngOnInit(): void {
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
  }
}
