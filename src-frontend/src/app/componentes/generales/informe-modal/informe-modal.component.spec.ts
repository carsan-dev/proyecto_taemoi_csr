import { ComponentFixture, TestBed } from '@angular/core/testing';

import { InformeModalComponent } from './informe-modal.component';

describe('InformeModalComponent', () => {
  let component: InformeModalComponent;
  let fixture: ComponentFixture<InformeModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InformeModalComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(InformeModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('agrupa los listados una sola vez y conserva las demás categorías', () => {
    const lists = ['general', 'taekwondo', 'kickboxing', 'competidores', 'dpf'];
    component.opcionesInforme = [...lists, 'licencias', 'infantiles', 'deudas'].map(value => ({ value, label: value }));
    component.organizarInformesPorCategoria();
    expect(component.categoriasInforme[0].title).toBe('Listados de Alumnos');
    expect(component.categoriasInforme[0].opciones.map(option => option.value)).toEqual(lists);
    expect(component.categoriasInforme.flatMap(category => category.opciones)).toHaveSize(8);
    expect(component.categoriasInforme.map(category => category.title)).toEqual([
      'Listados de Alumnos', 'Informes de Promoción', 'Informes Financieros', 'Otros Informes'
    ]);
    expect(component.getDescription('competidores')).toContain('Taekwondo y Kickboxing');
    expect(component.getDescription('dpf')).toContain('nombres y apellidos');
  });

  it('emite la vista previa y descarga de DPF con el filtro de alumnos activos', () => {
    const emitted = spyOn(component.informeSeleccionado, 'emit');
    component.selectedInforme = 'dpf';
    component.soloActivos = false;
    for (const accion of ['ver', 'descargar'] as const) {
      component.generarInforme(accion);
      expect(emitted).toHaveBeenCalledWith({ tipo: 'dpf', soloActivos: false, temporada: undefined, accion });
    }
  });
});
