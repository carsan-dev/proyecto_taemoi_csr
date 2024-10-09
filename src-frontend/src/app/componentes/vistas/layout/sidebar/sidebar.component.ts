// sidebar.component.ts
import { CommonModule } from '@angular/common';
import { Component, EventEmitter, OnDestroy, OnInit, Output, ViewChild, ElementRef } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { SidebarService } from '../../../../servicios/generales/sidebar.service';
import { Subscription } from 'rxjs';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent implements OnInit, OnDestroy {
  @Output() colapsoCambiado = new EventEmitter<boolean>();
  @ViewChild('sidebarContainer') sidebarContainer!: ElementRef;
  estaColapsado: boolean = true; // Start collapsed by default
  private subscription: Subscription = new Subscription();
  nombreUsuario: string | null = null;
  emailUsuario: string | null = null;

  constructor(
    private router: Router,
    private sidebarService: SidebarService,
    private authService: AuthenticationService
  ) {}

  ngOnInit(): void {
    this.subscription.add(
      this.sidebarService.obtenerSubjectAlternable().subscribe(() => {
        this.alternarColapso();
      })
    );
    this.subscription.add(
      this.authService.usernameCambio.subscribe((username) => {
        this.nombreUsuario = username;
      })
    );
    this.subscription.add(
      this.authService.emailCambio.subscribe((email) => {
        this.emailUsuario = email;
      })
    );
  }

  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  alternarColapso() {
    this.estaColapsado = !this.estaColapsado;
    this.colapsoCambiado.emit(this.estaColapsado);
    if (!this.estaColapsado) {
      this.resetScrollPosition();
    }
  }

  irARuta(ruta: string) {
    this.router.navigate([ruta]);
    this.alternarColapso(); // Auto-collapse sidebar when navigating
  }

  alternarVisibilidadSidebar(): void {
    this.alternarColapso();
  }

  resetScrollPosition(): void {
    if (this.sidebarContainer) {
      this.sidebarContainer.nativeElement.scrollTop = 0;
    }
  }
}