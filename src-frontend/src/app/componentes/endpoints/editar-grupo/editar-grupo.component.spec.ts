import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditarGrupoComponent } from './editar-grupo.component';

describe('EditarGrupoComponent', () => {
  let component: EditarGrupoComponent;
  let fixture: ComponentFixture<EditarGrupoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EditarGrupoComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(EditarGrupoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
