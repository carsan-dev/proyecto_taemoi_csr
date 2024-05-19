import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TurnosGrupoComponent } from './turnos-grupo.component';

describe('TurnosGrupoComponent', () => {
  let component: TurnosGrupoComponent;
  let fixture: ComponentFixture<TurnosGrupoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TurnosGrupoComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(TurnosGrupoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
