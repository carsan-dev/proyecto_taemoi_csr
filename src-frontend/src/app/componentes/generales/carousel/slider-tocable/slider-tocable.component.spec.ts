import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SliderTocableComponent } from './slider-tocable.component';

describe('SliderTocableComponent', () => {
  let component: SliderTocableComponent;
  let fixture: ComponentFixture<SliderTocableComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SliderTocableComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(SliderTocableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
