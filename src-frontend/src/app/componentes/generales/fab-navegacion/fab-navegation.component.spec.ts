import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FabNavegacionComponent } from './fab-navegacion.component';

describe('FabNavegacionComponent', () => {
  let component: FabNavegacionComponent;
  let fixture: ComponentFixture<FabNavegacionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FabNavegacionComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FabNavegacionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
