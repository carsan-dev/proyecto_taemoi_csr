package com.taemoi.project.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.server.PathContainer;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.taemoi.project.acceso.ConsultaAccesoRecursos;
import com.taemoi.project.integracion.MySqlIntegrationTest;

/**
 * Matriz de permisos (docs/multi-escuela/fase3-sedes-permisos.md §3-§4): red de seguridad de CARACTERIZACIÓN.
 *
 * <ul>
 * <li><b>A, completitud</b>: cada manejador de {@link RequestMappingHandlerMapping} (método HTTP × patrón) tiene
 * exactamente una línea en {@value RegistroMatrizPermisos#FUENTE}, y no sobra ninguna. Un endpoint nuevo sin línea
 * hace fallar la suite indicando qué añadir.</li>
 * <li><b>B, decisión</b>: para cada línea y rol, la decisión combinada de la regla de URL y de la seguridad de
 * método es la declarada ({@code PERMITE}, {@code ALCANCE} o {@code DENIEGA}). Se calcula con
 * {@link EvaluadorAutorizacion} sin ejecutar ningún controlador ni tocar datos: el puerto de {@code @acceso} lo
 * sustituye {@link RegistradorConsultaAcceso}.</li>
 * </ul>
 *
 * <p>Regenerar el registro desde el comportamiento actual (sobrescribe el fichero; revisar el diff):
 * {@code ./mvnw test -Dtest=MatrizPermisosTest -Dmatriz.regenerar=true}.
 *
 * <p>Comparte la configuración de contexto de la mayoría de ITs ({@code @SpringBootTest} +
 * {@code @AutoConfigureMockMvc} + {@code JavaMailSender} simulado) para reutilizar el contexto en caché; MockMvc no
 * se usa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("matriz-permisos")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatrizPermisosTest extends MySqlIntegrationTest {

	/** Métodos con los que se expande un mapeo que no restringe método (p. ej. /error). */
	private static final List<String> METODOS_SIN_RESTRICCION = List.of("GET", "HEAD", "POST", "PUT", "PATCH",
			"DELETE");
	private static final List<String> ORDEN_METODOS = List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE",
			"OPTIONS", "TRACE");

	/**
	 * Rutas de ejemplo para los manejadores comodín: cubren cada regla de URL distinta que les afecta
	 * (fotos de alumnos heredadas y por escuela, imágenes públicas, documentos de eventos y el resto).
	 */
	private static final Map<String, List<String>> MUESTRAS_COMODIN = Map.of(
			"/imagenes/**", List.of(
					"/imagenes/alumnos/1.jpg",
					"/imagenes/org-1/alumnos/1.jpg",
					"/imagenes/eventos/1.jpg",
					"/imagenes/org-1/eventos/1.jpg"),
			"/documentos/**", List.of(
					"/documentos/Documentos_Eventos/1.pdf",
					"/documentos/org-1/eventos/1.pdf",
					"/documentos/Documentos_Alumnos/1.pdf",
					"/documentos/org-1/alumnos/1.pdf"));

	@MockitoBean
	JavaMailSender mailSender;

	/**
	 * Sustituye SOLO el puerto de datos de {@code @acceso} por el registrador (entrega5-plan.md §4.4.2): la lógica
	 * de rol × operación del bean se evalúa de verdad. Cuesta un contexto de Spring más en caché.
	 */
	@TestConfiguration
	static class ConsultaAccesoRegistrada {
		@Bean
		@Primary
		ConsultaAccesoRecursos registradorConsultaAcceso() {
			return new RegistradorConsultaAcceso();
		}
	}

	@Autowired
	WebApplicationContext contexto;

	private EvaluadorAutorizacion evaluador;
	/** Clave "MÉTODO patrón" → manejadores (normalmente uno). */
	private final Map<String, List<HandlerMethod>> manejadoresPorClave = new TreeMap<>();

	@BeforeAll
	void inventariar() {
		evaluador = new EvaluadorAutorizacion(contexto);
		Set<RequestMappingHandlerMapping> mapeos = new LinkedHashSet<>(
				contexto.getBeansOfType(RequestMappingHandlerMapping.class).values());
		for (RequestMappingHandlerMapping mapeo : mapeos) {
			for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeo.getHandlerMethods().entrySet()) {
				RequestMappingInfo info = entrada.getKey();
				Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
				Set<String> nombres = new LinkedHashSet<>(metodos.isEmpty()
						? METODOS_SIN_RESTRICCION
						: metodos.stream().map(RequestMethod::name).toList());
				// Spring MVC atiende HEAD con los manejadores GET (RequestMethodsRequestCondition), y Spring
				// Security recibe la petición con método HEAD: es un endpoint más con su propia regla de URL.
				if (nombres.contains("GET")) {
					nombres.add("HEAD");
				}
				for (String patron : info.getPatternValues()) {
					for (String metodo : nombres) {
						List<HandlerMethod> lista = manejadoresPorClave.computeIfAbsent(metodo + " " + patron,
								k -> new ArrayList<>());
						if (!lista.contains(entrada.getValue())) {
							lista.add(entrada.getValue());
						}
					}
				}
			}
		}
		if (manejadoresPorClave.isEmpty()) {
			throw new IllegalStateException("RequestMappingHandlerMapping no tiene manejadores");
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// A. Completitud
	// ---------------------------------------------------------------------------------------------------------

	@Test
	void cadaEndpointTieneExactamenteUnaPoliticaYNoSobraNinguna() throws IOException {
		assumeFalse(regenerando(), "regenerando el registro");
		List<String> errores = new ArrayList<>();
		List<RegistroMatrizPermisos.Linea> lineas = leerRegistro(errores);

		Map<String, Integer> vistas = new LinkedHashMap<>();
		Set<String> clavesCubiertas = new LinkedHashSet<>();
		List<String> obsoletas = new ArrayList<>();
		for (RegistroMatrizPermisos.Linea linea : lineas) {
			Integer previa = vistas.putIfAbsent(linea.clave(), linea.numero());
			if (previa != null) {
				errores.add("línea " + linea.numero() + ": '" + linea.clave() + "' repetida (ya en la línea " + previa
						+ "); deja una sola");
				continue;
			}
			String clave = claveManejador(linea);
			if (clave == null) {
				obsoletas.add("línea " + linea.numero() + ": " + linea.clave());
			} else {
				clavesCubiertas.add(clave);
			}
		}

		List<String> sinPolitica = new ArrayList<>();
		for (String clave : manejadoresPorClave.keySet()) {
			String metodo = clave.substring(0, clave.indexOf(' '));
			String patron = clave.substring(clave.indexOf(' ') + 1);
			if (esComodin(patron)) {
				// Cada ruta de ejemplo obligatoria debe tener su línea (puede haber más, añadidas a mano).
				for (String ruta : muestras(patron)) {
					if (!vistas.containsKey(metodo + " " + ruta)) {
						sinPolitica.add(lineaSugerida(metodo, ruta, clave));
					}
				}
			} else if (!clavesCubiertas.contains(clave)) {
				sinPolitica.add(lineaSugerida(metodo, patron, clave));
			}
		}

		if (!obsoletas.isEmpty()) {
			errores.add("Líneas OBSOLETAS en " + RegistroMatrizPermisos.FUENTE
					+ " (ningún endpoint las atiende; bórralas o corrige la ruta):\n  " + String.join("\n  ", obsoletas));
		}
		if (!sinPolitica.isEmpty()) {
			errores.add("Endpoints SIN POLÍTICA en " + RegistroMatrizPermisos.FUENTE + ". Añade estas líneas. Llevan la"
					+ " decisión ACTUAL calculada: revísala contra docs/multi-escuela/fase3-sedes-permisos.md §3 y, si"
					+ " no es la deseada, corrige la seguridad (SecurityConfiguration / @PreAuthorize), no la línea:\n  "
					+ String.join("\n  ", sinPolitica));
		}
		if (!errores.isEmpty()) {
			fail(String.join("\n\n", errores));
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// B. Decisión
	// ---------------------------------------------------------------------------------------------------------

	@Test
	void laDecisionRealCoincideConLaDeclaradaParaCadaRol() throws IOException {
		assumeFalse(regenerando(), "regenerando el registro");
		List<String> errores = new ArrayList<>();
		List<RegistroMatrizPermisos.Linea> lineas = leerRegistro(errores);
		List<String> discrepancias = new ArrayList<>();
		for (RegistroMatrizPermisos.Linea linea : lineas) {
			String clave = claveManejador(linea);
			if (clave == null) {
				continue; // la línea obsoleta la señala el test de completitud
			}
			EvaluadorAutorizacion.Resultado real = evaluar(linea.metodo(), linea.ruta(), clave);
			for (RolMatriz rol : RolMatriz.values()) {
				Decision declarada = linea.decisiones().get(rol);
				Decision calculada = real.combinada().get(rol);
				if (declarada != calculada) {
					discrepancias.add(String.format("línea %d: %s %s, %s: declarado %s, real %s (url=%s, método=%s)",
							linea.numero(), linea.metodo(), linea.ruta(), rol, declarada, calculada,
							real.url().get(rol), real.metodo().get(rol)));
				}
			}
		}
		if (!discrepancias.isEmpty()) {
			errores.add("La seguridad real NO coincide con " + RegistroMatrizPermisos.FUENTE + " (" + discrepancias.size()
					+ " casillas). Si el cambio es intencionado, actualiza la línea y justifícalo en la revisión:\n  "
					+ String.join("\n  ", discrepancias));
		}
		if (!errores.isEmpty()) {
			fail(String.join("\n\n", errores));
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Política de los roles nuevos (entrega 6, entrega6-plan.md §2.2)
	// ---------------------------------------------------------------------------------------------------------

	/**
	 * Líneas ({@code "MÉTODO RUTA"}) en las que RESPONSABLE_SEDE puede ser PERMITE sin que el {@code @PreAuthorize}
	 * consulte {@code @acceso}: listados que el servicio filtra con {@code PredicadosAlcance} y rutas con ids en el
	 * cuerpo que el servicio comprueba. Cada entrada lleva el test ({@code paquete.Clase#método}, bajo
	 * {@code com.taemoi.project}) que prueba el filtro o la comprobación.
	 */
	static final Map<String, String> RESPONSABLE_SEDE_FILTRADO_EN_SERVICIO = filtradoEnServicio();

	private static Map<String, String> filtradoEnServicio() {
		String alumnos = "integracion.ResponsableSedeAlumnosIntegrationTest#";
		String grupos = "integracion.ResponsableSedeGruposIntegrationTest#";
		String preinscripciones = "integracion.ResponsableSedePreinscripcionesConvocatoriasIntegrationTest#";
		String tesoreria = "integracion.ResponsableSedeTesoreriaIntegrationTest#";
		String tarifasSede = "integracion.TarifaSedeApiIntegrationTest#";
		Map<String, String> m = new TreeMap<>();
		for (String metodo : List.of("GET", "HEAD")) {
			// 6-4 (D6-31): tarifas por sede, solo las de sus sedes (filtro del servicio); escribir es de gestión.
			m.put(metodo + " /api/tarifas-sede", tarifasSede + "responsable_leeSoloLasDeSusSedes");
			// 6-3: tesorería de sus sedes (por la sede del cargo, D6-9/D6-12); catálogo común en lectura (D6-11);
			// informes de deudas y listado mensual de sus sedes (D6-13). Los años disponibles no exponen datos.
			m.put(metodo + " /api/tesoreria/resumen", tesoreria + "resumen_soloSusSedes_yGestionConDesglosePorSede");
			m.put(metodo + " /api/tesoreria/movimientos", tesoreria + "movimientos_soloSusSedes");
			m.put(metodo + " /api/tesoreria/export/csv", tesoreria + "exportaciones_soloSusSedes");
			m.put(metodo + " /api/tesoreria/export/pdf", tesoreria + "exportaciones_soloSusSedes");
			m.put(metodo + " /api/tesoreria/anios-disponibles", tesoreria + "exportaciones_soloSusSedes");
			m.put(metodo + " /api/productos", tesoreria + "catalogo_leeConTarifasSinEditar");
			m.put(metodo + " /api/productos/todos", tesoreria + "catalogo_leeConTarifasSinEditar");
			m.put(metodo + " /api/productos/{id}", tesoreria + "catalogo_leeConTarifasSinEditar");
			m.put(metodo + " /api/catalogo", tesoreria + "catalogo_leeConTarifasSinEditar");
			m.put(metodo + " /api/informes/deudas", tesoreria + "informes_deudasYListadoMensual_soloSusSedes_restoDenegado");
			m.put(metodo + " /api/informes/deudas/csv",
					tesoreria + "informes_deudasYListadoMensual_soloSusSedes_restoDenegado");
			m.put(metodo + " /api/informes/listado-mensualidad-mensual",
					tesoreria + "informes_deudasYListadoMensual_soloSusSedes_restoDenegado");
			// 6-2: listado de preinscripciones por el grupo; convocatorias comunes (D6-8), sus alumnos por D9.
			m.put(metodo + " /api/preinscripciones", preinscripciones + "preinscripciones_listadoSoloDeSusSedes");
			m.put(metodo + " /api/convocatorias", preinscripciones + "convocatorias_listaYDetalleComunes");
			m.put(metodo + " /api/convocatorias/{id}", preinscripciones + "convocatorias_listaYDetalleComunes");
			m.put(metodo + " /api/convocatorias/{id}/alumnos", preinscripciones + "convocatoria_soloSusAlumnos");
			m.put(metodo + " /api/convocatorias/{id}/reporte", preinscripciones + "convocatoria_soloSusAlumnos");
			m.put(metodo + " /api/convocatorias/{id}/informe-pdf", preinscripciones + "convocatoria_soloSusAlumnos");
			m.put(metodo + " /api/alumnos", alumnos + "listado_soloAlumnosDeSusSedes");
			m.put(metodo + " /api/alumnos/con-deportes", alumnos + "listado_soloAlumnosDeSusSedes");
			m.put(metodo + " /api/alumnos/ids", alumnos + "ids_losDelListadoEnElMismoOrden");
			m.put(metodo + " /api/alumnos/count", alumnos + "contadorYDistribucion_soloSusAlumnos");
			m.put(metodo + " /api/alumnos/deportes/distribucion", alumnos + "contadorYDistribucion_soloSusAlumnos");
			m.put(metodo + " /api/alumnos/aptos", alumnos + "aptos_soloSusAlumnos");
			m.put(metodo + " /api/alumnos/aptos/deporte", alumnos + "aptos_soloSusAlumnos");
			m.put(metodo + " /api/alumnos/aptos/convocatoria", alumnos + "aptos_soloSusAlumnos");
			m.put(metodo + " /api/grupos", grupos + "grupos_listadoSoloDeSusSedes");
			m.put(metodo + " /api/turnos", grupos + "turnos_listadoSoloDeSusSedes");
			// R2-P4: versiones ligeras de las dos listas, con el mismo filtro (ver ResumenGruposTurnosJsonDoradoIntegrationTest).
			m.put(metodo + " /api/grupos/resumen", grupos + "grupos_listadoSoloDeSusSedes");
			m.put(metodo + " /api/turnos/resumen", grupos + "turnos_listadoSoloDeSusSedes");
			m.put(metodo + " /api/informes/asistencia", grupos + "asistencia_soloTurnosDeSusSedes");
		}
		// Pieza 6c-2: bandeja y contador de solicitudes (las suyas y los encargos de sus sedes), pedir sobre recursos
		// de sus sedes (403 si no) y retirar solo las suyas (403 si no).
		String solicitudes = "integracion.SolicitudesIntegrationTest#";
		for (String metodo : List.of("GET", "HEAD")) {
			m.put(metodo + " /api/solicitudes", solicitudes + "responsable_veYCuentaSoloLoSuyoYLosEncargosDeSusSedes");
			m.put(metodo + " /api/solicitudes/contador",
					solicitudes + "responsable_veYCuentaSoloLoSuyoYLosEncargosDeSusSedes");
		}
		m.put("POST /api/solicitudes", solicitudes + "responsable_soloPideSobreRecursosDeSusSedes");
		m.put("POST /api/solicitudes/{id}/retirar", solicitudes + "retirar_soloLaSuya_403ParaOtros");
		m.put("POST /api/alumnos/crear", alumnos + "alta_sedePrincipalObligatoriaYSuya");
		m.put("POST /api/grupos", grupos + "grupos_altaEnSuSede");
		m.put("POST /api/turnos/crear-asignando-grupo", grupos + "turnos_altaConGrupoDeSuSede");
		m.put("POST /api/tesoreria/certificados-cobros", tesoreria + "certificados_soloAlumnosDeSuSedePrincipal");
		return Map.copyOf(m);
	}

	/**
	 * Pieza 6b-2 (D4): las ÚNICAS rutas abiertas al profesor antes de la entrega 7, las suyas de la caja
	 * ({@code /api/caja/profesor/**}), con el test que prueba su alcance (sus grupos o sus cobros). En ellas PROFESOR
	 * puede ser ALCANCE ({@code @acceso} consultó) o PERMITE (el servicio filtra por sus grupos o por su cuenta).
	 */
	static final Map<String, String> PROFESOR_CAJA_6B2 = profesorCaja();

	private static Map<String, String> profesorCaja() {
		String caja = "integracion.CajaSedeIntegrationTest#";
		Map<String, String> m = new LinkedHashMap<>();
		for (String metodo : List.of("GET", "HEAD")) {
			m.put(metodo + " /api/caja/profesor/cargos-pendientes",
					caja + "profesor_veSoloLosCargosPendientesDeSusGrupos_sinTotales");
			m.put(metodo + " /api/caja/profesor/productos",
					caja + "profesor_listaLosProductosQuePuedeVender_conLaMismaListaBlanca");
			m.put(metodo + " /api/caja/profesor/cobros-pendientes-entrega",
					caja + "profesor_misCobrosPendientesDeEntrega_soloLosSuyosConSusTotales");
			// Pieza 6b-3b-1: el servicio filtra por sus grupos (y la escuela activa).
			m.put(metodo + " /api/caja/profesor/alumnos",
					"integracion.MiCajaProfesorIntegrationTest#alumnosDeMisGrupos_cadaProfesorLosSuyos_yOtraEscuelaAislada");
		}
		m.put("POST /api/caja/profesor/cargos/{cargoId}/cobrar",
				caja + "profesor_cobraEnEfectivo_conLosMismosEfectosQueElPut");
		m.put("POST /api/caja/profesor/cobros/{cobroId}/deshacer",
				caja + "profesor_deshaceSuCobro_noElAjeno_niElEntregado");
		m.put("POST /api/caja/profesor/alumnos/{alumnoId}/productos/{productoId}/cobrar",
				caja + "profesor_noApuntaProductosConEfectos_niAAlumnosAjenos");
		// Pieza 6c-5: encargos. Productos encargables (los de la escuela), sus solicitudes (el servicio filtra por su
		// cuenta), pedir para un alumno de sus grupos (@acceso) y retirar o marcar entregado solo los suyos (servicio).
		String encargos = "integracion.EncargosIntegrationTest#";
		for (String metodo : List.of("GET", "HEAD")) {
			m.put(metodo + " /api/caja/profesor/productos-encargables",
					encargos + "productosEncargables_otroSinGradoAunqueNoEsteMarcado");
			m.put(metodo + " /api/caja/profesor/solicitudes", encargos + "misSolicitudes_soloLasSuyas");
		}
		m.put("POST /api/caja/profesor/alumnos/{alumnoId}/encargos",
				encargos + "pedirEncargo_soloElProfesorParaAlumnosDeSusGrupos");
		m.put("POST /api/caja/profesor/solicitudes/{id}/retirar", encargos + "retirar_enPedido_cancelaElCargo");
		m.put("POST /api/caja/profesor/solicitudes/{id}/entregado",
				encargos + "entregado_soloElSolicitanteYEnRecibido_sinExigirCobro");
		// Pieza 6c-6 (D6c-10): la devolución de su encargo caído si aún lleva el dinero (el servicio exige que la pidió
		// él y que el cobro es suyo y sin entregar).
		m.put("POST /api/caja/profesor/solicitudes/{id}/devolucion",
				"integracion.DevolucionesIntegrationTest#devolucionDelProfesor_soloSiLlevaElDinero");
		// Pieza 6c-4 (D6c-14, decisión 193): sus entregados recientes (el servicio filtra por su cuenta) y pedir deshacer
		// un cobro suyo ya entregado (@acceso.cobroPropioDelProfesor; el servicio exige la entrega ACTIVA).
		String deshacer = "integracion.PedirDeshacerIntegrationTest#";
		for (String metodo : List.of("GET", "HEAD")) {
			m.put(metodo + " /api/caja/profesor/cobros-entregados-recientes",
					deshacer + "entregadosRecientes_30Dias_soloLosSuyos_conSuEstado");
		}
		m.put("POST /api/caja/profesor/cobros/{cobroId}/pedir-deshacer",
				deshacer + "pedirDeshacer_cobroAjeno403_yAislamiento");
		return Map.copyOf(m);
	}

	/**
	 * Decisión 180 (pieza 6b-3b-2): lo imprescindible de sesión que la sesión solo-profesor puede usar además de su
	 * caja, con el porqué. {@code SesionSoloProfesorAuthorizationManager} lo deja pasar a las reglas de siempre.
	 */
	static final Map<String, String> PROFESOR_SESION_180 = profesorSesion();

	private static Map<String, String> profesorSesion() {
		Map<String, String> m = new LinkedHashMap<>();
		for (String metodo : List.of("GET", "HEAD")) {
			m.put(metodo + " /api/auth/roles", "el frontend decide qué mostrar por los roles");
			m.put(metodo + " /api/auth/user", "el frontend sabe quién es al recargar (sin alumno)");
			m.put(metodo + " /api/auth/organizaciones", "selector de escuela");
		}
		m.put("POST /api/auth/organizacion", "cambio de escuela");
		return Map.copyOf(m);
	}

	/**
	 * Invariantes de los roles nuevos (§2.2):
	 * <ol>
	 * <li>PROFESOR (la sesión solo-profesor, decisión 180) es DENIEGA en cada línea salvo: las de
	 * {@link #PROFESOR_CAJA_6B2} (ALCANCE o PERMITE), las de {@link #PROFESOR_SESION_180} (PERMITE) y las públicas
	 * (las que son PERMITE para ANONIMO), que son PERMITE. Una ruta nueva abierta a esa sesión hace fallar el test.</li>
	 * <li>RESPONSABLE_SEDE solo puede ser DENIEGA, ALCANCE (el {@code @PreAuthorize} consultó {@code @acceso}) o
	 * PERMITE en una línea de {@link #RESPONSABLE_SEDE_FILTRADO_EN_SERVICIO} o en una ruta pública o de sesión que ya
	 * era PERMITE para SOLO_PLATAFORMA. En {@code /api/plataforma/**}, DENIEGA.</li>
	 * </ol>
	 * El test B garantiza que el registro es la decisión real.
	 */
	@Test
	void rolesNuevosSoloPasanPorAlcanceOPorUnFiltroDeclarado() throws IOException {
		assumeFalse(regenerando(), "regenerando el registro");
		List<String> errores = new ArrayList<>();
		List<RegistroMatrizPermisos.Linea> lineas = leerRegistro(errores);
		int lineasDePlataforma = 0;
		Set<String> declaradasUsadas = new LinkedHashSet<>();
		Set<String> profesorDeclaradasUsadas = new LinkedHashSet<>();
		Set<String> profesorSesionUsadas = new LinkedHashSet<>();
		for (RegistroMatrizPermisos.Linea linea : lineas) {
			boolean plataforma = esRutaDePlataforma(linea.ruta());
			if (plataforma) {
				lineasDePlataforma++;
			}
			Decision soloPlataforma = linea.decisiones().get(RolMatriz.SOLO_PLATAFORMA);
			Decision profesorEsperado = PROFESOR_SESION_180.containsKey(linea.clave())
					|| linea.decisiones().get(RolMatriz.ANONIMO) == Decision.PERMITE ? Decision.PERMITE : Decision.DENIEGA;
			Decision profesor = linea.decisiones().get(RolMatriz.PROFESOR);
			if (PROFESOR_SESION_180.containsKey(linea.clave())) {
				profesorSesionUsadas.add(linea.clave());
			}
			if (PROFESOR_CAJA_6B2.containsKey(linea.clave())) {
				profesorDeclaradasUsadas.add(linea.clave());
				if (profesor != Decision.ALCANCE && profesor != Decision.PERMITE) {
					errores.add(String.format("línea %d: %s, PROFESOR: %s en una ruta declarada en PROFESOR_CAJA_6B2",
							linea.numero(), linea.clave(), profesor));
				}
			} else if (profesor != profesorEsperado) {
				errores.add(String.format("línea %d: %s, PROFESOR: %s, se esperaba %s", linea.numero(), linea.clave(),
						linea.decisiones().get(RolMatriz.PROFESOR), profesorEsperado));
			}
			Decision responsable = linea.decisiones().get(RolMatriz.RESPONSABLE_SEDE);
			if (plataforma) {
				if (responsable != Decision.DENIEGA) {
					errores.add(String.format("línea %d: %s, RESPONSABLE_SEDE: %s en una ruta de plataforma",
							linea.numero(), linea.clave(), responsable));
				}
				continue;
			}
			if (responsable == Decision.PERMITE) {
				if (RESPONSABLE_SEDE_FILTRADO_EN_SERVICIO.containsKey(linea.clave())) {
					declaradasUsadas.add(linea.clave());
				} else if (soloPlataforma != Decision.PERMITE) {
					errores.add(String.format("línea %d: %s, RESPONSABLE_SEDE: PERMITE sin @acceso ni filtro declarado"
							+ " en RESPONSABLE_SEDE_FILTRADO_EN_SERVICIO", linea.numero(), linea.clave()));
				}
			}
		}
		for (Map.Entry<String, String> entrada : RESPONSABLE_SEDE_FILTRADO_EN_SERVICIO.entrySet()) {
			if (!declaradasUsadas.contains(entrada.getKey())) {
				errores.add("RESPONSABLE_SEDE_FILTRADO_EN_SERVICIO declara '" + entrada.getKey()
						+ "', que no es PERMITE para RESPONSABLE_SEDE en el registro: quítala");
			}
			if (!existeTest(entrada.getValue())) {
				errores.add("RESPONSABLE_SEDE_FILTRADO_EN_SERVICIO: '" + entrada.getKey() + "' cita el test "
						+ entrada.getValue() + ", que no existe");
			}
		}
		for (Map.Entry<String, String> entrada : PROFESOR_CAJA_6B2.entrySet()) {
			if (!profesorDeclaradasUsadas.contains(entrada.getKey())) {
				errores.add("PROFESOR_CAJA_6B2 declara '" + entrada.getKey() + "', que no está en el registro: quítala");
			}
			if (!existeTest(entrada.getValue())) {
				errores.add("PROFESOR_CAJA_6B2: '" + entrada.getKey() + "' cita el test " + entrada.getValue()
						+ ", que no existe");
			}
		}
		for (String clave : PROFESOR_SESION_180.keySet()) {
			if (!profesorSesionUsadas.contains(clave)) {
				errores.add("PROFESOR_SESION_180 declara '" + clave + "', que no está en el registro: quítala");
			}
		}
		if (lineasDePlataforma == 0) {
			errores.add("el registro no tiene ninguna línea de /api/plataforma/**: el invariante sería vacío");
		}
		if (!errores.isEmpty()) {
			fail("Los roles nuevos no cumplen la política de la entrega 6:\n  " + String.join("\n  ", errores));
		}
	}

	/**
	 * Decisión 180: la sesión solo-profesor la cierra la CAPA DE URL, no los {@code @PreAuthorize} repartidos. Para
	 * cada endpoint, la regla de URL solo puede dejarla pasar en su lista blanca (caja del profesor, las rutas de
	 * {@link #PROFESOR_SESION_180} y {@code /api/plataforma/**}, que además exige ROLE_ADMIN_PLATAFORMA) o donde deja
	 * pasar al anónimo. Así una ruta nueva sin {@code @PreAuthorize} (que caería en {@code anyRequest().authenticated()})
	 * queda cerrada a esa sesión.
	 */
	@Test
	void sesionSoloProfesorLaCierraLaCapaDeUrlFueraDeSuListaBlanca() throws IOException {
		assumeFalse(regenerando(), "regenerando el registro");
		List<String> errores = new ArrayList<>();
		List<RegistroMatrizPermisos.Linea> lineas = leerRegistro(errores);
		int cerradasPorLaUrl = 0;
		for (RegistroMatrizPermisos.Linea linea : lineas) {
			String clave = claveManejador(linea);
			if (clave == null) {
				continue;
			}
			EvaluadorAutorizacion.Resultado r = evaluar(linea.metodo(), linea.ruta(), clave);
			Decision urlProfesor = r.url().get(RolMatriz.PROFESOR);
			boolean listaBlanca = linea.ruta().startsWith("/api/caja/profesor/")
					|| PROFESOR_SESION_180.containsKey(linea.clave()) || esRutaDePlataforma(linea.ruta());
			boolean publica = r.url().get(RolMatriz.ANONIMO) == Decision.PERMITE;
			if (urlProfesor != Decision.DENIEGA && !listaBlanca && !publica) {
				errores.add(String.format("línea %d: %s, la regla de URL deja pasar a PROFESOR (%s) fuera de su lista"
						+ " blanca", linea.numero(), linea.clave(), urlProfesor));
			}
			if (urlProfesor == Decision.DENIEGA && r.url().get(RolMatriz.SOLO_PLATAFORMA) == Decision.PERMITE) {
				cerradasPorLaUrl++;
			}
		}
		if (cerradasPorLaUrl == 0) {
			errores.add("ninguna ruta abierta a cualquier sesión queda cerrada a PROFESOR en la URL: el invariante sería"
					+ " vacío");
		}
		if (!errores.isEmpty()) {
			fail("La sesión solo-profesor no está cerrada en la capa de URL:\n  " + String.join("\n  ", errores));
		}
	}

	/** {@code paquete.Clase#método} relativo a {@code com.taemoi.project}. */
	private static boolean existeTest(String referencia) {
		int almohadilla = referencia.indexOf('#');
		if (almohadilla < 0) {
			return false;
		}
		try {
			Class<?> clase = Class.forName("com.taemoi.project." + referencia.substring(0, almohadilla));
			String metodo = referencia.substring(almohadilla + 1);
			for (java.lang.reflect.Method m : clase.getDeclaredMethods()) {
				if (m.getName().equals(metodo) && m.isAnnotationPresent(Test.class)) {
					return true;
				}
			}
			return false;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	private static boolean esRutaDePlataforma(String ruta) {
		return ruta.equals("/api/plataforma") || ruta.startsWith("/api/plataforma/");
	}

	// ---------------------------------------------------------------------------------------------------------
	// Casos de control: comportamiento conocido (comprobado por HTTP real en otros tests) que el arnés debe
	// reproducir. Si el arnés dejara de ser fiel (p. ej. todo cae en anyRequest o no ve @PreAuthorize), fallan.
	// ---------------------------------------------------------------------------------------------------------

	@Test
	void controlInformeDeAsistenciaNoEsAnonimo() {
		EvaluadorAutorizacion.Resultado r = evaluarClave("GET /api/informes/asistencia");
		assertEquals(Decision.DENIEGA, r.combinada().get(RolMatriz.ANONIMO));
		assertEquals(Decision.DENIEGA, r.combinada().get(RolMatriz.FAMILIA));
		assertEquals(Decision.PERMITE, r.combinada().get(RolMatriz.GESTOR));
		assertEquals(Decision.PERMITE, r.combinada().get(RolMatriz.PROPIETARIO));
	}

	@Test
	void controlErroresDePlataformaSoloParaAdministradorDePlataforma() {
		// PlataformaYAuditoriaPropietarioIntegrationTest lo comprueba por HTTP: 200 con solo plataforma.
		EvaluadorAutorizacion.Resultado r = evaluarClave("GET /api/plataforma/errores");
		assertEquals(Decision.PERMITE, r.combinada().get(RolMatriz.SOLO_PLATAFORMA));
		assertEquals(Decision.DENIEGA, r.combinada().get(RolMatriz.PROPIETARIO));
		assertEquals(Decision.DENIEGA, r.url().get(RolMatriz.PROPIETARIO), "la regla de URL /api/plataforma/** deniega");
		assertEquals(Decision.DENIEGA, r.metodo().get(RolMatriz.PROPIETARIO), "y el @PreAuthorize del controlador también");
	}

	/**
	 * Entrega 5c-2 (entrega5-plan.md §4.4.7): la familia pasa solo con su alumno ({@code @acceso} consulta el
	 * puerto → ALCANCE); la gestión tiene alcance total y el bean corta sin consultar → PERMITE.
	 * RutasFamiliaTaemoiIntegrationTest lo comprueba por HTTP.
	 */
	@Test
	void controlRutaDeFamiliaConAlcanceParaLaFamilia() {
		EvaluadorAutorizacion.Resultado r = evaluarClave("GET /api/alumnos/{alumnoId}/grupos");
		assertEquals(Decision.ALCANCE, r.combinada().get(RolMatriz.FAMILIA));
		assertEquals(Decision.ALCANCE, r.metodo().get(RolMatriz.FAMILIA), "el ALCANCE viene de @acceso");
		assertEquals(Decision.PERMITE, r.combinada().get(RolMatriz.PROPIETARIO));
		assertEquals(Decision.PERMITE, r.combinada().get(RolMatriz.GESTOR));
		assertEquals(Decision.DENIEGA, r.combinada().get(RolMatriz.ANONIMO));
		assertEquals(Decision.DENIEGA, r.combinada().get(RolMatriz.SOLO_PLATAFORMA));
	}

	/**
	 * Decisión 180: {@code /api/auth/user/alumnos} solo tiene {@code anyRequest().authenticated()} en la URL (pasa
	 * cualquier sesión, p. ej. SOLO_PLATAFORMA); a la sesión solo-profesor la deniega ya la URL. Su lista blanca, no.
	 * SesionSoloProfesorIntegrationTest lo comprueba por HTTP.
	 */
	@Test
	void controlSesionSoloProfesorCerradaEnLaUrlYAbiertaEnSuListaBlanca() {
		EvaluadorAutorizacion.Resultado alumnos = evaluarClave("GET /api/auth/user/alumnos");
		assertEquals(Decision.PERMITE, alumnos.url().get(RolMatriz.SOLO_PLATAFORMA));
		assertEquals(Decision.DENIEGA, alumnos.url().get(RolMatriz.PROFESOR));
		EvaluadorAutorizacion.Resultado roles = evaluarClave("GET /api/auth/roles");
		assertEquals(Decision.PERMITE, roles.combinada().get(RolMatriz.PROFESOR));
		assertEquals(Decision.DENIEGA, roles.combinada().get(RolMatriz.RESPONSABLE_SEDE));
		assertEquals(Decision.PERMITE, evaluarClave("GET /api/eventos").combinada().get(RolMatriz.PROFESOR));
	}

	@Test
	void controlCasosComprobadosPorHttpEnSecurityAuthorizationWebMvcTest() {
		// anónimo → 401 en /api/auth/roles; ROLE_USER → 200.
		EvaluadorAutorizacion.Resultado roles = evaluarClave("GET /api/auth/roles");
		assertEquals(Decision.DENIEGA, roles.combinada().get(RolMatriz.ANONIMO));
		assertEquals(Decision.PERMITE, roles.combinada().get(RolMatriz.FAMILIA));
		// anónimo → 200 en el detalle de un evento visible (permitAll).
		assertEquals(Decision.PERMITE, evaluarClave("GET /api/eventos/{eventoId}").combinada().get(RolMatriz.ANONIMO));
	}

	@Test
	void controlLaCapaDeMetodoSeEvaluaPorSiSola() {
		// Un endpoint cuya regla de URL deja pasar a la familia y que solo el @PreAuthorize restringe: prueba que
		// el arnés aplica la seguridad de método y no solo la de URL.
		String clave = manejadoresPorClave.keySet().stream()
				.filter(c -> !esComodin(c))
				.filter(c -> {
					EvaluadorAutorizacion.Resultado r = evaluarClave(c);
					return r.url().get(RolMatriz.FAMILIA) == Decision.PERMITE
							&& r.metodo().get(RolMatriz.FAMILIA) == Decision.DENIEGA;
				})
				.findFirst()
				.orElse(null);
		assertNotNull(clave, "debería existir al menos un endpoint protegido solo por @PreAuthorize");
		HandlerMethod manejador = manejadoresPorClave.get(clave).get(0);
		assertEquals(true, AnnotatedElementUtils.hasAnnotation(manejador.getMethod(), PreAuthorize.class)
				|| AnnotatedElementUtils.hasAnnotation(ClassUtils.getUserClass(manejador.getBeanType()),
						PreAuthorize.class), clave + " debería llevar @PreAuthorize");
	}

	@Test
	void controlHeadSeEvaluaComoHeadConSusPropiasReglas() {
		// Los GET generan su HEAD en el inventario.
		assertNotNull(manejadoresPorClave.get("HEAD /api/alumnos/aptos"), "falta el HEAD implícito de un GET");
		// Regla explícita HEAD /api/eventos/{eventoId}/imagen → permitAll.
		assertEquals(Decision.PERMITE, evaluarClave("HEAD /api/eventos/{eventoId}/imagen").url().get(RolMatriz.ANONIMO));
		// Regla explícita HEAD /api/alumnos/{alumnoId}/imagen (ADMIN/MANAGER/USER): niega a SOLO_PLATAFORMA en la URL,
		// cosa que anyRequest().authenticated() no haría; prueba que la petición llega con método HEAD.
		assertEquals(Decision.DENIEGA,
				evaluarClave("HEAD /api/alumnos/{alumnoId}/imagen").url().get(RolMatriz.SOLO_PLATAFORMA));
	}

	// ---------------------------------------------------------------------------------------------------------
	// Regeneración (solo con -Dmatriz.regenerar=true)
	// ---------------------------------------------------------------------------------------------------------

	@Test
	void regenerarRegistro() throws IOException {
		assumeTrue(regenerando(), "solo con -Dmatriz.regenerar=true");
		List<String> errores = new ArrayList<>();
		List<RegistroMatrizPermisos.Linea> existentes = leerRegistroSiExiste(errores);

		Map<String, Set<String>> rutasPorClave = new TreeMap<>();
		for (String clave : manejadoresPorClave.keySet()) {
			String patron = clave.substring(clave.indexOf(' ') + 1);
			Set<String> rutas = new LinkedHashSet<>();
			if (esComodin(patron)) {
				rutas.addAll(muestras(patron));
			} else {
				rutas.add(patron);
			}
			rutasPorClave.put(clave, rutas);
		}
		// Conserva las rutas de ejemplo añadidas a mano para manejadores comodín.
		for (RegistroMatrizPermisos.Linea linea : existentes) {
			String clave = claveManejador(linea);
			if (clave != null && esComodin(clave)) {
				rutasPorClave.get(clave).add(linea.ruta());
			}
		}

		List<RegistroMatrizPermisos.Fila> filas = new ArrayList<>();
		List<String> detalle = new ArrayList<>();
		detalle.add("METODO\tRUTA\tMANEJADOR\tPREAUTHORIZE\t"
				+ columnas("URL_") + "\t" + columnas("METODO_") + "\t" + columnas(""));
		for (Map.Entry<String, Set<String>> entrada : rutasPorClave.entrySet()) {
			String clave = entrada.getKey();
			String metodo = clave.substring(0, clave.indexOf(' '));
			for (String ruta : entrada.getValue()) {
				EvaluadorAutorizacion.Resultado r = evaluar(metodo, ruta, clave);
				String manejadores = describir(manejadoresPorClave.get(clave));
				filas.add(new RegistroMatrizPermisos.Fila(metodo, ruta, r.combinada(), manejadores));
				detalle.add(metodo + "\t" + ruta + "\t" + manejadores + "\t" + preAuthorize(manejadoresPorClave.get(clave))
						+ "\t" + valores(r.url()) + "\t" + valores(r.metodo()) + "\t" + valores(r.combinada()));
			}
		}
		filas.sort(Comparator.comparing(RegistroMatrizPermisos.Fila::ruta)
				.thenComparing(f -> ORDEN_METODOS.indexOf(f.metodo())));
		Path fuente = Path.of(System.getProperty("basedir", "."), RegistroMatrizPermisos.FUENTE);
		Files.createDirectories(fuente.getParent());
		Files.writeString(fuente, RegistroMatrizPermisos.formatear(filas), StandardCharsets.UTF_8);
		Path detalleRuta = Path.of(System.getProperty("basedir", "."), "target", "matriz-permisos-detalle.tsv");
		Files.createDirectories(detalleRuta.getParent());
		Files.write(detalleRuta, detalle, StandardCharsets.UTF_8);
		System.out.println("Matriz regenerada: " + filas.size() + " líneas en " + fuente.toAbsolutePath()
				+ "; detalle por capa en " + detalleRuta.toAbsolutePath());
	}

	// ---------------------------------------------------------------------------------------------------------
	// Utilidades
	// ---------------------------------------------------------------------------------------------------------

	private static boolean regenerando() {
		return Boolean.getBoolean("matriz.regenerar");
	}

	private List<RegistroMatrizPermisos.Linea> leerRegistro(List<String> errores) throws IOException {
		try (InputStream entrada = getClass().getClassLoader().getResourceAsStream(RegistroMatrizPermisos.RECURSO)) {
			if (entrada == null) {
				fail("Falta " + RegistroMatrizPermisos.FUENTE + "; genéralo con -Dmatriz.regenerar=true");
			}
			return RegistroMatrizPermisos.leer(entrada, errores);
		}
	}

	private List<RegistroMatrizPermisos.Linea> leerRegistroSiExiste(List<String> errores) throws IOException {
		Path fuente = Path.of(System.getProperty("basedir", "."), RegistroMatrizPermisos.FUENTE);
		if (!Files.exists(fuente)) {
			return List.of();
		}
		try (InputStream entrada = Files.newInputStream(fuente)) {
			return RegistroMatrizPermisos.leer(entrada, errores);
		}
	}

	/**
	 * Manejador al que corresponde una línea: el de su misma clave "MÉTODO patrón" o, si la ruta es un ejemplo
	 * concreto, el manejador comodín del mismo método cuyo patrón la atiende. {@code null} si ninguno (obsoleta).
	 */
	private String claveManejador(RegistroMatrizPermisos.Linea linea) {
		if (manejadoresPorClave.containsKey(linea.clave())) {
			return linea.clave();
		}
		if (linea.ruta().contains("{") || linea.ruta().contains("*")) {
			return null;
		}
		PathContainer ruta = PathContainer.parsePath(linea.ruta());
		for (String clave : manejadoresPorClave.keySet()) {
			String metodo = clave.substring(0, clave.indexOf(' '));
			String patron = clave.substring(clave.indexOf(' ') + 1);
			if (metodo.equals(linea.metodo()) && esComodin(patron)) {
				PathPattern compilado = PathPatternParser.defaultInstance.parse(patron);
				if (compilado.matches(ruta)) {
					return clave;
				}
			}
		}
		return null;
	}

	private EvaluadorAutorizacion.Resultado evaluarClave(String clave) {
		List<HandlerMethod> manejadores = manejadoresPorClave.get(clave);
		assertNotNull(manejadores, "no existe el endpoint " + clave);
		String metodo = clave.substring(0, clave.indexOf(' '));
		String patron = clave.substring(clave.indexOf(' ') + 1);
		return evaluar(metodo, patron, clave);
	}

	private EvaluadorAutorizacion.Resultado evaluar(String metodo, String ruta, String claveManejador) {
		return evaluador.evaluar(metodo, concretar(ruta), manejadoresPorClave.get(claveManejador));
	}

	private String lineaSugerida(String metodo, String ruta, String clave) {
		EvaluadorAutorizacion.Resultado r = evaluar(metodo, ruta, clave);
		return RegistroMatrizPermisos.formatearFila(
				new RegistroMatrizPermisos.Fila(metodo, ruta, r.combinada(), describir(manejadoresPorClave.get(clave))),
				0, 0);
	}

	/** Rutas de ejemplo obligatorias de un patrón comodín (todas deben tener línea en el registro). */
	private static List<String> muestras(String patron) {
		return MUESTRAS_COMODIN.getOrDefault(patron, List.of(concretar(patron)));
	}

	private static boolean esComodin(String patronOClave) {
		return patronOClave.contains("*");
	}

	/** Ruta concreta para un patrón: cada variable {x} (o {x:regex}, {*x}) pasa a "1"; los comodines, a "x". */
	static String concretar(String patron) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (i < patron.length()) {
			char c = patron.charAt(i);
			if (c == '{') {
				int profundidad = 1;
				int j = i + 1;
				while (j < patron.length() && profundidad > 0) {
					char d = patron.charAt(j);
					if (d == '{') {
						profundidad++;
					} else if (d == '}') {
						profundidad--;
					}
					j++;
				}
				sb.append('1');
				i = j;
			} else {
				sb.append(c);
				i++;
			}
		}
		return sb.toString().replace("**", "x").replace("*", "x");
	}

	private static String describir(List<HandlerMethod> manejadores) {
		return manejadores.stream()
				.map(m -> ClassUtils.getUserClass(m.getBeanType()).getSimpleName() + "#" + m.getMethod().getName())
				.distinct()
				.collect(Collectors.joining(","));
	}

	private static String preAuthorize(List<HandlerMethod> manejadores) {
		return manejadores.stream().map(m -> {
			PreAuthorize enMetodo = AnnotatedElementUtils.findMergedAnnotation(m.getMethod(), PreAuthorize.class);
			if (enMetodo != null) {
				return enMetodo.value();
			}
			PreAuthorize enClase = AnnotatedElementUtils.findMergedAnnotation(
					ClassUtils.getUserClass(m.getBeanType()), PreAuthorize.class);
			return enClase != null ? "clase:" + enClase.value() : "-";
		}).distinct().collect(Collectors.joining(" | "));
	}

	private static String columnas(String prefijo) {
		List<String> nombres = new ArrayList<>();
		for (RolMatriz rol : RolMatriz.values()) {
			nombres.add(prefijo + rol.name());
		}
		return String.join("\t", nombres);
	}

	private static String valores(Map<RolMatriz, Decision> decisiones) {
		List<String> v = new ArrayList<>();
		for (RolMatriz rol : RolMatriz.values()) {
			v.add(decisiones.get(rol).name());
		}
		return String.join("\t", v);
	}
}
