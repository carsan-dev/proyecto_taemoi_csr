package com.taemoi.project.services;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.request.DisenoPanelRequests;
import com.taemoi.project.dtos.response.DisenoPanelDTO;
import com.taemoi.project.entities.ConfiguracionOrganizacion;
import com.taemoi.project.entities.DisenoPanelUsuario;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.ConfiguracionOrganizacionRepository;
import com.taemoi.project.repositories.DisenoPanelUsuarioRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.UsuarioRepository;

/**
 * Interruptor del diseño nuevo del panel por escuela y usuario (pieza 8b-I, fase3-e8b-plan.md §3; decisiones 236, 256 y
 * 258). Todo con la escuela activa de la sesión, nunca entre escuelas: {@link DisenoPanelUsuario} y
 * {@link ConfiguracionOrganizacion} son {@code @TenantId}, y {@code membresia} (que no lo es) se consulta siempre con
 * {@code organizacion_id} explícito.
 *
 * <ul>
 * <li>Regla efectiva ({@link #activoPara}): con fila y ({@code permitido} o el valor por defecto NUEVO), su
 * {@code activo}; si no, el valor por defecto de la escuela ({@value #CLAVE_POR_DEFECTO} = {@value #NUEVO}; sin la
 * clave, VIEJO). Sin filas ni clave, nadie cambia.</li>
 * <li>{@code permitido} es solo el que da el propietario (decisión 256): a la gestión de su escuela (PROPIETARIO o
 * ADMINISTRADOR activa), también a sí mismo; al darlo queda activo, y darlo otra vez no pisa lo que eligió la cuenta;
 * quitarlo no borra su elección (con NUEVO sigue contando; con VIEJO, no). Cuenta sin gestión en la escuela, 400; de otra
 * escuela o inexistente, 404. El gestor no asigna (403, en el controlador).</li>
 * <li>Cada cuenta con permiso, o con el valor por defecto NUEVO, lo enciende o apaga para sí; sin él, 403. Solo escribe
 * {@code activo}: sin fila, la crea con {@code permitido = false}; nunca toca {@code permitido}. Si el propietario vuelve
 * a VIEJO, la cuenta sin permiso vuelve al de siempre.</li>
 * <li>El valor por defecto («Para todo el equipo»), solo el propietario; la pantalla lo esconde hasta R5 (258).</li>
 * <li>Cada escritura correcta deja UNA fila en el registro ({@link RegistroAccionesPanel}, misma transacción), también la
 * que no cambia nada ({@code sinCambios=SI}): por eso las tres rutas están en
 * {@code AuditoriaRequestFilter.EscrituraConRegistroPropio}.</li>
 * </ul>
 * Las escrituras de una cuenta bloquean antes su fila de {@code usuario} ({@code FOR UPDATE}): dos cambios a la vez se
 * ordenan y no chocan con la clave única.
 */
@Service
public class DisenoPanelService {

	public static final String CLAVE_POR_DEFECTO = "panel.diseno_por_defecto";
	static final String NUEVO = "NUEVO";
	static final String VIEJO = "VIEJO";

	static final String NO_MIEMBRO = "La cuenta no es miembro de esta escuela.";
	static final String SIN_GESTION = "Solo el propietario y los gestores de la escuela pueden usar el diseño nuevo.";
	static final String SIN_PERMISO = "No tienes permiso para cambiar el diseño del panel.";
	static final String PERMITIDO_OBLIGATORIO = "Indica si la cuenta tiene permiso.";
	static final String ACTIVO_OBLIGATORIO = "Indica si el diseño nuevo queda encendido.";
	static final String NUEVO_OBLIGATORIO = "Indica el diseño para todo el equipo.";
	static final String A_LA_VEZ = "Otro cambio se guardó a la vez. Vuelve a intentarlo.";

	private static final List<RolMembresia> GESTION = List.of(RolMembresia.PROPIETARIO, RolMembresia.ADMINISTRADOR);
	private static final String RUTA = "/api/panel/diseno-nuevo";
	private static final String RUTA_MI_DISENO = "/api/panel/mi-diseno";

	private final DisenoPanelUsuarioRepository filas;
	private final ConfiguracionOrganizacionRepository configuracion;
	private final MembresiaRepository membresias;
	private final MembresiaService membresiaService;
	private final UsuarioRepository usuarios;
	private final RegistroAccionesPanel registro;

	public DisenoPanelService(DisenoPanelUsuarioRepository filas, ConfiguracionOrganizacionRepository configuracion,
			MembresiaRepository membresias, MembresiaService membresiaService, UsuarioRepository usuarios,
			RegistroAccionesPanel registro) {
		this.filas = filas;
		this.configuracion = configuracion;
		this.membresias = membresias;
		this.membresiaService = membresiaService;
		this.usuarios = usuarios;
		this.registro = registro;
	}

	// ------------------------------------------------------------------ lecturas

	/** Regla efectiva para la cuenta en la escuela activa; sin cuenta, apagado. */
	@Transactional(readOnly = true)
	public boolean activoPara(Long usuarioId) {
		if (usuarioId == null) {
			return false;
		}
		return regla(filas.findByUsuarioId(usuarioId), porDefectoNuevo());
	}

	/**
	 * {@code disenoNuevo} de la sesión ({@code GET /api/auth/permisos}): si lo ve y si puede cambiarlo para sí. Como
	 * mucho dos consultas pequeñas (su fila y la clave de la escuela).
	 */
	@Transactional(readOnly = true)
	public DisenoPanelDTO.Propio estadoPropio(Long usuarioId) {
		if (usuarioId == null) {
			return DisenoPanelDTO.Propio.APAGADO;
		}
		Optional<DisenoPanelUsuario> fila = filas.findByUsuarioId(usuarioId);
		boolean porDefecto = porDefectoNuevo();
		return new DisenoPanelDTO.Propio(regla(fila, porDefecto), puedeCambiarPropio(fila, porDefecto));
	}

	/** El valor por defecto y el interruptor de la gestión activa de la escuela, por id. Tres consultas. */
	@Transactional(readOnly = true)
	public DisenoPanelDTO listar() {
		Long organizacion = organizacionActiva();
		List<Long> gestion = membresias.findUsuariosConRolActivo(organizacion, GESTION);
		Map<Long, DisenoPanelUsuario> porCuenta = gestion.isEmpty() ? Map.of()
				: filas.findByUsuarioIdIn(gestion).stream()
						.collect(Collectors.toMap(DisenoPanelUsuario::getUsuarioId, Function.identity()));
		boolean porDefecto = porDefectoNuevo();
		List<DisenoPanelDTO.Usuario> usuariosDto = gestion.stream().map(id -> {
			Optional<DisenoPanelUsuario> fila = Optional.ofNullable(porCuenta.get(id));
			return new DisenoPanelDTO.Usuario(id, fila.map(DisenoPanelUsuario::isPermitido).orElse(false),
					regla(fila, porDefecto));
		}).toList();
		return new DisenoPanelDTO(porDefecto, usuariosDto);
	}

	// ------------------------------------------------------------------ escrituras

	/**
	 * El propietario da o quita el permiso a una cuenta de gestión de la escuela.
	 *
	 * @throws IllegalArgumentException (400) sin {@code permitido} o la cuenta no tiene gestión en la escuela
	 * @throws NoSuchElementException   (404) la cuenta es de otra escuela o no existe
	 * @throws IllegalStateException    (409) saltó la clave única por otro cambio a la vez
	 */
	@Transactional
	public DisenoPanelDTO.Usuario cambiarPermiso(Long usuarioId, DisenoPanelRequests.Permiso solicitud) {
		if (solicitud == null || solicitud.permitido() == null) {
			throw new IllegalArgumentException(PERMITIDO_OBLIGATORIO);
		}
		boolean permitir = solicitud.permitido();
		Long organizacion = organizacionActiva();
		Long actor = usuarioDeLaSesion();
		exigirMiembro(usuarioId, organizacion);
		if (!tieneGestion(usuarioId, organizacion)) {
			throw new IllegalArgumentException(SIN_GESTION);
		}
		Optional<DisenoPanelUsuario> actual = filas.findByUsuarioId(usuarioId);
		boolean yaPermitido = actual.map(DisenoPanelUsuario::isPermitido).orElse(false);
		boolean sinCambios = permitir == yaPermitido;
		if (!sinCambios) {
			DisenoPanelUsuario fila = actual.orElseGet(() -> new DisenoPanelUsuario(usuarioId));
			fila.setPermitido(permitir);
			// Al darlo queda activo (§3). Al quitarlo no se toca su elección: con VIEJO deja de contar.
			if (permitir) {
				fila.setActivo(true);
			}
			guardar(fila, actor);
		}
		registro.registrar(permitir ? RegistroAccionesPanel.Accion.DISENO_PERMITIR
				: RegistroAccionesPanel.Accion.DISENO_QUITAR, RUTA + "/" + usuarioId, valores(usuarioId, sinCambios));
		return new DisenoPanelDTO.Usuario(usuarioId, permitir, activoPara(usuarioId));
	}

	/**
	 * La cuenta de la sesión enciende o apaga el diseño nuevo para sí.
	 *
	 * @throws IllegalArgumentException (400) sin {@code activo}
	 * @throws SecurityException        (403) sin gestión en la escuela, o sin permiso y con el por defecto VIEJO
	 * @throws IllegalStateException    (409) saltó la clave única por otro cambio a la vez
	 */
	@Transactional
	public DisenoPanelDTO.Propio cambiarMiDiseno(DisenoPanelRequests.MiDiseno solicitud) {
		if (solicitud == null || solicitud.activo() == null) {
			throw new IllegalArgumentException(ACTIVO_OBLIGATORIO);
		}
		boolean activo = solicitud.activo();
		Long organizacion = organizacionActiva();
		Long usuario = usuarioDeLaSesion();
		usuarios.bloquearPorId(usuario).orElseThrow(() -> new AccessDeniedException("Acceso denegado."));
		if (!tieneGestion(usuario, organizacion)) {
			throw new SecurityException(SIN_PERMISO);
		}
		Optional<DisenoPanelUsuario> actual = filas.findByUsuarioId(usuario);
		boolean porDefecto = porDefectoNuevo();
		if (!puedeCambiarPropio(actual, porDefecto)) {
			throw new SecurityException(SIN_PERMISO);
		}
		boolean sinCambios = regla(actual, porDefecto) == activo;
		if (!sinCambios) {
			// Solo su elección: el permiso es del propietario (decisión 256). Una fila nueva nace sin permiso.
			DisenoPanelUsuario fila = actual.orElseGet(() -> new DisenoPanelUsuario(usuario));
			fila.setActivo(activo);
			guardar(fila, usuario);
		}
		registro.registrar(activo ? RegistroAccionesPanel.Accion.DISENO_ACTIVAR
				: RegistroAccionesPanel.Accion.DISENO_DESACTIVAR, RUTA_MI_DISENO, valores(usuario, sinCambios));
		return new DisenoPanelDTO.Propio(activo, puedeCambiarPropio(actual, porDefecto));
	}

	/**
	 * El propietario fija el diseño para todo el equipo de la escuela (clave {@value #CLAVE_POR_DEFECTO}).
	 *
	 * @throws IllegalArgumentException (400) sin {@code nuevo}
	 * @throws IllegalStateException    (409) otro cambio creó la clave a la vez
	 */
	@Transactional
	public DisenoPanelDTO.PorDefecto cambiarPorDefecto(DisenoPanelRequests.PorDefecto solicitud) {
		if (solicitud == null || solicitud.nuevo() == null) {
			throw new IllegalArgumentException(NUEVO_OBLIGATORIO);
		}
		boolean nuevo = solicitud.nuevo();
		organizacionActiva();
		Optional<ConfiguracionOrganizacion> actual = configuracion.findByClave(CLAVE_POR_DEFECTO);
		boolean sinCambios = esNuevo(actual) == nuevo;
		if (!sinCambios) {
			ConfiguracionOrganizacion clave = actual
					.orElseGet(() -> new ConfiguracionOrganizacion(CLAVE_POR_DEFECTO, VIEJO));
			clave.setValor(nuevo ? NUEVO : VIEJO);
			try {
				configuracion.saveAndFlush(clave);
			} catch (DataIntegrityViolationException error) {
				throw new IllegalStateException(A_LA_VEZ);
			}
		}
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("nuevo", nuevo);
		if (sinCambios) {
			valores.put("sinCambios", Boolean.TRUE);
		}
		registro.registrar(RegistroAccionesPanel.Accion.DISENO_POR_DEFECTO, RUTA + "/por-defecto", valores);
		return new DisenoPanelDTO.PorDefecto(nuevo);
	}

	// ------------------------------------------------------------------ reglas

	/** Con fila y (permitido o el por defecto NUEVO), su activo; si no, el valor por defecto. */
	static boolean regla(Optional<DisenoPanelUsuario> fila, boolean porDefectoNuevo) {
		return fila.filter(f -> f.isPermitido() || porDefectoNuevo).map(DisenoPanelUsuario::isActivo)
				.orElse(porDefectoNuevo);
	}

	/** Puede cambiarlo para sí: con permiso o con el por defecto NUEVO. */
	static boolean puedeCambiarPropio(Optional<DisenoPanelUsuario> fila, boolean porDefectoNuevo) {
		return porDefectoNuevo || fila.map(DisenoPanelUsuario::isPermitido).orElse(false);
	}

	private boolean porDefectoNuevo() {
		return esNuevo(configuracion.findByClave(CLAVE_POR_DEFECTO));
	}

	private static boolean esNuevo(Optional<ConfiguracionOrganizacion> clave) {
		return clave.map(c -> NUEVO.equals(c.getValor())).orElse(false);
	}

	// ------------------------------------------------------------------ utilidades

	/** La cuenta, bloqueada, si es miembro de la escuela (como {@code esUsuarioDeOrganizacion}); si no, 404. */
	private void exigirMiembro(Long usuarioId, Long organizacion) {
		if (usuarioId == null) {
			throw new NoSuchElementException(NO_MIEMBRO);
		}
		usuarios.bloquearPorId(usuarioId).orElseThrow(() -> new NoSuchElementException(NO_MIEMBRO));
		if (!membresiaService.esUsuarioDeOrganizacion(usuarioId, organizacion)) {
			throw new NoSuchElementException(NO_MIEMBRO);
		}
	}

	/** PROPIETARIO o ADMINISTRADOR activa en la escuela ({@code organizacion_id} explícito). */
	private boolean tieneGestion(Long usuarioId, Long organizacion) {
		return membresias.findRolesActivos(usuarioId, organizacion).stream().anyMatch(GESTION::contains);
	}

	/**
	 * Guarda con flush. El bloqueo de la cuenta ordena los cambios; si aun así saltara la clave única
	 * (organizacion_id, usuario_id), 409 con {@link #A_LA_VEZ} en vez de un 500.
	 */
	private void guardar(DisenoPanelUsuario fila, Long actor) {
		fila.marcarCambio(actor, LocalDateTime.now());
		try {
			filas.saveAndFlush(fila);
		} catch (DataIntegrityViolationException error) {
			throw new IllegalStateException(A_LA_VEZ);
		}
	}

	private static Map<String, Object> valores(Long usuario, boolean sinCambios) {
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("usuario", usuario);
		if (sinCambios) {
			valores.put("sinCambios", Boolean.TRUE);
		}
		return valores;
	}

	private static Long organizacionActiva() {
		Long organizacion = TenantContext.get();
		if (organizacion == null) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		return organizacion;
	}

	private static Long usuarioDeLaSesion() {
		Long usuario = CobroService.usuarioActualId();
		if (usuario == null) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		return usuario;
	}
}
