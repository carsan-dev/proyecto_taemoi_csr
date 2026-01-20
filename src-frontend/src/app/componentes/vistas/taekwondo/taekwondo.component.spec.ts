import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TaekwondoComponent } from './taekwondo.component';

describe('TaekwondoComponent', () => {
  let component: TaekwondoComponent;
  let fixture: ComponentFixture<TaekwondoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TaekwondoComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(TaekwondoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
