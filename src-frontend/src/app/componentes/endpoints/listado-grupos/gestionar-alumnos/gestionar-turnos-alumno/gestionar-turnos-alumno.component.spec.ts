import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GestionarTurnosAlumnoComponent } from './gestionar-turnos-alumno.component';

describe('GestionarTurnosAlumnoComponent', () => {
  let component: GestionarTurnosAlumnoComponent;
  let fixture: ComponentFixture<GestionarTurnosAlumnoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GestionarTurnosAlumnoComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(GestionarTurnosAlumnoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
