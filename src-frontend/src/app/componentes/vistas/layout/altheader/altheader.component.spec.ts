import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AltheaderComponent } from './altheader.component';

describe('AltheaderComponent', () => {
  let component: AltheaderComponent;
  let fixture: ComponentFixture<AltheaderComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AltheaderComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AltheaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
