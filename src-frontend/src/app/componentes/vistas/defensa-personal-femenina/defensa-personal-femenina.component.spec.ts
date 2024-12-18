import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DefensaPersonalFemeninaComponent } from './defensa-personal-femenina.component';

describe('DefensaPersonalFemeninaComponent', () => {
  let component: DefensaPersonalFemeninaComponent;
  let fixture: ComponentFixture<DefensaPersonalFemeninaComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DefensaPersonalFemeninaComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(DefensaPersonalFemeninaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
