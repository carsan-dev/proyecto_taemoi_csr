package com.taemoi.project.dtos.response;

import java.util.List;

import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.RolAsignacionProfesor;
import com.taemoi.project.entities.RolMembresia;

/**
 * Respuestas de la API de miembros (pieza E8-3; D8-4, D8-5). Solo datos de la escuela activa. E8-4 añade
 * {@code invitacionPendiente} (cuenta LOCAL que aún no ha creado su contraseña).
 */
public final class MiembrosDTOs {

	private MiembrosDTOs() {
	}

	/** Una membresía de la cuenta en la escuela (activa o no) y su cargo (texto libre, o {@code null}). */
	public record Rol(RolMembresia rol, boolean activa, String cargo) {
	}

	/** Una sede de su membresía RESPONSABLE_SEDE. */
	public record Sede(Long id, String nombre) {
	}

	/** Un grupo asignado a la cuenta como profesor. */
	public record Grupo(Long grupoId, String nombre, Long sedeId, RolAsignacionProfesor rol) {
	}

	/** Fila de la lista: roles en el orden del enum, sedes por id, número de grupos asignados en la escuela. */
	public record Miembro(Long usuarioId, String nombre, String apellidos, String email, AuthProvider authProvider,
			boolean invitacionPendiente, List<Rol> roles, List<Sede> sedes, int numeroGrupos) {
	}

	/** Ficha: la fila más sus grupos (por nombre e id del grupo). */
	public record Ficha(Long usuarioId, String nombre, String apellidos, String email, AuthProvider authProvider,
			boolean invitacionPendiente, List<Rol> roles, List<Sede> sedes, int numeroGrupos, List<Grupo> grupos) {
	}

	/** Resultado de cancelar la invitación (decisión 389): sin ficha, porque la cuenta puede ya no existir. */
	public record InvitacionCancelada(boolean cuentaBorrada) {
	}

	/** Una página de la lista ({@code pagina} desde 0). */
	public record Pagina(List<Miembro> contenido, int pagina, int tamano, long total, int totalPaginas) {
	}
}
