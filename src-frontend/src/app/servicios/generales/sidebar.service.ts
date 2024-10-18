import { Injectable } from '@angular/core';
import { Subject } from 'rxjs/internal/Subject';

@Injectable({
  providedIn: 'root'
})
export class SidebarService {
  private readonly alternarSubject = new Subject<void>();

  alternarSidebar() {
    this.alternarSubject.next();
  }

  obtenerSubjectAlternable() {
    return this.alternarSubject.asObservable();
  }
}
