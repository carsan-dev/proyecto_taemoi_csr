import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { GESTION_UNA_SEDE, GESTION_VARIAS_SEDES, RESPONSABLE_VARIAS_SEDES } from './filtro-sede-prueba';
import { FiltroSedeComponent, sedeVigente, sedesFiltrables, sedesParaFiltrar } from './filtro-sede.component';

@Component({
  standalone: true,
  imports: [FiltroSedeComponent],
  template: `<div class="barra"><pn-filtro-sede campoId="campo" [valor]="valor" [etiquetaVisible]="visible" [marcarElegida]="marcar"
    (cambio)="elegida = $event; cambios = cambios + 1" /></div>`,
})
class Anfitrion {
  valor: number | null = null;
  visible = false;
  marcar = false;
  elegida: number | null = null;
  cambios = 0;
}

describe('FiltroSedeComponent (9-6, decisión 376)', () => {
  let fixture: ComponentFixture<Anfitrion>;

  function crear(permisos: PermisosSesion | Error): void {
    TestBed.configureTestingModule({
      imports: [Anfitrion],
      providers: [
        {
          provide: PermisosSesionService,
          useValue: { obtener: () => (permisos instanceof Error ? throwError(() => permisos) : of(permisos)) },
        },
      ],
    });
    fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const select = () => el().querySelector<HTMLSelectElement>('select');

  describe('sedesParaFiltrar', () => {
    it('gestión y propietario con dos o más activas: las activas', () => {
      expect(sedesParaFiltrar(GESTION_VARIAS_SEDES).map((s) => s.nombre)).toEqual(['Casariche', 'Estepa']);
      expect(sedesParaFiltrar({ ...GESTION_VARIAS_SEDES, roles: ['PROPIETARIO'] }).length).toBe(2);
    });

    it('una sede, responsable, profesor, familia o sin permisos: ninguna', () => {
      expect(sedesParaFiltrar(GESTION_UNA_SEDE)).toEqual([]);
      expect(sedesParaFiltrar(RESPONSABLE_VARIAS_SEDES)).toEqual([]);
      expect(sedesParaFiltrar({ ...GESTION_VARIAS_SEDES, roles: ['PROFESOR'] })).toEqual([]);
      expect(sedesParaFiltrar({ ...GESTION_VARIAS_SEDES, roles: ['FAMILIA'] })).toEqual([]);
      expect(sedesParaFiltrar(null)).toEqual([]);
      expect(sedesParaFiltrar({} as PermisosSesion)).toEqual([]);
    });

    it('variasSedes con una sola activa en la lista: ninguna', () => {
      const [primera] = GESTION_VARIAS_SEDES.sedes;
      expect(sedesParaFiltrar({ ...GESTION_VARIAS_SEDES, sedes: [primera] })).toEqual([]);
    });
  });

  it('con una sede no pinta nada y su elemento no ocupa sitio en la barra', () => {
    crear(GESTION_UNA_SEDE);
    expect(select()).toBeNull();
    expect(el().querySelector('label')).toBeNull();
    expect(getComputedStyle(el().querySelector('pn-filtro-sede')!).display).toBe('none');
  });

  it('el responsable no lo ve', () => {
    crear(RESPONSABLE_VARIAS_SEDES);
    expect(select()).toBeNull();
  });

  it('si no se pueden leer los permisos, no se ve', () => {
    crear(new Error('sin permisos'));
    expect(select()).toBeNull();
  });

  it('gestión con dos sedes: «Todas las sedes» más las activas, con etiqueta «Sede» asociada', () => {
    crear(GESTION_VARIAS_SEDES);
    expect(Array.from(select()!.options).map((o) => [o.value, o.textContent!.trim()])).toEqual([
      ['', 'Todas las sedes'],
      ['1', 'Casariche'],
      ['2', 'Estepa'],
    ]);
    expect(select()!.value).toBe('');
    const etiqueta = el().querySelector('label')!;
    expect(etiqueta.textContent).toBe('Sede');
    expect(etiqueta.htmlFor).toBe(select()!.id);
    expect(select()!.labels![0]).toBe(etiqueta);
  });

  it('elegir emite el id, y «Todas las sedes», null', () => {
    crear(GESTION_VARIAS_SEDES);
    select()!.value = '2';
    select()!.dispatchEvent(new Event('change'));
    expect(fixture.componentInstance.elegida).toBe(2);
    select()!.value = '';
    select()!.dispatchEvent(new Event('change'));
    expect(fixture.componentInstance.elegida).toBeNull();
    expect(fixture.componentInstance.cambios).toBe(2);
  });

  it('refleja el valor de la pantalla, también al volver a null («Quitar filtros»)', () => {
    crear(GESTION_VARIAS_SEDES);
    const anfitrion = fixture.componentInstance;
    anfitrion.valor = 2;
    fixture.changeDetectorRef.detectChanges();
    expect(select()!.value).toBe('2');
    anfitrion.valor = null;
    fixture.changeDetectorRef.detectChanges();
    expect(select()!.value).toBe('');
  });

  it('el estado «Elegido» solo donde la barra lo pinta y con una sede elegida', () => {
    crear(GESTION_VARIAS_SEDES);
    const anfitrion = fixture.componentInstance;
    anfitrion.valor = 2;
    fixture.changeDetectorRef.detectChanges();
    expect(select()!.classList.contains('filtro-sede__control--elegido')).toBeFalse();
    anfitrion.marcar = true;
    fixture.changeDetectorRef.detectChanges();
    expect(select()!.classList.contains('filtro-sede__control--elegido')).toBeTrue();
    anfitrion.valor = null;
    fixture.changeDetectorRef.detectChanges();
    expect(select()!.classList.contains('filtro-sede__control--elegido')).toBeFalse();
  });

  it('una sede guardada solo vale si el selector la ofrece', () => {
    const conPermisos = (p: PermisosSesion) => ({ obtener: () => of(p) }) as unknown as PermisosSesionService;
    const leer = (p: PermisosSesion, id: number) => {
      let vigente: number | null | undefined;
      sedeVigente(conPermisos(p), id).subscribe((v) => (vigente = v));
      return vigente;
    };
    expect(leer(GESTION_VARIAS_SEDES, 2)).toBe(2);
    expect(leer(GESTION_VARIAS_SEDES, 3)).withContext('inactiva').toBeNull();
    expect(leer(GESTION_VARIAS_SEDES, 99)).toBeNull();
    expect(leer(GESTION_UNA_SEDE, 1)).toBeNull();
    expect(leer(RESPONSABLE_VARIAS_SEDES, 1)).toBeNull();
  });

  it('sedesFiltrables falla cerrado', () => {
    let sedes: unknown;
    sedesFiltrables({ obtener: () => throwError(() => new Error('x')) } as unknown as PermisosSesionService).subscribe((s) => (sedes = s));
    expect(sedes).toEqual([]);
  });
});
