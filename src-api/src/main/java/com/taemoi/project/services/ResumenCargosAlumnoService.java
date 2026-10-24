package com.taemoi.project.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.response.ResumenCargosAlumnoDTO;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.ResumenCargosAlumnoRepository;
import com.taemoi.project.repositories.ResumenCargosAlumnoRepository.PagosAlumno;
import com.taemoi.project.repositories.ResumenCargosAlumnoRepository.ResumenCargos;

/**
 * Pieza 8b-3l (P-3, decisión 285): el resumen de cargos de los alumnos que el propietario va a eliminar para siempre,
 * para avisar en el diálogo de borrado. Solo de la escuela activa: los ids ajenos o inexistentes no salen. Como mucho
 * {@link #MAX_ALUMNOS} (una página de «Bajas y eliminaciones»).
 */
@Service
public class ResumenCargosAlumnoService {

	public static final int MAX_ALUMNOS = 25;

	private final ResumenCargosAlumnoRepository repositorio;

	public ResumenCargosAlumnoService(ResumenCargosAlumnoRepository repositorio) {
		this.repositorio = repositorio;
	}

	/** En el orden de los ids pedidos, sin repetir; un alumno sin cargos sale con ceros. */
	@Transactional(readOnly = true)
	public List<ResumenCargosAlumnoDTO> resumir(List<Long> alumnoIds) {
		if (alumnoIds == null || alumnoIds.isEmpty() || alumnoIds.size() > MAX_ALUMNOS) {
			throw new IllegalArgumentException("Indica entre 1 y " + MAX_ALUMNOS + " alumnos.");
		}
		Long organizacion = TenantContext.get();
		if (organizacion == null) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		Set<Long> pedidos = new LinkedHashSet<>(alumnoIds);
		Map<Long, ResumenCargos> porAlumno = new HashMap<>();
		for (ResumenCargos r : repositorio.resumir(organizacion, pedidos)) {
			porAlumno.put(r.getAlumnoId(), r);
		}
		Map<Long, Long> pagos = new HashMap<>();
		for (PagosAlumno p : repositorio.contarPagos(organizacion, pedidos)) {
			pagos.put(p.getAlumnoId(), valor(p.getPagos()));
		}
		List<ResumenCargosAlumnoDTO> resultado = new ArrayList<>();
		for (Long alumno : pedidos) {
			ResumenCargos r = porAlumno.get(alumno);
			if (r != null) {
				resultado.add(new ResumenCargosAlumnoDTO(alumno, valor(r.getCargos()), valor(r.getPagados()),
						valor(r.getPendientes()), importe(r.getImportePagado()), importe(r.getImportePendiente()),
						pagos.getOrDefault(alumno, 0L)));
			}
		}
		return resultado;
	}

	/**
	 * Pieza 8b-3l bis (decisión 287): cuántos pagos tiene el alumno en la escuela activa (definición en
	 * {@link ResumenCargosAlumnoRepository#contarPagos}); 0 si no es de esta escuela.
	 */
	@Transactional(readOnly = true)
	public long contarPagos(Long alumnoId) {
		Long organizacion = TenantContext.get();
		if (organizacion == null || alumnoId == null) {
			return 0;
		}
		return repositorio.contarPagos(organizacion, List.of(alumnoId)).stream().mapToLong(p -> valor(p.getPagos()))
				.sum();
	}

	private static long valor(Long n) {
		return n == null ? 0 : n;
	}

	private static BigDecimal importe(BigDecimal v) {
		return v == null ? BigDecimal.ZERO : v;
	}
}
