import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GrupoAlumnosModalComponent } from './grupo-alumnos-modal.component';

describe('GrupoAlumnosModalComponent', () => {
  let component: GrupoAlumnosModalComponent;
  let fixture: ComponentFixture<GrupoAlumnosModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GrupoAlumnosModalComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(GrupoAlumnosModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
