package com.taemoi.project.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.SistemaGrados;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.ComportamientoLegadoService;
import com.taemoi.project.services.ProgresionGradosService;
import com.taemoi.project.services.impl.ProductosCatalogoServiceImpl;

/**
 * Grado que concede un cargo de examen o recompensa a ROJO resuelto por su concepto, sin producto (borrado: V24 pone
 * {@code producto_id} a NULL) o con un producto que no existe ni tiene grado. En kickboxing los cargos nuevos dicen
 * «MARRÓN» (el nombre del grado ROJO de su catálogo): conceden ROJO igual que los de taekwondo, que siguen diciendo ROJO.
 */
class ProductosCatalogoGradoRojoKickboxingTest {

	private final ProductoRepository productos = mock(ProductoRepository.class);
	private final GradoRepository grados = mock(GradoRepository.class);
	private final CatalogoDisciplinasService catalogo = mock(CatalogoDisciplinasService.class);
	private final ProgresionGradosService progresion = mock(ProgresionGradosService.class);
	private final ComportamientoLegadoService comportamientoLegado = mock(ComportamientoLegadoService.class);
	private final ProductosCatalogoServiceImpl servicio = new ProductosCatalogoServiceImpl(productos, grados, catalogo,
			progresion, comportamientoLegado);

	@BeforeEach
	void taemoiSinProductos() {
		lenient().when(comportamientoLegado.activo()).thenReturn(true);
		lenient().when(productos.findById(anyLong())).thenReturn(Optional.empty());
		lenient().when(productos.findFirstByConceptoAndGradoIsNotNullOrderByIdAsc(anyString())).thenReturn(Optional.empty());
		lenient().when(productos.findByTipoAndGradoIsNotNullOrderByIdAsc(any())).thenReturn(List.of());
		gradoRojoDeKickboxing("MARRÓN");
	}

	@ParameterizedTest(name = "{0} (producto {1})")
	@CsvSource(nullValues = "NULO", value = {
			"DERECHO A EXAMEN MARRÓN - KICKBOXING, NULO",
			"DERECHO A EXAMEN MARRÓN BORDADO - KICKBOXING, NULO",
			"DERECHO DE CAMBIO A MARRÓN POR RECOMPENSA - KICKBOXING, NULO",
			"DERECHO DE CAMBIO A MARRÓN BORDADO POR RECOMPENSA - KICKBOXING, NULO",
			"DERECHO A EXAMEN MARRÓN - KICKBOXING, 987654",
			"DERECHO A EXAMEN MARRÓN BORDADO - KICKBOXING, 987654",
			"DERECHO DE CAMBIO A MARRÓN POR RECOMPENSA - KICKBOXING, 987654",
			"DERECHO DE CAMBIO A MARRÓN BORDADO POR RECOMPENSA - KICKBOXING, 987654",
			"DERECHO A EXAMEN ROJO - TAEKWONDO, NULO",
			"DERECHO A EXAMEN ROJO BORDADO - TAEKWONDO, NULO",
			"DERECHO DE CAMBIO A ROJO POR RECOMPENSA - TAEKWONDO, NULO",
			"DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA - TAEKWONDO, NULO",
			"DERECHO A EXAMEN ROJO - TAEKWONDO, 987654",
			"DERECHO A EXAMEN ROJO BORDADO - TAEKWONDO, 987654",
			"DERECHO DE CAMBIO A ROJO POR RECOMPENSA - TAEKWONDO, 987654",
			"DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA - TAEKWONDO, 987654" })
	void cargoARojo_sinProducto_concedeRojo_enKickboxingConMarronYEnTaekwondoConRojo(String concepto, Long productoId) {
		assertEquals(Optional.of("ROJO"), servicio.buscarCodigoGradoConcedido(productoId, concepto));
	}

	/** Si el catálogo de kickboxing no da el nombre del grado ROJO (sin grados), «MARRÓN» sigue concediendo ROJO. */
	@Test
	void sinGradoRojoEnElCatalogoDeKickboxing_marronSigueConcediendoRojo() {
		lenient().when(catalogo.buscarPorCodigo("KICKBOXING")).thenReturn(Optional.empty());
		assertEquals(Optional.of("ROJO"),
				servicio.buscarCodigoGradoConcedido(null, "DERECHO A EXAMEN MARRÓN BORDADO - KICKBOXING"));
		assertEquals(Optional.of("ROJO"),
				servicio.buscarCodigoGradoConcedido(null, "DERECHO DE CAMBIO A MARRÓN POR RECOMPENSA - KICKBOXING"));
	}

	/** Otros conceptos con ROJO siguen concediendo su grado (gana el nombre más largo). */
	@Test
	void otrosGradosConRojo_noCambian() {
		assertEquals(Optional.of("AZUL_ROJO"),
				servicio.buscarCodigoGradoConcedido(null, "DERECHO A EXAMEN AZUL/ROJO - KICKBOXING"));
		assertEquals(Optional.of("ROJO_NEGRO_1_PUM"),
				servicio.buscarCodigoGradoConcedido(null, "DERECHO A EXAMEN ROJO/NEGRO 1º PUM - TAEKWONDO"));
		assertEquals(Optional.empty(), servicio.buscarCodigoGradoConcedido(null, "CAMISETA TAEMOI - KICKBOXING"));
	}

	/** Sin comportamiento heredado (otras escuelas) no hay nombres heredados: ni ROJO ni MARRÓN. */
	@Test
	void sinComportamientoHeredado_ningunNombreHeredado() {
		lenient().when(comportamientoLegado.activo()).thenReturn(false);
		assertEquals(Optional.empty(),
				servicio.buscarCodigoGradoConcedido(null, "DERECHO A EXAMEN MARRÓN BORDADO - KICKBOXING"));
		assertEquals(Optional.empty(),
				servicio.buscarCodigoGradoConcedido(null, "DERECHO A EXAMEN ROJO BORDADO - TAEKWONDO"));
	}

	private void gradoRojoDeKickboxing(String nombre) {
		SistemaGrados sistema = new SistemaGrados();
		org.springframework.test.util.ReflectionTestUtils.setField(sistema, "id", 77L);
		Disciplina kickboxing = new Disciplina();
		kickboxing.setCodigo("KICKBOXING");
		kickboxing.setSistemaGrados(sistema);
		Grado rojo = new Grado();
		rojo.setCodigo("ROJO");
		rojo.setNombre(nombre);
		lenient().when(catalogo.buscarPorCodigo("KICKBOXING")).thenReturn(Optional.of(kickboxing));
		lenient().when(grados.findBySistemaGradosIdAndCodigo(77L, "ROJO")).thenReturn(Optional.of(rojo));
		lenient().when(progresion.buscarGrado("KICKBOXING", "ROJO")).thenReturn(Optional.of(rojo));
	}
}
