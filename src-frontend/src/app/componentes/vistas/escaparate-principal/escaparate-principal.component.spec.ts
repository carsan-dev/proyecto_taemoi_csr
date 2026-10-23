import { TestBed } from '@angular/core/testing';
import { Navigation, Router, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import {
  EscaparatePrincipalComponent,
  reiniciarRedireccionEscaparate,
} from './escaparate-principal.component';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';

describe('EscaparatePrincipalComponent con sesión de gestión', () => {
  let router: Router;
  let navegar: jasmine.Spy;
  let roles$: Subject<string[]>;
  let navegacionActual: Navigation | null;

  interface Opciones {
    /** Estado de la navegación en curso (lo que manda «Ver la web»). */
    state?: Record<string, unknown>;
    /** Tipo de navegación del documento (`performance.getEntriesByType('navigation')`). */
    tipo?: string;
    /** Estado persistido en `history.state`. */
    historia?: Record<string, unknown> | null;
    logueado?: boolean;
    ruta?: string;
  }

  async function configurar(o: Opciones = {}): Promise<void> {
    roles$ = new Subject<string[]>();
    await TestBed.configureTestingModule({
      imports: [EscaparatePrincipalComponent],
      providers: [
        provideRouter([{ path: '**', component: EscaparatePrincipalComponent }]),
        {
          provide: AuthenticationService,
          useValue: {
            comprobarLogueado: () => o.logueado ?? true,
            usuarioLogueadoCambio: new Subject<boolean>(),
            rolesCambio: roles$,
            getRolesActuales: () => ['ROLE_ADMIN'],
            resolverRutaInicioPorRoles: () => o.ruta ?? '/adminpage',
          },
        },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    await router.navigateByUrl('/');
    navegacionActual = null;
    spyOn(router, 'getCurrentNavigation').and.callFake(() => navegacionActual);
    navegar = spyOn(router, 'navigate').and.resolveTo(true);
    spyOn(performance, 'getEntriesByType').and.returnValue([
      { type: o.tipo ?? 'navigate' } as PerformanceNavigationTiming,
    ]);
    spyOnProperty(history, 'state', 'get').and.returnValue(o.historia ?? null);
  }

  /** Crea la portada como si el router la activara con la navegación indicada. */
  function crearPortada(state?: Record<string, unknown>): void {
    navegacionActual = state ? ({ extras: { state } } as unknown as Navigation) : null;
    const f = TestBed.createComponent(EscaparatePrincipalComponent);
    f.detectChanges();
    roles$.next(['ROLE_ADMIN']);
  }

  async function montar(o: Opciones = {}): Promise<void> {
    await configurar(o);
    crearPortada(o.state);
  }

  beforeEach(() => {
    reiniciarRedireccionEscaparate();
    sessionStorage.clear();
  });
  afterEach(() => reiniciarRedireccionEscaparate());

  it('al cargar con sesión de gestión va a su panel', async () => {
    await montar();
    expect(navegar).toHaveBeenCalledWith(['/adminpage']);
  });

  it('con verWeb en la navegación en curso no redirige', async () => {
    await montar({ state: { verWeb: true } });
    expect(navegar).not.toHaveBeenCalled();
  });

  it('al recargar (F5) estando en «Ver la web» se queda en la portada', async () => {
    await montar({ tipo: 'reload', historia: { verWeb: true } });
    expect(navegar).not.toHaveBeenCalled();
  });

  it('al recargar sin verWeb en el historial redirige', async () => {
    await montar({ tipo: 'reload', historia: null });
    expect(navegar).toHaveBeenCalledWith(['/adminpage']);
  });

  it('con pestaña restaurada o URL escrita (navigate) y verWeb en el historial redirige', async () => {
    await montar({ tipo: 'navigate', historia: { verWeb: true } });
    expect(navegar).toHaveBeenCalledWith(['/adminpage']);
  });

  it('con back_forward y verWeb en el historial redirige', async () => {
    await montar({ tipo: 'back_forward', historia: { verWeb: true } });
    expect(navegar).toHaveBeenCalledWith(['/adminpage']);
  });

  it('no redirige dos veces en la misma carga', async () => {
    await montar();
    expect(navegar).toHaveBeenCalledTimes(1);
    crearPortada();
    expect(navegar).toHaveBeenCalledTimes(1);
  });

  it('un «Ver la web» tras redirigir se queda en la portada', async () => {
    await montar();
    expect(navegar).toHaveBeenCalledTimes(1);
    crearPortada({ verWeb: true });
    expect(navegar).toHaveBeenCalledTimes(1);
  });

  it('sin sesión no redirige', async () => {
    await montar({ logueado: false });
    expect(navegar).not.toHaveBeenCalled();
  });

  it('si la ruta de inicio es «/» no redirige', async () => {
    await montar({ ruta: '/' });
    expect(navegar).not.toHaveBeenCalled();
  });

  it('no usa sessionStorage', async () => {
    const leer = spyOn(Storage.prototype, 'getItem').and.callThrough();
    const escribir = spyOn(Storage.prototype, 'setItem').and.callThrough();
    await montar();
    expect(leer).not.toHaveBeenCalled();
    expect(escribir).not.toHaveBeenCalled();
  });
});
