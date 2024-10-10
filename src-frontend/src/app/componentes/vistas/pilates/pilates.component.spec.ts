import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PilatesComponent } from './pilates.component';

describe('PilatesComponent', () => {
  let component: PilatesComponent;
  let fixture: ComponentFixture<PilatesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PilatesComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(PilatesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
