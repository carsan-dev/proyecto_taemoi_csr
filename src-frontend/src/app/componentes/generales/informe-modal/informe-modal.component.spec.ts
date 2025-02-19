import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InformeModalComponent } from './informe-modal.component';

describe('InformeModalComponent', () => {
  let component: InformeModalComponent;
  let fixture: ComponentFixture<InformeModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InformeModalComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(InformeModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
