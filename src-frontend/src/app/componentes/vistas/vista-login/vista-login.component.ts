import { Component} from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-vista-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './vista-login.component.html',
  styleUrl: './vista-login.component.scss',
})
export class VistaLoginComponent {
  email: string = '';
  contrasena: string = '';

  constructor(
    private authService: AuthenticationService,
    private router: Router
  ) {}

  login() {
    this.authService
      .login(this.email, this.contrasena)
      .subscribe((response) => {
        localStorage.setItem('token', response.token);
        this.router.navigate(['/ejemplo']);
      });
  }
}
