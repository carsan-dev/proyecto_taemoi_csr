package com.taemoi.project.services;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.taemoi.project.entities.AuditoriaAccion;

/**
 * Registro de las rallas hacia el examen (decisiones 510-513) en el registro de auditoría de siempre
 * ({@link AuditoriaService#registrarAccesoSensible}): en la MISMA transacción que el cambio y sin capturar errores (si
 * no se puede registrar, el cambio no se hace). Quién, cuándo y escuela los pone el registro; aquí, el módulo
 * {@value #MODULO}, el tipo de acción y los ids implicados. Copia de {@link RegistroAccionesProfesor}.
 *
 * <p>El resumen solo lleva ids ({@code #n}), números enteros (el paso y las medias rallas), valores de enumeración y
 * sí/no: nunca texto libre, nombres ni fechas ({@code "RALLAS PASO alumno=#12 alumnoDeporte=#30 grupo=#4 paso=1
 * medias=3"}). Cualquier otro tipo de valor es un error de programación y el cambio no se hace.
 */
@Component
public class RegistroAccionesRallas {

	public static final String MODULO = "rallas";

	/** Tipo de acción registrada. */
	public enum Accion {
		/** Un paso de la hoja ({@code alumno grupo alumnoDeporte paso medias}: el paso dado y las medias que quedan). */
		PASO(AuditoriaAccion.UPDATE),
		/** «Marcar apto para examen» desde la hoja: apto y rallas a 0 ({@code alumno grupo alumnoDeporte}). */
		APTO_DESDE_HOJA(AuditoriaAccion.UPDATE),
		/** Rallas a 0 al cambiar el grado ({@code alumno alumnoDeporte gradoAnterior gradoNuevo}). */
		A_CERO_POR_GRADO(AuditoriaAccion.UPDATE),
		/** Configuración: la disciplina pasa a usar rallas ({@code disciplina}). */
		ACTIVADAS(AuditoriaAccion.UPDATE),
		/** Configuración: la disciplina deja de usar rallas; las guardadas se conservan ({@code disciplina}). */
		DESACTIVADAS(AuditoriaAccion.UPDATE);

		private final AuditoriaAccion auditoria;

		Accion(AuditoriaAccion auditoria) {
			this.auditoria = auditoria;
		}
	}

	private final AuditoriaService auditoria;

	public RegistroAccionesRallas(AuditoriaService auditoria) {
		this.auditoria = auditoria;
	}

	/**
	 * Registra la acción. {@code valores}: nombre → id ({@link Long}), número ({@link Integer}), enumeración o
	 * {@link Boolean} (SI/NO), en el orden dado; el primer id es el recurso. Debe llamarse dentro de la transacción.
	 *
	 * @throws IllegalStateException si algún valor no es de esos tipos (nunca texto libre)
	 */
	public void registrar(Accion accion, String endpoint, Map<String, ?> valores) {
		Map<String, ?> implicados = valores == null ? Map.of() : new LinkedHashMap<>(valores);
		Long recursoId = implicados.values().stream().filter(Long.class::isInstance).map(Long.class::cast).findFirst()
				.orElse(null);
		String detalle = implicados.entrySet().stream().map(e -> e.getKey() + "=" + formatear(e.getKey(), e.getValue()))
				.collect(Collectors.joining(" "));
		String resumen = "RALLAS " + accion.name() + (detalle.isEmpty() ? "" : " " + detalle);
		auditoria.registrarAccesoSensible(accion.auditoria, MODULO, endpoint, recursoId, resumen);
	}

	/** La ruta de la petición en curso; sin petición (tareas, tests de servicio), la dada. */
	public static String rutaActualO(String porDefecto) {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
			String ruta = atributos.getRequest().getRequestURI();
			if (ruta != null && !ruta.isBlank()) {
				return ruta;
			}
		}
		return porDefecto;
	}

	static String formatear(String nombre, Object valor) {
		if (valor instanceof Long id) {
			return "#" + id;
		}
		if (valor instanceof Integer numero) {
			return String.valueOf(numero);
		}
		if (valor instanceof Boolean si) {
			return si ? "SI" : "NO";
		}
		if (valor instanceof Enum<?> enumeracion) {
			return enumeracion.name();
		}
		if (valor == null) {
			return "NINGUNO";
		}
		throw new IllegalStateException("Valor no admitido en el registro de rallas: " + nombre);
	}
}
