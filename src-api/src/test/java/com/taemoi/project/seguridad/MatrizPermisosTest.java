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

import com.taemoi.project.integracion.MySqlIntegrationTest;

/**
 * Matriz de permisos (docs/multi-escuela/fase3-sedes-permisos.md §3-§4): red de seguridad de CARACTERIZACIÓN.
 *
 * <ul>
 * <li><b>A, completitud</b>: cada manejador de {@link RequestMappingHandlerMapping} (método HTTP × patrón) tiene
 * exactamente una línea en {@value RegistroMatrizPermisos#FUENTE}, y no sobra ninguna. Un endpoint nuevo sin línea
 * hace fallar la suite indicando qué añadir.</li>
 * <li><b>B, decisión</b>: para cada línea y rol, la decisión combinada de la regla de URL y de la seguridad de
 * método es la declarada. Se calcula con {@link EvaluadorAutorizacion} sin ejecutar ningún controlador ni tocar
 * datos.</li>
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
	// Invariante de la entrega 5b: los roles nuevos fallan cerrados
	// ---------------------------------------------------------------------------------------------------------

	/**
	 * Líneas ({@code "MÉTODO RUTA"}) en las que RESPONSABLE_SEDE o PROFESOR pueden diferir de una sesión sin rol de
	 * escuela. Vacía mientras ningún endpoint esté abierto a esos roles (D5-1 = A); las entregas 6 y 7 la
	 * sustituyen por la política declarada.
	 */
	private static final Set<String> ROLES_NUEVOS_LISTA_BLANCA = Set.of();

	/**
	 * RESPONSABLE_SEDE y PROFESOR deciden en cada línea lo mismo que SOLO_PLATAFORMA (una sesión autenticada sin
	 * ningún rol de escuela), salvo en {@code /api/plataforma/**}, que les deniega. Así un rol nuevo no pasa ninguna
	 * regla de gestión ni de familia. El test B garantiza que el registro es la decisión real.
	 */
	@Test
	void rolesNuevosNoPasanDondeNoPasaUnaSesionSinRolDeEscuela() throws IOException {
		assumeFalse(regenerando(), "regenerando el registro");
		List<String> errores = new ArrayList<>();
		List<RegistroMatrizPermisos.Linea> lineas = leerRegistro(errores);
		int lineasDePlataforma = 0;
		for (RegistroMatrizPermisos.Linea linea : lineas) {
			if (ROLES_NUEVOS_LISTA_BLANCA.contains(linea.clave())) {
				continue;
			}
			boolean plataforma = esRutaDePlataforma(linea.ruta());
			if (plataforma) {
				lineasDePlataforma++;
			}
			Decision esperada = plataforma ? Decision.DENIEGA : linea.decisiones().get(RolMatriz.SOLO_PLATAFORMA);
			for (RolMatriz rol : List.of(RolMatriz.RESPONSABLE_SEDE, RolMatriz.PROFESOR)) {
				if (linea.decisiones().get(rol) != esperada) {
					errores.add(String.format("línea %d: %s, %s: %s, se esperaba %s", linea.numero(), linea.clave(),
							rol, linea.decisiones().get(rol), esperada));
				}
			}
		}
		if (lineasDePlataforma == 0) {
			errores.add("el registro no tiene ninguna línea de /api/plataforma/**: el invariante sería vacío");
		}
		if (!errores.isEmpty()) {
			fail("Los roles nuevos pasan donde no pasa una sesión sin rol de escuela:\n  "
					+ String.join("\n  ", errores));
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

	@Test
	void controlRutaDeFamiliaPermitidaALaFamilia() {
		EvaluadorAutorizacion.Resultado r = evaluarClave("GET /api/alumnos/{alumnoId}/grupos");
		assertEquals(Decision.PERMITE, r.combinada().get(RolMatriz.FAMILIA));
		assertEquals(Decision.DENIEGA, r.combinada().get(RolMatriz.ANONIMO));
		assertEquals(Decision.DENIEGA, r.combinada().get(RolMatriz.SOLO_PLATAFORMA));
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
