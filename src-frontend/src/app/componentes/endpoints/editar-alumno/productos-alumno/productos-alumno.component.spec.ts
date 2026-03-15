import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProductosAlumnoComponent } from './productos-alumno.component';

describe('ProductosAlumnoComponent', () => {
  let component: ProductosAlumnoComponent;
  let fixture: ComponentFixture<ProductosAlumnoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductosAlumnoComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ProductosAlumnoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
