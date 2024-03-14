import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { SidebarComponent } from '../layout/sidebar/sidebar.component';

@Component({
  selector: 'app-vista-principal-admin',
  standalone: true,
  imports: [CommonModule, SidebarComponent],
  templateUrl: './vista-principal-admin.component.html',
  styleUrl: './vista-principal-admin.component.scss',
})
export class VistaPrincipalAdminComponent implements OnInit {
  token: string | null = null;
  tokenExiste: boolean = false;

  constructor(private router: Router) {}

  ngOnInit(): void {
    if (typeof localStorage !== 'undefined') {
      this.token = localStorage.getItem('token');
      this.tokenExiste = !!this.token;
    }
  }

  irAListado() {
    this.router.navigate(['/alumnos']);
  }
}
