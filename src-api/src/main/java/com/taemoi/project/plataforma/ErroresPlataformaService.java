package com.taemoi.project.plataforma;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Errores técnicos de la API de todas las escuelas (fase3-sedes-permisos.md §3 y §4): eventos de auditoría con
 * estado 4xx/5xx o sin sesión. Solo para el administrador de plataforma ({@link PlataformaController}).
 */
@Service
public class ErroresPlataformaService {

	static final int TAMANO_POR_DEFECTO = 25;
	static final int TAMANO_MAXIMO = 200;
	static final LocalDate FECHA_MINIMA = LocalDate.of(1000, 1, 1);
	static final LocalDate FECHA_MAXIMA = LocalDate.of(9998, 12, 31);

	private final ErroresPlataformaRepository repositorio;

	public ErroresPlataformaService(ErroresPlataformaRepository repositorio) {
		this.repositorio = repositorio;
	}

	/**
	 * @param estado {@code 4xx} o {@code 5xx} (sin distinguir mayúsculas); vacío = ambos más los anónimos.
	 * @param pagina empieza en 1, como el registro de acciones.
	 */
	public Page<ErrorPlataformaDTO> buscar(Long organizacionId, String estado, LocalDate desde, LocalDate hasta,
			Integer pagina, Integer tamano) {
		Integer minimo = null;
		Integer maximo = null;
		if (estado != null && !estado.isBlank()) {
			switch (estado.trim().toLowerCase(Locale.ROOT)) {
			case "4xx" -> {
				minimo = 400;
				maximo = 499;
			}
			case "5xx" -> {
				minimo = 500;
				maximo = 599;
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Estado no válido: use 4xx o 5xx");
			}
		}
		comprobarFecha(desde, "desde");
		comprobarFecha(hasta, "hasta");
		ZoneId zona = ZoneId.systemDefault();
		Timestamp inicio = desde == null ? null : Timestamp.from(desde.atStartOfDay(zona).toInstant());
		Timestamp fin = hasta == null ? null : Timestamp.from(hasta.plusDays(1).atStartOfDay(zona).toInstant());
		ErroresPlataformaRepository.Filtro filtro = new ErroresPlataformaRepository.Filtro(organizacionId, minimo,
				maximo, inicio, fin);

		int numero = pagina == null || pagina < 1 ? 1 : pagina;
		int size = tamano == null ? TAMANO_POR_DEFECTO : Math.max(1, Math.min(tamano, TAMANO_MAXIMO));
		PageRequest peticion = PageRequest.of(numero - 1, size,
				Sort.by(Sort.Order.desc("fechaEvento"), Sort.Order.desc("id")));
		long total = repositorio.contar(filtro);
		List<ErrorPlataformaDTO> contenido = total == 0 ? List.of()
				: repositorio.buscar(filtro, size, peticion.getOffset());
		return new PageImpl<>(contenido, peticion, total);
	}

	/** Todas las escuelas (id y nombre), para el filtro de la pantalla de errores. */
	public List<EscuelaPlataformaDTO> escuelas() {
		return repositorio.escuelas();
	}

	/** Fechas fuera de lo que guarda un DATETIME (años 1000-9999): 400, no 500 al calcular el día siguiente. */
	private static void comprobarFecha(LocalDate fecha, String nombre) {
		if (fecha != null && (fecha.isBefore(FECHA_MINIMA) || fecha.isAfter(FECHA_MAXIMA))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Fecha '" + nombre + "' fuera de rango (" + FECHA_MINIMA + " a " + FECHA_MAXIMA + ")");
		}
	}
}
