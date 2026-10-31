import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';

import { AuthenticationService, esSoloProfesor } from '../../../../servicios/authentication/authentication.service';
import { FooterComponent } from './footer.component';

describe('FooterComponent', () => {
  async function crear(roles: string[]) {
    const auth = {
      tieneAccesoAdmin: () => roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER'),
      esSoloProfesor: () => esSoloProfesor(roles),
    };
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [FooterComponent],
      providers: [provideRouter([]), { provide: AuthenticationService, useValue: auth }],
    }).compileComponents();
    const fixture = TestBed.createComponent(FooterComponent);
    fixture.detectChanges();
    return fixture;
  }

  /** Pulsa «Inicio» del pie y devuelve el `state` con el que navega. */
  async function estadoAlPulsarInicio(roles: string[]) {
    const fixture = await crear(roles);
    const navegar = spyOn(TestBed.inject(Router), 'navigateByUrl').and.returnValue(Promise.resolve(true));
    const inicio = fixture.debugElement
      .queryAll(By.css('a'))
      .find((a) => a.nativeElement.textContent.trim() === 'Inicio');
    inicio!.nativeElement.click();
    return navegar.calls.mostRecent().args[1]?.state;
  }

  it('should create', async () => {
    expect(await crear([])).toBeTruthy();
  });

  it('visitante: «Inicio» sin verWeb', async () => {
    expect(await estadoAlPulsarInicio([])).toBeUndefined();
  });

  it('familia: «Inicio» sin verWeb (sigue yendo a /userpage)', async () => {
    expect(await estadoAlPulsarInicio(['ROLE_USER'])).toBeUndefined();
  });

  it('gestión: «Inicio» con verWeb', async () => {
    expect(await estadoAlPulsarInicio(['ROLE_ADMIN'])).toEqual({ verWeb: true });
  });

  it('solo profesor: «Inicio» con verWeb', async () => {
    expect(await estadoAlPulsarInicio(['ROLE_PROFESOR'])).toEqual({ verWeb: true });
  });
});
