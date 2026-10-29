import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { BarraInferiorComponent } from './barra-inferior.component';
import { ContextoMenu, menuVisible } from './menu-panel';

describe('BarraInferiorComponent: navegación por sesión (R6)', () => {
  function montar(contexto: ContextoMenu, url: string) {
    TestBed.configureTestingModule({ imports: [BarraInferiorComponent], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(BarraInferiorComponent);
    fixture.componentRef.setInput('secciones', menuVisible(contexto));
    fixture.componentRef.setInput('soloProfesor', contexto.soloProfesor === true);
    fixture.componentRef.setInput('url', url);
    fixture.componentRef.setInput('activa', url.startsWith('/mis-grupos') ? 'grupos' : 'tesoreria');
    fixture.detectChanges();
    return fixture;
  }

  for (const misGrupos of [true, false]) {
    it(`solo-profesor con MIS_GRUPOS=${misGrupos}: solo sus enlaces, sin Más`, () => {
      const fixture = montar({ propietario: false, profesor: true, soloProfesor: true, misGrupos, plataforma: false }, '/mi-caja');
      const raiz = fixture.nativeElement as HTMLElement;
      const enlaces = Array.from(raiz.querySelectorAll<HTMLAnchorElement>('.pn-barra__entrada'));
      expect(enlaces.map((e) => e.getAttribute('href'))).toEqual(misGrupos ? ['/mis-grupos', '/mi-caja'] : ['/mi-caja']);
      expect(raiz.querySelector('.pn-barra__mas')).toBeNull();
      expect(raiz.querySelector('a[href="/mi-caja"]')?.getAttribute('aria-current')).toBe('page');
      expect(raiz.querySelector('.pn-barra__lista--profesor')).not.toBeNull();
      if (misGrupos) {
        fixture.componentRef.setInput('url', '/mis-grupos/4?mes=10');
        fixture.componentRef.setInput('activa', 'grupos');
        fixture.detectChanges();
        expect(raiz.querySelector('a[href="/mis-grupos"]')?.getAttribute('aria-current')).toBe('page');
        expect(raiz.querySelector('a[href="/mi-caja"]')?.hasAttribute('aria-current')).toBeFalse();
      }
    });
  }

  for (const propietario of [true, false]) {
    for (const profesor of [true, false]) {
      it(`gestión con propietario=${propietario} y profesor=${profesor}: conserva barra y desplegables`, () => {
        const fixture = montar({ propietario, profesor, misGrupos: profesor, plataforma: false }, '/mi-caja');
        const raiz = fixture.nativeElement as HTMLElement;
        expect(Array.from(raiz.querySelectorAll('.pn-barra__nombre')).map((e) => e.textContent?.trim()))
          .toEqual(['Inicio', 'Alumnos', 'Tesorería', 'Más']);
        raiz.querySelector<HTMLButtonElement>('.pn-barra__mas')!.click();
        fixture.detectChanges();
        expect(!!raiz.querySelector('#pn-menu-mas a[href="/mis-grupos"]')).toBe(profesor);
        expect(!!raiz.querySelector('#pn-menu-mas a[href="/mi-caja"]')).toBe(profesor);
        expect(raiz.querySelector('#pn-menu-mas a[href="/alumnosListar"]')).not.toBeNull();
      });
    }
  }
});
