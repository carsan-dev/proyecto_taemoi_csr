package com.taemoi.project.plataforma;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lectura ENTRE ESCUELAS de los errores técnicos de la API para el administrador de plataforma.
 *
 * <p>Excepción documentada (docs/multi-escuela/fase3-sedes-permisos.md §4, como la consulta nativa de
 * {@code AlumnoRepository} del acceso de familia): SQL con {@code JdbcTemplate}, que NO pasa por el filtro
 * {@code @TenantId} de Hibernate, para leer todas las organizaciones sin desactivar ese filtro en la sesión
 * (descartado {@code CurrentTenantIdentifierResolver.isRoot}, que también afectaría a las escrituras).
 * Reglas, vigiladas por {@code RepositorioPlataformaArquitecturaTest}:
 * <ul>
 * <li>solo SELECT y con la lista de columnas explícita (las de {@link ErrorPlataformaDTO}; nunca los datos
 * personales ni el contenido de la petición guardados en la auditoría);</li>
 * <li>solo lo usa {@link ErroresPlataformaService}, detrás de {@code /api/plataforma/**};</li>
 * <li>JdbcTemplate no se usa en ningún otro paquete.</li>
 * </ul>
 * Los valores de los filtros van siempre como parámetros ({@code ?}); el texto SQL es fijo.
 */
@Repository
public class ErroresPlataformaRepository {

	private static final String COLUMNAS =
			"id, fecha_evento, organizacion_id, metodo_http, endpoint, modulo, estado_http, usuario_id, resumen";

	/** Error técnico: respuesta 4xx/5xx o petición sin sesión. */
	private static final String ES_ERROR = "(estado_http >= 400 OR usuario_id IS NULL)";

	private final JdbcTemplate jdbc;

	public ErroresPlataformaRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Criterios de búsqueda; {@code null} = sin ese filtro.
	 *
	 * @param estadoMinimo   estado HTTP mínimo (incluido).
	 * @param estadoMaximo   estado HTTP máximo (incluido).
	 * @param desde          fecha y hora mínima (incluida).
	 * @param hastaExcluido  fecha y hora máxima (excluida).
	 */
	public record Filtro(Long organizacionId, Integer estadoMinimo, Integer estadoMaximo, Timestamp desde,
			Timestamp hastaExcluido) {
	}

	/** Página de errores, del más reciente al más antiguo. */
	public List<ErrorPlataformaDTO> buscar(Filtro filtro, int limite, long desplazamiento) {
		List<Object> parametros = new ArrayList<>();
		String sql = "SELECT " + COLUMNAS + " FROM auditoria_evento WHERE " + condiciones(filtro, parametros)
				+ " ORDER BY fecha_evento DESC, id DESC LIMIT ? OFFSET ?";
		parametros.add(limite);
		parametros.add(desplazamiento);
		return jdbc.query(sql, ErroresPlataformaRepository::fila, parametros.toArray());
	}

	public long contar(Filtro filtro) {
		List<Object> parametros = new ArrayList<>();
		String sql = "SELECT COUNT(id) FROM auditoria_evento WHERE " + condiciones(filtro, parametros);
		Long total = jdbc.queryForObject(sql, Long.class, parametros.toArray());
		return total == null ? 0L : total;
	}

	private static String condiciones(Filtro filtro, List<Object> parametros) {
		StringBuilder where = new StringBuilder(ES_ERROR);
		if (filtro.organizacionId() != null) {
			where.append(" AND organizacion_id = ?");
			parametros.add(filtro.organizacionId());
		}
		if (filtro.estadoMinimo() != null) {
			where.append(" AND estado_http >= ?");
			parametros.add(filtro.estadoMinimo());
		}
		if (filtro.estadoMaximo() != null) {
			where.append(" AND estado_http <= ?");
			parametros.add(filtro.estadoMaximo());
		}
		if (filtro.desde() != null) {
			where.append(" AND fecha_evento >= ?");
			parametros.add(filtro.desde());
		}
		if (filtro.hastaExcluido() != null) {
			where.append(" AND fecha_evento < ?");
			parametros.add(filtro.hastaExcluido());
		}
		return where.toString();
	}

	private static ErrorPlataformaDTO fila(ResultSet rs, int numero) throws SQLException {
		Timestamp fecha = rs.getTimestamp("fecha_evento");
		return new ErrorPlataformaDTO(
				rs.getLong("id"),
				fecha == null ? null : new java.util.Date(fecha.getTime()),
				rs.getObject("organizacion_id", Long.class),
				rs.getString("metodo_http"),
				rs.getString("endpoint"),
				rs.getString("modulo"),
				rs.getObject("estado_http", Integer.class),
				rs.getObject("usuario_id", Long.class),
				rs.getString("resumen"));
	}
}
