package com.taemoi.project.services;

import java.time.Month;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.request.PerfilCuentaRequest;
import com.taemoi.project.dtos.response.PerfilCuentaDTO;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.UsuarioRepository;

/**
 * Perfil de la cuenta (decisión 322): el nombre por el que quiere que la llamen y, si no lo da una ficha, su cumpleaños.
 * Opera SOLO sobre la cuenta de la sesión: ningún id llega del cliente.
 *
 * <ul>
 * <li>La cuenta es global y la ficha es de una escuela: con ficha propia válida en la escuela activa
 * ({@link PermisosSesionService#cumpleFicha}) el cumpleaños del cuerpo se ignora, también si es imposible, y lo guardado
 * no se toca, porque la misma cuenta puede no tener ficha en otra escuela. No es un error.</li>
 * <li>Escribe en la entidad gestionada, nunca en el principal de la sesión (separado de este contexto de persistencia).
 * Bloquea antes la fila ({@code FOR UPDATE}) como primera sentencia de la transacción: la lectura siguiente ve lo último
 * confirmado, y el {@code UPDATE} de la entidad, que lleva todas las columnas, no pisa otro cambio.</li>
 * <li>Cada 2xx deja UNA fila en el registro ({@link RegistroAccionesPanel}, misma transacción), también el que no cambia
 * nada ({@code sinCambios=SI}), con el id de la cuenta y nunca el nombre ni la fecha. Por eso la ruta está en
 * {@code AuditoriaRequestFilter.EscrituraConRegistroPropio}, y sus 4xx, con esas claves redactadas.</li>
 * </ul>
 */
@Service
public class PerfilCuentaService {

	static final int MAX_NOMBRE = 40;

	static final String SIN_DATOS = "Faltan los datos del perfil.";
	static final String NOMBRE_LARGO = "El nombre no puede tener más de " + MAX_NOMBRE + " caracteres.";
	static final String NOMBRE_NO_VALIDO = "El nombre tiene caracteres no válidos.";
	static final String CUMPLE_INCOMPLETO = "Elige día y mes, o deja los dos vacíos.";
	static final String CUMPLE_IMPOSIBLE = "Esa fecha no existe.";

	private static final String RUTA = "/api/panel/mi-perfil";

	private final UsuarioRepository usuarios;
	private final PermisosSesionService permisos;
	private final RegistroAccionesPanel registro;

	public PerfilCuentaService(UsuarioRepository usuarios, PermisosSesionService permisos,
			RegistroAccionesPanel registro) {
		this.usuarios = usuarios;
		this.permisos = permisos;
		this.registro = registro;
	}

	@Transactional(readOnly = true)
	public PerfilCuentaDTO obtener() {
		Long organizacion = organizacionActiva();
		Long usuarioId = usuarioDeLaSesion();
		Usuario cuenta = usuarios.findById(usuarioId).orElseThrow(PerfilCuentaService::denegado);
		return dto(cuenta, permisos.cumpleFicha(usuarioId, organizacion));
	}

	/**
	 * Sustituye el perfil de la cuenta de la sesión y devuelve lo mismo que {@link #obtener}.
	 *
	 * @throws IllegalArgumentException (400) sin cuerpo, nombre no válido o, sin ficha, cumpleaños incompleto o imposible
	 */
	@Transactional
	public PerfilCuentaDTO guardar(PerfilCuentaRequest solicitud) {
		if (solicitud == null) {
			throw new IllegalArgumentException(SIN_DATOS);
		}
		Long organizacion = organizacionActiva();
		Long usuarioId = usuarioDeLaSesion();
		String nombre = normalizarNombre(solicitud.nombrePreferido());

		usuarios.bloquearPorId(usuarioId).orElseThrow(PerfilCuentaService::denegado);
		Usuario cuenta = usuarios.findById(usuarioId).orElseThrow(PerfilCuentaService::denegado);
		Optional<PerfilCuentaDTO.DiaMes> ficha = permisos.cumpleFicha(usuarioId, organizacion);

		Integer dia = ficha.isPresent() ? cuenta.getCumpleDia() : solicitud.cumpleDia();
		Integer mes = ficha.isPresent() ? cuenta.getCumpleMes() : solicitud.cumpleMes();
		if (ficha.isEmpty()) {
			validarCumple(dia, mes);
		}

		boolean sinCambios = Objects.equals(nombre, cuenta.getNombrePreferido())
				&& Objects.equals(dia, cuenta.getCumpleDia()) && Objects.equals(mes, cuenta.getCumpleMes());
		cuenta.setNombrePreferido(nombre);
		cuenta.setCumpleDia(dia);
		cuenta.setCumpleMes(mes);
		registro.registrar(RegistroAccionesPanel.Accion.PERFIL_CAMBIADO, RUTA, valores(usuarioId, sinCambios));
		return dto(cuenta, ficha);
	}

	/**
	 * Primero los caracteres, sobre el texto tal como llega: un salto de línea o un tabulador es un carácter de control,
	 * no un espacio que se pueda colapsar. Después recorta y colapsa los espacios (también los Unicode, como el de no
	 * separación); vacío pasa a {@code null}. El límite se mide en puntos de código, como {@code VARCHAR(40)} en utf8mb4.
	 */
	static String normalizarNombre(String nombre) {
		if (nombre == null) {
			return null;
		}
		boolean noValido = nombre.codePoints().anyMatch(c -> Character.isISOControl(c)
				|| Character.getType(c) == Character.SURROGATE);
		if (noValido) {
			throw new IllegalArgumentException(NOMBRE_NO_VALIDO);
		}
		String limpio = nombre.replaceAll("\\p{Z}+", " ").strip();
		if (limpio.isEmpty()) {
			return null;
		}
		if (limpio.codePointCount(0, limpio.length()) > MAX_NOMBRE) {
			throw new IllegalArgumentException(NOMBRE_LARGO);
		}
		return limpio;
	}

	/** Los dos nulos, o los dos válidos para ese mes (29 de febrero vale). */
	static void validarCumple(Integer dia, Integer mes) {
		if (dia == null && mes == null) {
			return;
		}
		if (dia == null || mes == null) {
			throw new IllegalArgumentException(CUMPLE_INCOMPLETO);
		}
		if (mes < 1 || mes > 12 || dia < 1 || dia > Month.of(mes).maxLength()) {
			throw new IllegalArgumentException(CUMPLE_IMPOSIBLE);
		}
	}

	private static PerfilCuentaDTO dto(Usuario cuenta, Optional<PerfilCuentaDTO.DiaMes> ficha) {
		return new PerfilCuentaDTO(cuenta.getNombrePreferido(),
				PermisosSesionService.nombreVisible(cuenta.getNombre(), cuenta.getApellidos()), cuenta.getCumpleDia(),
				cuenta.getCumpleMes(), ficha.orElse(null));
	}

	private static Map<String, Object> valores(Long usuario, boolean sinCambios) {
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("usuario", usuario);
		if (sinCambios) {
			valores.put("sinCambios", Boolean.TRUE);
		}
		return valores;
	}

	private static Long organizacionActiva() {
		Long organizacion = TenantContext.get();
		if (organizacion == null) {
			throw denegado();
		}
		return organizacion;
	}

	private static Long usuarioDeLaSesion() {
		Long usuario = CobroService.usuarioActualId();
		if (usuario == null) {
			throw denegado();
		}
		return usuario;
	}

	private static AccessDeniedException denegado() {
		return new AccessDeniedException("Acceso denegado.");
	}
}
