import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VistaLoginComponent } from './vista-login.component';

describe('VistaLoginComponent', () => {
  let component: VistaLoginComponent;
  let fixture: ComponentFixture<VistaLoginComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VistaLoginComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(VistaLoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
