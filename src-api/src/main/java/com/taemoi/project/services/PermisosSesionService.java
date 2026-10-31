package com.taemoi.project.services;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.response.PerfilCuentaDTO;
import com.taemoi.project.dtos.response.PermisosSesionDTO;
import com.taemoi.project.dtos.response.PermisosSesionDTO.Capacidad;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.RelacionUsuarioAlumno;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.entities.UsuarioAlumno;
import com.taemoi.project.organizacion.AlcanceActual;
import com.taemoi.project.organizacion.AlcanceSesion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.plataforma.AutoridadPlataforma;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.repositories.UsuarioAlumnoRepository;

/**
 * Permisos de la sesión para menús y guards del frontend (pieza E8-1, D8-14; {@code GET /api/auth/permisos}). Solo
 * dice qué enseñar: la autorización real sigue en la cadena de seguridad y en cada servicio.
 *
 * <ul>
 * <li>Roles: los {@link RolMembresia} de las autoridades de la sesión (las membresías activas de la escuela activa,
 * ya calculadas al verificar el JWT; sin consulta), como {@code /api/auth/roles}.</li>
 * <li>Sedes visibles: alcance total → todas las de la escuela, también inactivas; {@code ROLE_RESPONSABLE_SEDE} → las
 * de su alcance (sin sedes asignadas, ninguna: decisión 65); resto, ninguna. {@code sede} es {@code @TenantId}: nunca
 * sale una de otra escuela.</li>
 * <li>{@code variasSedes}: más de una sede activa en la escuela (de la lista ya cargada con total; si no, un
 * {@code COUNT}). {@code tieneGrupos}: {@code alcance.grupos()} no vacío.</li>
 * <li>Sesión solo-plataforma (ninguna autoridad de escuela): roles, sedes y capacidades vacíos y sin consultar la
 * escuela.</li>
 * <li>{@code nombreVisible} (pieza 8b-T): «Nombre A.» de la cuenta de la sesión, o su nombre preferido (decisión 322),
 * sin consulta ni registro. {@code nombreSaludo} (decisión 322): el preferido entero o la primera palabra del nombre.
 * Los lee de la cuenta que {@code IdentificadorSesionJwt} carga de la base de datos en cada petición, sin caché: un
 * cambio se ve en la petición siguiente.</li>
 * <li>{@code cumpleanos} (pieza 8b-M2 y decisión 322): hoy (Europe/Madrid) es el cumpleaños de la ficha de alumno con
 * relación {@code PROPIO} de la cuenta, en la organización de la sesión; sin ficha, anonimizada o sin fecha, decide el
 * día y mes guardados en la cuenta. Solo el booleano.</li>
 * </ul>
 * Reutiliza el alcance de la petición ({@link AlcanceActual}): como mucho dos consultas pequeñas de sedes, sin N+1.
 */
@Service
public class PermisosSesionService {

	private final AlcanceActual alcanceActual;
	private final SedeRepository sedes;
	private final UsuarioAlumnoRepository usuarioAlumnos;
	private final AlumnoRepository alumnos;
	private final Clock reloj;

	public PermisosSesionService(AlcanceActual alcanceActual, SedeRepository sedes,
			UsuarioAlumnoRepository usuarioAlumnos, AlumnoRepository alumnos, Clock reloj) {
		this.alcanceActual = alcanceActual;
		this.sedes = sedes;
		this.usuarioAlumnos = usuarioAlumnos;
		this.alumnos = alumnos;
		this.reloj = reloj;
	}

	@Transactional(readOnly = true)
	public PermisosSesionDTO permisos() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		Set<Roles> autoridades = autoridades(autenticacion);
		boolean plataforma = tieneAutoridad(autenticacion, AutoridadPlataforma.ADMIN_PLATAFORMA);
		if (autoridades.isEmpty()) {
			return new PermisosSesionDTO(List.of(), plataforma, List.of(), false, false, List.of(),
					nombreVisible(autenticacion), nombreSaludo(autenticacion), false);
		}
		boolean soloProfesor = RolMembresia.sesionSoloProfesor(autoridades);
		AlcanceSesion alcance = alcanceActual.obtener();
		boolean tieneGrupos = !alcance.grupos().isEmpty();

		List<Sede> visibles;
		long activas;
		if (alcance.total()) {
			visibles = sedes.findAllByOrderByIdAsc();
			activas = visibles.stream().filter(Sede::isActiva).count();
		} else {
			visibles = autoridades.contains(Roles.ROLE_RESPONSABLE_SEDE) && !alcance.sedes().isEmpty()
					? sedes.findByIdInOrderByIdAsc(alcance.sedes())
					: List.of();
			activas = sedes.countByActivaTrue();
		}
		Usuario cuenta = cuentaDeLaSesion(autenticacion);
		return new PermisosSesionDTO(roles(autoridades), plataforma,
				visibles.stream().map(s -> new PermisosSesionDTO.Sede(s.getId(), s.getNombre(), s.isActiva())).toList(),
				activas > 1, tieneGrupos, capacidades(autoridades, tieneGrupos),
				nombreVisible(autenticacion), nombreSaludo(autenticacion),
				cumpleanos(CobroService.usuarioActualId(), TenantContext.get(),
						cuenta == null ? null : cuenta.getCumpleDia(), cuenta == null ? null : cuenta.getCumpleMes()));
	}

	/**
	 * Pieza 8b-M2 y decisión 322: si hoy es el cumpleaños de la cuenta. Con ficha propia válida ({@link #cumpleFicha})
	 * decide solo ella; si no, el día y mes guardados en la cuenta (los dos, o ninguno).
	 */
	boolean cumpleanos(Long usuarioId, Long organizacionId, Integer diaCuenta, Integer mesCuenta) {
		if (usuarioId == null || organizacionId == null) {
			return false;
		}
		LocalDate hoy = LocalDate.now(reloj);
		Optional<PerfilCuentaDTO.DiaMes> ficha = cumpleFicha(usuarioId, organizacionId);
		if (ficha.isPresent()) {
			return esCumpleanos(ficha.get().mes(), ficha.get().dia(), hoy);
		}
		return diaCuenta != null && mesCuenta != null && esCumpleanos(mesCuenta, diaCuenta, hoy);
	}

	/**
	 * Decisión 322: día y mes de la ficha PROPIA de la cuenta en la organización, no anonimizada y con fecha de
	 * nacimiento; con varias, la de menor id. Se para en la primera que vale. {@code usuario_alumno} no tiene tenant
	 * automático: se filtra por organización a mano; el alumno sí (@TenantId).
	 */
	public Optional<PerfilCuentaDTO.DiaMes> cumpleFicha(Long usuarioId, Long organizacionId) {
		if (usuarioId == null || organizacionId == null) {
			return Optional.empty();
		}
		return usuarioAlumnos
				.findByUsuarioIdAndOrganizacionIdAndRelacion(usuarioId, organizacionId, RelacionUsuarioAlumno.PROPIO)
				.stream()
				.map(UsuarioAlumno::getAlumnoId)
				.sorted()
				.map(id -> alumnos.buscarPorIdEnOrganizacionActiva(id).orElse(null))
				.filter(a -> a != null && !a.estaAnonimizado() && a.getFechaNacimiento() != null)
				.findFirst()
				.map(a -> new java.sql.Date(a.getFechaNacimiento().getTime()).toLocalDate())
				.map(fecha -> new PerfilCuentaDTO.DiaMes(fecha.getDayOfMonth(), fecha.getMonthValue()));
	}

	/** Cumple en {@code hoy}; los del 29-feb lo celebran el 28-feb en años no bisiestos. */
	static boolean esCumpleanos(int mes, int dia, LocalDate hoy) {
		int diaDeHoy = mes == Month.FEBRUARY.getValue() && dia == 29 && !hoy.isLeapYear() ? 28 : dia;
		return hoy.getMonthValue() == mes && hoy.getDayOfMonth() == diaDeHoy;
	}

	/**
	 * Capacidades por autoridades (D8-14): MIEMBROS, SEDES y ASIGNAR_PROFESORES con gestión; DAR_GESTION solo el
	 * propietario (D8); MIS_GRUPOS en la sesión solo-profesor y con responsable + profesor sin gestión siempre (371) y,
	 * fuera de ellas, con PROFESOR y grupos (217); MI_CAJA con PROFESOR; PANEL_SEDE con responsable sin gestión (370) y
	 * ELEGIR_VISTA si además es familia (371). En el orden de {@link Capacidad}.
	 */
	static List<Capacidad> capacidades(Set<Roles> autoridades, boolean tieneGrupos) {
		boolean gestion = autoridades.contains(Roles.ROLE_ADMIN) || autoridades.contains(Roles.ROLE_MANAGER);
		boolean profesor = autoridades.contains(Roles.ROLE_PROFESOR);
		boolean panelDeSede = !gestion && autoridades.contains(Roles.ROLE_RESPONSABLE_SEDE);
		Set<Capacidad> resultado = EnumSet.noneOf(Capacidad.class);
		if (gestion) {
			resultado.add(Capacidad.MIEMBROS);
			resultado.add(Capacidad.SEDES);
			resultado.add(Capacidad.ASIGNAR_PROFESORES);
		}
		if (autoridades.contains(Roles.ROLE_ADMIN)) {
			resultado.add(Capacidad.DAR_GESTION);
		}
		if (RolMembresia.sesionSoloProfesor(autoridades) || (profesor && (tieneGrupos || panelDeSede))) {
			resultado.add(Capacidad.MIS_GRUPOS);
		}
		if (profesor) {
			resultado.add(Capacidad.MI_CAJA);
		}
		if (panelDeSede) {
			resultado.add(Capacidad.PANEL_SEDE);
			if (autoridades.contains(Roles.ROLE_USER)) {
				resultado.add(Capacidad.ELEGIR_VISTA);
			}
		}
		return List.copyOf(resultado);
	}

	/** La cuenta de la sesión, la ya cargada al verificar el JWT en esta petición: sin consulta y nunca de una caché. */
	private static Usuario cuentaDeLaSesion(Authentication autenticacion) {
		return autenticacion != null && autenticacion.getPrincipal() instanceof Usuario usuario ? usuario : null;
	}

	/** El nombre visible de la cuenta de la sesión. */
	private static String nombreVisible(Authentication autenticacion) {
		Usuario cuenta = cuentaDeLaSesion(autenticacion);
		return cuenta == null ? null
				: nombreVisible(cuenta.getNombrePreferido(), cuenta.getNombre(), cuenta.getApellidos());
	}

	/** El nombre del saludo de la cuenta de la sesión. */
	private static String nombreSaludo(Authentication autenticacion) {
		Usuario cuenta = cuentaDeLaSesion(autenticacion);
		return cuenta == null ? null : nombreSaludo(cuenta.getNombrePreferido(), cuenta.getNombre());
	}

	/** Decisión 322: con nombre preferido, ese tal cual; si no, «Nombre A.». */
	static String nombreVisible(String preferido, String nombre, String apellidos) {
		return preferido != null ? preferido : nombreVisible(nombre, apellidos);
	}

	/** Decisión 322: el preferido entero; si no, la primera palabra del nombre; sin nombre, {@code null}. */
	static String nombreSaludo(String preferido, String nombre) {
		if (preferido != null) {
			return preferido;
		}
		String limpio = nombre == null ? "" : nombre.trim();
		return limpio.isEmpty() ? null : limpio.split("\\s+")[0];
	}

	/** Partículas que no dan la inicial del apellido («de la Fuente» → «F.»). */
	private static final Set<String> PARTICULAS = Set.of("de", "del", "la", "las", "los", "y", "da", "das", "do", "dos",
			"van", "von");

	/**
	 * Pieza 8b-T (§4): «Nombre A.», el nombre y la inicial del primer apellido (saltando las partículas), para el botón
	 * de cuenta. Sin apellido o si todo son partículas, solo el nombre; sin nombre, {@code null}.
	 */
	static String nombreVisible(String nombre, String apellidos) {
		String limpio = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
		if (limpio.isEmpty()) {
			return null;
		}
		if (apellidos != null) {
			for (String palabra : apellidos.trim().split("\\s+")) {
				if (!palabra.isEmpty() && !PARTICULAS.contains(palabra.toLowerCase(java.util.Locale.ROOT))) {
					int inicial = palabra.codePointAt(0);
					return limpio + " " + new String(Character.toChars(Character.toUpperCase(inicial))) + ".";
				}
			}
		}
		return limpio;
	}

	private static List<String> roles(Set<Roles> autoridades) {
		List<String> roles = new ArrayList<>();
		for (RolMembresia rol : RolMembresia.values()) {
			if (autoridades.contains(rol.autoridad())) {
				roles.add(rol.name());
			}
		}
		return roles;
	}

	/** Autoridades de escuela de la sesión (las de {@link Roles}); la de plataforma no cuenta. */
	private static Set<Roles> autoridades(Authentication autenticacion) {
		Set<Roles> roles = EnumSet.noneOf(Roles.class);
		if (autenticacion == null || autenticacion.getAuthorities() == null) {
			return roles;
		}
		for (GrantedAuthority autoridad : autenticacion.getAuthorities()) {
			for (Roles rol : Roles.values()) {
				if (rol.name().equals(autoridad.getAuthority())) {
					roles.add(rol);
				}
			}
		}
		return roles;
	}

	private static boolean tieneAutoridad(Authentication autenticacion, String nombre) {
		return autenticacion != null && autenticacion.getAuthorities() != null
				&& autenticacion.getAuthorities().stream().anyMatch(a -> nombre.equals(a.getAuthority()));
	}
}
