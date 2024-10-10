import { ComponentFixture, TestBed } from '@angular/core/testing';

import { KickboxingComponent } from './kickboxing.component';

describe('KickboxingComponent', () => {
  let component: KickboxingComponent;
  let fixture: ComponentFixture<KickboxingComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KickboxingComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(KickboxingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
