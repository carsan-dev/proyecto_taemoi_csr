package com.taemoi.project.services;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.taemoi.project.entities.Contador;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ContadorRepository;

/**
 * Contadores por organización con bloqueo pesimista ({@code SELECT ... FOR UPDATE}).
 *
 * <p>El bloqueo se mantiene hasta el final de la transacción que llama, así que el número
 * entregado y la fila que lo usa se confirman juntos: dos altas simultáneas de la misma
 * escuela nunca obtienen el mismo número. Si la transacción se deshace, el número se libera.
 *
 * <p>La entidad {@link Contador} solo se carga con la consulta bloqueante: una lectura previa
 * sin bloqueo la dejaría en el contexto de persistencia con un {@code valor} antiguo que el
 * {@code FOR UPDATE} posterior no refresca.
 */
@Service
public class ContadorService {

	private static final int INTENTOS_CREACION = 3;

	private final ContadorRepository contadores;
	private final AlumnoRepository alumnos;
	private final TransactionTemplate nuevaTransaccion;

	public ContadorService(ContadorRepository contadores, AlumnoRepository alumnos,
			PlatformTransactionManager transactionManager) {
		this.contadores = contadores;
		this.alumnos = alumnos;
		this.nuevaTransaccion = new TransactionTemplate(transactionManager);
		this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** Siguiente número de expediente de la organización activa. */
	@Transactional
	public int siguienteNumeroExpediente() {
		return Math.toIntExact(siguiente(Contador.NUMERO_EXPEDIENTE));
	}

	/**
	 * Siguiente valor bajo bloqueo: {@code max(contador, máximo ya usado) + 1}. Así, si el
	 * contador se quedó por detrás (restauración, importación por SQL, vuelta a código antiguo)
	 * el alta no choca con el único y el contador se pone al día.
	 */
	@Transactional
	public long siguiente(String clave) {
		Contador contador = bloquear(clave);
		long siguiente = Math.max(contador.getValor(), maximoUsado(clave)) + 1;
		contador.setValor(siguiente);
		contadores.saveAndFlush(contador);
		return siguiente;
	}

	private Contador bloquear(String clave) {
		// exists no carga la entidad ni deja bloqueos de hueco (lectura consistente sin bloqueo).
		// El FOR UPDATE se hace una sola vez y al final: si se hiciera sobre una fila inexistente
		// dejaría un bloqueo de hueco que impediría crearla desde otra transacción.
		if (!contadores.existsByClave(clave)) {
			asegurarCreado(clave);
		}
		return contadores.findByClaveParaActualizar(clave)
				.orElseThrow(() -> new IllegalStateException("No se pudo crear el contador " + clave));
	}

	private long maximoUsado(String clave) {
		if (!Contador.NUMERO_EXPEDIENTE.equals(clave)) {
			return 0L;
		}
		Integer maximo = alumnos.findMaxNumeroExpediente();
		return maximo == null ? 0L : maximo.longValue();
	}

	/**
	 * Primera vez para una organización (V18 ya lo crea para Taemoi): se crea en una
	 * transacción propia con valor 0 (el máximo usado se aplica bajo el bloqueo). Si otra alta
	 * lo crea a la vez, el índice único (organizacion_id, clave) rechaza la segunda fila y el
	 * {@code FOR UPDATE} posterior espera a la ya creada.
	 */
	private void asegurarCreado(String clave) {
		for (int intento = 1; intento <= INTENTOS_CREACION; intento++) {
			try {
				// Cada intento es una transacción nueva: ve lo confirmado por la otra creación.
				nuevaTransaccion.executeWithoutResult(estado -> {
					if (!contadores.existsByClave(clave)) {
						contadores.saveAndFlush(new Contador(clave, 0L));
					}
				});
				return;
			} catch (DataAccessException duplicadoOInterbloqueo) {
				// Otra alta lo creó a la vez (único) o fuimos víctima de un interbloqueo: se reintenta.
			}
		}
	}
}
