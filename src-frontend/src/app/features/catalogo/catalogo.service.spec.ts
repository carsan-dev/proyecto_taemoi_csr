import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { Catalogo } from './catalogo.models';
import { catalogoResolver } from './catalogo.resolver';
import { CatalogoService } from './catalogo.service';
import { CATALOGO_PUBLICO_TAEMOI, CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from './testing/catalogo.fixtures';

const URL = `${environment.apiUrl}/catalogo`;

describe('CatalogoService', () => {
  let http: HttpTestingController;
  let servicio: CatalogoService;
  let logueado: BehaviorSubject<boolean>;
  let email: BehaviorSubject<string | null>;

  function crear(sesion?: { logueado: boolean; email: string | null }): void {
    logueado = new BehaviorSubject<boolean>(sesion?.logueado ?? false);
    email = new BehaviorSubject<string | null>(sesion?.email ?? null);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthenticationService,
          useValue: { usuarioLogueadoCambio: logueado.asObservable(), emailCambio: email.asObservable() },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(CatalogoService);
  }

  afterEach(() => http.verify());

  it('sin sesión no pide nada y las lecturas están vacías', () => {
    crear();
    http.expectNone(URL);
    expect(servicio.estado().estado).toBe('vacio');
    expect(servicio.catalogo()).toBeNull();
    expect(servicio.disciplinasActivas()).toEqual([]);
    expect(servicio.esLegadoTaemoi()).toBeFalse();
  });

  describe('carga', () => {
    beforeEach(() => crear());

    it('cargar() pide /catalogo con credenciales y deja el estado en listo', () => {
      let recibido: Catalogo | undefined;
      servicio.cargar().subscribe((c) => (recibido = c));
      expect(servicio.estado().estado).toBe('cargando');

      const req = http.expectOne(URL);
      expect(req.request.method).toBe('GET');
      expect(req.request.withCredentials).toBeTrue();
      expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      req.flush(CATALOGO_TAEMOI);

      expect(recibido).toEqual(CATALOGO_TAEMOI);
      expect(servicio.estado().estado).toBe('listo');
      expect(servicio.cargado()).toBeTrue();
      expect(servicio.esLegadoTaemoi()).toBeTrue();
      expect(servicio.disciplinasActivas().map((d) => d.codigo)).toEqual([
        'TAEKWONDO', 'KICKBOXING', 'PILATES', 'DEFENSA_PERSONAL_FEMENINA',
      ]);
    });

    it('las llamadas concurrentes comparten una sola petición', () => {
      const resultados: Catalogo[] = [];
      servicio.cargar().subscribe((c) => resultados.push(c));
      servicio.cargar().subscribe((c) => resultados.push(c));
      servicio.cargar().subscribe((c) => resultados.push(c));

      http.expectOne(URL).flush(CATALOGO_TAEMOI);
      expect(resultados.length).toBe(3);
    });

    it('una vez cargado no vuelve a pedir', () => {
      servicio.cargar().subscribe();
      http.expectOne(URL).flush(CATALOGO_TAEMOI);

      let segundo: Catalogo | undefined;
      servicio.cargar().subscribe((c) => (segundo = c));
      http.expectNone(URL);
      expect(segundo).toEqual(CATALOGO_TAEMOI);
    });

    it('lecturas síncronas del catálogo cargado', () => {
      servicio.cargar().subscribe();
      http.expectOne(URL).flush(CATALOGO_TAEMOI);

      expect(servicio.etiquetaDisciplina('DEFENSA_PERSONAL_FEMENINA')).toBe('Defensa Personal Femenina');
      expect(servicio.etiquetaDisciplina('ANTIGUA')).toBe('ANTIGUA');
      expect(servicio.gradosSeleccionables('KICKBOXING').length).toBe(11);
      expect(servicio.grado('KICKBOXING', 'ROJO')?.nombre).toBe('MARRÓN');
      expect(servicio.grados('PILATES')).toEqual([]);
      expect(servicio.tarifasDe('KICKBOXING').map((t) => t.codigo)).toEqual(['KICKBOXING']);
      expect(servicio.tarifa('HERMANOS')?.requiereGrupoFamiliar).toBeTrue();
      expect(servicio.categoriasDe('TAEKWONDO').length).toBe(5);
      expect(servicio.categoriasDe('KICKBOXING')).toEqual([]);
    });
  });

  describe('invalidación por sesión', () => {
    it('al iniciar sesión precarga una sola vez (esperando a que llegue el correo)', () => {
      crear();
      logueado.next(true); // el correo aún no ha llegado
      http.expectNone(URL);
      email.next('a@escuela.test');
      http.expectOne(URL).flush(CATALOGO_TAEMOI);
      expect(servicio.catalogo()).toEqual(CATALOGO_TAEMOI);

      logueado.next(true); // el login vuelve a emitir true
      http.expectNone(URL);
      expect(servicio.cargado()).toBeTrue();
    });

    it('si el servicio nace con la sesión ya abierta, precarga', () => {
      crear({ logueado: true, email: 'a@escuela.test' });
      http.expectOne(URL).flush(CATALOGO_TAEMOI);
      expect(servicio.cargado()).toBeTrue();
    });

    it('al cerrar sesión se vacía (no queda catálogo del usuario anterior)', () => {
      crear({ logueado: true, email: 'a@escuela.test' });
      http.expectOne(URL).flush(CATALOGO_TAEMOI);

      logueado.next(false);
      email.next(null);

      http.expectNone(URL);
      expect(servicio.estado().estado).toBe('vacio');
      expect(servicio.catalogo()).toBeNull();
      expect(servicio.disciplinasActivas()).toEqual([]);
      expect(servicio.esLegadoTaemoi()).toBeFalse();
    });

    it('otro usuario / otra escuela nunca ve el catálogo anterior', () => {
      crear({ logueado: true, email: 'a@taemoi.test' });
      http.expectOne(URL).flush(CATALOGO_TAEMOI);

      email.next('b@otra-escuela.test');
      expect(servicio.catalogo()).toBeNull(); // vacío hasta que llegue el nuevo
      http.expectOne(URL).flush(CATALOGO_SEGUNDA_ESCUELA);

      expect(servicio.catalogo()).toEqual(CATALOGO_SEGUNDA_ESCUELA);
      expect(servicio.esLegadoTaemoi()).toBeFalse();
      expect(servicio.disciplinasActivas().map((d) => d.codigo)).toEqual(['JUDO']);
    });

    it('una respuesta en vuelo de la sesión anterior se descarta', () => {
      crear({ logueado: true, email: 'a@taemoi.test' });
      const enVueloA = http.expectOne(URL); // petición de A sin responder

      email.next('b@otra-escuela.test');
      const peticionB = http.expectOne(URL);

      enVueloA.flush(CATALOGO_TAEMOI); // llega tarde
      expect(servicio.catalogo()).toBeNull();
      expect(servicio.estado().estado).toBe('cargando');

      peticionB.flush(CATALOGO_SEGUNDA_ESCUELA);
      expect(servicio.catalogo()).toEqual(CATALOGO_SEGUNDA_ESCUELA);
    });

    it('invalidar() y recargar() manuales (futuro cambio de escuela)', () => {
      crear({ logueado: true, email: 'a@taemoi.test' });
      http.expectOne(URL).flush(CATALOGO_TAEMOI);

      servicio.invalidar();
      expect(servicio.estado().estado).toBe('vacio');

      servicio.recargar().subscribe();
      http.expectOne(URL).flush(CATALOGO_SEGUNDA_ESCUELA);
      expect(servicio.catalogo()).toEqual(CATALOGO_SEGUNDA_ESCUELA);
    });
  });

  describe('error (D15)', () => {
    it('pasa a error con listas vacías, sin constantes de Taemoi, y propaga el error', () => {
      crear();
      let fallo: unknown;
      servicio.cargar().subscribe({ error: (e) => (fallo = e) });
      http.expectOne(URL).flush('boom', { status: 500, statusText: 'Server Error' });

      expect(fallo).toBeTruthy();
      expect(servicio.estado().estado).toBe('error');
      expect(servicio.hayError()).toBeTrue();
      expect(servicio.error()).toBeTruthy();
      expect(servicio.catalogo()).toBeNull();
      expect(servicio.disciplinas()).toEqual([]);
      expect(servicio.disciplinasActivas()).toEqual([]);
      expect(servicio.tarifasDe('TAEKWONDO')).toEqual([]);
      expect(servicio.gradosSeleccionables('TAEKWONDO')).toEqual([]);
      expect(servicio.categoriasDe('TAEKWONDO')).toEqual([]);
      expect(servicio.esLegadoTaemoi()).toBeFalse();
    });

    it('tras un error, cargar() vuelve a pedir y se recupera', () => {
      crear();
      servicio.cargar().subscribe({ error: () => undefined });
      http.expectOne(URL).flush('boom', { status: 503, statusText: 'x' });

      servicio.cargar().subscribe();
      http.expectOne(URL).flush(CATALOGO_TAEMOI);
      expect(servicio.estado().estado).toBe('listo');
      expect(servicio.hayError()).toBeFalse();
    });

    it('un fallo de la precarga no rompe nada y deja el estado en error', () => {
      crear({ logueado: true, email: 'a@taemoi.test' });
      http.expectOne(URL).flush('boom', { status: 500, statusText: 'x' });
      expect(servicio.hayError()).toBeTrue();
    });
  });

  describe('catálogo público', () => {
    it('GET /catalogo/publico sin tocar el estado del catálogo autenticado', () => {
      crear();
      let recibido: unknown;
      servicio.cargarPublico().subscribe((c) => (recibido = c));
      const req = http.expectOne(`${URL}/publico`);
      expect(req.request.method).toBe('GET');
      req.flush(CATALOGO_PUBLICO_TAEMOI);

      expect(recibido).toEqual(CATALOGO_PUBLICO_TAEMOI);
      expect(servicio.estado().estado).toBe('vacio');
    });

    it('un error del público se propaga al llamador', () => {
      crear();
      let fallo: unknown;
      servicio.cargarPublico().subscribe({ error: (e) => (fallo = e) });
      http.expectOne(`${URL}/publico`).flush('x', { status: 500, statusText: 'x' });
      expect(fallo).toBeTruthy();
      expect(servicio.hayError()).toBeFalse();
    });
  });

  describe('catalogoResolver', () => {
    function resolver(): Observable<boolean> {
      return TestBed.runInInjectionContext(() =>
        catalogoResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
      ) as Observable<boolean>;
    }

    beforeEach(() => crear());

    it('espera a la carga y devuelve true', () => {
      let resultado: boolean | undefined;
      resolver().subscribe((r) => (resultado = r));
      expect(resultado).toBeUndefined();
      http.expectOne(URL).flush(CATALOGO_TAEMOI);
      expect(resultado).toBeTrue();
    });

    it('ya cargado: resuelve sin pedir', () => {
      servicio.cargar().subscribe();
      http.expectOne(URL).flush(CATALOGO_TAEMOI);
      let resultado: boolean | undefined;
      resolver().subscribe((r) => (resultado = r));
      http.expectNone(URL);
      expect(resultado).toBeTrue();
    });

    it('reintenta una vez y, si vuelve a fallar, no bloquea la ruta: false con estado de error', () => {
      let resultado: boolean | undefined;
      resolver().subscribe((r) => (resultado = r));
      http.expectOne(URL).flush('x', { status: 500, statusText: 'x' });
      expect(resultado).toBeUndefined();
      http.expectOne(URL).flush('x', { status: 500, statusText: 'x' });
      expect(resultado).toBeFalse();
      expect(servicio.hayError()).toBeTrue();
    });

    it('si el reintento acierta, true', () => {
      let resultado: boolean | undefined;
      resolver().subscribe((r) => (resultado = r));
      http.expectOne(URL).flush('x', { status: 500, statusText: 'x' });
      http.expectOne(URL).flush(CATALOGO_TAEMOI);
      expect(resultado).toBeTrue();
      expect(servicio.cargado()).toBeTrue();
    });
  });
});
