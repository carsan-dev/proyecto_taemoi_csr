package com.taemoi.project.services;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.cifrado.CifradorDatos;
import com.taemoi.project.dtos.request.AlumnoSaludRequest;
import com.taemoi.project.dtos.response.AlumnoSaludDTO;
import com.taemoi.project.entities.AlumnoSalud;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.OrigenDato;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.AlumnoSaludRepository;

/**
 * Datos de salud de un alumno (decisión 7). Cada lectura queda en {@code auditoria_evento} (quién,
 * cuándo y qué alumno, nunca el contenido) dentro de la misma transacción: si no se puede auditar, no
 * se lee. Sin caché. Nada de este servicio escribe el contenido en logs.
 *
 * <p>Cifra y descifra aquí (no con un conversor JPA) para ligar cada texto a su fila y columna: el
 * dato autenticado de AES-GCM es {@code alumno_salud.<columna>#<alumno_id>}.
 */
@Service
public class AlumnoSaludService {

	/** Módulo de sus eventos de auditoría; identifica las lecturas de salud en el registro del propietario (D2). */
	public static final String MODULO_AUDITORIA = "salud";
	private static final String TABLA = "alumno_salud";

	private final AlumnoSaludRepository salud;
	private final AlumnoRepository alumnos;
	private final AuditoriaService auditoria;
	private final CifradorDatos cifrador;

	public AlumnoSaludService(AlumnoSaludRepository salud, AlumnoRepository alumnos, AuditoriaService auditoria,
			CifradorDatos cifrador) {
		this.salud = salud;
		this.alumnos = alumnos;
		this.auditoria = auditoria;
		this.cifrador = cifrador;
	}

	@Transactional
	public AlumnoSaludDTO obtener(Long alumnoId) {
		comprobarAlumno(alumnoId);
		auditoria.registrarAccesoSensible(AuditoriaAccion.READ, MODULO_AUDITORIA, ruta(alumnoId), alumnoId,
				"Lectura de datos de salud del alumno #" + alumnoId);
		return salud.findById(alumnoId).map(this::aDto).orElseGet(() -> AlumnoSaludDTO.vacio(alumnoId));
	}

	@Transactional
	public AlumnoSaludDTO guardar(Long alumnoId, AlumnoSaludRequest datos) {
		comprobarAlumno(alumnoId);
		String descripcion = texto(datos.descripcion(), "La descripción");
		String impideDetalle = texto(datos.impideDetalle(), "El detalle de lo que impide la actividad");
		String alergias = texto(datos.alergias(), "Las alergias");

		AlumnoSalud registro = salud.findById(alumnoId).orElseGet(() -> new AlumnoSalud(alumnoId));
		registro.setLesionEnfermedad(datos.lesionEnfermedad());
		registro.setDescripcionCifrada(cifrador.cifrar(descripcion, contexto("descripcion", alumnoId)));
		registro.setImpideActividad(datos.impideActividad());
		registro.setImpideDetalleCifrado(cifrador.cifrar(impideDetalle, contexto("impide_detalle", alumnoId)));
		registro.setAlergiasCifradas(cifrador.cifrar(alergias, contexto("alergias", alumnoId)));
		registro.setOrigen(datos.origen() != null ? datos.origen() : OrigenDato.APP);
		registro.setActualizadoEn(Instant.now());
		AlumnoSalud guardado = salud.saveAndFlush(registro);
		return AlumnoSaludDTO.de(guardado, descripcion, impideDetalle, alergias);
	}

	private AlumnoSaludDTO aDto(AlumnoSalud s) {
		Long id = s.getAlumnoId();
		return AlumnoSaludDTO.de(s,
				cifrador.descifrar(s.getDescripcionCifrada(), contexto("descripcion", id)),
				cifrador.descifrar(s.getImpideDetalleCifrado(), contexto("impide_detalle", id)),
				cifrador.descifrar(s.getAlergiasCifradas(), contexto("alergias", id)));
	}

	private static String contexto(String columna, Long alumnoId) {
		return CifradorDatos.contexto(TABLA, columna, alumnoId);
	}

	private void comprobarAlumno(Long alumnoId) {
		if (alumnoId == null || alumnos.buscarPorIdEnOrganizacionActiva(alumnoId).isEmpty()) {
			throw new AlumnoNoEncontradoException("Alumno no encontrado.");
		}
	}

	private static String ruta(Long alumnoId) {
		return "/api/alumnos/" + alumnoId + "/salud";
	}

	/** Texto opcional; el mensaje de error nunca repite el valor. */
	private static String texto(String valor, String campo) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpio = valor.strip();
		if (limpio.length() > AlumnoSaludRequest.MAX_TEXTO) {
			throw new IllegalArgumentException(
					campo + " no puede superar " + AlumnoSaludRequest.MAX_TEXTO + " caracteres.");
		}
		return limpio;
	}
}
