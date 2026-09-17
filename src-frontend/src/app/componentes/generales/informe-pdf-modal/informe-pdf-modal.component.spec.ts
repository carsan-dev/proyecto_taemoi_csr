import { TestBed } from '@angular/core/testing';
import { InformePdfModalComponent } from './informe-pdf-modal.component';
import { ScrollLockService } from '../../../servicios/generales/scroll-lock.service';

describe('InformePdfModalComponent', () => {
  it('descarga sólo el documento visible sin generar otra petición; restaura foco y scroll', () => {
    TestBed.configureTestingModule({ imports: [InformePdfModalComponent] });
    const trigger = document.createElement('button'); document.body.append(trigger); trigger.focus();
    const fixture = TestBed.createComponent(InformePdfModalComponent);
    const component = fixture.componentInstance;
    const uno = { blob: new Blob(['uno']), nombreArchivo: 'uno.pdf', titulo: 'Uno' };
    const dos = { blob: new Blob(['dos']), nombreArchivo: 'dos.pdf', titulo: 'Dos' };
    // Keep rendering outside this modal test; its failures must never gate download.
    fixture.detectChanges();
    component.documentos = [uno, dos]; component.indice = 1;
    spyOn(URL, 'createObjectURL').and.returnValue('blob:test');
    spyOn(URL, 'revokeObjectURL');
    let nombre = '';
    spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function(this: HTMLAnchorElement) { nombre = this.download; });
    component.descargar();
    expect(URL.createObjectURL).toHaveBeenCalledOnceWith(dos.blob);
    expect(nombre).toBe('dos.pdf');
    expect(TestBed.inject(ScrollLockService).activeLocks).toBe(1);
    expect(component.dialog.nativeElement.open).toBeTrue();
    fixture.destroy();
    expect(TestBed.inject(ScrollLockService).activeLocks).toBe(0);
    expect(document.activeElement).toBe(trigger);
    trigger.remove();
  });
  it('Escape cierra primero expansión', () => {
    const fixture = TestBed.createComponent(InformePdfModalComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();
    component.visor = { cerrarExpansion: jasmine.createSpy().and.returnValues(true, false) } as any;
    const first = new Event('cancel', { cancelable: true }); component.cancelar(first);
    expect(first.defaultPrevented).toBeTrue();
    const second = new Event('cancel', { cancelable: true }); component.cancelar(second);
    expect(second.defaultPrevented).toBeFalse();
    fixture.destroy();
  });
});
