package com.taemoi.project.organizacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.taemoi.project.archivos.RutasArchivos;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AccesoFamiliaService;
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
 * petición; si solo es familia, además algún alumno accesible en esa escuela ({@link AccesoFamiliaService}).</li>
 * </ol>
 * Administrador de plataforma ({@code usuario.es_admin_plataforma}, fase3-sedes-permisos.md §4): la sesión lleva
 * además {@code ROLE_ADMIN_PLATAFORMA} en cualquier escuela, sumada a sus autoridades de membresía. Si en la
 * escuela del token no tiene membresía válida, solo se acepta en la organización por defecto y SOLO con esa
 * autoridad (ninguna de escuela); en cualquier otra escuela se rechaza como a cualquiera.
 * Si algo falla, la petición sigue como anónima (Spring Security responde 401 donde haga falta).
 *
 * <p>Se ejecuta antes de conocer la organización: {@link OrganizacionRequestFilter} la invoca con la
 * organización por defecto fijada, y aquí solo se consultan tablas globales ({@code usuario},
 * {@code membresia}, {@code organizacion}) y la consulta nativa del acceso de familia, filtrada explícitamente.
 */
@Component
public class IdentificadorSesionJwt implements IdentificadorSesion {

	private static final Logger logger = LoggerFactory.getLogger(IdentificadorSesionJwt.class);

	private final JwtService jwtService;
	private final UsuarioRepository usuarios;
	private final MembresiaService membresias;
	private final AccesoFamiliaService accesoFamilia;
	private final ResolutorOrganizacion resolutor;

	public IdentificadorSesionJwt(JwtService jwtService, UsuarioRepository usuarios, MembresiaService membresias,
			AccesoFamiliaService accesoFamilia, ResolutorOrganizacion resolutor) {
		this.jwtService = jwtService;
		this.usuarios = usuarios;
		this.membresias = membresias;
		this.accesoFamilia = accesoFamilia;
		this.resolutor = resolutor;
	}

	@Override
	public Optional<SesionVerificada> identificar(HttpServletRequest request) {
		String path = request.getRequestURI();
		// Imágenes públicas: nunca se autentican (igual que antes en JwtAuthenticationFilter).
		if (esImagenPublicaSinSesion(path)) {
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

	/**
	 * Solo se omite la sesión en imágenes claramente públicas: sin ningún segmento {@code alumnos} (tampoco
	 * {@code org-<id>/alumnos}, que necesita la escuela de la sesión) y sin nada codificado. Ante la duda se
	 * verifica el token: nunca se pierde la escuela activa de una petición privada.
	 */
	static boolean esImagenPublicaSinSesion(String path) {
		return path != null && path.startsWith("/imagenes/") && path.indexOf('%') < 0
				&& RutasArchivos.esImagenPublica(path.substring("/imagenes/".length()));
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

		boolean plataforma = usuario.isEsAdminPlataforma();
		Set<Roles> autoridades = membresias.autoridadesEn(usuario.getId(), organizacionId);
		if (autoridades.isEmpty()) {
			if (plataforma && organizacionId.equals(resolutor.getOrganizacionPorDefecto())) {
				return Optional.of(soloPlataforma(usuario, organizacionId, token));
			}
			logger.info(">>> JWT rechazado para {}: sin membresía activa en la organización {}",
					usuario.getEmail(), organizacionId);
			return Optional.empty();
		}
		if (RolMembresia.requiereAlumnoActivo(autoridades)
				&& !accesoFamilia.tieneAlgunoAccesible(usuario, organizacionId)) {
			if (plataforma && organizacionId.equals(resolutor.getOrganizacionPorDefecto())) {
				return Optional.of(soloPlataforma(usuario, organizacionId, token));
			}
			logger.info(">>> JWT rechazado para usuario sin alumnos activos: {} ({})", usuario.getEmail(), path);
			return Optional.empty();
		}
		return Optional.of(new SesionVerificada(usuario, organizacionId, autoridades, token, plataforma));
	}

	/** Sesión de un administrador de plataforma sin membresía válida: ninguna autoridad de escuela. */
	private static SesionVerificada soloPlataforma(Usuario usuario, Long organizacionId, TokenJwt token) {
		return new SesionVerificada(usuario, organizacionId, Set.of(), token, true);
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
