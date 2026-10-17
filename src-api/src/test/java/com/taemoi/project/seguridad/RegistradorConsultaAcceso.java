package com.taemoi.project.seguridad;

import java.util.Set;

import org.springframework.security.core.Authentication;

import com.taemoi.project.acceso.ConsultaAccesoRecursos;

/**
 * Sustituto del puerto de datos de {@code @acceso} en la matriz (entrega5-plan.md §4.4.2): responde siempre
 * {@code true} y anota, en el hilo en curso, que se consultó. Así la lógica real de rol × operación del bean se
 * evalúa de verdad y el arnés distingue un PERMITE sin mirar el recurso (alcance total) de un ALCANCE (depende de
 * los datos). Lo instala {@code MatrizPermisosTest.ConsultaAccesoRegistrada}.
 */
final class RegistradorConsultaAcceso implements ConsultaAccesoRecursos {

	private static final ThreadLocal<Boolean> CONSULTADO = new ThreadLocal<>();

	/** Pone a cero la marca del hilo en curso. */
	static void reiniciar() {
		CONSULTADO.remove();
	}

	/** Si se consultó el puerto desde el último {@link #reiniciar()} en este hilo. */
	static boolean consultado() {
		return Boolean.TRUE.equals(CONSULTADO.get());
	}

	private static boolean registrar() {
		CONSULTADO.set(Boolean.TRUE);
		return true;
	}

	@Override
	public boolean familiaVeAlumno(Authentication autenticacion, Long alumnoId) {
		return registrar();
	}

	@Override
	public boolean alumnoEnSedes(Long alumnoId, Set<Long> sedes) {
		return registrar();
	}

	@Override
	public boolean alumnoConSedePrincipalEn(Long alumnoId, Set<Long> sedes) {
		return registrar();
	}

	@Override
	public boolean alumnoEnGrupos(Long alumnoId, Set<Long> grupos) {
		return registrar();
	}

	@Override
	public boolean grupoEnSedes(Long grupoId, Set<Long> sedes) {
		return registrar();
	}

	@Override
	public boolean grupoEnGrupos(Long grupoId, Set<Long> grupos) {
		return registrar();
	}

	@Override
	public boolean turnoEnSedes(Long turnoId, Set<Long> sedes) {
		return registrar();
	}

	@Override
	public boolean turnoEnGrupos(Long turnoId, Set<Long> grupos) {
		return registrar();
	}

	@Override
	public boolean preinscripcionEnSedes(String referencia, Set<Long> sedes) {
		return registrar();
	}

	@Override
	public boolean preinscripcionEnGrupos(String referencia, Set<Long> grupos) {
		return registrar();
	}

	@Override
	public boolean alumnoConvocatoriaEnSedes(Long alumnoConvocatoriaId, Set<Long> sedes) {
		return registrar();
	}

	@Override
	public boolean alumnoConvocatoriaConSedePrincipalEn(Long alumnoConvocatoriaId, Set<Long> sedes) {
		return registrar();
	}
}
