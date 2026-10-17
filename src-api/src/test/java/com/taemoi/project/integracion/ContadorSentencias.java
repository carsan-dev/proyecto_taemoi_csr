package com.taemoi.project.integracion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Cuenta las sentencias SQL que Hibernate prepara en el hilo actual (un {@link StatementInspector}). Se activa por
 * clase de test con la propiedad
 * {@code spring.jpa.properties.hibernate.session_factory.statement_inspector=com.taemoi.project.integracion.ContadorSentencias}.
 *
 * <p>Solo cuenta mientras el hilo está dentro de {@link #contar(Callable)}: MockMvc atiende la petición en el hilo del
 * test, así que las sentencias de tareas en segundo plano (otros hilos) no se suman. Las consultas por
 * {@code JdbcTemplate} no pasan por Hibernate y tampoco cuentan.
 */
public class ContadorSentencias implements StatementInspector {

	private static final long serialVersionUID = 1L;

	private static final ThreadLocal<List<String>> SENTENCIAS = new ThreadLocal<>();

	@Override
	public String inspect(String sql) {
		List<String> sentencias = SENTENCIAS.get();
		if (sentencias != null) {
			sentencias.add(sql);
		}
		return sql;
	}

	/** Ejecuta la acción y devuelve su resultado junto con las sentencias preparadas mientras tanto. */
	public static <T> Conteo<T> contar(Callable<T> accion) throws Exception {
		List<String> sentencias = new ArrayList<>();
		SENTENCIAS.set(sentencias);
		try {
			T resultado = accion.call();
			return new Conteo<>(resultado, List.copyOf(sentencias));
		} finally {
			SENTENCIAS.remove();
		}
	}

	public record Conteo<T>(T resultado, List<String> sentencias) {

		public int total() {
			return sentencias.size();
		}
	}
}
