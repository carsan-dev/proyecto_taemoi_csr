package com.taemoi.project.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.OrganizacionRepository;

/**
 * Pieza E7-7a (decisión 211): la {@link MarcaEscuela} de cada escuela para sus PDF y correos.
 *
 * <p>Escuela con {@code comportamiento_legado_taemoi} (Moiskimdo) → los mismos textos de siempre y, sin logo subido (o
 * si el apuntado no existe o no es suyo), los mismos logos de classpath convertidos igual que antes; con logo subido
 * (E7-8, decisión 213), ese PNG en los tres huecos, como en otra escuela. Otra escuela → {@code organizacion.nombre}
 * y su logo ({@code organizacion.logo_imagen_id}, PNG en {@code org-<id>/logo}); sin logo, solo el nombre.
 *
 * <p>La escuela es SIEMPRE explícita: {@link #deActiva()} lee la del hilo y falla cerrado si no hay ninguna (nunca cae
 * en la escuela por defecto); los correos que salen tras el commit usan {@link #de(Long)} con la de la entidad. Si el
 * hilo no tiene esa escuela fijada, la lectura se hace en una transacción nueva con ella (sesión de Hibernate propia).
 *
 * <p>Caché por escuela de {@link #VIGENCIA_MS} (el nombre solo cambia por SQL), invalidada al subir o quitar un logo
 * ({@link #invalidar}).
 */
@Service
public class MarcaEscuelaService {

	private static final Logger log = LoggerFactory.getLogger(MarcaEscuelaService.class);

	static final long VIGENCIA_MS = 30_000L;

	private record Lectura(MarcaEscuela marca, long leidaEn) {
	}

	/** Logos de classpath de Moiskimdo, cargados una vez. */
	private record LogosLegado(String informes, String solicitud, byte[] certificado) {
	}

	private final OrganizacionRepository organizaciones;
	private final ImagenRepository imagenes;
	private final ImagenService imagenService;
	private final TransaccionEnEscuela enEscuela;
	private final LongSupplier reloj;
	private final Map<Long, Lectura> lecturas = new ConcurrentHashMap<>();
	private volatile LogosLegado logosLegado;

	@Autowired
	public MarcaEscuelaService(OrganizacionRepository organizaciones, ImagenRepository imagenes,
			ImagenService imagenService, TransaccionEnEscuela enEscuela) {
		this(organizaciones, imagenes, imagenService, enEscuela, System::currentTimeMillis);
	}

	/** Para los tests: reloj controlable (milisegundos). */
	MarcaEscuelaService(OrganizacionRepository organizaciones, ImagenRepository imagenes, ImagenService imagenService,
			TransaccionEnEscuela enEscuela, LongSupplier reloj) {
		this.organizaciones = organizaciones;
		this.imagenes = imagenes;
		this.imagenService = imagenService;
		this.enEscuela = enEscuela;
		this.reloj = reloj;
	}

	/**
	 * Marca de la escuela activa del hilo.
	 *
	 * @throws IllegalStateException si no hay escuela activa.
	 */
	public MarcaEscuela deActiva() {
		Long activa = TenantContext.get();
		if (activa == null) {
			throw new IllegalStateException("No hay escuela activa para la marca de los documentos");
		}
		return de(activa);
	}

	/** Marca de la escuela de una entidad ({@code organizacion_id}); sin ella (aún sin guardar), la activa. */
	public MarcaEscuela deEntidadOActiva(Long organizacionId) {
		return organizacionId != null ? de(organizacionId) : deActiva();
	}

	/**
	 * Marca de una escuela concreta.
	 *
	 * @throws IllegalStateException si la escuela no existe.
	 */
	public MarcaEscuela de(Long organizacionId) {
		Objects.requireNonNull(organizacionId, "organizacionId");
		long ahora = reloj.getAsLong();
		Lectura lectura = lecturas.get(organizacionId);
		if (lectura == null || ahora - lectura.leidaEn() > VIGENCIA_MS) {
			lectura = new Lectura(cargar(organizacionId), ahora);
			if (!faltaLogoLegado(lectura.marca())) {
				lecturas.put(organizacionId, lectura);
			}
		}
		return lectura.marca();
	}

	/** Olvida la marca guardada de una escuela (logo subido o quitado). */
	public void invalidar(Long organizacionId) {
		if (organizacionId != null) {
			lecturas.remove(organizacionId);
		}
	}

	private MarcaEscuela cargar(Long organizacionId) {
		if (organizacionId.equals(TenantContext.get())) {
			return leer(organizacionId);
		}
		// Hilo sin escuela (correo tras el commit) u otra escuela: sesión nueva con la escuela pedida.
		return enEscuela.leer(organizacionId, () -> leer(organizacionId));
	}

	private MarcaEscuela leer(Long organizacionId) {
		Organizacion organizacion = organizaciones.findById(organizacionId)
				.orElseThrow(() -> new IllegalStateException("Escuela no encontrada: " + organizacionId));
		byte[] logo = logoSubido(organizacion);
		String dataUri = logo == null ? "" : "data:image/png;base64," + Base64.getEncoder().encodeToString(logo);
		if (Boolean.TRUE.equals(organizacion.getComportamientoLegadoTaemoi())) {
			// E7-8 (decisión 213): con logo subido, ese en sus tres huecos (como en otra escuela); sin él, los de siempre.
			if (logo != null) {
				return new MarcaEscuela(organizacionId, true, organizacion.getNombre(), dataUri, dataUri, logo, null,
						null);
			}
			LogosLegado logos = logosLegado();
			return new MarcaEscuela(organizacionId, true, organizacion.getNombre(), logos.informes(), logos.solicitud(),
					logos.certificado(), null, null);
		}
		return new MarcaEscuela(organizacionId, false, organizacion.getNombre(), dataUri, dataUri, logo,
				new MarcaEscuela.DatosLegales(organizacion.getNombreLegal(), organizacion.getCif(),
						organizacion.getLocalidad(), organizacion.getFirmanteNombre(), organizacion.getFirmanteNif(),
						organizacion.getFirmanteCargo()),
				// Remitente: organizacion.email_remitente_nombre si está lleno; si no, su nombre.
				organizacion.getEmailRemitenteNombre());
	}

	/**
	 * PNG del logo de siempre de Moiskimdo (el de la cabecera de los informes), para previsualizarlo cuando no tiene
	 * otro subido; {@code null} si no se pudo cargar.
	 */
	public byte[] logoDeSiemprePng() {
		String dataUri = logosLegado().informes();
		int coma = dataUri.indexOf(',');
		return coma < 0 ? null : Base64.getDecoder().decode(dataUri.substring(coma + 1));
	}

	/** PNG del logo de la escuela, solo si la imagen es de ESA escuela y el fichero existe; si no, {@code null}. */
	private byte[] logoSubido(Organizacion organizacion) {
		Long imagenId = organizacion.getLogoImagenId();
		if (imagenId == null) {
			return null;
		}
		Imagen imagen = imagenes.findById(imagenId).orElse(null);
		if (imagen == null || !organizacion.getId().equals(imagen.getOrganizacionId())) {
			log.warn("Logo {} de la escuela {} no encontrado en esa escuela: se ignora", imagenId,
					organizacion.getId());
			return null;
		}
		Path fichero = imagenService.resolverRutaArchivo(imagen);
		try {
			return fichero == null ? null : Files.readAllBytes(fichero);
		} catch (IOException e) {
			log.warn("No se pudo leer el logo {} de la escuela {}: se ignora", imagenId, organizacion.getId());
			return null;
		}
	}

	// ------------------------------------------------------------------ Moiskimdo: lo de siempre

	private LogosLegado logosLegado() {
		LogosLegado logos = logosLegado;
		if (logos == null) {
			logos = new LogosLegado(logoInformesLegado(), logoSolicitudLegado(), bytesClasspath(
					"static/Logo_nuevo_sin_vectorizar.jpeg"));
			// Un fallo al cargar un logo de classpath no se guarda: se reintenta en el siguiente documento, como antes.
			if (!logos.informes().isEmpty() && !logos.solicitud().isEmpty() && logos.certificado() != null) {
				logosLegado = logos;
			}
		}
		return logos;
	}

	/** Moiskimdo sin alguno de sus logos (falló su carga): esa marca no se guarda en la caché. */
	private static boolean faltaLogoLegado(MarcaEscuela marca) {
		return marca.legado() && (marca.logoInformes().isEmpty() || marca.logoSolicitud().isEmpty()
				|| marca.logoCertificado() == null);
	}

	/** logo_escuela.svg a PNG de 300x300 con Batik, como hacía PDFServiceImpl#getLogoPngBase64; vacío si falla. */
	private static String logoInformesLegado() {
		try (InputStream svg = new ClassPathResource("static/logo_escuela.svg").getInputStream()) {
			PNGTranscoder transcoder = new PNGTranscoder();
			transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, 300f);
			transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, 300f);
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			transcoder.transcode(new TranscoderInput(svg), new TranscoderOutput(png));
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
		} catch (Exception e) {
			log.error("Error converting logo SVG to PNG: {}", e.getMessage());
			return "";
		}
	}

	/** logo_tkd_kick.jpeg como data URI, como hacía PDFServiceImpl#getLogoPreinscripcionBase64; vacío si falla. */
	private static String logoSolicitudLegado() {
		byte[] jpeg = bytesClasspath("static/logo_tkd_kick.jpeg");
		return jpeg == null ? "" : "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
	}

	private static byte[] bytesClasspath(String ruta) {
		ClassPathResource recurso = new ClassPathResource(ruta);
		if (!recurso.exists()) {
			return null;
		}
		try (InputStream entrada = recurso.getInputStream()) {
			return entrada.readAllBytes();
		} catch (IOException e) {
			return null;
		}
	}
}
