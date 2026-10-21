package com.taemoi.project.plataforma;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.ImagenService;
import com.taemoi.project.services.MarcaEscuelaService;
import com.taemoi.project.utils.DocumentoSecurityUtils;

/**
 * Pieza E7-7a (decisión 212): el logo de una escuela, que sube o quita el superadmin. Es su ÚNICA escritura dentro de
 * una escuela, acotada a la marca, y queda en el registro de acciones de ESA escuela (D3): acción, superadmin, imagen
 * y escuela por id; nunca el nombre del fichero subido.
 *
 * <p>La escuela destino es explícita: todo se hace en una transacción NUEVA con esa escuela fijada (sesión de
 * Hibernate propia), aunque el superadmin tenga otra activa o ninguna; así la imagen y el evento de auditoría llevan su
 * {@code organizacion_id}. La escuela se bloquea mientras tanto (dos subidas a la vez se ordenan).
 *
 * <p>El logo anterior se BORRA (fila y fichero; el fichero, tras confirmar): no quedan huérfanos. Moiskimdo (marca
 * heredada) también admite logo desde la E7-8 (decisión 213): solo cambia el logo de sus documentos; quitarlo lo
 * devuelve al de siempre.
 */
@Service
public class LogoEscuelaService {

	private static final Logger log = LoggerFactory.getLogger(LogoEscuelaService.class);

	static final String MODULO_AUDITORIA = "plataforma";

	private final OrganizacionRepository organizaciones;
	private final ImagenRepository imagenes;
	private final ImagenService imagenService;
	private final LogoEscuelaProcesador procesador;
	private final MarcaEscuelaService marcaEscuela;
	private final AuditoriaService auditoria;
	private final TransaccionEnEscuela enEscuela;

	public LogoEscuelaService(OrganizacionRepository organizaciones, ImagenRepository imagenes,
			ImagenService imagenService, LogoEscuelaProcesador procesador, MarcaEscuelaService marcaEscuela,
			AuditoriaService auditoria, TransaccionEnEscuela enEscuela) {
		this.organizaciones = organizaciones;
		this.imagenes = imagenes;
		this.imagenService = imagenService;
		this.procesador = procesador;
		this.marcaEscuela = marcaEscuela;
		this.auditoria = auditoria;
		this.enEscuela = enEscuela;
	}

	/** Resultado de un cambio: la escuela tal como queda y el fichero que hay que borrar tras confirmar. */
	private record Cambio(EscuelaPlataformaDTO escuela, Path ficheroAnterior) {
	}

	/**
	 * Sube (o sustituye) el logo: PNG, JPG o SVG validados por contenido; se guarda solo el PNG resultante.
	 *
	 * @throws IllegalArgumentException archivo vacío, de otro tipo, demasiado grande o con dimensiones fuera de rango
	 * @throws NoSuchElementException   la escuela no existe
	 */
	public EscuelaPlataformaDTO subir(Long organizacionId, MultipartFile archivo) {
		if (archivo == null || archivo.isEmpty()) {
			throw new IllegalArgumentException("Falta el fichero del logo.");
		}
		// Decisión 227: HEIC/HEIF se rechaza con el mensaje de convertir a JPG (no se convierte).
		DocumentoSecurityUtils.rechazarHeic(archivo.getOriginalFilename(), archivo.getContentType());
		if (archivo.getSize() > LogoEscuelaProcesador.TAMANO_MAXIMO) {
			throw new IllegalArgumentException(
					"El logo no puede superar " + (LogoEscuelaProcesador.TAMANO_MAXIMO / 1024) + " KB.");
		}
		byte[] contenido;
		try {
			contenido = archivo.getBytes();
		} catch (IOException e) {
			throw new IllegalArgumentException("No se pudo leer el fichero del logo.");
		}
		byte[] png = procesador.procesar(contenido).png();

		Path[] escrito = new Path[1];
		Cambio cambio;
		try {
			cambio = enLaEscuela(organizacionId, () -> {
				Organizacion escuela = bloquear(organizacionId);
				Imagen nueva;
				try {
					nueva = imagenService.guardarLogoEscuela(organizacionId, png);
				} catch (IOException e) {
					throw new UncheckedIOException("No se pudo guardar el logo", e);
				}
				escrito[0] = imagenService.resolverRutaArchivo(nueva);
				nueva = imagenes.save(nueva);
				Long anteriorId = escuela.getLogoImagenId();
				escuela.setLogoImagenId(nueva.getId());
				organizaciones.save(escuela);
				Path ficheroAnterior = borrarFila(anteriorId, organizacionId);
				auditoria.registrarAccesoSensible(AuditoriaAccion.UPDATE, MODULO_AUDITORIA, ruta(organizacionId),
						nueva.getId(), "Superadmin: logo de la escuela " + organizacionId + " cambiado (imagen "
								+ nueva.getId() + (anteriorId == null ? "" : ", sustituye a la imagen " + anteriorId)
								+ ")");
				return new Cambio(dto(escuela), ficheroAnterior);
			});
		} catch (RuntimeException e) {
			// Deshecho: el fichero recién escrito no lo apunta nadie.
			borrarFichero(escrito[0]);
			throw e;
		}
		borrarFichero(cambio.ficheroAnterior());
		marcaEscuela.invalidar(organizacionId);
		return cambio.escuela();
	}

	/**
	 * Quita el logo (la escuela vuelve a salir solo con su nombre; Moiskimdo, con el de siempre). Sin logo, no cambia
	 * nada ni se registra.
	 */
	public EscuelaPlataformaDTO quitar(Long organizacionId) {
		Cambio cambio = enLaEscuela(organizacionId, () -> {
			Organizacion escuela = bloquear(organizacionId);
			Long anteriorId = escuela.getLogoImagenId();
			if (anteriorId == null) {
				return new Cambio(dto(escuela), null);
			}
			escuela.setLogoImagenId(null);
			organizaciones.save(escuela);
			Path ficheroAnterior = borrarFila(anteriorId, organizacionId);
			auditoria.registrarAccesoSensible(AuditoriaAccion.DELETE, MODULO_AUDITORIA, ruta(organizacionId),
					anteriorId, "Superadmin: logo de la escuela " + organizacionId + " quitado (imagen " + anteriorId
							+ ")");
			return new Cambio(dto(escuela), ficheroAnterior);
		});
		borrarFichero(cambio.ficheroAnterior());
		marcaEscuela.invalidar(organizacionId);
		return cambio.escuela();
	}

	/**
	 * PNG del logo subido (para previsualizarlo); Moiskimdo sin logo subido, el PNG de su logo de siempre.
	 *
	 * @throws NoSuchElementException la escuela no existe o no tiene logo
	 */
	public byte[] logo(Long organizacionId) {
		if (organizacionId == null || organizacionId <= 0) {
			throw new NoSuchElementException("Escuela no encontrada.");
		}
		return enEscuela.leer(organizacionId, () -> {
			Organizacion escuela = organizaciones.findById(organizacionId)
					.orElseThrow(() -> new NoSuchElementException("Escuela no encontrada."));
			Imagen imagen = escuela.getLogoImagenId() == null ? null
					: imagenes.findById(escuela.getLogoImagenId())
							.filter(i -> organizacionId.equals(i.getOrganizacionId())).orElse(null);
			Path fichero = imagen == null ? null : imagenService.resolverRutaArchivo(imagen);
			if (fichero == null || !Files.isRegularFile(fichero)) {
				// Moiskimdo sin logo subido: el de siempre (el que llevan sus documentos), para la miniatura.
				byte[] deSiempre = Boolean.TRUE.equals(escuela.getComportamientoLegadoTaemoi())
						? marcaEscuela.logoDeSiemprePng() : null;
				if (deSiempre == null) {
					throw new NoSuchElementException("La escuela no tiene logo.");
				}
				return deSiempre;
			}
			try {
				return Files.readAllBytes(fichero);
			} catch (IOException e) {
				throw new UncheckedIOException("No se pudo leer el logo", e);
			}
		});
	}

	// ------------------------------------------------------------------ apoyo

	private <T> T enLaEscuela(Long organizacionId, java.util.function.Supplier<T> accion) {
		if (organizacionId == null || organizacionId <= 0) {
			throw new NoSuchElementException("Escuela no encontrada.");
		}
		return enEscuela.escribir(organizacionId, accion);
	}

	private Organizacion bloquear(Long organizacionId) {
		return organizaciones.findByIdParaActualizar(organizacionId)
				.orElseThrow(() -> new NoSuchElementException("Escuela no encontrada."));
	}

	/** Borra la fila de la imagen anterior (solo si es de esta escuela); devuelve su fichero para borrarlo después. */
	private Path borrarFila(Long imagenId, Long organizacionId) {
		if (imagenId == null) {
			return null;
		}
		Imagen anterior = imagenes.findById(imagenId).filter(i -> organizacionId.equals(i.getOrganizacionId()))
				.orElse(null);
		if (anterior == null) {
			return null;
		}
		Path fichero = imagenService.resolverRutaArchivo(anterior);
		imagenes.delete(anterior);
		return fichero;
	}

	private static void borrarFichero(Path fichero) {
		if (fichero == null) {
			return;
		}
		try {
			Files.deleteIfExists(fichero);
		} catch (IOException e) {
			log.warn("No se pudo borrar el fichero de logo {}: {}", fichero.getFileName(), e.getMessage());
		}
	}

	private static String ruta(Long organizacionId) {
		return "/api/plataforma/escuelas/" + organizacionId + "/logo";
	}

	private static EscuelaPlataformaDTO dto(Organizacion escuela) {
		return new EscuelaPlataformaDTO(escuela.getId(), escuela.getNombre(), escuela.getLogoImagenId() != null,
				Boolean.TRUE.equals(escuela.getComportamientoLegadoTaemoi()), escuela.getColorPrimario());
	}
}
