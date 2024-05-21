import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VistaPrincipalAdminComponent } from './vista-principal-admin.component';

describe('VistaPrincipalAdminComponent', () => {
  let component: VistaPrincipalAdminComponent;
  let fixture: ComponentFixture<VistaPrincipalAdminComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VistaPrincipalAdminComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(VistaPrincipalAdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
