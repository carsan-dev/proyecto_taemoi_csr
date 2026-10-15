package com.taemoi.project.integracion;

import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.SistemaGrados;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.legado.TipoGrado;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.SistemaGradosRepository;
import com.taemoi.project.repositories.TarifaRepository;

/**
 * Disciplinas para los datos de prueba. Desde V26 toda inscripción, grupo, convocatoria y
 * preinscripción lleva {@code disciplina_id} (NOT NULL): Taemoi la tiene sembrada (V23) y las
 * escuelas creadas en un test necesitan la suya.
 */
final class DisciplinasDePrueba {

	/** Subconsulta SQL para {@code disciplina_id}: parámetros (organizacion_id, codigo). */
	static final String SUBCONSULTA_ID = "(SELECT d.id FROM disciplina d WHERE d.organizacion_id = ? AND d.codigo = ?)";

	private DisciplinasDePrueba() {
	}

	/**
	 * Disciplina de la organización activa con ese código; si no existe, la crea sin sistema de grados
	 * (edad adulta 14, sin competición ni licencia).
	 */
	static Disciplina asegurar(DisciplinaRepository disciplinas, String codigo) {
		return disciplinas.findByCodigo(codigo).orElseGet(() -> {
			Disciplina nueva = new Disciplina();
			nueva.setCodigo(codigo);
			nueva.setNombre(codigo);
			nueva.setEdadAdulto(14);
			return disciplinas.save(nueva);
		});
	}

	/**
	 * Tarifa de la organización activa (desde V27 la cuantía por defecto del alta sale de la tabla
	 * tarifa de cada escuela, no de los valores de Taemoi).
	 */
	static Tarifa tarifa(TarifaRepository tarifas, String codigo, String importe, Disciplina disciplina) {
		Tarifa tarifa = new Tarifa();
		tarifa.setCodigo(codigo);
		tarifa.setNombre(codigo);
		tarifa.setImporte(new java.math.BigDecimal(importe));
		tarifa.setDisciplina(disciplina);
		return tarifas.save(tarifa);
	}

	/**
	 * Crea en la organización activa una disciplina con su propio sistema de grados, con esos grados
	 * en ese orden (todos {@code TODOS}, 2 meses para menores y 3 para adultos).
	 */
	static Disciplina conGrados(DisciplinaRepository disciplinas, SistemaGradosRepository sistemas,
			GradoRepository grados, String codigo, int edadAdulto, TipoGrado... tipos) {
		SistemaGrados sistema = new SistemaGrados();
		sistema.setNombre(codigo);
		sistema.setPlantillaOrigen(codigo);
		sistema = sistemas.save(sistema);
		int orden = 1;
		for (TipoGrado tipo : tipos) {
			Grado grado = new Grado();
			grado.setCodigo(tipo.name());
			grado.setNombre(tipo.getNombre());
			grado.setOrden(orden++);
			grado.setSistemaGrados(sistema);
			grado.setMesesMinimosMenor(2);
			grado.setMesesMinimosAdulto(3);
			grados.save(grado);
		}
		Disciplina disciplina = new Disciplina();
		disciplina.setCodigo(codigo);
		disciplina.setNombre(codigo);
		disciplina.setSistemaGrados(sistema);
		disciplina.setEdadAdulto(edadAdulto);
		disciplina.setPermiteCompeticion(true);
		disciplina.setRequiereLicencia(true);
		return disciplinas.save(disciplina);
	}
}
