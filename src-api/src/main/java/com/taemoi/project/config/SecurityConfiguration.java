package com.taemoi.project.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.taemoi.project.entities.Roles;
import com.taemoi.project.plataforma.AutoridadPlataforma;
import com.taemoi.project.services.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Configuración de seguridad para la aplicación.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

	/*
	 * Entregas 6-1 a 6-3 (docs/multi-escuela/entrega6-plan.md §3.1 a §3.4): rutas abiertas al responsable de sede.
	 * Reglas CONCRETAS antes de las amplias (/api/alumnos/**, /api/grupos/**, /api/turnos/**, /api/informes/**,
	 * /api/preinscripciones/**, /api/convocatorias/**, /api/tesoreria/**, /api/productos/**,
	 * /api/productos-alumno/**), que siguen
	 * siendo solo de gestión: así una ruta sin @PreAuthorize bajo esos patrones no queda abierta. Cada ruta lleva
	 * además su @PreAuthorize con @acceso (por id) o un filtro del servicio (listados, ids en el cuerpo); la matriz
	 * de permisos lo comprueba (MatrizPermisosTest).
	 */
	private static final String[] RESPONSABLE_SEDE_GET = {
			"/api/alumnos", "/api/alumnos/ids", "/api/alumnos/count", "/api/alumnos/con-deportes",
			"/api/alumnos/deportes/distribucion", "/api/alumnos/aptos", "/api/alumnos/aptos/deporte",
			"/api/alumnos/aptos/convocatoria", "/api/alumnos/aptos/{id}", "/api/alumnos/{id}",
			"/api/alumnos/{id}/completo", "/api/alumnos/{alumnoId}/salud", "/api/alumnos/{alumnoId}/tutores",
			"/api/alumnos/{alumnoId}/consentimientos",
			"/api/grupos", "/api/grupos/{id}",
			"/api/turnos", "/api/turnos/{turnoId}",
			// 6-2: sin plantillas ni rutas públicas (sus reglas van antes).
			"/api/preinscripciones", "/api/preinscripciones/{referencia}",
			"/api/preinscripciones/{referencia}/documento-firmado", "/api/preinscripciones/{referencia}/firma",
			// 6-2 (D6-8): la lista y el detalle son comunes; los alumnos, el reporte y el PDF, filtrados en el servicio.
			"/api/convocatorias", "/api/convocatorias/{id}", "/api/convocatorias/{id}/alumnos",
			"/api/convocatorias/{id}/reporte", "/api/convocatorias/{id}/informe-pdf",
			// 6-3 (D4, D6-11 a D6-13): tesorería de sus sedes (filtrada en el servicio), cargos de sus alumnos,
			// catálogo de productos en lectura e informes de deudas y del listado mensual.
			"/api/tesoreria/resumen", "/api/tesoreria/movimientos", "/api/tesoreria/anios-disponibles",
			"/api/tesoreria/export/csv", "/api/tesoreria/export/pdf",
			"/api/productos-alumno/alumno/{alumnoId}",
			"/api/productos", "/api/productos/todos", "/api/productos/{id}",
			"/api/informes/deudas", "/api/informes/deudas/csv", "/api/informes/listado-mensualidad-mensual" };
	private static final String[] RESPONSABLE_SEDE_POST = {
			"/api/alumnos/crear", "/api/alumnos/{alumnoId}/documentos", "/api/alumnos/{alumnoId}/grupos/{grupoId}",
			"/api/alumnos/{alumnoId}/turnos/{turnoId}", "/api/alumnos/{id}/deportes",
			"/api/alumnos/{id}/deportes/{deporte}/pase-examen", "/api/alumnos/{id}/deportes/{deporte}/pase-recompensa",
			"/api/alumnos/{alumnoId}/tutores", "/api/alumnos/{alumnoId}/consentimientos",
			"/api/grupos", "/api/grupos/{grupoId}/alumnos", "/api/grupos/{grupoId}/alumnos/{alumnoId}",
			"/api/grupos/{grupoId}/turnos/{turnoId}",
			"/api/turnos/crear-asignando-grupo",
			// 6-2 (D6-8): apuntar a sus alumnos a una convocatoria.
			"/api/alumnos/{convocatoriaId}/alumno/{alumnoId}",
			"/api/preinscripciones/{referencia}/cancelar", "/api/preinscripciones/{referencia}/finalizar",
			"/api/preinscripciones/{referencia}/reenviar", "/api/preinscripciones/{referencia}/reenviar-cambio-turnos",
			"/api/preinscripciones/{referencia}/reenviar-finalizacion",
			// 6-3: cargos sueltos de sus alumnos (D6-3) y certificados; sin las generaciones masivas (D6-10).
			"/api/tesoreria/certificados-cobros",
			"/api/productos-alumno/alumno/{alumnoId}/producto/{productoId}",
			"/api/productos-alumno/alumno/{alumnoId}/producto/{productoId}/deporte/{deporte}",
			"/api/productos-alumno/{alumnoId}/reservar-plaza", "/api/productos-alumno/{alumnoId}/reservar-plaza-deporte",
			"/api/productos-alumno/{alumnoId}/renovar-licencia", "/api/productos-alumno/mensualidades/individual",
			"/api/productos-alumno/mensualidades/individual-deporte", "/api/productos-alumno/licencias/individual" };
	/** tipo-tarifa y cuantia-tarifa desde la entrega 6-3 (tesorería, D6-3: solo su sede principal). */
	private static final String[] RESPONSABLE_SEDE_PUT = {
			"/api/alumnos/{id}", "/api/alumnos/{id}/observaciones", "/api/alumnos/{id}/fecha-alta-inicial",
			"/api/alumnos/{id}/baja", "/api/alumnos/{id}/alta", "/api/alumnos/{alumnoId}/salud",
			"/api/alumnos/{alumnoId}/tutores/{tutorId}",
			"/api/alumnos/{id}/deportes/{deporte}/desactivar", "/api/alumnos/{id}/deportes/{deporte}/activar",
			"/api/alumnos/{id}/deportes/{deporte}/principal", "/api/alumnos/{id}/deportes/{deporte}/grado",
			"/api/alumnos/{id}/deportes/{deporte}/apto-examen", "/api/alumnos/{id}/deportes/{deporte}/fecha-grado",
			"/api/alumnos/{id}/deportes/{deporte}/fecha-alta", "/api/alumnos/{id}/deportes/{deporte}/fecha-baja",
			"/api/alumnos/{id}/deportes/{deporte}/fecha-alta-inicial",
			"/api/alumnos/{id}/deportes/{deporte}/rol-familiar", "/api/alumnos/{id}/deportes/{deporte}/grupo-familiar",
			"/api/alumnos/{id}/deportes/{deporte}/tiene-licencia",
			"/api/alumnos/{id}/deportes/{deporte}/numero-licencia",
			"/api/alumnos/{id}/deportes/{deporte}/fecha-licencia", "/api/alumnos/{id}/deportes/{deporte}/competidor",
			"/api/alumnos/{id}/deportes/{deporte}/peso", "/api/alumnos/{id}/deportes/{deporte}/fecha-peso",
			"/api/alumnos/{id}/deportes/{deporte}/categoria",
			"/api/alumnos/{id}/deportes/{deporte}/fecha-alta-competicion",
			"/api/alumnos/{id}/deportes/{deporte}/fecha-alta-competidor-inicial",
			"/api/alumnos/{id}/deportes/{deporte}/datos-competidor",
			"/api/grupos/{id}",
			"/api/turnos/{turnoId}",
			"/api/preinscripciones/{referencia}/turnos",
			// 6-2 (D6-8): cuantía y pago del examen de sus alumnos.
			"/api/convocatorias/alumno/{alumnoConvocatoriaId}",
			// 6-3: tarifa y cuantía de sus alumnos; cobrar los cargos de sus sedes.
			"/api/alumnos/{id}/deportes/{deporte}/tipo-tarifa", "/api/alumnos/{id}/deportes/{deporte}/cuantia-tarifa",
			"/api/productos-alumno/{id}" };
	/** Sin DELETE /api/alumnos/{id}: el borrado definitivo es solo de gestión (D6-4). */
	private static final String[] RESPONSABLE_SEDE_DELETE = {
			"/api/alumnos/{id}/imagen", "/api/alumnos/{alumnoId}/documentos/{documentoId}",
			"/api/alumnos/{alumnoId}/grupos/{grupoId}", "/api/alumnos/{alumnoId}/turnos/{turnoId}",
			"/api/alumnos/{id}/deportes/{deporte}", "/api/alumnos/{alumnoId}/tutores/{tutorId}",
			"/api/grupos/{id}", "/api/grupos/{grupoId}/alumnos/{alumnoId}", "/api/grupos/{grupoId}/turnos/{turnoId}",
			"/api/turnos/{turnoId}",
			// 6-2 (D6-8): quitar a sus alumnos de una convocatoria. Sin DELETE /api/preinscripciones/{referencia}.
			"/api/alumnos/{convocatoriaId}/alumno/{alumnoId}",
			// 6-3: borrar un cargo de sus sedes que no esté cobrado (el servicio deniega los cobrados).
			"/api/productos-alumno/{id}" };

	/**
	 * Inyección del filtro de JWT.
	 */
	@Autowired
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@Autowired
	private AuditoriaRequestFilter auditoriaRequestFilter;

	/**
	 * Inyección del servicio de usuario.
	 */
	@Autowired
	private UsuarioService usuarioService;

	/**
	 * Inyección del manejador de éxito de OAuth2.
	 */
	@Autowired
	private OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;

	/**
	 * Inyección del codificador de contraseñas.
	 */
	@Autowired
	private PasswordEncoder passwordEncoder;

	/**
	 * Configures which paths should be completely ignored by Spring Security.
	 * No paths are completely ignored anymore - all go through SecurityFilterChain.
	 * This allows granular control over alumno images vs public images.
	 */
	@Bean
	org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer webSecurityCustomizer() {
		logger.info("========================================");
		logger.info("WebSecurityCustomizer: No paths ignored");
		logger.info("All image and document access controlled by SecurityFilterChain");
		logger.info("========================================");
		return (web) -> {};
	}

	/**
	 * Configuración del filtro de seguridad para las solicitudes HTTP.
	 *
	 * @param http El objeto HttpSecurity que se configura.
	 * @return Un objeto SecurityFilterChain configurado.
	 * @throws Exception Si hay un error durante la configuración.
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		logger.info("========================================");
		logger.info("Configuring SecurityFilterChain");
		logger.info("CSRF ignoring: /api/**, /login/oauth2/**, /oauth2/**");
		logger.info("Protected resources: /imagenes/alumnos/**, /imagenes/org-*/alumnos/**, /documentos/** (ADMIN/MANAGER of the active school)");
		logger.info("Public images: /imagenes/** (except alumnos subdirectory)");
		logger.info("========================================");

		http.cors(cors -> {});
		http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.ignoringRequestMatchers("/api/**", "/login/oauth2/**", "/oauth2/**", "/imagenes/**"))
				.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
				.authorizeHttpRequests(request -> request
						// HEAD recibe la misma decisión que GET a la misma ruta (MVC lo atiende con el handler de GET).
						.withObjectPostProcessor(HeadComoGetAuthorizationManager.postProcesador())
						// OAuth2 endpoints
						.requestMatchers("/login/oauth2/**").permitAll()
						.requestMatchers("/oauth2/**").permitAll()
						// Static resources that browsers request automatically
						.requestMatchers("/favicon.ico", "/error").permitAll()
						// Healthcheck de Docker/despliegue: solo GET (y HEAD, que se autoriza como GET) y solo esta
						// ruta; fuera de /api, no se audita.
						.requestMatchers(HttpMethod.GET, "/healthz").permitAll()
						// Protected images - alumno photos require ADMIN or MANAGER role (heredadas y por escuela).
						// ArchivoEstaticoController/AccesoArchivos además exige la escuela activa y la fila Imagen.
						.requestMatchers("/imagenes/alumnos/**", "/imagenes/org-*/alumnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						// Public images - eventos, etc. (must come after specific rules)
						.requestMatchers("/imagenes/**").permitAll()
						// Public event documents (heredados y por escuela)
						.requestMatchers(HttpMethod.GET, "/documentos/Documentos_Eventos/**", "/documentos/org-*/eventos/**")
						.permitAll()
						// Document access - requires ADMIN or MANAGER role
						.requestMatchers("/documentos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						// API endpoints
						.requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
						// Catálogo: el público (disciplinas activas) para cualquiera; el completo, con sesión de la
						// escuela (las tarifas solo para gestión, en CatalogoController). Toda sesión de escuela tiene
						// alguno de estos tres roles; la de un administrador de plataforma sin membresía, no.
						.requestMatchers(HttpMethod.GET, "/api/catalogo/publico").permitAll()
						.requestMatchers("/api/catalogo", "/api/catalogo/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString(), Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.POST, "/api/preinscripciones").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/preinscripciones/public/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/preinscripciones/*/documento").permitAll()
						.requestMatchers("/api/preinscripciones/plantillas/**").hasAnyAuthority(Roles.ROLE_ADMIN.toString())
						// Entrega 6-2: preinscripciones abiertas al responsable de sede (constantes RESPONSABLE_SEDE_*).
						.requestMatchers(HttpMethod.GET, preinscripciones(RESPONSABLE_SEDE_GET))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.POST, preinscripciones(RESPONSABLE_SEDE_POST))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.PUT, preinscripciones(RESPONSABLE_SEDE_PUT))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers("/api/preinscripciones/**").hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(
								"/api/auth/signin",
								"/api/auth/signup",
								"/api/auth/password/forgot",
								"/api/auth/password/reset",
								"/api/auth/register/request",
								"/api/auth/register/confirm",
								"/api/auth/logout",
								"/api/auth/auth-status",
								"/api/auth/oauth2/google-login-url")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/grupos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString(), Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/deportes")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString(), Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/turnos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString(), Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/documentos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString(), Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/documentos/{documentoId}/descargar")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString(), Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/deportes/{deporte}/material-examen")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET,
								"/api/alumnos/{alumnoId}/deportes/{deporte}/material-examen/temario")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET,
								"/api/alumnos/{alumnoId}/deportes/{deporte}/material-examen/videos/{videoFile}")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET,
								"/api/alumnos/{alumnoId}/deportes/{deporte}/material-examen/documentacion/{documentoFile}")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/imagen")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString(), Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/reto-diario")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/reto-diario/ranking-semanal")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/{alumnoId}/reto-diario/ranking-general")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/alumnos/{alumnoId}/reto-diario/completar")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString())
						.requestMatchers(HttpMethod.POST, "/api/alumnos/aptos/{id}/actualizar-grado")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						// Entrega 6-1: alumnos abiertos al responsable de sede (constantes RESPONSABLE_SEDE_*).
						.requestMatchers(HttpMethod.GET, alumnos(RESPONSABLE_SEDE_GET))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.POST, alumnos(RESPONSABLE_SEDE_POST))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.PUT, alumnos(RESPONSABLE_SEDE_PUT))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.DELETE, alumnos(RESPONSABLE_SEDE_DELETE))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/alumnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/alumnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/alumnos/{alumnoId}/turnos/{turnoId}")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/alumnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/alumnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/alumnos/{alumnoId}/turnos/{turnoId}")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/grupos/{grupoId}/turnos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/grupos/{grupoId}/alumnos/{alumnoId}/turnos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString(), Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/grupos/conteo-alumnos")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						// Entrega 6-1: grupos abiertos al responsable de sede.
						.requestMatchers(HttpMethod.GET, grupos(RESPONSABLE_SEDE_GET))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.POST, grupos(RESPONSABLE_SEDE_POST))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.PUT, grupos(RESPONSABLE_SEDE_PUT))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.DELETE, grupos(RESPONSABLE_SEDE_DELETE))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/grupos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/grupos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/grupos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/grupos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/turnos/dto").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/turnos/limite").permitAll()
						// Entrega 6-1: turnos abiertos al responsable de sede (no POST /api/turnos/crear: sin grupo).
						.requestMatchers(HttpMethod.GET, turnos(RESPONSABLE_SEDE_GET))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.POST, turnos(RESPONSABLE_SEDE_POST))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.PUT, turnos(RESPONSABLE_SEDE_PUT))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.DELETE, turnos(RESPONSABLE_SEDE_DELETE))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/turnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/turnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/turnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/turnos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/eventos").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/eventos/{eventoId}").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/eventos/{eventoId}/imagen").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/eventos/{eventoId}/documentos/{documentoId}/descargar")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/eventos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/eventos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/eventos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/eventos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/pagos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/pagos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/pagos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/pagos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						// Entrega 6-3: tesorería, catálogo de productos (lectura) y cargos abiertos al responsable de sede.
						.requestMatchers(HttpMethod.GET, tesoreria(RESPONSABLE_SEDE_GET))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.POST, tesoreria(RESPONSABLE_SEDE_POST))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, productos(RESPONSABLE_SEDE_GET))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, productosAlumno(RESPONSABLE_SEDE_GET))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.POST, productosAlumno(RESPONSABLE_SEDE_POST))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.PUT, productosAlumno(RESPONSABLE_SEDE_PUT))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.DELETE, productosAlumno(RESPONSABLE_SEDE_DELETE))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/tesoreria/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/tesoreria/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/tesoreria/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/tesoreria/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/productos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/productos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/productos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/productos/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/productos-alumno/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/productos-alumno/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/productos-alumno/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/productos-alumno/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.GET, "/api/convocatorias/alumnos/{alumnoId}")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_USER.toString(), Roles.ROLE_RESPONSABLE_SEDE.toString())
						// Entrega 6-2 (D6-8): convocatorias abiertas al responsable de sede.
						.requestMatchers(HttpMethod.GET, convocatorias(RESPONSABLE_SEDE_GET))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.PUT, convocatorias(RESPONSABLE_SEDE_PUT))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/convocatorias/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/convocatorias/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/convocatorias/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/convocatorias/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						// Entrega 6-1d: listado de asistencia del responsable (solo los turnos de sus sedes, en el servicio).
						.requestMatchers(HttpMethod.GET, "/api/informes/asistencia")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						// Entrega 6-3 (D6-13): deudas y listado mensual del responsable (solo sus sedes, en el servicio).
						.requestMatchers(HttpMethod.GET, informes(RESPONSABLE_SEDE_GET))
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers(HttpMethod.GET, "/api/informes/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.POST, "/api/informes/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.PUT, "/api/informes/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers(HttpMethod.DELETE, "/api/informes/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						// Entrega 6-4 (D6-30, D6-31): tarifas por sede. Escriben propietario y gestor; el responsable de
						// sede solo lee (las de sus sedes, filtradas en el servicio).
						.requestMatchers(HttpMethod.GET, "/api/tarifas-sede")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString(),
								Roles.ROLE_RESPONSABLE_SEDE.toString())
						.requestMatchers("/api/tarifas-sede", "/api/tarifas-sede/**")
						.hasAnyAuthority(Roles.ROLE_ADMIN.toString(), Roles.ROLE_MANAGER.toString())
						.requestMatchers("/api/admin/**").hasAnyAuthority(Roles.ROLE_ADMIN.toString())
						// Administrador de plataforma (lecturas entre escuelas); el controlador lo exige también.
						.requestMatchers("/api/plataforma/**").hasAuthority(AutoridadPlataforma.ADMIN_PLATAFORMA)
						.requestMatchers(HttpMethod.POST, "/api/mail/**").permitAll().anyRequest().authenticated())
				.sessionManagement(manager -> manager.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authenticationProvider(authenticationProvider())
				.oauth2Login(oauth2 -> oauth2
						.successHandler(oauth2SuccessHandler)
						.permitAll())
						.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, authException) -> {
							String requestURI = request.getRequestURI();
							logger.warn("========================================");
							logger.warn("AUTHENTICATION ENTRY POINT TRIGGERED!");
							logger.warn("Request URI: {}", requestURI);
							logger.warn("Request Method: {}", request.getMethod());
							logger.warn("Auth Exception: {}", authException.getMessage());
							logger.warn("========================================");
							if (isApiRequest(request)) {
								writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "No autenticado.");
								return;
							}
							if (shouldRedirectToOAuthLogin(request)) {
								response.sendRedirect("/oauth2/authorization/google");
								return;
							}
							response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
						})
						.accessDeniedHandler((request, response, accessDeniedException) -> {
							logger.warn("========================================");
							logger.warn("ACCESS DENIED HANDLER TRIGGERED!");
							logger.warn("Request URI: {}", request.getRequestURI());
							logger.warn("Request Method: {}", request.getMethod());
							logger.warn("Access Denied Exception: {}", accessDeniedException.getMessage());
							logger.warn("========================================");
							if (isApiRequest(request)) {
								writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado.");
								return;
							}
							response.sendError(HttpServletResponse.SC_FORBIDDEN);
						}));
		http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		http.addFilterAfter(auditoriaRequestFilter, JwtAuthenticationFilter.class);
		return http.build();
	}

	private boolean shouldRedirectToOAuthLogin(HttpServletRequest request) {
		String acceptHeader = request.getHeader("Accept");
		boolean expectsHtml = acceptHeader != null && acceptHeader.contains("text/html");
		boolean safeNavigationMethod = "GET".equalsIgnoreCase(request.getMethod());
		return safeNavigationMethod && expectsHtml && !isApiRequest(request);
	}

	private boolean isApiRequest(HttpServletRequest request) {
		String path = getPathWithoutContext(request);
		return "/api".equals(path) || path.startsWith("/api/");
	}

	private String getPathWithoutContext(HttpServletRequest request) {
		String requestUri = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath == null || contextPath.isEmpty()) {
			return requestUri;
		}
		if (!requestUri.startsWith(contextPath)) {
			return requestUri;
		}
		return requestUri.substring(contextPath.length());
	}

	private void writeJsonError(HttpServletResponse response, int status, String message) throws java.io.IOException {
		if (response.isCommitted()) {
			return;
		}
		response.setStatus(status);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"mensaje\":\"" + message + "\"}");
	}

	/**
	 * Crea un proveedor de autenticación.
	 *
	 * @return Un objeto AuthenticationProvider.
	 */
	@Bean
	@SuppressWarnings("deprecation")
	AuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(usuarioService);
		authProvider.setPasswordEncoder(passwordEncoder);
		return authProvider;
	}

	/**
	 * Obtiene el AuthenticationManager.
	 *
	 * @param config La configuración de autenticación.
	 * @return Un objeto AuthenticationManager.
	 * @throws Exception Si hay un error al obtener el AuthenticationManager.
	 */
	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	private static String[] alumnos(String[] rutas) {
		return conPrefijo(rutas, "/api/alumnos");
	}

	private static String[] grupos(String[] rutas) {
		return conPrefijo(rutas, "/api/grupos");
	}

	private static String[] turnos(String[] rutas) {
		return conPrefijo(rutas, "/api/turnos");
	}

	private static String[] preinscripciones(String[] rutas) {
		return conPrefijo(rutas, "/api/preinscripciones");
	}

	private static String[] convocatorias(String[] rutas) {
		return conPrefijo(rutas, "/api/convocatorias");
	}

	private static String[] tesoreria(String[] rutas) {
		return conPrefijo(rutas, "/api/tesoreria");
	}

	private static String[] productos(String[] rutas) {
		return conPrefijo(rutas, "/api/productos");
	}

	private static String[] productosAlumno(String[] rutas) {
		return conPrefijo(rutas, "/api/productos-alumno");
	}

	/** Solo las de la 6-3; la regla de /api/informes/asistencia (6-1d) va aparte, antes. */
	private static String[] informes(String[] rutas) {
		return conPrefijo(rutas, "/api/informes");
	}

	/** Las rutas que son {@code prefijo} o empiezan por {@code prefijo/}. */
	private static String[] conPrefijo(String[] rutas, String prefijo) {
		return java.util.Arrays.stream(rutas).filter(r -> r.equals(prefijo) || r.startsWith(prefijo + "/"))
				.toArray(String[]::new);
	}
}
