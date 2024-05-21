import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SeleccionarGrupoComponent } from './seleccionar-grupo.component';

describe('SeleccionarGrupoComponent', () => {
  let component: SeleccionarGrupoComponent;
  let fixture: ComponentFixture<SeleccionarGrupoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SeleccionarGrupoComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(SeleccionarGrupoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
