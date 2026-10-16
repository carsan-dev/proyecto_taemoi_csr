package com.taemoi.project.services;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;

import com.taemoi.project.dtos.response.AuditoriaEventoDTO;
import com.taemoi.project.dtos.response.AuditoriaEventoDetalleDTO;
import com.taemoi.project.entities.AuditoriaAccion;

public interface AuditoriaService {

	void registrarEvento(
			AuditoriaAccion accion,
			String metodoHttp,
			String endpoint,
			String queryParamsJson,
			String payloadJson,
			boolean payloadTruncado,
			Integer estadoHttp,
			String ipCliente,
			String userAgent);

	/**
	 * Registra el acceso a datos sensibles (p. ej. salud) en la transacción del llamador y SIN
	 * capturar errores: si no se puede auditar, el acceso falla. Guarda quién, cuándo, la acción y el
	 * recurso; nunca el contenido.
	 */
	void registrarAccesoSensible(AuditoriaAccion accion, String modulo, String endpoint, Long recursoId,
			String resumen);

	/**
	 * Eventos de la escuela activa según la vista (siempre explícita: no hay sobrecarga con vista implícita, para
	 * que ningún llamador obtenga el registro completo por accidente). En {@link VistaAuditoria#PROPIETARIO} el filtro
	 * {@code resultado=ERROR} devuelve una página vacía (misma forma) y {@code texto} busca solo en el resumen.
	 */
	Page<AuditoriaEventoDTO> obtenerEventos(
			VistaAuditoria vista,
			LocalDate desde,
			LocalDate hasta,
			String resultado,
			String accion,
			String modulo,
			String usuario,
			String endpoint,
			String texto,
			Integer page,
			Integer size,
			Boolean incluirRuido);

	/**
	 * Detalle de un evento de la escuela activa; 404 si no existe o no entra en la vista. En
	 * {@link VistaAuditoria#PROPIETARIO}, sin payload ni parámetros.
	 */
	AuditoriaEventoDetalleDTO obtenerEventoPorId(VistaAuditoria vista, Long id);

	/** Módulos con algún evento de la vista en la escuela activa. */
	List<String> obtenerModulosDisponibles(VistaAuditoria vista);

	long eliminarEventosAnterioresA(LocalDate fechaLimite);
}
