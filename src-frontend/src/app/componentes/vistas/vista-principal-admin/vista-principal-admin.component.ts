import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-vista-principal-admin',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './vista-principal-admin.component.html',
  styleUrl: './vista-principal-admin.component.scss'
})
export class VistaPrincipalAdminComponent implements OnInit {
  token: string | null = null;

  ngOnInit(): void {
    this.token = localStorage.getItem('token');
  }
}
