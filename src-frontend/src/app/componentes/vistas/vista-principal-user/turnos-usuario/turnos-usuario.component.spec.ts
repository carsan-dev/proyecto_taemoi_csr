import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TurnosUsuarioComponent } from './turnos-usuario.component';

describe('TurnosUsuarioComponent', () => {
  let component: TurnosUsuarioComponent;
  let fixture: ComponentFixture<TurnosUsuarioComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TurnosUsuarioComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(TurnosUsuarioComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
