package com.taemoi.project.acceso;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.OrigenCobro;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.CobroRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.repositories.SolicitudRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AccesoFamiliaService;
import com.taemoi.project.utils.EmailUtils;

/**
 * Implementación de {@link ConsultaAccesoRecursos} sobre entidades {@code @TenantId} (consultas JPA con los
 * {@link PredicadosAlcance}): solo ve la escuela activa. Sin escuela activa, con id nulo o con un conjunto vacío
 * responde {@code false} sin consultar.
 *
 * <p>La familia conserva exactamente la regla anterior a la entrega 5c ({@code AlumnoAccessControlServiceImpl}):
 * escuela de la sesión, cuenta del principal (o, con otra autenticación, por su email) y
 * {@link AccesoFamiliaService#puedeAcceder}.
 */
@Component
public class ConsultaAccesoRecursosJpa implements ConsultaAccesoRecursos {

	private final AccesoFamiliaService accesoFamilia;
	private final UsuarioRepository usuarios;
	private final AlumnoRepository alumnos;
	private final GrupoRepository grupos;
	private final TurnoRepository turnos;
	private final PreinscripcionRepository preinscripciones;
	private final ProductoAlumnoRepository productosAlumno;
	private final CobroRepository cobros;
	private final SedeRepository sedes;
	private final SolicitudRepository solicitudes;

	public ConsultaAccesoRecursosJpa(AccesoFamiliaService accesoFamilia, UsuarioRepository usuarios,
			AlumnoRepository alumnos, GrupoRepository grupos, TurnoRepository turnos,
			PreinscripcionRepository preinscripciones, ProductoAlumnoRepository productosAlumno,
			CobroRepository cobros, SedeRepository sedes, SolicitudRepository solicitudes) {
		this.accesoFamilia = accesoFamilia;
		this.usuarios = usuarios;
		this.alumnos = alumnos;
		this.grupos = grupos;
		this.turnos = turnos;
		this.preinscripciones = preinscripciones;
		this.productosAlumno = productosAlumno;
		this.cobros = cobros;
		this.sedes = sedes;
		this.solicitudes = solicitudes;
	}

	@Override
	public boolean familiaVeAlumno(Authentication autenticacion, Long alumnoId) {
		if (autenticacion == null || alumnoId == null) {
			return false;
		}
		Long organizacionId = TenantContext.get();
		if (organizacionId == null) {
			return false;
		}
		Usuario usuario = cuentaDe(autenticacion);
		return usuario != null && accesoFamilia.puedeAcceder(usuario, organizacionId, alumnoId);
	}

	@Override
	public boolean alumnoEnSedes(Long alumnoId, Set<Long> sedes) {
		return existe(alumnos, alumnoId, sedes, PredicadosAlcance.alumnosEnSedes(sedes));
	}

	@Override
	public boolean alumnoConSedePrincipalEn(Long alumnoId, Set<Long> sedes) {
		return existe(alumnos, alumnoId, sedes, PredicadosAlcance.alumnosConSedePrincipalEn(sedes));
	}

	@Override
	public boolean alumnoEnGrupos(Long alumnoId, Set<Long> grupos) {
		return existe(alumnos, alumnoId, grupos, PredicadosAlcance.alumnosEnGrupos(grupos));
	}

	@Override
	public boolean grupoEnSedes(Long grupoId, Set<Long> sedes) {
		return existe(grupos, grupoId, sedes, PredicadosAlcance.gruposEnSedes(sedes));
	}

	@Override
	public boolean grupoEnGrupos(Long grupoId, Set<Long> grupos) {
		return existe(this.grupos, grupoId, grupos, PredicadosAlcance.gruposEnGrupos(grupos));
	}

	@Override
	public boolean turnoEnSedes(Long turnoId, Set<Long> sedes) {
		return existe(turnos, turnoId, sedes, PredicadosAlcance.turnosEnSedes(sedes));
	}

	@Override
	public boolean turnoEnGrupos(Long turnoId, Set<Long> grupos) {
		return existe(turnos, turnoId, grupos, PredicadosAlcance.turnosEnGrupos(grupos));
	}

	@Override
	public boolean preinscripcionEnSedes(String referencia, Set<Long> sedes) {
		return existePreinscripcion(referencia, sedes, PredicadosAlcance.preinscripcionesEnSedes(sedes));
	}

	@Override
	public boolean preinscripcionEnGrupos(String referencia, Set<Long> grupos) {
		return existePreinscripcion(referencia, grupos, PredicadosAlcance.preinscripcionesEnGrupos(grupos));
	}

	@Override
	public boolean alumnoConvocatoriaEnSedes(Long alumnoConvocatoriaId, Set<Long> sedes) {
		return existeAlumnoDeInscripcion(alumnoConvocatoriaId, sedes, PredicadosAlcance.alumnosEnSedes(sedes));
	}

	@Override
	public boolean alumnoConvocatoriaConSedePrincipalEn(Long alumnoConvocatoriaId, Set<Long> sedes) {
		return existeAlumnoDeInscripcion(alumnoConvocatoriaId, sedes,
				PredicadosAlcance.alumnosConSedePrincipalEn(sedes));
	}

	@Override
	public boolean productoAlumnoEnSedes(Long productoAlumnoId, Set<Long> sedes) {
		if (productoAlumnoId == null || sedes == null || sedes.isEmpty() || TenantContext.get() == null) {
			return false;
		}
		return productosAlumno.existsByIdAndSedeIdIn(productoAlumnoId, sedes);
	}

	@Override
	public boolean productoAlumnoEnGrupos(Long productoAlumnoId, Set<Long> grupos) {
		if (productoAlumnoId == null || grupos == null || grupos.isEmpty() || TenantContext.get() == null) {
			return false;
		}
		return productosAlumno.contarCargoDeAlumnoEnGrupos(productoAlumnoId, grupos) > 0;
	}

	@Override
	public boolean cobroDelProfesor(Long cobroId, Long usuarioId) {
		if (cobroId == null || usuarioId == null || TenantContext.get() == null) {
			return false;
		}
		return cobros.existsByIdAndCobradoPorUsuarioIdAndOrigen(cobroId, usuarioId, OrigenCobro.PROFESOR);
	}

	@Override
	public boolean sedeEnSedes(Long sedeId, Set<Long> conjunto) {
		if (sedeId == null || conjunto == null || !conjunto.contains(sedeId) || TenantContext.get() == null) {
			return false;
		}
		return sedes.buscarEnEscuelaActiva(sedeId).isPresent();
	}

	@Override
	public boolean solicitudEnSedes(Long solicitudId, Set<Long> conjunto) {
		if (solicitudId == null || conjunto == null || conjunto.isEmpty() || TenantContext.get() == null) {
			return false;
		}
		return solicitudes.existsByIdAndSedeIdIn(solicitudId, conjunto);
	}

	/** El alumno de la inscripción {@code alumnoConvocatoriaId} (de la escuela activa) cumple el predicado. */
	private boolean existeAlumnoDeInscripcion(Long alumnoConvocatoriaId, Set<Long> sedes,
			Specification<Alumno> predicado) {
		if (alumnoConvocatoriaId == null || sedes == null || sedes.isEmpty() || TenantContext.get() == null) {
			return false;
		}
		return alumnos.exists(
				PredicadosAlcance.alumnoDeInscripcionEnConvocatoria(alumnoConvocatoriaId).and(predicado));
	}

	private static <T> boolean existe(JpaSpecificationExecutor<T> repositorio, Long id, Set<Long> conjunto,
			Specification<T> predicado) {
		if (id == null || conjunto == null || conjunto.isEmpty() || TenantContext.get() == null) {
			return false;
		}
		Specification<T> porId = (root, query, cb) -> cb.equal(root.get("id"), id);
		return repositorio.exists(porId.and(predicado));
	}

	private boolean existePreinscripcion(String referencia, Set<Long> conjunto,
			Specification<Preinscripcion> predicado) {
		if (referencia == null || conjunto == null || conjunto.isEmpty() || TenantContext.get() == null) {
			return false;
		}
		Specification<Preinscripcion> porReferencia = (root, query, cb) -> cb.equal(root.get("referencia"),
				referencia);
		return preinscripciones.exists(porReferencia.and(predicado));
	}

	/** La sesión lleva la cuenta como principal; con otra autenticación se busca por su email. */
	private Usuario cuentaDe(Authentication autenticacion) {
		if (autenticacion.getPrincipal() instanceof Usuario usuario) {
			return usuario;
		}
		String email = EmailUtils.normalizeEmail(autenticacion.getName());
		if (!EmailUtils.puedeVincularAlumnos(email)) {
			return null;
		}
		return usuarios.findByEmailIgnoreCase(email).orElse(null);
	}
}
