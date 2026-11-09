import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HojaRallasComponent } from './hoja-rallas.component';

describe('HojaRallasComponent (hoja de rallas, decisiones 510-513)', () => {
  let fixture: ComponentFixture<HojaRallasComponent>;
  let pasos: number[];
  let aptos: number;
  let cierres: number;

  const dialogo = () => fixture.nativeElement.querySelector('dialog') as HTMLDialogElement;
  const texto = (n: Element | null | undefined) => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const paso = (p: number) => dialogo().querySelector<HTMLButtonElement>(`[data-paso="${p}"]`)!;
  const deshabilitados = () =>
    Array.from(dialogo().querySelectorAll<HTMLButtonElement>('[data-paso]')).filter((b) => b.getAttribute('aria-disabled') === 'true').map((b) => texto(b));

  function crear(medias: number, extra: Partial<{ genero: string | null; enCurso: boolean }> = {}): void {
    fixture = TestBed.createComponent(HojaRallasComponent);
    fixture.componentRef.setInput('nombre', 'Lucía Romero Gil');
    fixture.componentRef.setInput('nombreCorto', 'Lucía');
    fixture.componentRef.setInput('subtitulo', 'Taekwondo · Verde');
    fixture.componentRef.setInput('color1', '#008000');
    fixture.componentRef.setInput('medias', medias);
    fixture.componentRef.setInput('genero', extra.genero ?? null);
    fixture.componentRef.setInput('enCurso', extra.enCurso ?? false);
    pasos = [];
    aptos = 0;
    cierres = 0;
    fixture.componentInstance.paso.subscribe((p) => pasos.push(p));
    fixture.componentInstance.apto.subscribe(() => aptos++);
    fixture.componentInstance.cerrar.subscribe(() => cierres++);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  }

  function cambiarMedias(medias: number): void {
    fixture.componentRef.setInput('medias', medias);
    fixture.detectChanges();
  }

  beforeEach(() => TestBed.configureTestingModule({ imports: [HojaRallasComponent] }));
  afterEach(() => {
    fixture.destroy();
    fixture.nativeElement.remove();
  });

  it('abre modal con el nombre, la disciplina y el grado, la punta grande y la cuenta', () => {
    crear(5);
    expect(dialogo().open).toBeTrue();
    expect(texto(dialogo().querySelector('h2'))).toBe('Lucía Romero Gil');
    expect(texto(dialogo().querySelector('.rallas-hoja__sub'))).toBe('Taekwondo · Verde');
    expect(dialogo().querySelector('pn-cinturon svg[data-tamano="hoja"]')).not.toBeNull();
    expect(texto(dialogo().querySelector('[data-cuenta]'))).toBe('2 y media de 5 rallas');
    expect(texto(dialogo().querySelector('.rallas-hoja__falta'))).toBe('Le faltan 2 y media para el examen.');
    expect(Array.from(dialogo().querySelectorAll('[data-paso]')).map((b) => texto(b))).toEqual(['− Una', '− Media', '+ Media', '+ Una']);
    expect(deshabilitados()).toEqual([]);
    expect(dialogo().querySelector('[data-nota]')).toBeNull();
    expect(dialogo().querySelector('[data-apto]')).toBeNull();
  });

  it('el foco empieza en el primer paso que se puede dar', () => {
    crear(0);
    expect(document.activeElement).toBe(paso(1));
  });

  it('deshabilitados del boceto (aria-disabled, siguen enfocables) con su nota', () => {
    crear(0);
    expect(deshabilitados()).toEqual(['− Una', '− Media']);
    expect(texto(dialogo().querySelector('[data-nota]'))).toBe('Aún no tiene rallas.');
    expect(paso(-2).disabled).toBeFalse();
    cambiarMedias(1);
    expect(deshabilitados()).toEqual(['− Una']);
    expect(texto(dialogo().querySelector('[data-nota]'))).toBe('Solo tiene media ralla.');
    cambiarMedias(9);
    expect(deshabilitados()).toEqual(['+ Una']);
    expect(texto(dialogo().querySelector('[data-nota]'))).toBe('Solo cabe media ralla más.');
    cambiarMedias(10);
    expect(deshabilitados()).toEqual(['+ Media', '+ Una']);
  });

  it('un paso habilitado se emite; uno deshabilitado no', () => {
    crear(1);
    paso(-2).click();
    paso(-1).click();
    paso(2).click();
    expect(pasos).toEqual([-1, 2]);
  });

  it('con una llamada en curso no se emite nada (sin dobles toques)', () => {
    crear(4, { enCurso: true });
    paso(1).click();
    expect(pasos).toEqual([]);
    expect(dialogo().querySelector('[role="group"]')!.getAttribute('aria-busy')).toBe('true');
  });

  it('con 10 medias: «Lucía ha completado sus 5 rallas.» y «Marcar apta para examen»', () => {
    crear(10, { genero: 'FEMENINO' });
    expect(texto(dialogo().querySelector('.rallas-hoja__completa'))).toBe('Lucía ha completado sus 5 rallas.');
    const apto = dialogo().querySelector<HTMLButtonElement>('[data-apto]')!;
    expect(texto(apto)).toBe('Marcar apta para examen');
    expect(dialogo().querySelector('.rallas-hoja__falta')).toBeNull();
    apto.click();
    expect(aptos).toBe(1);
  });

  it('sin género en la ficha: «Marcar apto para examen»', () => {
    crear(10);
    expect(texto(dialogo().querySelector('[data-apto]'))).toBe('Marcar apto para examen');
  });

  it('Esc y la X cierran', () => {
    crear(3);
    dialogo().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    dialogo().querySelector<HTMLButtonElement>('[aria-label="Cerrar"]')!.click();
    expect(cierres).toBe(2);
  });
});
