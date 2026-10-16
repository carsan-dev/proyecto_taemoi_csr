package com.taemoi.project.organizacion;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.taemoi.project.repositories.AlcanceRepository;

/**
 * Alcance de la sesión por sede y grupo (entrega 5b-2, docs/multi-escuela/entrega5-plan.md §3.2). Lo calcula
 * {@link IdentificadorSesionJwt} una vez por petición y lo lee {@link AlcanceActual}. Todavía no lo usa ningún
 * listado ni endpoint (entregas 5c, 6 y 7).
 *
 * @param total  {@code ROLE_ADMIN} o {@code ROLE_MANAGER}: ve toda la escuela.
 * @param sedes  sedes de sus membresías RESPONSABLE_SEDE activas (gestión por sede).
 * @param grupos grupos de {@code asignacion_profesor} con membresía PROFESOR activa (lectura por grupo).
 *               Se guardan por separado: la operación decide qué conjunto vale.
 */
public record AlcanceSesion(boolean total, Set<Long> sedes, Set<Long> grupos) implements Serializable {

	private static final long serialVersionUID = 1L;

	/** Gestión de la escuela (PROPIETARIO o ADMINISTRADOR). */
	public static final AlcanceSesion TOTAL = new AlcanceSesion(true, Set.of(), Set.of());

	/** Sin alcance: familia (su acceso sigue en {@code AccesoFamiliaService}), solo plataforma o sin sesión. */
	public static final AlcanceSesion NINGUNO = new AlcanceSesion(false, Set.of(), Set.of());

	public AlcanceSesion {
		sedes = sedes == null ? Set.of() : Set.copyOf(sedes);
		grupos = grupos == null ? Set.of() : Set.copyOf(grupos);
	}

	/** Alcance limitado a esas sedes y esos grupos. */
	public static AlcanceSesion limitado(Collection<Long> sedes, Collection<Long> grupos) {
		return new AlcanceSesion(false, sedes == null ? null : Set.copyOf(sedes),
				grupos == null ? null : Set.copyOf(grupos));
	}

	/** Alcance limitado a partir de las filas de {@link AlcanceRepository}; un tipo desconocido no da alcance. */
	public static AlcanceSesion desde(Collection<AlcanceRepository.FilaAlcance> filas) {
		Set<Long> sedes = new HashSet<>();
		Set<Long> grupos = new HashSet<>();
		for (AlcanceRepository.FilaAlcance fila : filas) {
			if (fila.getId() == null) {
				continue;
			}
			if (AlcanceRepository.TIPO_SEDE.equals(fila.getTipo())) {
				sedes.add(fila.getId());
			} else if (AlcanceRepository.TIPO_GRUPO.equals(fila.getTipo())) {
				grupos.add(fila.getId());
			}
		}
		return limitado(sedes, grupos);
	}

	public boolean veSede(Long sedeId) {
		return total || (sedeId != null && sedes.contains(sedeId));
	}

	public boolean veGrupo(Long grupoId) {
		return total || (grupoId != null && grupos.contains(grupoId));
	}

	/** Sin alcance total y sin ninguna sede ni grupo. */
	public boolean esVacio() {
		return !total && sedes.isEmpty() && grupos.isEmpty();
	}
}
