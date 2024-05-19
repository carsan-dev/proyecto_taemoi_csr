import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VistaPrincipalUserComponent } from './vista-principal-user.component';

describe('VistaPrincipalUserComponent', () => {
  let component: VistaPrincipalUserComponent;
  let fixture: ComponentFixture<VistaPrincipalUserComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VistaPrincipalUserComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(VistaPrincipalUserComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
