import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProductosAlumnoNotasComponent } from './productos-alumno-notas.component';

describe('ProductosAlumnoNotasComponent', () => {
  let component: ProductosAlumnoNotasComponent;
  let fixture: ComponentFixture<ProductosAlumnoNotasComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductosAlumnoNotasComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ProductosAlumnoNotasComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
