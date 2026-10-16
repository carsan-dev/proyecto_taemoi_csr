package com.taemoi.project.seguridad;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.MethodMatchers;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.AuthorizationAdvisor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.parameters.DefaultSecurityParameterNameDiscoverer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;

import com.taemoi.project.acceso.ConsultaAccesoRecursos;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletContext;

/**
 * Calcula la decisión de autorización de un endpoint para un rol SIN ejecutar el controlador.
 *
 * <p>Usa las piezas reales del contexto de la aplicación, no una copia de las reglas:
 * <ol>
 * <li><b>Capa URL</b>: el {@link AuthorizationManager} del {@link AuthorizationFilter} de la
 * {@link SecurityFilterChain} de producción que atiende la petición (las {@code requestMatchers} de
 * {@code SecurityConfiguration}, en su orden, terminando en {@code anyRequest().authenticated()}). Se le pasa una
 * petición construida igual que la construye MockMvc. Igual que {@code AuthorizationFilter}, un resultado
 * {@code null} cuenta como concedido.</li>
 * <li><b>Capa método</b>: los {@link AuthorizationAdvisor} ({@code @PreAuthorize} y compañía) que Spring aplicó
 * al proxy real del controlador, invocados en su orden sobre una invocación simulada cuyo {@code proceed()} final
 * NO llama al método: solo anota que la seguridad lo habría dejado pasar.</li>
 * </ol>
 * La decisión combinada es DENIEGA si alguna capa deniega; ALCANCE si la capa de método concedió tras consultar el
 * puerto de datos de {@code @acceso} (sustituido por {@link RegistradorConsultaAcceso}); y PERMITE si no. El resto de filtros (CSRF, JWT, auditoría) y
 * las comprobaciones programáticas dentro de los servicios quedan fuera a propósito: la matriz fija la política
 * declarativa (ver docs/multi-escuela/matriz-endpoints.md).
 */
final class EvaluadorAutorizacion {

	private final ApplicationContext contexto;
	private final ServletContext servletContext;
	private final List<SecurityFilterChain> cadenas;

	EvaluadorAutorizacion(WebApplicationContext contexto) {
		this.contexto = contexto;
		this.servletContext = contexto.getServletContext();
		// Las cadenas en el orden en que las recorre el FilterChainProxy de producción (gana la primera que casa).
		FilterChainProxy proxy = contexto.getBean(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME,
				FilterChainProxy.class);
		this.cadenas = new ArrayList<>(proxy.getFilterChains());
		if (this.cadenas.isEmpty()) {
			throw new IllegalStateException("El FilterChainProxy no tiene ninguna SecurityFilterChain");
		}
		// Sin el registrador, @acceso consultaría la base de datos con ids de ejemplo y daría DENIEGA falsos.
		ConsultaAccesoRecursos consulta = contexto.getBean(ConsultaAccesoRecursos.class);
		if (!(consulta instanceof RegistradorConsultaAcceso)) {
			throw new IllegalStateException("El puerto de @acceso del contexto es " + consulta.getClass().getName()
					+ ", no RegistradorConsultaAcceso: falta la @TestConfiguration de MatrizPermisosTest");
		}
	}

	/** Decisión por rol de ambas capas, con el detalle de cada una para los mensajes. */
	record Resultado(Map<RolMatriz, Decision> combinada, Map<RolMatriz, Decision> url, Map<RolMatriz, Decision> metodo) {
	}

	private static final List<Class<? extends Annotation>> ANOTACIONES_DE_METODO = List.of(PreAuthorize.class,
			PostAuthorize.class, PreFilter.class, PostFilter.class);

	/** Llamada al bean {@code @acceso} sin paréntesis anidados; el grupo 1 son sus argumentos. */
	private static final Pattern LLAMADA_ACCESO = Pattern.compile("@acceso\\.\\w+\\(([^()]*)\\)");
	/** Argumento admitido dentro de una llamada a {@code @acceso}: un parámetro del método. */
	private static final Pattern ARGUMENTO_PARAMETRO = Pattern.compile("#(\\w+)");
	/** Argumento admitido dentro de una llamada a {@code @acceso}: un literal de texto (la operación). */
	private static final Pattern ARGUMENTO_LITERAL = Pattern.compile("'[^']*'");
	/** Valor de ejemplo, no nulo, para el parámetro que se pasa a {@code @acceso} (como el "1" de las rutas). */
	private static final Map<Class<?>, Object> VALORES_DE_EJEMPLO = Map.of(Long.class, 1L, long.class, 1L,
			Integer.class, 1, int.class, 1, String.class, "1");
	private static final ParameterNameDiscoverer NOMBRES_DE_PARAMETROS = new DefaultSecurityParameterNameDiscoverer();

	/**
	 * Salvaguarda: el arnés invoca la seguridad de método con una invocación simulada, sin objeto devuelto y con
	 * argumentos nulos salvo los que se pasan a {@code @acceso} (ver {@link #argumentosDeEjemplo}). Solo se admiten
	 * expresiones de roles y llamadas {@code @acceso.<método>(...)} cuyos argumentos son {@code #parámetro} del
	 * método o literales {@code 'TEXTO'}. Cualquier otro bean, un {@code #x} fuera de esas llamadas,
	 * {@code returnObject} o {@code filterObject} daría una decisión falsa: se lanza una excepción que nombra el
	 * endpoint, en vez de calcular nada.
	 */
	static void comprobarExpresionesSoportadas(String endpoint, Method metodo, Class<?> claseObjetivo) {
		parametrosDeAcceso(endpoint, metodo, claseObjetivo);
	}

	/**
	 * Argumentos de la invocación simulada: los parámetros que una expresión pasa a {@code @acceso} reciben un valor
	 * de ejemplo no nulo (con un id nulo el bean deniega y daría un DENIEGA falso); el resto, {@code null}. Como el
	 * puerto de datos está sustituido por {@link RegistradorConsultaAcceso}, el valor concreto no importa.
	 */
	static Object[] argumentosDeEjemplo(Method metodo, Class<?> claseObjetivo) {
		String endpoint = claseObjetivo.getSimpleName() + "#" + metodo.getName();
		Set<String> usados = parametrosDeAcceso(endpoint, metodo, claseObjetivo);
		Object[] argumentos = new Object[metodo.getParameterCount()];
		if (usados.isEmpty()) {
			return argumentos;
		}
		String[] nombres = nombresDeParametros(metodo);
		Class<?>[] tipos = metodo.getParameterTypes();
		for (int i = 0; i < argumentos.length; i++) {
			if (!usados.contains(nombres[i])) {
				continue;
			}
			Object valor = VALORES_DE_EJEMPLO.get(tipos[i]);
			if (valor == null) {
				throw new IllegalStateException("El arnés de la matriz no tiene valor de ejemplo para el parámetro #"
						+ nombres[i] + " (" + tipos[i].getName() + ") que " + endpoint + " pasa a @acceso. Hay que"
						+ " ampliar VALORES_DE_EJEMPLO en EvaluadorAutorizacion.");
			}
			argumentos[i] = valor;
		}
		return argumentos;
	}

	/**
	 * Valida las expresiones de seguridad del método y de su clase y devuelve los nombres de los parámetros que se
	 * pasan a {@code @acceso}. Lanza si una expresión no está soportada (ver {@link #comprobarExpresionesSoportadas}).
	 */
	private static Set<String> parametrosDeAcceso(String endpoint, Method metodo, Class<?> claseObjetivo) {
		Set<String> usados = new LinkedHashSet<>();
		for (Class<? extends Annotation> tipo : ANOTACIONES_DE_METODO) {
			for (AnnotatedElement elemento : List.<AnnotatedElement>of(metodo, claseObjetivo)) {
				Annotation anotacion = AnnotatedElementUtils.findMergedAnnotation(elemento, tipo);
				if (anotacion == null) {
					continue;
				}
				Object valor = AnnotationUtils.getValue(anotacion);
				String expresion = valor == null ? "" : valor.toString();
				String sinAcceso = expresion;
				Matcher llamada = LLAMADA_ACCESO.matcher(expresion);
				while (llamada.find()) {
					String argumentos = llamada.group(1).trim();
					if (!argumentos.isEmpty()) {
						for (String argumento : argumentos.split(",")) {
							usados.addAll(argumentoDeAcceso(endpoint, metodo, claseObjetivo, tipo, expresion,
									argumento.trim()));
						}
					}
					sinAcceso = sinAcceso.replace(llamada.group(), "true");
				}
				if (sinAcceso.contains("#") || sinAcceso.contains("@") || sinAcceso.contains("returnObject")
						|| sinAcceso.contains("filterObject")) {
					throw noSoportada(endpoint, metodo, claseObjetivo, tipo, expresion,
							"usa argumentos (#) fuera de @acceso, otros beans (@) o el objeto devuelto");
				}
			}
		}
		return usados;
	}

	/** Un argumento de una llamada a {@code @acceso}: {@code #parámetro} existente o literal; si no, lanza. */
	private static Set<String> argumentoDeAcceso(String endpoint, Method metodo, Class<?> claseObjetivo,
			Class<? extends Annotation> tipo, String expresion, String argumento) {
		if (ARGUMENTO_LITERAL.matcher(argumento).matches()) {
			return Set.of();
		}
		Matcher parametro = ARGUMENTO_PARAMETRO.matcher(argumento);
		if (!parametro.matches()) {
			throw noSoportada(endpoint, metodo, claseObjetivo, tipo, expresion,
					"pasa a @acceso el argumento '" + argumento + "', que no es #parámetro ni literal 'TEXTO'");
		}
		String nombre = parametro.group(1);
		if (!Arrays.asList(nombresDeParametros(metodo)).contains(nombre)) {
			throw noSoportada(endpoint, metodo, claseObjetivo, tipo, expresion,
					"pasa a @acceso #" + nombre + ", que no es un parámetro del método (en producción llegaría nulo)");
		}
		return Set.of(nombre);
	}

	/** Los mismos nombres que usa la seguridad de método de Spring para las variables {@code #x}. */
	private static String[] nombresDeParametros(Method metodo) {
		String[] nombres = NOMBRES_DE_PARAMETROS.getParameterNames(metodo);
		if (nombres == null) {
			throw new IllegalStateException("Sin nombres de parámetros para " + metodo + ": ¿se compiló sin -parameters?");
		}
		return nombres;
	}

	private static IllegalStateException noSoportada(String endpoint, Method metodo, Class<?> claseObjetivo,
			Class<? extends Annotation> tipo, String expresion, String motivo) {
		return new IllegalStateException("El arnés de la matriz no sabe evaluar " + endpoint + " ("
				+ claseObjetivo.getSimpleName() + "#" + metodo.getName() + "): @" + tipo.getSimpleName() + "(\""
				+ expresion + "\") " + motivo + ". Hay que ampliar EvaluadorAutorizacion para pasar argumentos/objetos"
				+ " de ejemplo antes de declarar su política; no se calcula para no dar un PERMITE/DENIEGA falso.");
	}

	Resultado evaluar(String metodoHttp, String rutaConcreta, List<HandlerMethod> manejadores) {
		for (HandlerMethod manejador : manejadores) {
			comprobarExpresionesSoportadas(metodoHttp + " " + rutaConcreta, manejador.getMethod(),
					ClassUtils.getUserClass(manejador.getBeanType()));
		}
		Map<RolMatriz, Decision> combinada = new EnumMap<>(RolMatriz.class);
		Map<RolMatriz, Decision> url = new EnumMap<>(RolMatriz.class);
		Map<RolMatriz, Decision> metodo = new EnumMap<>(RolMatriz.class);
		for (RolMatriz rol : RolMatriz.values()) {
			Authentication autenticacion = rol.autenticacion();
			Decision decisionUrl = decisionUrl(metodoHttp, rutaConcreta, autenticacion);
			Decision decisionMetodo = Decision.PERMITE;
			for (HandlerMethod manejador : manejadores) {
				decisionMetodo = masRestrictiva(decisionMetodo, decisionMetodo(manejador, autenticacion));
			}
			url.put(rol, decisionUrl);
			metodo.put(rol, decisionMetodo);
			combinada.put(rol, masRestrictiva(decisionUrl, decisionMetodo));
		}
		return new Resultado(combinada, url, metodo);
	}

	/** Combinación de dos capas o manejadores: DENIEGA gana; si no, ALCANCE gana a PERMITE. */
	private static Decision masRestrictiva(Decision a, Decision b) {
		if (a == Decision.DENIEGA || b == Decision.DENIEGA) {
			return Decision.DENIEGA;
		}
		if (a == Decision.ALCANCE || b == Decision.ALCANCE) {
			return Decision.ALCANCE;
		}
		return Decision.PERMITE;
	}

	/** Capa URL: el AuthorizationManager real de la cadena que atiende la petición. */
	Decision decisionUrl(String metodoHttp, String rutaConcreta, Authentication autenticacion) {
		MockHttpServletRequest peticion = MockMvcRequestBuilders
				.request(HttpMethod.valueOf(metodoHttp), URI.create(rutaConcreta))
				.buildRequest(servletContext);
		SecurityFilterChain cadena = cadenas.stream()
				.filter(c -> c.matches(peticion))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Ninguna cadena atiende " + metodoHttp + " " + rutaConcreta));
		AuthorizationManager<jakarta.servlet.http.HttpServletRequest> gestor = null;
		for (Filter filtro : cadena.getFilters()) {
			if (filtro instanceof AuthorizationFilter autorizacion) {
				gestor = autorizacion.getAuthorizationManager();
			}
		}
		if (gestor == null) {
			// Sin AuthorizationFilter la cadena no restringe por URL.
			return Decision.PERMITE;
		}
		AuthorizationResult resultado = gestor.authorize(() -> autenticacion, peticion);
		// AuthorizationFilter solo deniega con un resultado no nulo y no concedido.
		return resultado != null && !resultado.isGranted() ? Decision.DENIEGA : Decision.PERMITE;
	}

	/** Capa método: la cadena real de AuthorizationAdvisor del proxy, sin llegar a ejecutar el método. */
	Decision decisionMetodo(HandlerMethod manejador, Authentication autenticacion) {
		Object bean = manejador.getBean() instanceof String nombre ? contexto.getBean(nombre) : manejador.getBean();
		Method metodo = manejador.getMethod();
		Class<?> claseObjetivo = ClassUtils.getUserClass(manejador.getBeanType());
		Object objetivo = bean;
		List<MethodInterceptor> cadena = new ArrayList<>();
		if (bean instanceof Advised aconsejado) {
			try {
				objetivo = aconsejado.getTargetSource().getTarget();
			} catch (Exception e) {
				throw new IllegalStateException("No se pudo obtener el objetivo del proxy de " + claseObjetivo, e);
			}
			for (Advisor advisor : aconsejado.getAdvisors()) {
				// Spring Security registra cada AuthorizationAdvisor envuelto (MethodSecurityAdvisorRegistrar.AdvisorWrapper,
				// que delega en él): se reconoce por el propio advisor o por su consejo.
				if (advisor instanceof PointcutAdvisor conPuntoDeCorte
						&& (advisor instanceof AuthorizationAdvisor || advisor.getAdvice() instanceof AuthorizationAdvisor)
						&& aplica(conPuntoDeCorte, metodo, claseObjetivo)) {
					cadena.add(advisor instanceof MethodInterceptor interceptor
							? interceptor
							: (MethodInterceptor) advisor.getAdvice());
				}
			}
		}
		boolean anotado = AnnotatedElementUtils.hasAnnotation(metodo, PreAuthorize.class)
				|| AnnotatedElementUtils.hasAnnotation(claseObjetivo, PreAuthorize.class);
		if (anotado && cadena.isEmpty()) {
			throw new IllegalStateException("El arnés no encuentra la seguridad de método de " + claseObjetivo.getSimpleName()
					+ "#" + metodo.getName() + " aunque tiene @PreAuthorize: el arnés ya no es fiel (bean "
					+ bean.getClass().getName() + ", advisors "
					+ (bean instanceof Advised a ? java.util.Arrays.stream(a.getAdvisors())
							.map(x -> x.getClass().getName()).toList() : "ninguno") + ")");
		}
		if (cadena.isEmpty()) {
			return Decision.PERMITE;
		}

		SecurityContextHolderStrategy estrategia = SecurityContextHolder.getContextHolderStrategy();
		SecurityContext anterior = estrategia.getContext();
		SecurityContext nuevo = estrategia.createEmptyContext();
		nuevo.setAuthentication(autenticacion);
		estrategia.setContext(nuevo);
		InvocacionSimulada invocacion = new InvocacionSimulada(metodo, objetivo, cadena,
				argumentosDeEjemplo(metodo, claseObjetivo));
		RegistradorConsultaAcceso.reiniciar();
		try {
			invocacion.proceed();
			if (!invocacion.alcanzado) {
				throw new IllegalStateException("La cadena de seguridad de " + metodo + " terminó sin conceder ni denegar");
			}
			// Concedido tras consultar el puerto de @acceso: depende de que el recurso esté en el alcance de la sesión.
			return RegistradorConsultaAcceso.consultado() ? Decision.ALCANCE : Decision.PERMITE;
		} catch (AccessDeniedException denegado) {
			return Decision.DENIEGA;
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		} finally {
			RegistradorConsultaAcceso.reiniciar();
			estrategia.setContext(anterior);
		}
	}

	private static boolean aplica(PointcutAdvisor advisor, Method metodo, Class<?> claseObjetivo) {
		if (!advisor.getPointcut().getClassFilter().matches(claseObjetivo)) {
			return false;
		}
		MethodMatcher matcher = advisor.getPointcut().getMethodMatcher();
		return MethodMatchers.matches(matcher, AopUtils.getMostSpecificMethod(metodo, claseObjetivo), claseObjetivo, false);
	}

	/**
	 * Invocación que recorre los interceptores de seguridad y, al final, NO llama al método real: solo marca que
	 * la seguridad lo habría permitido. Argumentos de {@link #argumentosDeEjemplo}: no nulos solo los que se pasan
	 * a {@code @acceso}; {@link #comprobarExpresionesSoportadas} impide llegar aquí con otras expresiones que
	 * dependan de argumentos o de beans.
	 */
	private static final class InvocacionSimulada implements MethodInvocation {
		private final Method metodo;
		private final Object objetivo;
		private final List<MethodInterceptor> cadena;
		private final Object[] argumentos;
		private int siguiente;
		private boolean alcanzado;

		InvocacionSimulada(Method metodo, Object objetivo, List<MethodInterceptor> cadena, Object[] argumentos) {
			this.metodo = metodo;
			this.objetivo = objetivo;
			this.cadena = cadena;
			this.argumentos = argumentos;
		}

		@Override
		public Object proceed() throws Throwable {
			if (siguiente < cadena.size()) {
				return cadena.get(siguiente++).invoke(this);
			}
			alcanzado = true;
			return null;
		}

		@Override
		public Method getMethod() {
			return metodo;
		}

		@Override
		public Object[] getArguments() {
			return argumentos;
		}

		@Override
		public Object getThis() {
			return objetivo;
		}

		@Override
		public AccessibleObject getStaticPart() {
			return metodo;
		}
	}
}
