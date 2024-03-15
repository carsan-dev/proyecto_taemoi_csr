import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EliminarAlumnoComponent } from './eliminar-alumno.component';

describe('EliminarAlumnoComponent', () => {
  let component: EliminarAlumnoComponent;
  let fixture: ComponentFixture<EliminarAlumnoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EliminarAlumnoComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(EliminarAlumnoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
