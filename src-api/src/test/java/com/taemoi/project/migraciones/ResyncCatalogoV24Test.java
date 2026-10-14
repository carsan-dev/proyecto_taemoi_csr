package com.taemoi.project.migraciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.taemoi.project.services.CatalogoLegadoTaemoi;

/**
 * El texto de db/resync/resync_catalogo_v24.sql solo puede ir en V24, V26 y V27: su UPDATE que pone
 * tarifa_id = NULL donde tipo_tarifa es NULL no se limita a una organización y, desde V27, borraría las
 * tarifas con código propio de otras escuelas. Y los conceptos heredados que usa el código
 * ({@link CatalogoLegadoTaemoi}) son exactamente los de esa tabla.
 */
class ResyncCatalogoV24Test {

	private static final Pattern VERSION = Pattern.compile("V(\\d+)__.*\\.sql");

	@Test
	void ningunaMigracionPosteriorAV27IncluyeElResync() throws Exception {
		String resync = leer(new ClassPathResource("db/resync/resync_catalogo_v24.sql"));
		Resource[] migraciones = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*.sql");
		assertTrue(migraciones.length >= 27);
		Map<Integer, Boolean> incluyen = new LinkedHashMap<>();
		for (Resource migracion : migraciones) {
			Matcher m = VERSION.matcher(migracion.getFilename());
			assertTrue(m.matches(), migracion.getFilename());
			int version = Integer.parseInt(m.group(1));
			boolean incluye = leer(migracion).contains(resync);
			incluyen.put(version, incluye);
			if (version > 27) {
				assertFalse(incluye, migracion.getFilename() + " no debe incluir resync_catalogo_v24.sql");
			}
		}
		assertTrue(incluyen.get(24));
		assertTrue(incluyen.get(26));
		assertTrue(incluyen.get(27));
	}

	@Test
	void conceptosHeredadosDelCodigo_sonLosDeLaTablaDeV24() throws Exception {
		String resync = leer(new ClassPathResource("db/resync/resync_catalogo_v24.sql"));
		Matcher fila = Pattern.compile("\\('([^']+)', '([A-Z0-9_]+)', '[A-Z_]+', (?:'[A-Z_]+'|NULL)\\)").matcher(resync);
		Map<String, String> tabla = new LinkedHashMap<>();
		while (fila.find()) {
			tabla.put(fila.group(2), fila.group(1));
		}
		assertEquals(40, tabla.size());
		assertEquals(tabla, CatalogoLegadoTaemoi.conceptosPorCodigo());
	}

	private static String leer(Resource recurso) throws Exception {
		return new String(recurso.getInputStream().readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}
}
