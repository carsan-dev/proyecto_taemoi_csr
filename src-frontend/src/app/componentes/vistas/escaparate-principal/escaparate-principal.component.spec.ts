import { TestBed } from '@angular/core/testing';
import { Navigation, Router, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { EscaparatePrincipalComponent } from './escaparate-principal.component';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';

describe('EscaparatePrincipalComponent con sesión de gestión', () => {
  let router: Router;
  let navegar: jasmine.Spy;
  let roles$: Subject<string[]>;

  async function montar(state?: Record<string, unknown>): Promise<void> {
    sessionStorage.removeItem('escaparate-auth-autoredirect-done');
    roles$ = new Subject<string[]>();
    await TestBed.configureTestingModule({
      imports: [EscaparatePrincipalComponent],
      providers: [
        provideRouter([{ path: '**', component: EscaparatePrincipalComponent }]),
        {
          provide: AuthenticationService,
          useValue: {
            comprobarLogueado: () => true,
            usuarioLogueadoCambio: new Subject<boolean>(),
            rolesCambio: roles$,
            getRolesActuales: () => ['ROLE_ADMIN'],
            resolverRutaInicioPorRoles: () => '/adminpage',
          },
        },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    await router.navigateByUrl('/');
    // Durante la activación de la ruta el router expone el estado de la navegación en curso.
    spyOn(router, 'getCurrentNavigation').and.returnValue(state ? ({ extras: { state } } as unknown as Navigation) : null);
    navegar = spyOn(router, 'navigate').and.resolveTo(true);
    const f = TestBed.createComponent(EscaparatePrincipalComponent);
    f.detectChanges();
    roles$.next(['ROLE_ADMIN']);
  }

  afterEach(() => sessionStorage.removeItem('escaparate-auth-autoredirect-done'));

  it('con la marca verWeb no redirige aunque haya sesión de gestión', async () => {
    await montar({ verWeb: true });
    expect(navegar).not.toHaveBeenCalled();
  });

  it('sin la marca redirige como hoy', async () => {
    await montar();
    expect(navegar).toHaveBeenCalledWith(['/adminpage']);
  });
});
