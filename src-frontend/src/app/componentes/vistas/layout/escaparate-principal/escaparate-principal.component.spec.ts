import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EscaparatePrincipalComponent } from './escaparate-principal.component';

describe('EscaparatePrincipalComponent', () => {
  let component: EscaparatePrincipalComponent;
  let fixture: ComponentFixture<EscaparatePrincipalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EscaparatePrincipalComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(EscaparatePrincipalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
