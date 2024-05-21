import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SidebarService {
  private alternarSubject = new Subject<void>();

  alternarSidebar() {
    this.alternarSubject.next();
  }

  obtenerSubjectAlternable() {
    return this.alternarSubject.asObservable();
  }
}
