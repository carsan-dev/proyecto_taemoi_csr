package com.taemoi.project.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.request.MiembrosRequests;
import com.taemoi.project.dtos.response.MiembrosDTOs;
import com.taemoi.project.entities.AsignacionProfesor;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.MembresiaSede;
import com.taemoi.project.entities.RolAsignacionProfesor;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.ResolutorOrganizacion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AsignacionProfesorRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.MembresiaSedeRepository;
import com.taemoi.project.repositories.MiembrosRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.utils.EmailUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Miembros de la escuela activa (pieza E8-3; D8-4 a D8-12, D8-16, D8-19; fase3-e8-plan.md §4): lista, ficha, alta de
 * cuentas existentes, dar, reactivar y quitar roles con su cargo, sedes del responsable y grupos del profesor.
 *
 * <p>{@code membresia}, {@code membresia_sede} y {@code asignacion_profesor} NO son {@code @TenantId}: cada consulta y
 * escritura lleva {@code organizacion_id} explícito, el de la escuela activa ({@link TenantContext}); las sedes y los
 * grupos se comprueban contra esa escuela antes de escribir nada. Miembro = cuenta con alguna membresía (activa o no)
 * en la escuela; en la escuela por defecto, también la cuenta legado sin ninguna membresía
 * ({@link MembresiaService#esUsuarioDeOrganizacion}). Una cuenta que solo es de otra escuela no existe aquí (404).
 *
 * <p>D8-8: solo {@code ROLE_ADMIN} da, reactiva o quita PROPIETARIO y ADMINISTRADOR (403); pieza 9-8 (decisión 396):
 * también FAMILIA, y ahí el gestor tampoco toca el rol ya activo. D8-9: nunca sin una
 * membresía PROPIETARIO activa (409), con las filas PROPIETARIO bloqueadas antes de contar. Las escrituras son
 * {@code READ_COMMITTED}, bloquean antes la fila de la cuenta ({@code UsuarioRepository#bloquearPorId}, como
 * {@link AsignacionProfesorService}) y son todo o nada. Cada cambio queda en {@link RegistroAccionesMiembros} (y las
 * asignaciones en {@link RegistroAccionesProfesor}) en la misma transacción, solo con ids y enumeraciones; un alta o un
 * rol sin cambios registra su acción con {@code sinCambios=SI}.
 */
@Service
public class GestionMiembrosService {

	static final String NO_MIEMBRO = "La cuenta no es miembro de esta escuela.";
	static final String SIN_CUENTA = "No hay ninguna cuenta con ese email.";
	static final String FAMILIA_EN_ALTA = "La familia se vincula desde la ficha del alumno.";
	static final String FAMILIA_SIN_CARGO = "El rol de familia no lleva cargo.";
	static final String SOLO_PROPIETARIO = "Solo el propietario da o quita los roles de propietario y gestor.";
	static final String SIN_PROPIETARIO = "La escuela se quedaría sin propietario.";
	static final String SEDE_AJENA = "La sede no existe en la escuela.";
	static final String SIN_SEDES = "Indica al menos una sede.";
	static final String SEDES_REPETIDAS = "Hay sedes repetidas.";
	static final String GRUPOS_REPETIDOS = "Hay grupos repetidos.";
	static final String NO_RESPONSABLE = "La cuenta no es responsable de sede en esta escuela.";
	static final String SIN_PROFESOR_ACTIVO = "La cuenta no tiene el rol de profesor activo en esta escuela.";
	static final String SIN_ESE_ROL = "La cuenta no tiene ese rol en esta escuela.";
	static final String ROL_NO_VALIDO = "El rol no es válido.";
	static final String EMAIL_OBLIGATORIO = "El email es obligatorio.";
	static final String SIN_DATOS = "Indica los datos del miembro.";
	static final String SIN_ASIGNACIONES = "Indica las asignaciones del profesor.";
	static final String SEDES_SOLO_RESPONSABLE = "Las sedes solo se indican para el rol de responsable de sede.";
	static final String GRUPOS_SOLO_PROFESOR = "Los grupos solo se indican para el rol de profesor.";
	static final String CARGO_LARGO = "El cargo no puede pasar de 100 caracteres.";
	static final String TAMANO_NO_VALIDO = "El tamaño de página debe estar entre 1 y 100.";
	static final String PAGINA_NO_VALIDA = "La página no es válida.";
	static final String BUSQUEDA_LARGA = "La búsqueda no puede pasar de 100 caracteres.";
	static final String YA_ACEPTADA = "La invitación ya se aceptó.";
	static final String SIN_ROL_ACTIVO = "La cuenta no tiene ningún rol activo en esta escuela.";
	static final String OTRA_ESCUELA = "El usuario también pertenece a otra escuela: no se puede cambiar su contraseña desde esta escuela.";
	static final String CUENTA_GOOGLE = "Las cuentas de Google no permiten restablecer contrasena manualmente.";
	static final String INVITACION_PENDIENTE = "Tiene una invitación pendiente: reenvíala o cancélala.";

	static final int TAMANO_POR_DEFECTO = 20;
	static final int TAMANO_MAXIMO = 100;
	private static final int CARGO_MAXIMO = 100;
	private static final int BUSQUEDA_MAXIMA = 100;
	private static final String RUTA = "/api/miembros";

	private final UsuarioRepository usuarios;
	private final MembresiaRepository membresias;
	private final MiembrosRepository consultas;
	private final MembresiaSedeRepository membresiaSedes;
	private final AsignacionProfesorRepository asignaciones;
	private final MembresiaService membresiaService;
	private final ResolutorOrganizacion resolutor;
	private final RegistroAccionesMiembros registro;
	private final RegistroAccionesProfesor registroProfesor;
	private final InvitacionService invitaciones;
	private final UsuarioService usuarioService;

	@PersistenceContext
	private EntityManager em;

	public GestionMiembrosService(UsuarioRepository usuarios, MembresiaRepository membresias,
			MiembrosRepository consultas, MembresiaSedeRepository membresiaSedes,
			AsignacionProfesorRepository asignaciones, MembresiaService membresiaService,
			ResolutorOrganizacion resolutor, RegistroAccionesMiembros registro,
			RegistroAccionesProfesor registroProfesor, InvitacionService invitaciones, UsuarioService usuarioService) {
		this.usuarios = usuarios;
		this.membresias = membresias;
		this.consultas = consultas;
		this.membresiaSedes = membresiaSedes;
		this.asignaciones = asignaciones;
		this.membresiaService = membresiaService;
		this.resolutor = resolutor;
		this.registro = registro;
		this.registroProfesor = registroProfesor;
		this.invitaciones = invitaciones;
		this.usuarioService = usuarioService;
	}

	// ------------------------------------------------------------------ lecturas

	/**
	 * Una página de miembros (D8-4), paginada y buscada en la base: página desde 0, tamaño de 1 a 100 (20 por defecto).
	 * Sin {@code incluirFamilias}, solo el personal; {@code rol}, quien tiene una membresía de ese rol (activa o no).
	 * Cinco consultas por página, sea cual sea su tamaño.
	 *
	 * @throws IllegalArgumentException (400) rol, página, tamaño o búsqueda no válidos
	 */
	@Transactional(readOnly = true)
	public MiembrosDTOs.Pagina listar(String q, String rol, boolean incluirFamilias, Integer pagina, Integer tamano) {
		int numero = pagina == null ? 0 : pagina;
		int porPagina = tamano == null ? TAMANO_POR_DEFECTO : tamano;
		if (numero < 0) {
			throw new IllegalArgumentException(PAGINA_NO_VALIDA);
		}
		if (porPagina < 1 || porPagina > TAMANO_MAXIMO) {
			throw new IllegalArgumentException(TAMANO_NO_VALIDO);
		}
		if ((long) numero * porPagina > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(PAGINA_NO_VALIDA);
		}
		RolMembresia filtro = rol == null || rol.isBlank() ? null : rolDesde(rol);
		String busqueda = q == null ? null : q.strip();
		if (busqueda != null && busqueda.codePointCount(0, busqueda.length()) > BUSQUEDA_MAXIMA) {
			throw new IllegalArgumentException(BUSQUEDA_LARGA);
		}
		boolean buscar = busqueda != null && !busqueda.isEmpty();
		Long organizacion = organizacionActiva();

		Page<MiembrosRepository.FilaCuenta> filas = consultas.pagina(organizacion, esPorDefecto(organizacion),
				incluirFamilias, RolMembresia.FAMILIA, filtro != null, filtro == null ? RolMembresia.FAMILIA : filtro,
				buscar, buscar ? patron(busqueda) : "%", PageRequest.of(numero, porPagina));
		List<Long> ids = filas.getContent().stream().map(MiembrosRepository.FilaCuenta::getId).toList();
		Datos datos = datosDe(organizacion, ids);
		List<MiembrosDTOs.Miembro> contenido = filas.getContent().stream()
				.map(f -> new MiembrosDTOs.Miembro(f.getId(), f.getNombre(), f.getApellidos(), f.getEmail(),
						f.getAuthProvider(), pendiente(f), datos.roles(f.getId()), datos.sedes(f.getId()),
						datos.numeroGrupos(f.getId())))
				.toList();
		return new MiembrosDTOs.Pagina(contenido, numero, porPagina, filas.getTotalElements(), filas.getTotalPages());
	}

	/** Ficha de un miembro (D8-5); de otra escuela o inexistente, 404 {@link #NO_MIEMBRO}. */
	@Transactional(readOnly = true)
	public MiembrosDTOs.Ficha ficha(Long usuarioId) {
		Long organizacion = organizacionActiva();
		return ficha(exigirMiembro(usuarioId, organizacion), organizacion);
	}

	// ------------------------------------------------------------------ alta

	/**
	 * Alta de una cuenta EXISTENTE con un rol (D8-6): crea la membresía si falta; desactivada, 409 salvo
	 * {@code reactivar}; activa, se conserva. Cargo si llega; RESPONSABLE_SEDE con sus sedes (obligatorias si el rol
	 * nace o se reactiva sin sedes guardadas; si llegan, reemplazan); PROFESOR, añade asignaciones (en un grupo ya
	 * asignado cambia el rol). Todo o nada.
	 *
	 * <p>Pieza E8-4 (D8-13): sin cuenta con ese email y con {@code nombre} y {@code apellidos}, la invitación: en esta
	 * misma transacción, cuenta pendiente (LOCAL sin contraseña, {@link InvitacionService#crearCuentaPendiente}) con su
	 * fila {@code MIEMBRO_INVITADO} y lo de siempre; el correo, tras el commit. Con cuenta, {@code nombre} y
	 * {@code apellidos} se ignoran: ni token, ni contraseña, ni correo. Dos invitaciones a la vez del mismo email: la
	 * clave única hace fallar la segunda y {@link AltaDeMiembro} la repite (entonces ya hay cuenta).
	 *
	 * <p>FAMILIA no entra por el alta (D8-6): el rol se da en la ficha del miembro, solo el propietario (decisión 396).
	 *
	 * @throws IllegalArgumentException (400) cuerpo no válido o FAMILIA
	 * @throws SecurityException        (403) D8-8
	 * @throws NoSuchElementException   (404) sin cuenta con ese email (ni nombre y apellidos) o grupo ajeno
	 * @throws IllegalStateException    (409) rol desactivado sin {@code reactivar}
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MiembrosDTOs.Ficha alta(MiembrosRequests.Alta solicitud) {
		if (solicitud == null) {
			throw new IllegalArgumentException(SIN_DATOS);
		}
		String email = EmailUtils.normalizeEmail(solicitud.email());
		if (email == null || email.isEmpty()) {
			throw new IllegalArgumentException(EMAIL_OBLIGATORIO);
		}
		RolMembresia rol = rolDeAlta(solicitud.rol());
		Cargo cargo = Cargo.de(solicitud.cargo());
		if (solicitud.sedeIds() != null && rol != RolMembresia.RESPONSABLE_SEDE) {
			throw new IllegalArgumentException(SEDES_SOLO_RESPONSABLE);
		}
		if (solicitud.grupos() != null && rol != RolMembresia.PROFESOR) {
			throw new IllegalArgumentException(GRUPOS_SOLO_PROFESOR);
		}
		Set<Long> sedesPedidas = solicitud.sedeIds() == null ? null : idsDeSedes(solicitud.sedeIds());
		Map<Long, RolAsignacionProfesor> gruposPedidos = solicitud.grupos() == null ? Map.of()
				: asignacionesDe(solicitud.grupos());
		exigirPropietarioPara(rol);

		Long organizacion = organizacionActiva();
		Usuario cuenta = usuarios.findByEmailIgnoreCase(email).orElse(null);
		// Sin cuenta: la invitación (E8-4) si llegan nombre y apellidos; si no, el 404 de siempre.
		InvitacionService.Invitado invitado = cuenta != null ? null
				: InvitacionService.invitadoDe(solicitud.nombre(), solicitud.apellidos())
						.orElseThrow(() -> new NoSuchElementException(SIN_CUENTA));
		if (cuenta != null) {
			usuarios.bloquearPorId(cuenta.getId()).orElseThrow(() -> new NoSuchElementException(SIN_CUENTA));
		}
		if (sedesPedidas != null) {
			exigirSedesDeLaEscuela(sedesPedidas, organizacion);
		}
		exigirGruposDeLaEscuela(gruposPedidos.keySet(), organizacion);

		Membresia membresia = cuenta == null ? null : membresiaDe(cuenta.getId(), organizacion, rol).orElse(null);
		if (membresia != null && !membresia.isActiva() && !Boolean.TRUE.equals(solicitud.reactivar())) {
			throw new IllegalStateException(
					"La cuenta tiene el rol de " + enPantalla(rol) + " desactivado en esta escuela.");
		}
		exigirSedesSiNace(rol, membresia, sedesPedidas, organizacion);

		if (cuenta == null) {
			cuenta = invitaciones.crearCuentaPendiente(email, invitado, organizacion, rol);
		}
		Cambios cambios = new Cambios(cuenta.getId(), RUTA);
		if (invitado != null) {
			cambios.rol(RegistroAccionesMiembros.Accion.MIEMBRO_INVITADO, rol);
		}
		membresia = asegurarActiva(membresia, cuenta.getId(), organizacion, rol, cambios);
		fijarCargo(membresia, cargo, cambios);
		if (sedesPedidas != null) {
			fijarSedes(membresia, sedesPedidas, organizacion, cambios);
		}
		for (Map.Entry<Long, RolAsignacionProfesor> grupo : gruposPedidos.entrySet()) {
			asignar(cuenta.getId(), grupo.getKey(), grupo.getValue(), organizacion, cambios);
		}
		cambios.sinCambiosSiHaceFalta(RegistroAccionesMiembros.Accion.ROL_DADO, rol);
		return ficha(cuenta, organizacion);
	}

	// ------------------------------------------------------------------ invitación

	/**
	 * Reenvía la invitación (pieza E8-4, D8-13): token nuevo (el anterior deja de valer) y correo tras el commit, con la
	 * marca de esta escuela, que pasa a ser la de la invitación. Fila {@code MIEMBROS INVITACION_REENVIADA usuario=#id}.
	 * Exige algún rol ACTIVO en esta escuela (sin ninguno, la invitación no daría acceso a nada: 409); el texto del correo
	 * usa solo los activos. D8-8: una cuenta cuyos roles activos en esta escuela son solo PROPIETARIO o ADMINISTRADOR
	 * solo pudo invitarla el propietario: el gestor no la reenvía (403).
	 *
	 * @throws NoSuchElementException (404) no es miembro de la escuela activa
	 * @throws SecurityException      (403) D8-8
	 * @throws IllegalStateException  (409) {@value #SIN_ROL_ACTIVO} o {@value #YA_ACEPTADA}
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MiembrosDTOs.Ficha reenviarInvitacion(Long usuarioId) {
		Long organizacion = organizacionActiva();
		Usuario cuenta = exigirMiembro(usuarioId, organizacion);
		usuarios.bloquearPorId(cuenta.getId()).orElseThrow(() -> new NoSuchElementException(NO_MIEMBRO));
		List<Membresia> activas = membresias.findByUsuarioIdAndOrganizacionId(cuenta.getId(), organizacion).stream()
				.filter(Membresia::isActiva).toList();
		if (activas.isEmpty()) {
			throw new IllegalStateException(SIN_ROL_ACTIVO);
		}
		boolean soloGestion = activas.stream()
				.allMatch(m -> m.getRol() == RolMembresia.PROPIETARIO || m.getRol() == RolMembresia.ADMINISTRADOR);
		if (soloGestion && !sesionDePropietario()) {
			throw new SecurityException(SOLO_PROPIETARIO);
		}
		// Comprobación rápida y, al escribir, un UPDATE condicionado (si otra petición la aceptó entre medias, 409).
		if (!cuenta.invitacionPendiente()
				|| !invitaciones.renovarToken(cuenta, organizacion, rolDeLaInvitacion(activas))) {
			throw new IllegalStateException(YA_ACEPTADA);
		}
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("usuario", cuenta.getId());
		registro.registrar(RegistroAccionesMiembros.Accion.INVITACION_REENVIADA,
				RUTA + "/" + cuenta.getId() + "/invitacion", valores);
		return ficha(cuenta, organizacion);
	}

	/**
	 * Cancela la invitación (decisión 389): retira los roles activos de esta escuela, el enlace deja de servir y el aviso
	 * sale tras el commit. Fila {@code MIEMBROS INVITACION_CANCELADA usuario=#id cuentaBorrada=SI|NO}. Más estricto que
	 * reenviar: si CUALQUIER rol activo es PROPIETARIO o ADMINISTRADOR, solo el propietario (403), porque aquí se retiran
	 * todos.
	 *
	 * <p>La cuenta, según lo que tenga además (en TODAS las escuelas; borrar es irreversible, ante la duda se conserva):
	 * <ul>
	 * <li>Rol activo en otra escuela activa: se conserva. Si la invitación vigente es de esta escuela, se renueva para la
	 * otra (la de menor id), con su nombre y su correo; si es de otra, su token sigue vivo tal cual.</li>
	 * <li>Otra membresía (inactiva o de una escuela desactivada) o cualquier dato de
	 * {@link UsuarioRepository#tieneDatosFueraDeLaInvitacion}: se conserva sin token vivo. FAMILIA activa con alumnos
	 * vinculados aquí no se toca: los vínculos se gestionan aparte; sin más roles que retirar ni token vivo, repetir no
	 * tendría efecto: 409 {@value #SIN_ROL_ACTIVO}, sin correo ni {@code INVITACION_CANCELADA}.</li>
	 * <li>Nada más: se borran sus membresías de esta escuela, con sus sedes, sus asignaciones y la cuenta.</li>
	 * </ul>
	 *
	 * <p>Carrera con {@code aceptar}: esta transacción tiene bloqueada la fila desde el principio, así que el UPDATE de
	 * {@code aceptar} espera y, al confirmar, no encuentra el hash (400). Si {@code aceptar} confirmó antes, 409: con la
	 * fila bloqueada, la cuenta se relee de la base ({@code refresh}), porque la entidad de la sesión pudo cargarse antes
	 * del bloqueo; nada depende del recuento de filas de un UPDATE. Las altas desde otra escuela bloquean la misma fila, y
	 * cualquier fila nueva con clave ajena a la cuenta también espera a este bloqueo: lo que se decide aquí no cambia hasta
	 * el commit.
	 *
	 * @throws NoSuchElementException (404) no es miembro de la escuela activa
	 * @throws SecurityException      (403) D8-8
	 * @throws IllegalStateException  (409) {@value #SIN_ROL_ACTIVO}, {@value #SIN_PROPIETARIO} o {@value #YA_ACEPTADA}
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MiembrosDTOs.InvitacionCancelada cancelarInvitacion(Long usuarioId) {
		Long organizacion = organizacionActiva();
		Usuario cuenta = exigirMiembro(usuarioId, organizacion);
		Long id = cuenta.getId();
		usuarios.bloquearPorId(id).orElseThrow(() -> new NoSuchElementException(NO_MIEMBRO));
		// Antes de leer ninguna membresía: las filas PROPIETARIO llegan con el estado ya confirmado.
		List<Membresia> propietarios = membresias.bloquearPropietariosActivos(organizacion);
		exigirCancelable(id, organizacion, propietarios);
		// Desde aquí, el estado ya confirmado de la cuenta, con su fila bloqueada; no la entidad vieja de la sesión.
		em.refresh(cuenta);
		if (!cuenta.invitacionPendiente()) {
			throw new IllegalStateException(YA_ACEPTADA);
		}

		List<Membresia> todas = membresias.findByUsuarioId(id);
		List<Membresia> deEstaEscuela = todas.stream().filter(m -> organizacion.equals(m.getOrganizacionId())).toList();
		List<Membresia> aRetirar = rolesARetirar(id, deEstaEscuela, organizacion);
		if (aRetirar.isEmpty() && !tieneTokenVivo(cuenta)) {
			throw new IllegalStateException(SIN_ROL_ACTIVO);
		}
		boolean borrar = deEstaEscuela.size() == todas.size()
				&& usuarios.tieneDatosFueraDeLaInvitacion(id, organizacion) == 0;

		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("usuario", id);
		valores.put("cuentaBorrada", borrar);
		registro.registrar(RegistroAccionesMiembros.Accion.INVITACION_CANCELADA, RUTA + "/" + id + "/invitacion", valores);
		invitaciones.avisarCancelacion(cuenta, organizacion);

		if (borrar) {
			borrarCuentaPendiente(id, deEstaEscuela, organizacion);
		} else {
			for (Membresia membresia : aRetirar) {
				membresia.setActiva(false);
				membresias.saveAndFlush(membresia);
			}
			resolverToken(cuenta, organizacion);
		}
		return new MiembrosDTOs.InvitacionCancelada(borrar);
	}

	/** Comprobaciones de cancelar que dependen de sus roles: 409 sin rol activo, 403 D8-8 y 409 último propietario. */
	private void exigirCancelable(Long id, Long organizacion, List<Membresia> propietarios) {
		List<Membresia> activas = membresias.findByUsuarioIdAndOrganizacionId(id, organizacion).stream()
				.filter(Membresia::isActiva).toList();
		if (activas.isEmpty()) {
			throw new IllegalStateException(SIN_ROL_ACTIVO);
		}
		boolean algunRolReservado = activas.stream()
				.anyMatch(m -> m.getRol() == RolMembresia.PROPIETARIO || m.getRol() == RolMembresia.ADMINISTRADOR);
		if (algunRolReservado && !sesionDePropietario()) {
			throw new SecurityException(SOLO_PROPIETARIO);
		}
		boolean esPropietario = propietarios.stream().anyMatch(p -> id.equals(p.getUsuarioId()));
		if (esPropietario && propietarios.size() <= 1) {
			throw new IllegalStateException(SIN_PROPIETARIO);
		}
	}

	/**
	 * Lo que cancelar pasa a {@code activa=false}, sin borrar filas, sedes ni asignaciones: sus roles activos, salvo FAMILIA
	 * con alumnos.
	 */
	private List<Membresia> rolesARetirar(Long id, List<Membresia> deEstaEscuela, Long organizacion) {
		boolean conAlumnos = consultas.alumnosVinculados(id, organizacion) > 0;
		return deEstaEscuela.stream().filter(Membresia::isActiva)
				.filter(m -> !(m.getRol() == RolMembresia.FAMILIA && conAlumnos)).toList();
	}

	/** Si aún hay un enlace que sirva: con hash y sin caducar, como exige aceptar la invitación. */
	private static boolean tieneTokenVivo(Usuario cuenta) {
		return cuenta.getResetTokenHash() != null && cuenta.getResetTokenExpiresAt() != null
				&& cuenta.getResetTokenExpiresAt().isAfter(LocalDateTime.now());
	}

	/**
	 * El enlace de esta escuela muere en todo caso. Con rol activo en otra escuela activa, la invitación pasa a la de menor
	 * id si era de esta (o de ninguna conocida); si ya era de otra, su token sigue tal cual. Sin rol activo fuera, ningún
	 * token vivo. Con la fila bloqueada y la cuenta releída pendiente, retirar el token no decide nada: puede no haber
	 * ninguno. Renovar sí cambia el hash, y su recuento se comprueba.
	 */
	private void resolverToken(Usuario cuenta, Long organizacion) {
		List<Membresia> activasFuera = membresias.findActivasDeUsuario(cuenta.getId()).stream()
				.filter(m -> !organizacion.equals(m.getOrganizacionId())).toList();
		Long escuelaInvitacion = cuenta.getUltimaOrganizacionId();
		if (activasFuera.isEmpty()) {
			usuarios.retirarTokenInvitacion(cuenta.getId());
		} else if (escuelaInvitacion == null || organizacion.equals(escuelaInvitacion)) {
			Long otra = activasFuera.get(0).getOrganizacionId();
			List<Membresia> activasDeLaOtra = activasFuera.stream().filter(m -> otra.equals(m.getOrganizacionId()))
					.toList();
			if (!invitaciones.renovarToken(cuenta, otra, rolDeLaInvitacion(activasDeLaOtra))) {
				throw new IllegalStateException(YA_ACEPTADA);
			}
		}
	}

	/** Las filas propias de la invitación en esta escuela y la cuenta, que ya no tiene nada más. */
	private void borrarCuentaPendiente(Long id, List<Membresia> deEstaEscuela, Long organizacion) {
		for (Membresia membresia : deEstaEscuela) {
			consultas.borrarSedesDeMembresia(membresia.getId(), organizacion);
		}
		asignaciones.borrarDeLaEscuela(id, organizacion);
		membresias.borrarDeLaEscuela(id, organizacion);
		if (usuarios.borrarCuentaPendiente(id) != 1) {
			throw new IllegalStateException(YA_ACEPTADA);
		}
	}

	/** El rol del texto del correo: el primero (orden del enum) de sus roles activos de personal; si no, FAMILIA. */
	private static RolMembresia rolDeLaInvitacion(List<Membresia> activas) {
		return activas.stream().map(Membresia::getRol).filter(rol -> rol != RolMembresia.FAMILIA).sorted().findFirst()
				.orElse(RolMembresia.FAMILIA);
	}

	// ------------------------------------------------------------------ roles

	/**
	 * Da o reactiva el rol (D8-7) y fija el cargo si llega; con el rol ya activo, solo el cargo. RESPONSABLE_SEDE:
	 * {@code sedeIds} reemplaza sus sedes; sin sedes guardadas ni pedidas, 400. El gestor no da ni reactiva PROPIETARIO
	 * ni ADMINISTRADOR (403), pero sí cambia el cargo de esos roles ya activos.
	 *
	 * <p>FAMILIA (decisión 396, cambia la D8-6): solo el propietario, también con el rol ya activo (403 antes de mirar nada
	 * más); no lleva cargo ni sedes (400). Crea o reactiva la membresía, como {@code reemplazarAutoridades}; nunca toca
	 * {@code usuario_alumno}.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MiembrosDTOs.Ficha darRol(Long usuarioId, String rolTexto, MiembrosRequests.Rol solicitud) {
		RolMembresia rol = rolDeLaRuta(rolTexto);
		Cargo cargo = Cargo.de(solicitud == null ? null : solicitud.cargo());
		if (rol == RolMembresia.FAMILIA && cargo.valor() != null) {
			throw new IllegalArgumentException(FAMILIA_SIN_CARGO);
		}
		List<Long> sedeIds = solicitud == null ? null : solicitud.sedeIds();
		if (sedeIds != null && rol != RolMembresia.RESPONSABLE_SEDE) {
			throw new IllegalArgumentException(SEDES_SOLO_RESPONSABLE);
		}
		Set<Long> sedesPedidas = sedeIds == null ? null : idsDeSedes(sedeIds);
		Long organizacion = organizacionActiva();
		Usuario cuenta = exigirMiembro(usuarioId, organizacion);
		usuarios.bloquearPorId(cuenta.getId()).orElseThrow(() -> new NoSuchElementException(NO_MIEMBRO));

		Membresia membresia = membresiaDe(cuenta.getId(), organizacion, rol).orElse(null);
		if (membresia == null || !membresia.isActiva()) {
			exigirPropietarioPara(rol);
		}
		if (sedesPedidas != null) {
			exigirSedesDeLaEscuela(sedesPedidas, organizacion);
		}
		exigirSedesSiNace(rol, membresia, sedesPedidas, organizacion);

		Cambios cambios = new Cambios(cuenta.getId(), RUTA + "/" + cuenta.getId() + "/roles/" + rol.name());
		membresia = asegurarActiva(membresia, cuenta.getId(), organizacion, rol, cambios);
		fijarCargo(membresia, cargo, cambios);
		if (sedesPedidas != null) {
			fijarSedes(membresia, sedesPedidas, organizacion, cambios);
		}
		cambios.sinCambiosSiHaceFalta(RegistroAccionesMiembros.Accion.ROL_DADO, rol);
		return ficha(cuenta, organizacion);
	}

	/**
	 * Quita el rol (D8-7): {@code activa=false}, sin borrar la fila; conserva asignaciones y sedes. Ya desactivado: 200
	 * con {@code sinCambios=SI}. PROPIETARIO: bloquea antes las filas PROPIETARIO activas de la escuela y, si es la
	 * última, 409 {@link #SIN_PROPIETARIO} (D8-9, D8-19). FAMILIA (decisión 396), solo el propietario: sus alumnos siguen
	 * vinculados y, sin otro rol, la cuenta deja de entrar. A una cuenta pendiente que se queda sin ninguna membresía
	 * activa en esta escuela (FAMILIA incluida), el enlace de esta escuela se le invalida, sin correo de cancelación
	 * ({@link #invalidarEnlaceSiNoQuedanRoles}).
	 *
	 * @throws SecurityException      (403) D8-8
	 * @throws NoSuchElementException (404) no es miembro o no tiene ese rol
	 * @throws IllegalStateException  (409) último propietario
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MiembrosDTOs.Ficha quitarRol(Long usuarioId, String rolTexto) {
		RolMembresia rol = rolDeLaRuta(rolTexto);
		exigirPropietarioPara(rol);
		Long organizacion = organizacionActiva();
		Usuario cuenta = exigirMiembro(usuarioId, organizacion);
		usuarios.bloquearPorId(cuenta.getId()).orElseThrow(() -> new NoSuchElementException(NO_MIEMBRO));
		// Antes de leer ninguna membresía: las filas PROPIETARIO llegan con el estado ya confirmado.
		List<Membresia> propietarios = rol == RolMembresia.PROPIETARIO
				? membresias.bloquearPropietariosActivos(organizacion)
				: List.of();
		Membresia membresia = membresiaDe(cuenta.getId(), organizacion, rol)
				.orElseThrow(() -> new NoSuchElementException(SIN_ESE_ROL));

		Cambios cambios = new Cambios(cuenta.getId(), RUTA + "/" + cuenta.getId() + "/roles/" + rol.name());
		if (membresia.isActiva()) {
			if (rol == RolMembresia.PROPIETARIO) {
				Long id = membresia.getId();
				boolean bloqueada = propietarios.stream().anyMatch(p -> Objects.equals(p.getId(), id));
				if (bloqueada && propietarios.size() <= 1) {
					throw new IllegalStateException(SIN_PROPIETARIO);
				}
			}
			membresia.setActiva(false);
			membresias.saveAndFlush(membresia);
			cambios.rol(RegistroAccionesMiembros.Accion.ROL_QUITADO, rol);
			invalidarEnlaceSiNoQuedanRoles(cuenta, organizacion);
		}
		cambios.sinCambiosSiHaceFalta(RegistroAccionesMiembros.Accion.ROL_QUITADO, rol);
		return ficha(cuenta, organizacion);
	}

	/**
	 * Tras quitar un rol: a una cuenta que sigue pendiente y no conserva ninguna membresía activa aquí se le resuelve el
	 * token como al cancelar ({@link #resolverToken}). Quien llama tiene la fila bloqueada: lo escrito se vuelca antes y la
	 * cuenta se relee, porque la entidad de la sesión pudo cargarse antes del bloqueo; {@code aceptar} pudo confirmar.
	 */
	private void invalidarEnlaceSiNoQuedanRoles(Usuario cuenta, Long organizacion) {
		em.flush();
		em.refresh(cuenta);
		boolean sinRoles = membresias.findByUsuarioIdAndOrganizacionId(cuenta.getId(), organizacion).stream()
				.noneMatch(Membresia::isActiva);
		if (cuenta.invitacionPendiente() && sinRoles) {
			resolverToken(cuenta, organizacion);
		}
	}

	// ------------------------------------------------------------------ sedes y grupos

	/**
	 * Reemplaza las sedes del responsable (D8-10). Exige su membresía RESPONSABLE_SEDE (activa o no); cada sede, de la
	 * escuela activa ({@code membresia_sede} no lo impide: lo valida este servicio).
	 *
	 * @throws IllegalArgumentException (400) lista vacía, repetidas, nulas o sede de otra escuela
	 * @throws NoSuchElementException   (404) no es miembro
	 * @throws IllegalStateException    (409) sin membresía RESPONSABLE_SEDE
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MiembrosDTOs.Ficha reemplazarSedes(Long usuarioId, MiembrosRequests.Sedes solicitud) {
		Set<Long> pedidas = idsDeSedes(solicitud == null ? null : solicitud.sedeIds());
		Long organizacion = organizacionActiva();
		Usuario cuenta = exigirMiembro(usuarioId, organizacion);
		usuarios.bloquearPorId(cuenta.getId()).orElseThrow(() -> new NoSuchElementException(NO_MIEMBRO));
		Membresia membresia = membresiaDe(cuenta.getId(), organizacion, RolMembresia.RESPONSABLE_SEDE)
				.orElseThrow(() -> new IllegalStateException(NO_RESPONSABLE));
		exigirSedesDeLaEscuela(pedidas, organizacion);

		Cambios cambios = new Cambios(cuenta.getId(), RUTA + "/" + cuenta.getId() + "/sedes");
		if (!fijarSedes(membresia, pedidas, organizacion, cambios)) {
			cambios.sedes(pedidas, true);
		}
		return ficha(cuenta, organizacion);
	}

	/**
	 * Reemplaza el conjunto de grupos del profesor en la escuela (D8-11), todo o nada: quita los que sobran, cambia el
	 * rol de los que siguen y crea los nuevos (acciones del módulo {@code profesor}). Lista vacía: los quita todos (la
	 * membresía queda, D7-21). Igual que ya está: 200 con {@code MIEMBROS GRUPOS_CAMBIADOS usuario=#id sinCambios=SI}
	 * (todo 2xx registra: la ruta está en {@code EscrituraConRegistroPropio}).
	 *
	 * @throws IllegalArgumentException (400) sin lista, grupo repetido o rol no válido
	 * @throws NoSuchElementException   (404) no es miembro o grupo ajeno o inexistente
	 * @throws IllegalStateException    (409) sin PROFESOR activa
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MiembrosDTOs.Ficha reemplazarGrupos(Long usuarioId, MiembrosRequests.Grupos solicitud) {
		if (solicitud == null || solicitud.asignaciones() == null) {
			throw new IllegalArgumentException(SIN_ASIGNACIONES);
		}
		Map<Long, RolAsignacionProfesor> pedidas = asignacionesDe(solicitud.asignaciones());
		Long organizacion = organizacionActiva();
		Usuario cuenta = exigirMiembro(usuarioId, organizacion);
		usuarios.bloquearPorId(cuenta.getId()).orElseThrow(() -> new NoSuchElementException(NO_MIEMBRO));
		boolean profesorActivo = membresiaDe(cuenta.getId(), organizacion, RolMembresia.PROFESOR)
				.map(Membresia::isActiva).orElse(false);
		if (!profesorActivo) {
			throw new IllegalStateException(SIN_PROFESOR_ACTIVO);
		}
		exigirGruposDeLaEscuela(pedidas.keySet(), organizacion);

		Cambios cambios = new Cambios(cuenta.getId(), RUTA + "/" + cuenta.getId() + "/grupos");
		for (AsignacionProfesor actual : asignaciones.findByUsuarioIdAndOrganizacionId(cuenta.getId(), organizacion)) {
			if (!pedidas.containsKey(actual.getGrupoId())) {
				asignaciones.borrar(cuenta.getId(), actual.getGrupoId(), organizacion);
				cambios.asignacion(RegistroAccionesProfesor.Accion.ASIGNACION_QUITADA, actual.getGrupoId(),
						actual.getRol());
			}
		}
		for (Map.Entry<Long, RolAsignacionProfesor> grupo : pedidas.entrySet()) {
			asignar(cuenta.getId(), grupo.getKey(), grupo.getValue(), organizacion, cambios);
		}
		cambios.gruposSinCambiosSiHaceFalta();
		return ficha(cuenta, organizacion);
	}

	// ------------------------------------------------------------------ contraseña

	/**
	 * Restablece la contraseña de un miembro (pieza 9-8, decisión 373), solo el propietario (lo exige el controlador). Las
	 * reglas de {@code /api/admin/users/{id}/password} y, además, 409 con invitación pendiente: ahí valen reenviar y
	 * cancelar. Fila {@code MIEMBROS CONTRASENA_RESTABLECIDA usuario=#id}; nunca la contraseña.
	 *
	 * <p>Con la fila bloqueada, la cuenta se relee de la base ({@code refresh}): la entidad de la sesión pudo cargarse
	 * antes del bloqueo, y aceptar la invitación o entrar con Google entre medias cambia lo que se decide aquí.
	 *
	 * @throws NoSuchElementException   (404) no es miembro de la escuela activa
	 * @throws SecurityException        (403) {@value #OTRA_ESCUELA}
	 * @throws IllegalArgumentException (400) {@value #CUENTA_GOOGLE}
	 * @throws IllegalStateException    (409) {@value #INVITACION_PENDIENTE}
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MiembrosDTOs.Ficha restablecerContrasena(Long usuarioId, String nuevaContrasena) {
		Long organizacion = organizacionActiva();
		Usuario cuenta = exigirMiembro(usuarioId, organizacion);
		Long id = cuenta.getId();
		usuarios.bloquearPorId(id).orElseThrow(() -> new NoSuchElementException(NO_MIEMBRO));
		em.refresh(cuenta);
		if (!membresiaService.puedeGestionarUsuarioGlobal(id, organizacion)) {
			throw new SecurityException(OTRA_ESCUELA);
		}
		if (cuenta.getAuthProvider() == AuthProvider.GOOGLE) {
			throw new IllegalArgumentException(CUENTA_GOOGLE);
		}
		if (cuenta.invitacionPendiente()) {
			throw new IllegalStateException(INVITACION_PENDIENTE);
		}
		usuarioService.actualizarContrasena(id, nuevaContrasena);
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("usuario", id);
		registro.registrar(RegistroAccionesMiembros.Accion.CONTRASENA_RESTABLECIDA, RUTA + "/" + id + "/contrasena",
				valores);
		return ficha(cuenta, organizacion);
	}

	// ------------------------------------------------------------------ escrituras comunes

	/** La membresía activa del rol: la crea si falta ({@code ROL_DADO}) o la reactiva ({@code ROL_REACTIVADO}). */
	private Membresia asegurarActiva(Membresia membresia, Long usuarioId, Long organizacion, RolMembresia rol,
			Cambios cambios) {
		if (membresia == null) {
			Membresia nueva = membresias.saveAndFlush(new Membresia(usuarioId, organizacion, rol));
			cambios.rol(RegistroAccionesMiembros.Accion.ROL_DADO, rol);
			return nueva;
		}
		if (!membresia.isActiva()) {
			membresia.setActiva(true);
			Membresia reactivada = membresias.saveAndFlush(membresia);
			cambios.rol(RegistroAccionesMiembros.Accion.ROL_REACTIVADO, rol);
			return reactivada;
		}
		return membresia;
	}

	private void fijarCargo(Membresia membresia, Cargo cargo, Cambios cambios) {
		if (!cargo.fijar() || Objects.equals(membresia.getCargo(), cargo.valor())) {
			return;
		}
		membresia.setCargo(cargo.valor());
		membresias.saveAndFlush(membresia);
		cambios.rol(RegistroAccionesMiembros.Accion.CARGO_CAMBIADO, membresia.getRol());
	}

	/** Reemplaza las sedes de la membresía (ya validadas); {@code false} si eran las mismas. */
	private boolean fijarSedes(Membresia membresia, Set<Long> pedidas, Long organizacion, Cambios cambios) {
		Set<Long> guardadas = new TreeSet<>(consultas.sedesDeMembresia(membresia.getId(), organizacion));
		if (guardadas.equals(pedidas)) {
			return false;
		}
		consultas.borrarSedesDeMembresia(membresia.getId(), organizacion);
		List<MembresiaSede> filas = pedidas.stream().map(sede -> new MembresiaSede(membresia.getId(), sede)).toList();
		membresiaSedes.saveAll(filas);
		membresiaSedes.flush();
		cambios.sedes(pedidas, false);
		return true;
	}

	/** Crea la asignación o le cambia el rol; igual, nada. */
	private void asignar(Long usuarioId, Long grupoId, RolAsignacionProfesor rol, Long organizacion, Cambios cambios) {
		Optional<AsignacionProfesor> existente = asignaciones.findByUsuarioIdAndGrupoIdAndOrganizacionId(usuarioId,
				grupoId, organizacion);
		if (existente.isEmpty()) {
			asignaciones.saveAndFlush(new AsignacionProfesor(organizacion, usuarioId, grupoId, rol));
			cambios.asignacion(RegistroAccionesProfesor.Accion.ASIGNACION_CREADA, grupoId, rol);
		} else if (existente.get().getRol() != rol) {
			AsignacionProfesor asignacion = existente.get();
			asignacion.setRol(rol);
			asignaciones.saveAndFlush(asignacion);
			cambios.asignacion(RegistroAccionesProfesor.Accion.ASIGNACION_ROL_CAMBIADO, grupoId, rol);
		}
	}

	/** RESPONSABLE_SEDE que nace o se reactiva sin sedes pedidas ni guardadas: 400 (sin sedes no vería nada, 65). */
	private void exigirSedesSiNace(RolMembresia rol, Membresia membresia, Set<Long> pedidas, Long organizacion) {
		if (rol != RolMembresia.RESPONSABLE_SEDE || pedidas != null || (membresia != null && membresia.isActiva())) {
			return;
		}
		if (membresia == null || consultas.sedesDeMembresia(membresia.getId(), organizacion).isEmpty()) {
			throw new IllegalArgumentException(SIN_SEDES);
		}
	}

	/** Lo que cambia una escritura, para el registro (mismo orden en que ocurre). */
	private final class Cambios {
		private final Long usuarioId;
		private final String endpoint;
		private boolean alguno;

		Cambios(Long usuarioId, String endpoint) {
			this.usuarioId = usuarioId;
			this.endpoint = endpoint;
		}

		void rol(RegistroAccionesMiembros.Accion accion, RolMembresia rol) {
			registro.registrar(accion, endpoint, valoresRol(rol, false));
			alguno = true;
		}

		void sedes(Set<Long> sedes, boolean sinCambios) {
			Map<String, Object> valores = new LinkedHashMap<>();
			valores.put("usuario", usuarioId);
			valores.put("sedes", List.copyOf(sedes));
			if (sinCambios) {
				valores.put("sinCambios", Boolean.TRUE);
			}
			registro.registrar(RegistroAccionesMiembros.Accion.SEDES_CAMBIADAS, endpoint, valores);
			alguno = true;
		}

		void asignacion(RegistroAccionesProfesor.Accion accion, Long grupoId, RolAsignacionProfesor rol) {
			Map<String, Object> valores = new LinkedHashMap<>();
			valores.put("grupo", grupoId);
			valores.put("usuario", usuarioId);
			valores.put("rol", rol);
			registroProfesor.registrar(accion, endpoint, valores);
			alguno = true;
		}

		/** Si nada cambió, la acción con {@code sinCambios=SI}: todo 2xx de estas rutas deja su fila explícita. */
		void sinCambiosSiHaceFalta(RegistroAccionesMiembros.Accion accion, RolMembresia rol) {
			if (!alguno) {
				registro.registrar(accion, endpoint, valoresRol(rol, true));
				alguno = true;
			}
		}

		/** Reemplazar grupos sin cambiar nada: {@code GRUPOS_CAMBIADOS usuario=#id sinCambios=SI}. */
		void gruposSinCambiosSiHaceFalta() {
			if (!alguno) {
				Map<String, Object> valores = new LinkedHashMap<>();
				valores.put("usuario", usuarioId);
				valores.put("sinCambios", Boolean.TRUE);
				registro.registrar(RegistroAccionesMiembros.Accion.GRUPOS_CAMBIADOS, endpoint, valores);
				alguno = true;
			}
		}

		private Map<String, Object> valoresRol(RolMembresia rol, boolean sinCambios) {
			Map<String, Object> valores = new LinkedHashMap<>();
			valores.put("usuario", usuarioId);
			valores.put("rol", rol);
			if (sinCambios) {
				valores.put("sinCambios", Boolean.TRUE);
			}
			return valores;
		}
	}

	// ------------------------------------------------------------------ validaciones

	/** Cargo del cuerpo: ausente o {@code null}, no se toca; recortado, en blanco, se borra; más de 100, 400. */
	record Cargo(boolean fijar, String valor) {
		static Cargo de(String cargo) {
			if (cargo == null) {
				return new Cargo(false, null);
			}
			String recortado = cargo.strip();
			if (recortado.codePointCount(0, recortado.length()) > CARGO_MAXIMO) {
				throw new IllegalArgumentException(CARGO_LARGO);
			}
			return new Cargo(true, recortado.isEmpty() ? null : recortado);
		}
	}

	/** Uno de los cinco roles, tal cual; otro valor o ninguno, 400. */
	private static RolMembresia rolDesde(String rol) {
		if (rol != null) {
			for (RolMembresia valor : RolMembresia.values()) {
				if (valor.name().equals(rol)) {
					return valor;
				}
			}
		}
		throw new IllegalArgumentException(ROL_NO_VALIDO);
	}

	/** Rol del alta (D8-6): PROPIETARIO, ADMINISTRADOR, RESPONSABLE_SEDE o PROFESOR; FAMILIA, 400 propio. */
	private static RolMembresia rolDeAlta(String rol) {
		RolMembresia valor = rolDesde(rol);
		if (valor == RolMembresia.FAMILIA) {
			throw new IllegalArgumentException(FAMILIA_EN_ALTA);
		}
		return valor;
	}

	/** Rol de {@code /roles/{rol}}. FAMILIA (decisión 396), solo el propietario: 403 en todos los casos, sin mirar más. */
	private static RolMembresia rolDeLaRuta(String rol) {
		RolMembresia valor = rolDesde(rol);
		if (valor == RolMembresia.FAMILIA && !sesionDePropietario()) {
			throw new SecurityException(SOLO_PROPIETARIO);
		}
		return valor;
	}

	/** Sedes pedidas: al menos una, sin nulas ni repetidas. */
	private static Set<Long> idsDeSedes(List<Long> sedeIds) {
		if (sedeIds == null || sedeIds.isEmpty()) {
			throw new IllegalArgumentException(SIN_SEDES);
		}
		Set<Long> ids = new TreeSet<>();
		for (Long id : sedeIds) {
			if (id == null) {
				throw new IllegalArgumentException(SEDE_AJENA);
			}
			if (!ids.add(id)) {
				throw new IllegalArgumentException(SEDES_REPETIDAS);
			}
		}
		return ids;
	}

	/** Asignaciones pedidas, en su orden: grupo obligatorio y sin repetir, rol TITULAR o AYUDANTE. */
	private static Map<Long, RolAsignacionProfesor> asignacionesDe(List<MiembrosRequests.Asignacion> lista) {
		Map<Long, RolAsignacionProfesor> resultado = new LinkedHashMap<>();
		for (MiembrosRequests.Asignacion asignacion : lista) {
			if (asignacion == null || asignacion.grupoId() == null) {
				throw new IllegalArgumentException(AsignacionProfesorService.GRUPO_NO_EXISTE);
			}
			RolAsignacionProfesor rol = AsignacionProfesorService.rolDesde(asignacion.rol());
			if (resultado.put(asignacion.grupoId(), rol) != null) {
				throw new IllegalArgumentException(GRUPOS_REPETIDOS);
			}
		}
		return resultado;
	}

	private void exigirSedesDeLaEscuela(Set<Long> ids, Long organizacion) {
		if (new HashSet<>(consultas.sedesDeLaEscuela(organizacion, ids)).size() != ids.size()) {
			throw new IllegalArgumentException(SEDE_AJENA);
		}
	}

	private void exigirGruposDeLaEscuela(Collection<Long> ids, Long organizacion) {
		if (!ids.isEmpty() && new HashSet<>(consultas.gruposDeLaEscuela(organizacion, ids)).size() != ids.size()) {
			throw new NoSuchElementException(AsignacionProfesorService.GRUPO_NO_EXISTE);
		}
	}

	/** D8-8: PROPIETARIO y ADMINISTRADOR solo los da, reactiva o quita {@code ROLE_ADMIN} (con MANAGER también). */
	private static void exigirPropietarioPara(RolMembresia rol) {
		if ((rol == RolMembresia.PROPIETARIO || rol == RolMembresia.ADMINISTRADOR) && !sesionDePropietario()) {
			throw new SecurityException(SOLO_PROPIETARIO);
		}
	}

	private static boolean sesionDePropietario() {
		Authentication sesion = SecurityContextHolder.getContext().getAuthentication();
		return sesion != null && sesion.getAuthorities().stream()
				.anyMatch(a -> Roles.ROLE_ADMIN.name().equals(a.getAuthority()));
	}

	/** La cuenta, si es miembro de la escuela (como {@code esUsuarioDeOrganizacion}); si no, 404. */
	private Usuario exigirMiembro(Long usuarioId, Long organizacion) {
		if (usuarioId == null) {
			throw new NoSuchElementException(NO_MIEMBRO);
		}
		return usuarios.findById(usuarioId)
				.filter(u -> membresiaService.esUsuarioDeOrganizacion(u.getId(), organizacion))
				.orElseThrow(() -> new NoSuchElementException(NO_MIEMBRO));
	}

	/** La membresía del rol de la cuenta en la escuela ({@code organizacion_id} explícito). */
	private Optional<Membresia> membresiaDe(Long usuarioId, Long organizacion, RolMembresia rol) {
		return membresias.findByUsuarioIdAndOrganizacionId(usuarioId, organizacion).stream()
				.filter(m -> m.getRol() == rol).findFirst();
	}

	private static String enPantalla(RolMembresia rol) {
		return switch (rol) {
			case PROPIETARIO -> "propietario";
			case ADMINISTRADOR -> "gestor";
			case RESPONSABLE_SEDE -> "responsable de sede";
			case PROFESOR -> "profesor";
			case FAMILIA -> "familia";
		};
	}

	/** Patrón LIKE en minúsculas con {@code !} como escape: {@code %} y {@code _} cuentan como texto. */
	private static String patron(String busqueda) {
		String escapada = busqueda.toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return "%" + escapada + "%";
	}

	private boolean esPorDefecto(Long organizacion) {
		return Objects.equals(resolutor.getOrganizacionPorDefecto(), organizacion);
	}

	private static Long organizacionActiva() {
		Long organizacion = TenantContext.get();
		if (organizacion == null) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		return organizacion;
	}

	// ------------------------------------------------------------------ respuesta

	private MiembrosDTOs.Ficha ficha(Usuario cuenta, Long organizacion) {
		Datos datos = datosDe(organizacion, List.of(cuenta.getId()));
		List<MiembrosDTOs.Grupo> grupos = consultas.gruposDe(organizacion, cuenta.getId()).stream()
				.map(f -> new MiembrosDTOs.Grupo((Long) f[0], (String) f[1], (Long) f[2], (RolAsignacionProfesor) f[3]))
				.toList();
		return new MiembrosDTOs.Ficha(cuenta.getId(), cuenta.getNombre(), cuenta.getApellidos(), cuenta.getEmail(),
				cuenta.getAuthProvider(), cuenta.invitacionPendiente(), datos.roles(cuenta.getId()),
				datos.sedes(cuenta.getId()), grupos.size(), grupos, estadoContrasena(cuenta, organizacion));
	}

	/**
	 * Si la contraseña se puede restablecer desde esta escuela o por qué no (pieza 9-8). Una consulta como mucho y solo en
	 * la ficha: la lista no lo calcula.
	 */
	private MiembrosDTOs.EstadoContrasena estadoContrasena(Usuario cuenta, Long organizacion) {
		if (cuenta.invitacionPendiente()) {
			return MiembrosDTOs.EstadoContrasena.INVITACION_PENDIENTE;
		}
		if (cuenta.getAuthProvider() == AuthProvider.GOOGLE) {
			return MiembrosDTOs.EstadoContrasena.GOOGLE;
		}
		return membresiaService.puedeGestionarUsuarioGlobal(cuenta.getId(), organizacion)
				? MiembrosDTOs.EstadoContrasena.RESTABLECIBLE
				: MiembrosDTOs.EstadoContrasena.OTRA_ESCUELA;
	}

	/** Invitación pendiente de una fila de la lista (LOCAL o sin proveedor y sin contraseña), sin consulta nueva. */
	private static boolean pendiente(MiembrosRepository.FilaCuenta fila) {
		return Boolean.TRUE.equals(fila.getSinContrasena())
				&& (fila.getAuthProvider() == null || fila.getAuthProvider() == AuthProvider.LOCAL);
	}

	/** Membresías, sedes y número de grupos de esas cuentas: tres consultas, sean cuantas sean. */
	private Datos datosDe(Long organizacion, List<Long> usuarioIds) {
		if (usuarioIds.isEmpty()) {
			return new Datos(Map.of(), Map.of(), Map.of());
		}
		Map<Long, List<Membresia>> porCuenta = new HashMap<>();
		Map<Long, Long> responsablePorCuenta = new HashMap<>();
		for (Membresia m : consultas.membresiasDe(organizacion, usuarioIds)) {
			porCuenta.computeIfAbsent(m.getUsuarioId(), k -> new ArrayList<>()).add(m);
			if (m.getRol() == RolMembresia.RESPONSABLE_SEDE) {
				responsablePorCuenta.put(m.getUsuarioId(), m.getId());
			}
		}
		Map<Long, List<MiembrosDTOs.Sede>> sedesPorMembresia = new HashMap<>();
		if (!responsablePorCuenta.isEmpty()) {
			for (Object[] f : consultas.sedesDeMembresias(organizacion, responsablePorCuenta.values())) {
				sedesPorMembresia.computeIfAbsent((Long) f[0], k -> new ArrayList<>())
						.add(new MiembrosDTOs.Sede((Long) f[1], (String) f[2]));
			}
		}
		Map<Long, List<MiembrosDTOs.Sede>> sedes = new HashMap<>();
		responsablePorCuenta.forEach((cuenta, membresia) -> sedes.put(cuenta,
				sedesPorMembresia.getOrDefault(membresia, List.of())));
		Map<Long, Integer> grupos = new HashMap<>();
		for (Object[] f : consultas.numeroDeGrupos(organizacion, usuarioIds)) {
			grupos.put((Long) f[0], ((Number) f[1]).intValue());
		}
		return new Datos(porCuenta, sedes, grupos);
	}

	private record Datos(Map<Long, List<Membresia>> membresias, Map<Long, List<MiembrosDTOs.Sede>> sedesPorCuenta,
			Map<Long, Integer> grupos) {

		List<MiembrosDTOs.Rol> roles(Long usuarioId) {
			return membresias.getOrDefault(usuarioId, List.of()).stream()
					.sorted((a, b) -> a.getRol().compareTo(b.getRol()))
					.map(m -> new MiembrosDTOs.Rol(m.getRol(), m.isActiva(), m.getCargo()))
					.toList();
		}

		List<MiembrosDTOs.Sede> sedes(Long usuarioId) {
			return sedesPorCuenta.getOrDefault(usuarioId, List.of());
		}

		int numeroGrupos(Long usuarioId) {
			return grupos.getOrDefault(usuarioId, 0);
		}
	}
}
