import { Component } from '@angular/core';
import { HeaderComponent } from '../header/header.component';
import { NavegacionComponent } from '../navegacion/navegacion.component';
import { FooterComponent } from '../footer/footer.component';
import { SidebarComponent } from '../sidebar/sidebar.component';

@Component({
  selector: 'app-escaparate-principal',
  standalone: true,
  imports: [HeaderComponent, NavegacionComponent, FooterComponent, SidebarComponent],
  templateUrl: './escaparate-principal.component.html',
  styleUrl: './escaparate-principal.component.scss'
})
export class EscaparatePrincipalComponent {

}
