import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LoadingService } from '../../../servicios/generales/loading.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-loading-spinner',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './loading-spinner.component.html',
  styleUrls: ['./loading-spinner.component.scss'],
})
export class LoadingSpinnerComponent implements OnInit {
  loading$: Observable<boolean>;

  constructor(private loadingService: LoadingService) {
    this.loading$ = this.loadingService.loading$;
  }

  ngOnInit(): void {}
}
