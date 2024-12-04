import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ListadoConvocatoriasComponent } from './listado-convocatorias.component';

describe('ListadoConvocatoriasComponent', () => {
  let component: ListadoConvocatoriasComponent;
  let fixture: ComponentFixture<ListadoConvocatoriasComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListadoConvocatoriasComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ListadoConvocatoriasComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
