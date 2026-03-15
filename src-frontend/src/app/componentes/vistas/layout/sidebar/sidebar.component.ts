// sidebar.component.ts
import { CommonModule } from '@angular/common';
import {
  Component,
  EventEmitter,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
  ElementRef,
} from '@angular/core';
import { SidebarService } from '../../../../servicios/generales/sidebar.service';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { Subscription } from 'rxjs/internal/Subscription';
import { NavigationStart, Router, RouterModule } from '@angular/router';

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
  estaColapsado: boolean = true;
  private readonly subscription: Subscription = new Subscription();
  nombreUsuario: string | null = null;
  emailUsuario: string | null = null;

  constructor(
    private readonly sidebarService: SidebarService,
    private readonly authService: AuthenticationService,
    private readonly router: Router
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

    this.subscription.add(
      this.router.events.subscribe((event) => {
        if (event instanceof NavigationStart) {
          this.estaColapsado = true;
        }
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

  alternarVisibilidadSidebar(): void {
    this.alternarColapso();
  }

  cerrarSidebar() {
    if (!this.estaColapsado) {
      this.estaColapsado = true;
      this.colapsoCambiado.emit(this.estaColapsado);
      this.resetScrollPosition();
    }
  }

  resetScrollPosition(): void {
    if (this.sidebarContainer) {
      this.sidebarContainer.nativeElement.scrollTop = 0;
    }
  }
}
