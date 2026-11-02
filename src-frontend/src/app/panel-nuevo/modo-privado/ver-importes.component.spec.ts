import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of } from 'rxjs';

import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { ModoPrivadoService } from './modo-privado.service';
import { VerImportesComponent } from './ver-importes.component';

describe('VerImportesComponent (decisión 419)', () => {
  let fixture: ComponentFixture<VerImportesComponent>;
  let modo: ModoPrivadoService;

  function montar(respuesta: Observable<PermisosSesion> = of({ importesTapados: true } as PermisosSesion)): void {
    TestBed.configureTestingModule({
      imports: [VerImportesComponent],
      providers: [{ provide: PermisosSesionService, useValue: { obtener: () => respuesta, generacion: 0, refrescado$: new Subject<void>() } }],
    });
    modo = TestBed.inject(ModoPrivadoService);
    fixture = TestBed.createComponent(VerImportesComponent);
    fixture.detectChanges();
  }

  const boton = () => (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button');
  const icono = () => boton()!.querySelector('i')!;

  it('con la preferencia apagada, sin botón y sin ocupar hueco', () => {
    montar(of({ importesTapados: false } as PermisosSesion));

    expect(boton()).toBeNull();
    expect(getComputedStyle(fixture.nativeElement).display).toBe('none');
  });

  it('mientras no se sabe, sin botón', () => {
    montar(new Subject<PermisosSesion>());

    expect(boton()).toBeNull();
    expect(modo.tapado()).toBeTrue();
  });

  it('con la preferencia activa: «Ver importes», sin pulsar, ojo abierto y 44 px de alto', () => {
    montar();

    expect(boton()!.textContent!.trim()).toBe('Ver importes');
    expect(boton()!.getAttribute('aria-pressed')).toBe('false');
    expect(icono().classList).toContain('bi-eye');
    expect(icono().getAttribute('aria-hidden')).toBe('true');
    expect(boton()!.getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
  });

  it('pulsarlo destapa y pasa a «Ocultar importes»; otra vez, vuelve a tapar', () => {
    montar();

    boton()!.click();
    fixture.detectChanges();

    expect(modo.tapado()).toBeFalse();
    expect(boton()!.textContent!.trim()).toBe('Ocultar importes');
    expect(boton()!.getAttribute('aria-pressed')).toBe('true');
    expect(icono().classList).toContain('bi-eye-slash');

    boton()!.click();
    fixture.detectChanges();

    expect(modo.tapado()).toBeTrue();
    expect(boton()!.textContent!.trim()).toBe('Ver importes');
  });

  it('al salir de la pantalla vuelve a taparse', () => {
    montar();
    boton()!.click();
    expect(modo.tapado()).toBeFalse();

    fixture.destroy();

    expect(modo.tapado()).toBeTrue();
  });
});
