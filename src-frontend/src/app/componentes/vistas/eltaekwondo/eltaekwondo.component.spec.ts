import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EltaekwondoComponent } from './eltaekwondo.component';

describe('EltaekwondoComponent', () => {
  let component: EltaekwondoComponent;
  let fixture: ComponentFixture<EltaekwondoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EltaekwondoComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(EltaekwondoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
