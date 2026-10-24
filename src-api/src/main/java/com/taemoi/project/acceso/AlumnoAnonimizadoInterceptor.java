package com.taemoi.project.acceso;

import java.util.Map;
import java.util.Set;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.taemoi.project.controllers.AlumnoController;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.repositories.AlumnoRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Pieza 8b-3l bis (decisión 287): un alumno eliminado anonimizando es de solo lectura. Toda petición que escribe
 * (POST, PUT, PATCH, DELETE) sobre un alumno de la escuela activa, identificado por la variable de ruta
 * {@code alumnoId} (o {@code id} en {@link AlumnoController}) o por el parámetro {@code alumnoId}, recibe 409
 * {@link Alumno#ALUMNO_ANONIMIZADO_NO_SE_MODIFICA} antes de llegar al controlador: su ficha, deportes, grupos, turnos,
 * documentos, foto, tutores, salud, consentimientos, convocatorias y cargos nuevos. Las lecturas siguen igual.
 *
 * <p>Se permite repetir {@code POST /api/alumnos/{id}/anonimizar} (no hace nada). Las operaciones sobre un cargo o un
 * cobro ya existentes van por su propio id, no por el del alumno, y no se tocan aquí (reactivar, borrar o editar un
 * cargo suyo se rechaza en {@code ProductoAlumnoServiceImpl}). Una petición sin sesión no se comprueba. Las escrituras internas sin ruta
 * propia (preinscripción, generaciones masivas) se comprueban en su servicio.
 */
@Component
public class AlumnoAnonimizadoInterceptor implements HandlerInterceptor {

	private static final Set<String> ESCRITURAS = Set.of("POST", "PUT", "PATCH", "DELETE");

	/** Métodos permitidos sobre un alumno anonimizado (ver la clase). */
	private static final Set<String> PERMITIDOS = Set.of(AlumnoController.class.getName() + "#anonimizarAlumno");

	private final AlumnoRepository alumnos;

	public AlumnoAnonimizadoInterceptor(AlumnoRepository alumnos) {
		this.alumnos = alumnos;
	}

	@Override
	public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull Object handler) {
		// Sin sesión (petición anónima, en la escuela por defecto) no se mira: no delata si un id está anonimizado. Las
		// rutas protegidas ya responden 401 antes; las públicas no escriben sobre un alumno por su id.
		if (!ESCRITURAS.contains(request.getMethod()) || !(handler instanceof HandlerMethod metodo)
				|| !autenticado()) {
			return true;
		}
		if (PERMITIDOS.contains(metodo.getBeanType().getName() + "#" + metodo.getMethod().getName())) {
			return true;
		}
		Long alumnoId = alumnoDe(request, metodo);
		if (alumnoId != null && alumnos.existsByIdAndAnonimizadoEnIsNotNull(alumnoId)) {
			// GlobalExceptionHandler: 409 con {"mensaje": ...}.
			throw new IllegalStateException(Alumno.ALUMNO_ANONIMIZADO_NO_SE_MODIFICA);
		}
		return true;
	}

	private static boolean autenticado() {
		Authentication sesion = SecurityContextHolder.getContext().getAuthentication();
		return sesion != null && sesion.isAuthenticated() && !(sesion instanceof AnonymousAuthenticationToken);
	}

	private static Long alumnoDe(HttpServletRequest request, HandlerMethod metodo) {
		@SuppressWarnings("unchecked")
		Map<String, String> variables = (Map<String, String>) request
				.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		String valor = null;
		if (variables != null) {
			valor = variables.get("alumnoId");
			if (valor == null && AlumnoController.class.isAssignableFrom(metodo.getBeanType())) {
				valor = variables.get("id");
			}
		}
		if (valor == null) {
			valor = request.getParameter("alumnoId");
		}
		if (valor == null) {
			return null;
		}
		try {
			return Long.valueOf(valor.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
