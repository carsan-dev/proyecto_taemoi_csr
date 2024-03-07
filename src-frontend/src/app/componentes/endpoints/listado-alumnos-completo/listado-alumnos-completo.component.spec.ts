import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ListadoAlumnosCompletoDTOComponent } from './listado-alumnos-completo.component';

describe('ListadoAlumnosCompletoComponent', () => {
  let component: ListadoAlumnosCompletoDTOComponent;
  let fixture: ComponentFixture<ListadoAlumnosCompletoDTOComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListadoAlumnosCompletoDTOComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ListadoAlumnosCompletoDTOComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
