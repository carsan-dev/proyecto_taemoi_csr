package com.taemoi.project.services;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.taemoi.project.dtos.request.DatosEscuelaRequest;
import com.taemoi.project.dtos.response.DatosEscuelaDTO;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.OrganizacionRepository;

/**
 * Pieza 394: datos de la escuela ACTIVA (los de la sesión; ningún id llega del cliente): nombre legal, CIF, dirección
 * fiscal, contacto, firmante de los documentos y día límite de la mensualidad (decisiones 267 y 375). Quién puede, en
 * {@code EscuelaDatosController} y {@code SecurityConfiguration}.
 *
 * <ul>
 * <li>Guardar sustituye todos los campos (formulario completo), ya validados y normalizados en
 * {@link DatosEscuelaRequest}. No toca el nombre visible, el logo, el color ni ningún otro dato de la escuela.</li>
 * <li>Bloquea antes la fila ({@code FOR UPDATE}, como el logo y el color): el {@code UPDATE} de la entidad lleva todas
 * las columnas y así no pisa un cambio de otra petición a la vez.</li>
 * <li>Cada 2xx deja UNA fila en el registro ({@link RegistroAccionesPanel}, misma transacción), también el que no
 * cambia nada ({@code sinCambios=SI}), con el id de la escuela y si cambió el día; nunca los datos. Por eso la ruta está
 * en {@code AuditoriaRequestFilter.EscrituraConRegistroPropio}, y sus 4xx, con esas claves redactadas.</li>
 * <li>Tras confirmar, olvida la lectura en memoria del día límite ({@link DiaLimiteMensualidadService#olvidar}): el
 * pendiente vencido aplica el día nuevo al momento.</li>
 * </ul>
 */
@Service
public class DatosEscuelaService {

	private static final String RUTA = "/api/escuela/datos";

	private final OrganizacionRepository organizaciones;
	private final RegistroAccionesPanel registro;
	private final DiaLimiteMensualidadService diaLimite;

	public DatosEscuelaService(OrganizacionRepository organizaciones, RegistroAccionesPanel registro,
			DiaLimiteMensualidadService diaLimite) {
		this.organizaciones = organizaciones;
		this.registro = registro;
		this.diaLimite = diaLimite;
	}

	@Transactional(readOnly = true)
	public DatosEscuelaDTO obtener() {
		Long organizacion = organizacionActiva();
		return DatosEscuelaDTO.de(organizaciones.findById(organizacion).orElseThrow(DatosEscuelaService::denegado));
	}

	/** Sustituye los datos de la escuela activa y devuelve lo mismo que {@link #obtener}. */
	@Transactional
	public DatosEscuelaDTO guardar(DatosEscuelaRequest datos) {
		if (datos == null) {
			throw new IllegalArgumentException("Faltan los datos de la escuela.");
		}
		Long organizacion = organizacionActiva();
		Organizacion escuela = organizaciones.findByIdParaActualizar(organizacion)
				.orElseThrow(DatosEscuelaService::denegado);

		boolean diaCambiado = !Objects.equals(datos.diaLimiteMensualidad(), escuela.getDiaLimiteMensualidad());
		boolean sinCambios = !diaCambiado && Objects.equals(datos.nombreLegal(), escuela.getNombreLegal())
				&& Objects.equals(datos.cif(), escuela.getCif()) && Objects.equals(datos.direccion(), escuela.getDireccion())
				&& Objects.equals(datos.emailContacto(), escuela.getEmailContacto())
				&& Objects.equals(datos.telefono(), escuela.getTelefono())
				&& Objects.equals(datos.firmanteNombre(), escuela.getFirmanteNombre())
				&& Objects.equals(datos.firmanteCargo(), escuela.getFirmanteCargo())
				&& Objects.equals(datos.firmanteNif(), escuela.getFirmanteNif());

		escuela.setNombreLegal(datos.nombreLegal());
		escuela.setCif(datos.cif());
		escuela.setDireccion(datos.direccion());
		escuela.setEmailContacto(datos.emailContacto());
		escuela.setTelefono(datos.telefono());
		escuela.setFirmanteNombre(datos.firmanteNombre());
		escuela.setFirmanteCargo(datos.firmanteCargo());
		escuela.setFirmanteNif(datos.firmanteNif());
		escuela.setDiaLimiteMensualidad(datos.diaLimiteMensualidad());
		organizaciones.save(escuela);

		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("organizacion", organizacion);
		valores.put("diaLimiteCambiado", diaCambiado);
		valores.put("sinCambios", sinCambios);
		registro.registrar(RegistroAccionesPanel.Accion.DATOS_ESCUELA_CAMBIADOS, RUTA, valores);

		olvidarAlConfirmar(organizacion);
		return DatosEscuelaDTO.de(escuela);
	}

	private void olvidarAlConfirmar(Long organizacion) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					diaLimite.olvidar(organizacion);
				}
			});
		} else {
			diaLimite.olvidar(organizacion);
		}
	}

	private static Long organizacionActiva() {
		Long organizacion = TenantContext.get();
		if (organizacion == null) {
			throw denegado();
		}
		return organizacion;
	}

	private static AccessDeniedException denegado() {
		return new AccessDeniedException("Acceso denegado.");
	}
}
