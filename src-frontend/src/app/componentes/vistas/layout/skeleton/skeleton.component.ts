import { Component } from '@angular/core';
import { FooterComponent } from '../footer/footer.component';
import { HeaderComponent } from '../header/header.component';
import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [
    HeaderComponent,
    FooterComponent,
    BotonscrollComponent,
    CommonModule,
  ],
  templateUrl: './skeleton.component.html',
  styleUrl: './skeleton.component.scss',
})
export class SkeletonComponent {}
