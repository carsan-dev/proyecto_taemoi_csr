import { TestBed } from '@angular/core/testing';
import { SelectorDiscapacidadComponent } from './selector-discapacidad.component';

describe('SelectorDiscapacidadComponent', () => {
  it('marca y desmarca pastillas accesibles en el orden aprobado', () => {
    const fixture = TestBed.createComponent(SelectorDiscapacidadComponent);
    const c = fixture.componentInstance;
    fixture.componentRef.setInput('id', 'prueba');
    c.tiposChange.subscribe(tipos => { c.tipos = tipos; });
    fixture.detectChanges();
    const botones = (): HTMLButtonElement[] => Array.from(fixture.nativeElement.querySelectorAll('.op'));
    expect(botones().map(b => b.textContent!.trim())).toEqual(['Física', 'Intelectual', 'Visual', 'Auditiva', 'Psicosocial', 'Otra']);
    botones()[0].click(); fixture.detectChanges();
    expect(botones()[0].getAttribute('aria-pressed')).toBe('true');
    expect(botones()[0].textContent).toContain('✓');
    botones()[0].click(); fixture.detectChanges();
    expect(c.tipos).toEqual([]);
    expect(botones()[0].getAttribute('aria-pressed')).toBe('false');
    fixture.destroy();
  });
  it('Otra muestra campo obligatorio, contador vivo y error; al desmarcar se vacía', () => {
    const fixture = TestBed.createComponent(SelectorDiscapacidadComponent);
    const c = fixture.componentInstance;
    fixture.componentRef.setInput('id', 'prueba');
    c.tiposChange.subscribe(tipos => { c.tipos = tipos; });
    c.otraChange.subscribe(otra => { c.otra = otra; });
    c.alternar('OTRA'); fixture.detectChanges();
    const campo = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(campo.required).toBeTrue(); expect(campo.maxLength).toBe(4000);
    campo.value = 'Detalle'; campo.dispatchEvent(new Event('input')); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('7 / 4000');
    c.otra = ''; fixture.componentRef.setInput('error', true); fixture.detectChanges();
    expect(campo.getAttribute('aria-invalid')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('Indica cuál es la discapacidad.');
    c.alternar('OTRA'); fixture.detectChanges();
    expect(c.otra).toBe(''); expect(fixture.nativeElement.querySelector('input')).toBeNull();
    fixture.destroy();
  });
});
