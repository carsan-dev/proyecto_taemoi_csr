package com.taemoi.project.marca;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.dtos.response.MarcaPanelDTO;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.plataforma.EscuelaPlataformaConTemaDTO;
import com.taemoi.project.plataforma.EscuelaPlataformaDTO;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.ImagenService;

/**
 * Pieza 8b-T (fase3-e8b-plan.md §4): la marca de la escuela en el panel.
 *
 * <ul>
 * <li>Lectura ({@link #marcaActiva()}, {@link #logoActivo()}): SIEMPRE la escuela activa de la sesión
 * ({@link TenantContext}); sin ella, 403. La imagen del logo se filtra además por {@code organizacion_id}: otra escuela
 * no se ve.</li>
 * <li>Escritura ({@link #cambiarColor}): solo el superadmin (decisiones 135, 136 y 212), en la escuela DESTINO con
 * {@link TransaccionEnEscuela} como el logo (E7-7a). Se guarda el color tal cual (en mayúsculas) y se registra en el
 * registro de acciones de esa escuela con su id y {@code quitado}; nunca el valor del color. Todo 2xx registra, también
 * el que no cambia nada ({@code sinCambios=SI}).</li>
 * </ul>
 */
@Service
public class MarcaPanelService {

	static final String MODULO_AUDITORIA = "plataforma";

	/** PNG del logo subido de la escuela activa y el id de su imagen (la versión, para el ETag). */
	public record LogoPanel(byte[] png, Long imagenId) {
	}

	private final OrganizacionRepository organizaciones;
	private final ImagenRepository imagenes;
	private final ImagenService imagenService;
	private final AuditoriaService auditoria;
	private final TransaccionEnEscuela enEscuela;

	public MarcaPanelService(OrganizacionRepository organizaciones, ImagenRepository imagenes,
			ImagenService imagenService, AuditoriaService auditoria, TransaccionEnEscuela enEscuela) {
		this.organizaciones = organizaciones;
		this.imagenes = imagenes;
		this.imagenService = imagenService;
		this.auditoria = auditoria;
		this.enEscuela = enEscuela;
	}

	/** La marca de la escuela activa: nombre, de dónde sale el logo y el tema. */
	@Transactional(readOnly = true)
	public MarcaPanelDTO marcaActiva() {
		Organizacion escuela = escuelaActiva();
		Optional<Imagen> logo = logoSubido(escuela);
		MarcaPanelDTO.Logo origen = logo.isPresent() ? MarcaPanelDTO.Logo.SUBIDO
				: Boolean.TRUE.equals(escuela.getComportamientoLegadoTaemoi()) ? MarcaPanelDTO.Logo.LEGADO
						: MarcaPanelDTO.Logo.NINGUNO;
		return new MarcaPanelDTO(escuela.getNombre(), origen, logo.map(Imagen::getId).orElse(null),
				ReglaContraste.tema(escuela.getColorPrimario()));
	}

	/**
	 * Versión (id de la imagen) del logo subido de la escuela activa, sin leer el fichero: para responder 304 con el
	 * ETag sin tocar el disco.
	 *
	 * @throws NoSuchElementException (404) sin logo subido
	 */
	@Transactional(readOnly = true)
	public Long versionLogoActiva() {
		return logoSubido(escuelaActiva()).map(Imagen::getId)
				.orElseThrow(() -> new NoSuchElementException("La escuela no tiene logo."));
	}

	/**
	 * El PNG del logo subido de la escuela activa.
	 *
	 * @throws NoSuchElementException (404) sin logo subido (también Moiskimdo, que usa el SVG de siempre) o sin fichero
	 */
	@Transactional(readOnly = true)
	public LogoPanel logoActivo() {
		Organizacion escuela = escuelaActiva();
		Imagen imagen = logoSubido(escuela).orElseThrow(() -> new NoSuchElementException("La escuela no tiene logo."));
		Path fichero = imagenService.resolverRutaArchivo(imagen);
		if (fichero == null || !Files.isRegularFile(fichero)) {
			throw new NoSuchElementException("La escuela no tiene logo.");
		}
		try {
			return new LogoPanel(Files.readAllBytes(fichero), imagen.getId());
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer el logo", e);
		}
	}

	/**
	 * El superadmin guarda ({@code #RRGGBB}) o quita ({@code null}) el color de una escuela.
	 *
	 * @throws IllegalArgumentException (400) el color no tiene el formato {@code #RRGGBB}
	 * @throws NoSuchElementException   (404) la escuela no existe
	 */
	public EscuelaPlataformaConTemaDTO cambiarColor(Long organizacionId, String colorPrimario) {
		String nuevo = colorPrimario == null ? null : ReglaContraste.normalizar(colorPrimario);
		if (organizacionId == null || organizacionId <= 0) {
			throw new NoSuchElementException("Escuela no encontrada.");
		}
		return enEscuela.escribir(organizacionId, () -> {
			Organizacion escuela = organizaciones.findByIdParaActualizar(organizacionId)
					.orElseThrow(() -> new NoSuchElementException("Escuela no encontrada."));
			String anterior = escuela.getColorPrimario();
			boolean sinCambios = anterior == null ? nuevo == null : anterior.equalsIgnoreCase(nuevo);
			if (!sinCambios) {
				escuela.setColorPrimario(nuevo);
				organizaciones.save(escuela);
			}
			// Solo ids y sí/no (punto de revisión 3): nunca el valor del color.
			auditoria.registrarAccesoSensible(AuditoriaAccion.UPDATE, MODULO_AUDITORIA,
					"/api/plataforma/escuelas/" + organizacionId + "/marca", organizacionId,
					"PLATAFORMA MARCA_COLOR organizacion=#" + organizacionId + " quitado=" + siNo(nuevo == null)
							+ (sinCambios ? " sinCambios=SI" : ""));
			EscuelaPlataformaDTO dto = new EscuelaPlataformaDTO(escuela.getId(), escuela.getNombre(),
					escuela.getLogoImagenId() != null, Boolean.TRUE.equals(escuela.getComportamientoLegadoTaemoi()),
					escuela.getColorPrimario());
			return EscuelaPlataformaConTemaDTO.de(dto, ReglaContraste.tema(escuela.getColorPrimario()));
		});
	}

	// ------------------------------------------------------------------ apoyo

	private Organizacion escuelaActiva() {
		Long organizacion = TenantContext.get();
		if (organizacion == null) {
			throw new AccessDeniedException("Acceso denegado.");
		}
		return organizaciones.findById(organizacion)
				.orElseThrow(() -> new NoSuchElementException("Escuela no encontrada."));
	}

	/** La imagen del logo subido, solo si es de esta escuela. */
	private Optional<Imagen> logoSubido(Organizacion escuela) {
		if (escuela.getLogoImagenId() == null) {
			return Optional.empty();
		}
		return imagenes.findById(escuela.getLogoImagenId()).filter(i -> escuela.getId().equals(i.getOrganizacionId()));
	}

	private static String siNo(boolean valor) {
		return valor ? "SI" : "NO";
	}
}
