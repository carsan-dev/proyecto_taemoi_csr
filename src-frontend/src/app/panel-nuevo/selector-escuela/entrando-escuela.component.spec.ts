import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { CambiandoEscuelaComponent, sinSalirDuranteElCambio } from './cambiando-escuela.component';
import { EntrandoEscuelaComponent } from './entrando-escuela.component';
import { escuela } from './escuelas-prueba';
import { SelectorEscuelaService } from './selector-escuela.service';

@Component({
  standalone: true,
  imports: [EntrandoEscuelaComponent],
  template: `
    <div class="pn">
      <button type="button" class="pantalla-vieja">Guardar alumno</button>
      @if (visible()) {
      <pn-entrando-escuela [escuela]="escuela" />
      }
    </div>
  `,
})
class AnfitrionComponent {
  readonly visible = signal(true);
  readonly escuela = escuela({ id: 2, nombre: 'Club Ficticio Sur' });
}

describe('EntrandoEscuelaComponent («Entrando en …»)', () => {
  let fixture: ComponentFixture<AnfitrionComponent>;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const dialogo = (): HTMLDialogElement | null => el().querySelector<HTMLDialogElement>('dialog.pn-entrando');

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AnfitrionComponent] }).compileComponents();
    fixture = TestBed.createComponent(AnfitrionComponent);
    fixture.detectChanges();
  });

  it('es un diálogo modal: el resto de la página queda inerte', () => {
    const capa = dialogo()!;
    expect(capa.open).toBeTrue();
    expect(capa.matches(':modal')).toBeTrue();
    const vieja = el().querySelector<HTMLButtonElement>('.pantalla-vieja')!;
    vieja.focus();
    expect(document.activeElement).not.toBe(vieja);
  });

  it('mueve el foco al texto, que se anuncia', () => {
    const texto = el().querySelector<HTMLElement>('.pn-entrando__texto')!;
    expect(document.activeElement).toBe(texto);
    expect(texto.getAttribute('role')).toBe('status');
    expect(texto.textContent).toContain('Entrando en Club Ficticio Sur…');
    expect(dialogo()!.getAttribute('aria-labelledby')).toBe('pn-entrando-texto');
  });

  it('Esc no lo cierra', () => {
    const esc = new Event('cancel', { cancelable: true });
    dialogo()!.dispatchEvent(esc);
    expect(esc.defaultPrevented).toBeTrue();
    expect(dialogo()!.open).toBeTrue();
  });

  it('al quitarlo, la página vuelve a ser operable', () => {
    fixture.componentInstance.visible.set(false);
    fixture.detectChanges();
    expect(dialogo()).toBeNull();
    const vieja = el().querySelector<HTMLButtonElement>('.pantalla-vieja')!;
    vieja.focus();
    expect(document.activeElement).toBe(vieja);
  });
});

describe('CambiandoEscuelaComponent (ruta neutra)', () => {
  const SUR = escuela({ id: 2, nombre: 'Club Ficticio Sur' });

  function montar(entrando: typeof SUR | null): { fixture: ComponentFixture<CambiandoEscuelaComponent>; navegar: jasmine.Spy } {
    TestBed.configureTestingModule({
      imports: [CambiandoEscuelaComponent],
      providers: [
        provideRouter([]),
        {
          provide: SelectorEscuelaService,
          useValue: { entrando: signal(entrando), ocupado: entrando !== null, retieneLaRuta: entrando !== null },
        },
      ],
    });
    const navegar = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    const fixture = TestBed.createComponent(CambiandoEscuelaComponent);
    fixture.detectChanges();
    return { fixture, navegar };
  }

  it('con un cambio en vuelo pinta «Entrando en …» modal', () => {
    const { fixture, navegar } = montar(SUR);
    const capa = (fixture.nativeElement as HTMLElement).querySelector<HTMLDialogElement>('dialog.pn-entrando')!;
    expect(capa.matches(':modal')).toBeTrue();
    expect(capa.textContent).toContain('Entrando en Club Ficticio Sur…');
    expect(navegar).not.toHaveBeenCalled();
    expect(TestBed.runInInjectionContext(() => sinSalirDuranteElCambio())).toBeFalse();
  });

  it('sin cambio en vuelo (URL escrita a mano) va a la portada', () => {
    const { fixture, navegar } = montar(null);
    expect((fixture.nativeElement as HTMLElement).querySelector('dialog')).toBeNull();
    expect(navegar).toHaveBeenCalledOnceWith('/', { replaceUrl: true });
    expect(TestBed.runInInjectionContext(() => sinSalirDuranteElCambio())).toBeTrue();
  });
});
