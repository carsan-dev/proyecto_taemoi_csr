package com.taemoi.project.organizacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.JwtService;
import com.taemoi.project.services.MembresiaService;
import com.taemoi.project.services.TokenJwt;
import com.taemoi.project.utils.EmailUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Punto ÚNICO de verificación del JWT de una petición (cookie {@code jwt}):
 * <ol>
 * <li>firma y caducidad ({@link JwtService#verificar});</li>
 * <li>el usuario existe y está habilitado;</li>
 * <li>organización: el claim {@code org}; en tokens heredados (sin claim) solo se acepta si el usuario
 * tiene membresías activas en exactamente UNA organización;</li>
 * <li>el usuario sigue teniendo membresía activa en esa organización (y esta está activa), en CADA
 * petición; si solo es familia, además algún alumno activo con su email en esa escuela.</li>
 * </ol>
 * Si algo falla, la petición sigue como anónima (Spring Security responde 401 donde haga falta).
 *
 * <p>Se ejecuta antes de conocer la organización: {@link OrganizacionRequestFilter} la invoca con la
 * organización por defecto fijada, y aquí solo se consultan tablas globales ({@code usuario},
 * {@code membresia}, {@code organizacion}) y la consulta nativa de alumnos filtrada explícitamente.
 */
@Component
public class IdentificadorSesionJwt implements IdentificadorSesion {

	private static final Logger logger = LoggerFactory.getLogger(IdentificadorSesionJwt.class);

	private final JwtService jwtService;
	private final UsuarioRepository usuarios;
	private final MembresiaService membresias;
	private final AlumnoRepository alumnos;

	public IdentificadorSesionJwt(JwtService jwtService, UsuarioRepository usuarios, MembresiaService membresias,
			AlumnoRepository alumnos) {
		this.jwtService = jwtService;
		this.usuarios = usuarios;
		this.membresias = membresias;
		this.alumnos = alumnos;
	}

	@Override
	public Optional<SesionVerificada> identificar(HttpServletRequest request) {
		String path = request.getRequestURI();
		// Imágenes públicas: nunca se autentican (igual que antes en JwtAuthenticationFilter).
		if (path != null && path.startsWith("/imagenes/") && !path.startsWith("/imagenes/alumnos/")) {
			return Optional.empty();
		}
		for (String jwt : candidatos(request)) {
			try {
				Optional<SesionVerificada> sesion = verificar(jwt, path);
				if (sesion.isPresent()) {
					return sesion;
				}
			} catch (RuntimeException e) {
				logger.warn(">>> JWT candidato inválido para {}: {}", path, e.getMessage());
			}
		}
		return Optional.empty();
	}

	private Optional<SesionVerificada> verificar(String jwt, String path) {
		TokenJwt token = jwtService.verificar(jwt);
		if (token.email() == null || token.email().isBlank()) {
			return Optional.empty();
		}
		Usuario usuario = usuarios.findByEmailIgnoreCase(EmailUtils.normalizeEmail(token.email())).orElse(null);
		if (usuario == null || !usuario.isEnabled()) {
			return Optional.empty();
		}

		Long organizacionId = token.organizacionId();
		if (organizacionId == null) {
			List<Long> suyas = membresias.organizacionesConMembresiaActiva(usuario.getId());
			if (suyas.size() != 1) {
				logger.info(">>> JWT sin organización rechazado para {}: membresías en {} organizaciones",
						usuario.getEmail(), suyas.size());
				return Optional.empty();
			}
			organizacionId = suyas.get(0);
		}

		Set<Roles> autoridades = membresias.autoridadesEn(usuario.getId(), organizacionId);
		if (autoridades.isEmpty()) {
			logger.info(">>> JWT rechazado para {}: sin membresía activa en la organización {}",
					usuario.getEmail(), organizacionId);
			return Optional.empty();
		}
		if (RolMembresia.requiereAlumnoActivo(autoridades)
				&& alumnos.contarActivosPorEmailEnOrganizacion(usuario.getEmail(), organizacionId) == 0) {
			logger.info(">>> JWT rechazado para usuario sin alumnos activos: {} ({})", usuario.getEmail(), path);
			return Optional.empty();
		}
		return Optional.of(new SesionVerificada(usuario, organizacionId, autoridades, token));
	}

	private static List<String> candidatos(HttpServletRequest request) {
		List<String> resultado = new ArrayList<>();
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if ("jwt".equals(cookie.getName())) {
					String valor = cookie.getValue();
					if (valor != null && !valor.isBlank()) {
						resultado.add(valor.trim());
					}
				}
			}
		}
		return resultado;
	}
}
