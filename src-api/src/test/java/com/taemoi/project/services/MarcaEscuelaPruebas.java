package com.taemoi.project.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.OrganizacionRepository;

import jakarta.persistence.EntityManagerFactory;

/** Pieza E7-7a: marcas de escuela para los tests unitarios (datos ficticios). */
public final class MarcaEscuelaPruebas {

	private MarcaEscuelaPruebas() {
	}

	/** Marca de Moiskimdo SIN logos (para correos, que no los usan). */
	public static MarcaEscuela moiskimdoSinLogos() {
		return new MarcaEscuela(1L, true, "Moi's Kim Do", "", "", null, null, null);
	}

	/** Servicio simulado que da la marca de Moiskimdo para cualquier escuela (tests anteriores a la E7-7a). */
	public static MarcaEscuelaService soloMoiskimdo() {
		MarcaEscuelaService servicio = mock(MarcaEscuelaService.class, withSettings().strictness(Strictness.LENIENT));
		MarcaEscuela marca = moiskimdoSinLogos();
		when(servicio.de(anyLong())).thenReturn(marca);
		when(servicio.deActiva()).thenReturn(marca);
		when(servicio.deEntidadOActiva(any())).thenReturn(marca);
		return servicio;
	}

	/** Organización de Moiskimdo (id 1, con la marca de legado). */
	public static Organizacion moiskimdo() {
		Organizacion organizacion = escuela(1L, "Moi's Kim Do");
		organizacion.setComportamientoLegadoTaemoi(true);
		return organizacion;
	}

	/** Otra escuela, sin legado ni logo. */
	public static Organizacion escuela(long id, String nombre) {
		Organizacion organizacion = new Organizacion();
		ReflectionTestUtils.setField(organizacion, "id", id);
		organizacion.setNombre(nombre);
		organizacion.setComportamientoLegadoTaemoi(false);
		return organizacion;
	}

	/**
	 * Servicio REAL sobre repositorios simulados con estas escuelas; el gestor de transacciones simulado ejecuta la
	 * transacción nueva sin base de datos.
	 */
	public static MarcaEscuelaService real(OrganizacionRepository organizaciones, ImagenRepository imagenes,
			ImagenService imagenService, Organizacion... escuelas) {
		Map<Long, Organizacion> porId = new HashMap<>();
		for (Organizacion escuela : escuelas) {
			porId.put(escuela.getId(), escuela);
		}
		lenient().when(organizaciones.findById(anyLong()))
				.thenAnswer(inv -> Optional.ofNullable(porId.get(inv.<Long>getArgument(0))));
		return new MarcaEscuelaService(organizaciones, imagenes, imagenService,
				new TransaccionEnEscuela(mock(EntityManagerFactory.class), transacciones()), new AtomicLong()::get);
	}

	public static MarcaEscuelaService real(Organizacion... escuelas) {
		return real(mock(OrganizacionRepository.class), mock(ImagenRepository.class), mock(ImagenService.class),
				escuelas);
	}

	static PlatformTransactionManager transacciones() {
		PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class,
				withSettings().strictness(Strictness.LENIENT));
		when(transacciones.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		return transacciones;
	}
}
