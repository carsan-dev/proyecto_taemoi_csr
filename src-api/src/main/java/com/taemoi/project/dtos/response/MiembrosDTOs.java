package com.taemoi.project.dtos.response;

import java.util.List;

import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.RolAsignacionProfesor;
import com.taemoi.project.entities.RolMembresia;

/**
 * Respuestas de la API de miembros (pieza E8-3; D8-4, D8-5). Solo datos de la escuela activa. E8-4 añade
 * {@code invitacionPendiente} (cuenta LOCAL que aún no ha creado su contraseña); la pieza 9-8, {@code contrasena} en la
 * ficha.
 */
public final class MiembrosDTOs {

	private MiembrosDTOs() {
	}

	/**
	 * Si el propietario puede restablecer la contraseña desde esta escuela o por qué no. Si se cumplen varios motivos,
	 * manda el primero en este orden: {@code INVITACION_PENDIENTE}, {@code GOOGLE}, {@code OTRA_ESCUELA}.
	 */
	public enum EstadoContrasena {
		RESTABLECIBLE, GOOGLE, OTRA_ESCUELA, INVITACION_PENDIENTE
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

	/** Ficha: la fila más sus grupos (por nombre e id del grupo) y {@code contrasena}. */
	public record Ficha(Long usuarioId, String nombre, String apellidos, String email, AuthProvider authProvider,
			boolean invitacionPendiente, List<Rol> roles, List<Sede> sedes, int numeroGrupos, List<Grupo> grupos,
			EstadoContrasena contrasena) {
	}

	/** Resultado de cancelar la invitación (decisión 389): sin ficha, porque la cuenta puede ya no existir. */
	public record InvitacionCancelada(boolean cuentaBorrada) {
	}

	/** Una página de la lista ({@code pagina} desde 0). */
	public record Pagina(List<Miembro> contenido, int pagina, int tamano, long total, int totalPaginas) {
	}
}
