import { Component, OnInit } from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-eltaekwondo',
  standalone: true,
  imports: [],
  templateUrl: './eltaekwondo.component.html',
  styleUrl: './eltaekwondo.component.scss'
})
export class EltaekwondoComponent implements OnInit {
  usuarioLogueado: boolean = false;

  constructor(private authService: AuthenticationService, private router: Router) { }

  ngOnInit(): void {
    this.usuarioLogueado = this.authService.comprobarLogueado();
    this.authService.usuarioLogueadoCambio.subscribe((estado: boolean) => {
      this.usuarioLogueado = estado;
    });
  }

  irARuta(ruta: string) {
    this.router.navigate([ruta]);
  }
}
