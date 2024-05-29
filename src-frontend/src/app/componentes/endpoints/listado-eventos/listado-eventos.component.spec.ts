import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ListadoEventosComponent } from './listado-eventos.component';

describe('ListadoEventosComponent', () => {
  let component: ListadoEventosComponent;
  let fixture: ComponentFixture<ListadoEventosComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListadoEventosComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ListadoEventosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
