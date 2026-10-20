package com.taemoi.project.config;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Roles;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Decisiones 180 y 184 (pieza 6b-3b-2, matizan la D5-1): una sesión solo-profesor
 * ({@link RolMembresia#sesionSoloProfesor}: PROFESOR sin FAMILIA ni gestión, también con RESPONSABLE_SEDE) solo usa su
 * caja hasta la E9; su autoridad de responsable, si la tiene, no abre nada más. Envuelve el {@link AuthorizationManager} de las reglas de URL de
 * {@code SecurityConfiguration} y, para esa sesión, deja decidir a las reglas de siempre SOLO en:
 * <ul>
 * <li>{@link #LISTA_BLANCA}: su caja ({@code /api/caja/profesor/**}), sus rutas de la entrega 7
 * ({@code /api/profesor/**}), lo imprescindible de sesión (roles, quién es,
 * si es de plataforma, selector y cambio de escuela) y {@code /api/plataforma/**}, que además exige ROLE_ADMIN_PLATAFORMA (un administrador
 * de plataforma que también es profesor no la pierde);</li>
 * <li>lo público: lo que esas mismas reglas conceden a un anónimo.</li>
 * </ul>
 * Todo lo demás, denegado en la URL, aunque la regla fuese {@code authenticated()} o la ruta no tuviese
 * {@code @PreAuthorize}: una ruta nueva nace cerrada para esa sesión. Las demás sesiones (y el anónimo) no cambian:
 * la regla delega sin más. La lista blanca no concede por sí misma; {@code HEAD} cuenta como {@code GET}.
 * {@code MatrizPermisosTest} lo comprueba sobre la cadena real.
 */
final class SesionSoloProfesorAuthorizationManager<T> implements AuthorizationManager<T> {

	/** Lo que el profesor puede pedir con su sesión; lo decide después la regla de siempre de cada ruta. */
	static final List<RequestMatcher> LISTA_BLANCA = listaBlanca();

	private static final AnonymousAuthenticationToken ANONIMO = new AnonymousAuthenticationToken(
			"sesion-solo-profesor", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
	private static final AuthorizationDecision DENEGADA = new AuthorizationDecision(false);

	private final AuthorizationManager<T> delegado;

	private SesionSoloProfesorAuthorizationManager(AuthorizationManager<T> delegado) {
		this.delegado = delegado;
	}

	static <T> AuthorizationManager<T> envolver(AuthorizationManager<T> delegado) {
		return delegado instanceof SesionSoloProfesorAuthorizationManager ? delegado
				: new SesionSoloProfesorAuthorizationManager<>(delegado);
	}

	/**
	 * Post-procesador para {@code authorizeHttpRequests(...).withObjectPostProcessor(...)}, como
	 * {@link HeadComoGetAuthorizationManager#postProcesador()}: envuelve el {@code AuthorizationManager} de las reglas
	 * de URL y deja pasar sin tocar cualquier otro objeto.
	 */
	static ObjectPostProcessor<Object> postProcesador() {
		return new ObjectPostProcessor<>() {
			@Override
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public <O> O postProcess(O objeto) {
				if (objeto instanceof AuthorizationManager manager) {
					return (O) envolver(manager);
				}
				return objeto;
			}
		};
	}

	@Override
	@Deprecated
	@SuppressWarnings("deprecation")
	public AuthorizationDecision check(Supplier<Authentication> autenticacion, T objeto) {
		AuthorizationResult resultado = authorize(autenticacion, objeto);
		if (resultado == null || resultado instanceof AuthorizationDecision) {
			return (AuthorizationDecision) resultado;
		}
		return new AuthorizationDecision(resultado.isGranted());
	}

	@Override
	public AuthorizationResult authorize(Supplier<Authentication> autenticacion, T objeto) {
		if (!(objeto instanceof HttpServletRequest peticion)) {
			return delegado.authorize(autenticacion, objeto);
		}
		Authentication sesion = autenticacion.get();
		if (!esSesionSoloProfesor(sesion)) {
			return delegado.authorize(() -> sesion, objeto);
		}
		if (enListaBlanca(peticion)) {
			return delegado.authorize(() -> sesion, objeto);
		}
		// Lo público: lo que las reglas de siempre conceden a un anónimo (un resultado nulo no cuenta).
		AuthorizationResult publico = delegado.authorize(() -> ANONIMO, objeto);
		return publico != null && publico.isGranted() ? publico : DENEGADA;
	}

	static boolean esSesionSoloProfesor(Authentication sesion) {
		if (sesion == null || !sesion.isAuthenticated() || sesion instanceof AnonymousAuthenticationToken) {
			return false;
		}
		Set<Roles> roles = EnumSet.noneOf(Roles.class);
		for (GrantedAuthority autoridad : sesion.getAuthorities()) {
			for (Roles rol : Roles.values()) {
				if (rol.name().equals(autoridad.getAuthority())) {
					roles.add(rol);
				}
			}
		}
		return RolMembresia.sesionSoloProfesor(roles);
	}

	private static boolean enListaBlanca(HttpServletRequest peticion) {
		HttpServletRequest comoGet = HeadComoGetAuthorizationManager.comoGet(peticion);
		for (RequestMatcher matcher : LISTA_BLANCA) {
			if (matcher.matches(comoGet)) {
				return true;
			}
		}
		return false;
	}

	private static List<RequestMatcher> listaBlanca() {
		PathPatternRequestMatcher.Builder rutas = PathPatternRequestMatcher.withDefaults();
		return List.of(
				// Su caja: cargos por cobrar, vender, lo que lleva encima, deshacer (y lo que se añada bajo ella).
				rutas.matcher("/api/caja/profesor/**"),
				// Entrega 7 (D7-9): sus rutas propias (grupos, alumnos y ficha reducida; filtro por sus grupos).
				rutas.matcher("/api/profesor/**"),
				// Sesión: quién es y sus roles (el frontend redirige con ellos), selector y cambio de escuela.
				rutas.matcher(HttpMethod.GET, "/api/auth/roles"),
				rutas.matcher(HttpMethod.GET, "/api/auth/user"),
				rutas.matcher(HttpMethod.GET, "/api/auth/organizaciones"),
				rutas.matcher(HttpMethod.POST, "/api/auth/organizacion"),
				// Si además es administrador de plataforma: el frontend lo pregunta aquí en vez de sondear
				// /api/plataforma/errores. Solo GET (HEAD cuenta como GET); solo lee la autoridad de la sesión.
				rutas.matcher(HttpMethod.GET, "/api/auth/plataforma"),
				// Administrador de plataforma que además es profesor: su regla exige ROLE_ADMIN_PLATAFORMA.
				rutas.matcher("/api/plataforma/**"));
	}
}
