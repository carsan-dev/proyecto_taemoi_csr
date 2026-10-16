package com.taemoi.project.config;

import java.util.function.Supplier;

import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.core.Authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Autoriza cada {@code HEAD} como el {@code GET} a la misma ruta.
 *
 * <p>Spring MVC responde {@code HEAD} con el handler de {@code GET}, pero las reglas de URL con
 * {@code HttpMethod.GET} no casan con {@code HEAD}, que caía en {@code anyRequest().authenticated()}: se abría a
 * cualquier sesión lo que con {@code GET} es solo de gestión, y se cerraba lo público. Envolviendo el
 * {@code AuthorizationManager} de {@code authorizeHttpRequests}, las reglas ven {@code GET} en las peticiones
 * {@code HEAD}; solo cambia el método que ve la decisión, no la petición que sigue la cadena. La seguridad de
 * método ({@code @PreAuthorize}) no se toca: ya se aplica al handler de {@code GET}.
 */
final class HeadComoGetAuthorizationManager<T> implements AuthorizationManager<T> {

	private final AuthorizationManager<T> delegado;

	private HeadComoGetAuthorizationManager(AuthorizationManager<T> delegado) {
		this.delegado = delegado;
	}

	/**
	 * Post-procesador para {@code authorizeHttpRequests(...).withObjectPostProcessor(...)}: envuelve el
	 * {@code AuthorizationManager} de las reglas de URL y deja pasar sin tocar cualquier otro objeto.
	 */
	static ObjectPostProcessor<Object> postProcesador() {
		return new ObjectPostProcessor<>() {
			@Override
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public <O> O postProcess(O objeto) {
				if (objeto instanceof AuthorizationManager manager
						&& !(objeto instanceof HeadComoGetAuthorizationManager)) {
					return (O) new HeadComoGetAuthorizationManager<>(manager);
				}
				return objeto;
			}
		};
	}

	@Override
	@Deprecated
	@SuppressWarnings("deprecation")
	public AuthorizationDecision check(Supplier<Authentication> authentication, T objeto) {
		return delegado.check(authentication, comoGet(objeto));
	}

	@Override
	public AuthorizationResult authorize(Supplier<Authentication> authentication, T objeto) {
		return delegado.authorize(authentication, comoGet(objeto));
	}

	@SuppressWarnings("unchecked")
	static <T> T comoGet(T objeto) {
		if (objeto instanceof HttpServletRequest request && HttpMethod.HEAD.matches(request.getMethod())) {
			return (T) new HttpServletRequestWrapper(request) {
				@Override
				public String getMethod() {
					return HttpMethod.GET.name();
				}
			};
		}
		return objeto;
	}
}
