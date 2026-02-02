import { Component } from '@angular/core';
import { FooterComponent } from '../footer/footer.component';
import { HeaderComponent } from '../header/header.component';
import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { FabNavegacionComponent } from '../../../generales/fab-navegacion/fab-navegacion.component';
import { CookieConsentComponent } from '../../../generales/cookie-consent/cookie-consent.component';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [
    HeaderComponent,
    FooterComponent,
    BotonscrollComponent,
    FabNavegacionComponent,
    CookieConsentComponent,
    CommonModule,
  ],
  templateUrl: './skeleton.component.html',
  styleUrl: './skeleton.component.scss',
})
export class SkeletonComponent {}
