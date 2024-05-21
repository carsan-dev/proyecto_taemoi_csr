import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BotonscrollComponent } from './botonscroll.component';

describe('BotonscrollComponent', () => {
  let component: BotonscrollComponent;
  let fixture: ComponentFixture<BotonscrollComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BotonscrollComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(BotonscrollComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
