package com.taemoi.project.dtos.request;

import java.util.Set;

/**
 * {@code POST /api/sedes} y {@code PUT /api/sedes/{id}} (pieza E8-2, D8-2): los datos editables de una sede. Solo el
 * nombre es obligatorio; {@code aforoTurno} {@code null} = el límite de turno de la escuela. {@code PUT} es un
 * reemplazo: lo que no llega queda vacío. La activación va por {@code PUT /api/sedes/{id}/activa}.
 *
 * <p>Web por plantilla (W2): {@code slug}, {@code lugar} e {@code indicacionesAcceso}, opcionales. En {@code PUT}, si
 * no llegan se conservan (el panel de hoy no los envía); si llegan con {@code null} o en blanco, se vacían. Las fotos de
 * la sede no se escriben por aquí.
 */
public record SedeRequest(String nombre, String direccion, String localidad, String codigoPostal, String telefono,
		String email, String codigoClubFederacion, Integer aforoTurno, String slug, String lugar,
		String indicacionesAcceso) {

	/** Los campos de la web que {@code PUT} conserva si no llegan en el cuerpo. */
	public static final Set<String> CAMPOS_WEB = Set.of("slug", "lugar", "indicacionesAcceso");

	/**
	 * Esta petición con los campos de la web que no llegaron ({@code recibidos} no los contiene) tomados de los valores
	 * actuales.
	 */
	public SedeRequest conservandoWeb(Set<String> recibidos, String slugActual, String lugarActual,
			String indicacionesAccesoActuales) {
		return new SedeRequest(nombre, direccion, localidad, codigoPostal, telefono, email, codigoClubFederacion,
				aforoTurno, recibidos.contains("slug") ? slug : slugActual,
				recibidos.contains("lugar") ? lugar : lugarActual,
				recibidos.contains("indicacionesAcceso") ? indicacionesAcceso : indicacionesAccesoActuales);
	}
}
