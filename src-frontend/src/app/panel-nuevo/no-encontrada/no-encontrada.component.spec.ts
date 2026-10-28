import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';

import { SeoService } from '../../servicios/generales/seo.service';
import { MascotaComponent } from '../mascota/mascota.component';
import { NoEncontradaPanelComponent, RUTA_INICIO_GESTION } from './no-encontrada.component';

describe('NoEncontradaPanelComponent (8b-M2b)', () => {
  let fixture: ComponentFixture<NoEncontradaPanelComponent>;
  let navegar: jasmine.Spy;
  let seo: jasmine.SpyObj<SeoService>;

  beforeEach(async () => {
    navegar = jasmine.createSpy('navigateByUrl').and.resolveTo(true);
    seo = jasmine.createSpyObj<SeoService>('SeoService', ['updateDynamicSeo']);
    await TestBed.configureTestingModule({
      imports: [NoEncontradaPanelComponent],
      providers: [
        { provide: Router, useValue: { navigateByUrl: navegar } },
        { provide: SeoService, useValue: seo },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(NoEncontradaPanelComponent);
    fixture.detectChanges();
  });

  const el = (): HTMLElement => fixture.nativeElement;

  it('es una pantalla .pn y pinta título y texto', () => {
    expect(el().querySelector('.pn')).not.toBeNull();
    expect(el().querySelector('.pn-bloque-vacio__titulo')?.textContent?.trim()).toBe('No encontramos esta página');
    expect(el().querySelector('.pn-bloque-vacio__texto')?.textContent?.trim()).toBe(
      'Puede que el enlace esté mal o que la página ya no exista.'
    );
  });

  it('lleva la mascota en la pose buscar', () => {
    const mascota = fixture.debugElement.query(By.directive(MascotaComponent));
    expect(mascota).not.toBeNull();
    expect((mascota.componentInstance as MascotaComponent).pose).toBe('buscar');
  });

  it('«Volver a Inicio» navega a la pantalla de inicio de gestión', () => {
    const boton = el().querySelector<HTMLButtonElement>('.pn-bloque-vacio__accion')!;
    expect(boton.textContent?.trim()).toBe('Volver a Inicio');
    boton.click();
    expect(navegar).toHaveBeenCalledOnceWith(RUTA_INICIO_GESTION);
    expect(RUTA_INICIO_GESTION).toBe('/adminpage');
  });

  it('fija el mismo título que el 404 público y no se indexa', () => {
    expect(seo.updateDynamicSeo).toHaveBeenCalledWith(
      jasmine.objectContaining({ title: "Página no encontrada (404) | Moi's Kim Do", noIndex: true })
    );
  });
});
