package com.taemoi.project.services;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.response.GrupoProfesoresDTOs;
import com.taemoi.project.entities.AsignacionProfesor;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.RolAsignacionProfesor;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AsignacionProfesorRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.utils.EmailUtils;

/**
 * Asignación de profesores a un grupo por propietario y gestor (pieza E7-4; decisión 208, D7-19 a D7-21;
 * fase3-e7-plan.md §5).
 *
 * <p>{@code asignacion_profesor} y {@code membresia} NO son {@code @TenantId}: cada consulta y cada escritura lleva
 * {@code organizacion_id} explícito, el de la escuela activa ({@link TenantContext}). El grupo se lee con
 * {@link GrupoRepository} (acotado por {@code @TenantId}) y se comprueba otra vez su organización: un grupo de otra
 * escuela o inexistente da 404 antes de leer o escribir nada más.
 *
 * <p>Asignar crea la membresía PROFESOR de la escuela si falta (activa; {@code RolMembresia.desdeAutoridad} rechaza
 * PROFESOR, así que va con {@code new Membresia}); si está desactivada, 409 {@link #PROFESOR_DESACTIVADO} (reactivar
 * es de la E8). Ninguna otra membresía se toca. Quitar borra solo la fila de {@code asignacion_profesor}; la membresía
 * queda (D7-21). El alcance del profesor se recalcula en cada petición ({@code IdentificadorSesionJwt}), así que los
 * cambios valen en su siguiente petición.
 *
 * <p>Cada cambio queda en {@link RegistroAccionesProfesor} en la misma transacción, solo con ids y enumeraciones (nunca
 * el email).
 */
@Service
public class AsignacionProfesorService {

	public static final String EMAIL_SIN_CUENTA = "No hay ninguna cuenta con ese email. Que se registre primero.";

	public static final String PROFESOR_DESACTIVADO = "La cuenta tiene el rol de profesor desactivado en esta escuela";

	static final String EMAIL_OBLIGATORIO = "El email es obligatorio";
	static final String ROL_NO_VALIDO = "El rol debe ser TITULAR o AYUDANTE";
	static final String GRUPO_NO_EXISTE = "El grupo no existe.";
	static final String NO_ASIGNADO = "La cuenta no está asignada a este grupo.";

	/** Titulares primero; después por apellidos, nombre e id. */
	private static final Comparator<GrupoProfesoresDTOs.Profesor> ORDEN = Comparator
			.comparing(GrupoProfesoresDTOs.Profesor::rol)
			.thenComparing(GrupoProfesoresDTOs.Profesor::apellidos, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
			.thenComparing(GrupoProfesoresDTOs.Profesor::nombre, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
			.thenComparing(GrupoProfesoresDTOs.Profesor::usuarioId);

	private final GrupoRepository grupos;
	private final UsuarioRepository usuarios;
	private final MembresiaRepository membresias;
	private final AsignacionProfesorRepository asignaciones;
	private final RegistroAccionesProfesor registro;

	public AsignacionProfesorService(GrupoRepository grupos, UsuarioRepository usuarios, MembresiaRepository membresias,
			AsignacionProfesorRepository asignaciones, RegistroAccionesProfesor registro) {
		this.grupos = grupos;
		this.usuarios = usuarios;
		this.membresias = membresias;
		this.asignaciones = asignaciones;
		this.registro = registro;
	}

	/** Profesores del grupo (titulares primero). Grupo de otra escuela o inexistente: 404. */
	@Transactional(readOnly = true)
	public List<GrupoProfesoresDTOs.Profesor> listar(Long grupoId) {
		Long organizacion = organizacionActiva();
		exigirGrupo(grupoId, organizacion);
		List<AsignacionProfesor> filas = asignaciones.findByGrupoIdAndOrganizacionId(grupoId, organizacion);
		if (filas.isEmpty()) {
			return List.of();
		}
		Map<Long, Usuario> cuentas = new HashMap<>();
		usuarios.findAllById(filas.stream().map(AsignacionProfesor::getUsuarioId).toList())
				.forEach(u -> cuentas.put(u.getId(), u));
		return filas.stream()
				.filter(a -> cuentas.containsKey(a.getUsuarioId()))
				.map(a -> profesor(cuentas.get(a.getUsuarioId()), a.getRol()))
				.sorted(ORDEN)
				.toList();
	}

	/**
	 * Como {@link #asignar(Long, String, RolAsignacionProfesor)} con el rol en texto, tal como llega en el cuerpo: email
	 * vacío, 400 {@link #EMAIL_OBLIGATORIO}; rol que no es {@code TITULAR} ni {@code AYUDANTE}, 400 {@link #ROL_NO_VALIDO}.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public GrupoProfesoresDTOs.Profesor asignar(Long grupoId, String email, String rol) {
		exigirEmail(email);
		return asignar(grupoId, email, rolDesde(rol));
	}

	/**
	 * Asigna la cuenta con ese email al grupo con ese rol, o le cambia el rol si ya estaba asignada (PK
	 * {@code usuario_id, grupo_id}). Email vacío o rol no válido: 400; grupo ajeno: 404; sin cuenta: 404
	 * {@link #EMAIL_SIN_CUENTA}; membresía PROFESOR desactivada: 409 {@link #PROFESOR_DESACTIVADO}, sin escribir nada.
	 *
	 * <p>Concurrencia: {@code READ_COMMITTED} y la fila de la cuenta bloqueada antes de leer su membresía y su
	 * asignación. Una segunda alta simultánea de la misma cuenta espera y ve lo que confirmó la primera: no choca con la
	 * clave única de {@code membresia} ni con la PK de {@code asignacion_profesor}.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public GrupoProfesoresDTOs.Profesor asignar(Long grupoId, String email, RolAsignacionProfesor rol) {
		String normalizado = exigirEmail(email);
		if (rol == null) {
			throw new IllegalArgumentException(ROL_NO_VALIDO);
		}
		Long organizacion = organizacionActiva();
		exigirGrupo(grupoId, organizacion);
		Usuario cuenta = usuarios.findByEmailIgnoreCase(normalizado)
				.orElseThrow(() -> new NoSuchElementException(EMAIL_SIN_CUENTA));
		Long usuarioId = cuenta.getId();
		usuarios.bloquearPorId(usuarioId).orElseThrow(() -> new NoSuchElementException(EMAIL_SIN_CUENTA));

		boolean membresiaCreada = asegurarMembresiaProfesor(usuarioId, organizacion);
		String endpoint = ruta(grupoId);
		if (membresiaCreada) {
			registro.registrar(RegistroAccionesProfesor.Accion.MEMBRESIA_PROFESOR_CREADA, endpoint,
					Map.of("usuario", usuarioId));
		}

		Optional<AsignacionProfesor> existente = asignaciones.findByUsuarioIdAndGrupoIdAndOrganizacionId(usuarioId,
				grupoId, organizacion);
		if (existente.isEmpty()) {
			asignaciones.saveAndFlush(new AsignacionProfesor(organizacion, usuarioId, grupoId, rol));
			registro.registrar(RegistroAccionesProfesor.Accion.ASIGNACION_CREADA, endpoint,
					valores(grupoId, usuarioId, rol));
		} else if (existente.get().getRol() != rol) {
			AsignacionProfesor asignacion = existente.get();
			asignacion.setRol(rol);
			asignaciones.saveAndFlush(asignacion);
			registro.registrar(RegistroAccionesProfesor.Accion.ASIGNACION_ROL_CAMBIADO, endpoint,
					valores(grupoId, usuarioId, rol));
		}
		return profesor(cuenta, rol);
	}

	/**
	 * Quita la asignación de la cuenta al grupo (solo esa fila; la membresía queda, D7-21). Grupo ajeno: 404; la cuenta no
	 * está asignada a ese grupo en esta escuela (o otra petición ya la quitó): 404.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void quitar(Long grupoId, Long usuarioId) {
		Long organizacion = organizacionActiva();
		exigirGrupo(grupoId, organizacion);
		AsignacionProfesor asignacion = asignaciones
				.findByUsuarioIdAndGrupoIdAndOrganizacionId(usuarioId, grupoId, organizacion)
				.orElseThrow(() -> new NoSuchElementException(NO_ASIGNADO));
		RolAsignacionProfesor rol = asignacion.getRol();
		if (asignaciones.borrar(usuarioId, grupoId, organizacion) == 0) {
			throw new NoSuchElementException(NO_ASIGNADO);
		}
		registro.registrar(RegistroAccionesProfesor.Accion.ASIGNACION_QUITADA, ruta(grupoId) + "/" + usuarioId,
				valores(grupoId, usuarioId, rol));
	}

	/** {@code TITULAR} o {@code AYUDANTE}, tal cual; cualquier otro valor (o ninguno): 400. */
	static RolAsignacionProfesor rolDesde(String rol) {
		if (rol != null) {
			for (RolAsignacionProfesor valor : RolAsignacionProfesor.values()) {
				if (valor.name().equals(rol)) {
					return valor;
				}
			}
		}
		throw new IllegalArgumentException(ROL_NO_VALIDO);
	}

	/**
	 * Membresía PROFESOR de la cuenta en la escuela, {@code organizacion_id} explícito: si no existe la crea activa y
	 * devuelve {@code true}; si está desactivada, 409. Las demás membresías no se tocan.
	 */
	private boolean asegurarMembresiaProfesor(Long usuarioId, Long organizacion) {
		Optional<Membresia> profesor = membresias.findByUsuarioIdAndOrganizacionId(usuarioId, organizacion).stream()
				.filter(m -> m.getRol() == RolMembresia.PROFESOR)
				.findFirst();
		if (profesor.isPresent()) {
			if (!profesor.get().isActiva()) {
				throw new IllegalStateException(PROFESOR_DESACTIVADO);
			}
			return false;
		}
		membresias.saveAndFlush(new Membresia(usuarioId, organizacion, RolMembresia.PROFESOR));
		return true;
	}

	/** El email normalizado (sin espacios, en minúsculas); vacío o ausente: 400. */
	private static String exigirEmail(String email) {
		String normalizado = EmailUtils.normalizeEmail(email);
		if (normalizado == null || normalizado.isEmpty()) {
			throw new IllegalArgumentException(EMAIL_OBLIGATORIO);
		}
		return normalizado;
	}

	private void exigirGrupo(Long grupoId, Long organizacion) {
		Grupo grupo = grupoId == null ? null : grupos.findById(grupoId).orElse(null);
		if (grupo == null || !Objects.equals(grupo.getOrganizacionId(), organizacion)) {
			throw new NoSuchElementException(GRUPO_NO_EXISTE);
		}
	}

	private static Long organizacionActiva() {
		Long organizacion = TenantContext.get();
		if (organizacion == null) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		return organizacion;
	}

	private static Map<String, Object> valores(Long grupoId, Long usuarioId, RolAsignacionProfesor rol) {
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("grupo", grupoId);
		valores.put("usuario", usuarioId);
		valores.put("rol", rol);
		return valores;
	}

	private static String ruta(Long grupoId) {
		return "/api/grupos/" + grupoId + "/profesores";
	}

	private static GrupoProfesoresDTOs.Profesor profesor(Usuario cuenta, RolAsignacionProfesor rol) {
		return new GrupoProfesoresDTOs.Profesor(cuenta.getId(), cuenta.getNombre(), cuenta.getApellidos(),
				cuenta.getEmail(), rol);
	}
}
