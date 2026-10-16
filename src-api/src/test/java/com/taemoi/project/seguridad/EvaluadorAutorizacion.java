package com.taemoi.project.seguridad;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.MethodMatchers;
import org.springframework.context.ApplicationContext;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;

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
 * La decisión combinada es PERMITE solo si ambas capas conceden. El resto de filtros (CSRF, JWT, auditoría) y
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
	}

	/** Decisión por rol de ambas capas, con el detalle de cada una para los mensajes. */
	record Resultado(Map<RolMatriz, Decision> combinada, Map<RolMatriz, Decision> url, Map<RolMatriz, Decision> metodo) {
	}

	private static final List<Class<? extends Annotation>> ANOTACIONES_DE_METODO = List.of(PreAuthorize.class,
			PostAuthorize.class, PreFilter.class, PostFilter.class);

	/**
	 * Salvaguarda: el arnés invoca la seguridad de método con argumentos nulos y sin objeto devuelto, así que una
	 * expresión que use argumentos ({@code #id}) o beans ({@code @acceso…}) daría una decisión falsa. En ese caso
	 * se lanza una excepción que nombra el endpoint, en vez de calcular nada.
	 */
	static void comprobarExpresionesSoportadas(String endpoint, Method metodo, Class<?> claseObjetivo) {
		for (Class<? extends Annotation> tipo : ANOTACIONES_DE_METODO) {
			for (AnnotatedElement elemento : List.<AnnotatedElement>of(metodo, claseObjetivo)) {
				Annotation anotacion = AnnotatedElementUtils.findMergedAnnotation(elemento, tipo);
				if (anotacion == null) {
					continue;
				}
				Object valor = AnnotationUtils.getValue(anotacion);
				String expresion = valor == null ? "" : valor.toString();
				if (expresion.contains("#") || expresion.contains("@") || expresion.contains("returnObject")
						|| expresion.contains("filterObject")) {
					throw new IllegalStateException("El arnés de la matriz no sabe evaluar " + endpoint + " ("
							+ claseObjetivo.getSimpleName() + "#" + metodo.getName() + "): @" + tipo.getSimpleName()
							+ "(\"" + expresion + "\") usa argumentos (#), beans (@) o el objeto devuelto. Hay que ampliar"
							+ " EvaluadorAutorizacion para pasar argumentos/objetos de ejemplo antes de declarar su"
							+ " política; no se calcula para no dar un PERMITE/DENIEGA falso.");
				}
			}
		}
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
				if (decisionMetodo(manejador, autenticacion) == Decision.DENIEGA) {
					decisionMetodo = Decision.DENIEGA;
				}
			}
			url.put(rol, decisionUrl);
			metodo.put(rol, decisionMetodo);
			combinada.put(rol, decisionUrl == Decision.PERMITE && decisionMetodo == Decision.PERMITE
					? Decision.PERMITE
					: Decision.DENIEGA);
		}
		return new Resultado(combinada, url, metodo);
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
		InvocacionSimulada invocacion = new InvocacionSimulada(metodo, objetivo, cadena);
		try {
			invocacion.proceed();
			if (!invocacion.alcanzado) {
				throw new IllegalStateException("La cadena de seguridad de " + metodo + " terminó sin conceder ni denegar");
			}
			return Decision.PERMITE;
		} catch (AccessDeniedException denegado) {
			return Decision.DENIEGA;
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		} finally {
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
	 * la seguridad lo habría permitido. Argumentos nulos: {@link #comprobarExpresionesSoportadas} impide llegar
	 * aquí con expresiones que dependan de argumentos o de beans.
	 */
	private static final class InvocacionSimulada implements MethodInvocation {
		private final Method metodo;
		private final Object objetivo;
		private final List<MethodInterceptor> cadena;
		private final Object[] argumentos;
		private int siguiente;
		private boolean alcanzado;

		InvocacionSimulada(Method metodo, Object objetivo, List<MethodInterceptor> cadena) {
			this.metodo = metodo;
			this.objetivo = objetivo;
			this.cadena = cadena;
			this.argumentos = new Object[metodo.getParameterCount()];
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
