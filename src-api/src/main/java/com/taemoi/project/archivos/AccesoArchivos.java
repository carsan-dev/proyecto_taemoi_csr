package com.taemoi.project.archivos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.organizacion.ResolutorOrganizacion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.DocumentoRepository;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.services.DocumentoService;
import com.taemoi.project.services.ImagenService;

/**
 * Decide si una petición a {@code /imagenes/**} o {@code /documentos/**} puede leer un fichero y cuál.
 *
 * <ol>
 * <li>La ruta de la URL se valida y decodifica ({@link RutasArchivos#normalizarRutaSolicitada}); cualquier
 * recorrido se rechaza.</li>
 * <li>El fichero debe existir dentro de la raíz configurada, comprobado sobre la ruta REAL (sin enlaces,
 * con las mayúsculas reales del disco), y se clasifica sobre esa ruta real.</li>
 * <li>Público (eventos y demás imágenes públicas): se sirve a cualquiera, de cualquier escuela.</li>
 * <li>Privado (fotos de alumnos, documentos de alumnos y cualquier otro documento): solo personal
 * ({@code ROLE_ADMIN}/{@code ROLE_MANAGER}, que ya se derivan de la membresía en la escuela activa); la
 * ruta debe ser de la escuela activa ({@code org-<activa>/…}, o sin prefijo solo si la activa es la escuela
 * por defecto); y debe existir una fila {@code Documento}/{@code Imagen} de la escuela activa (consulta
 * filtrada por {@code @TenantId}) cuyo fichero sea exactamente el pedido. Sin fila, no se sirve.</li>
 * </ol>
 * Cualquier denegación se presenta como «no encontrado» para no revelar qué existe.
 */
@Service
public class AccesoArchivos {

	private static final Logger logger = LoggerFactory.getLogger(AccesoArchivos.class);

	/** Fichero que se puede servir y si es público (para la caché). */
	public record ArchivoServible(Path ruta, boolean publico) {
	}

	/** Fichero real dentro de la raíz y su ruta relativa real (separador {@code /}). */
	private record Ubicacion(Path real, String relativa) {
	}

	private final ImagenRepository imagenes;
	private final DocumentoRepository documentos;
	private final ImagenService imagenService;
	private final DocumentoService documentoService;
	private final ResolutorOrganizacion resolutor;

	@Value("${app.imagenes.directorio.linux}")
	private String directorioImagenesLinux;

	@Value("${app.imagenes.directorio.windows}")
	private String directorioImagenesWindows;

	@Value("${app.documentos.directorio.linux}")
	private String directorioDocumentosLinux;

	@Value("${app.documentos.directorio.windows}")
	private String directorioDocumentosWindows;

	public AccesoArchivos(ImagenRepository imagenes, DocumentoRepository documentos, ImagenService imagenService,
			DocumentoService documentoService, ResolutorOrganizacion resolutor) {
		this.imagenes = imagenes;
		this.documentos = documentos;
		this.imagenService = imagenService;
		this.documentoService = documentoService;
		this.resolutor = resolutor;
	}

	/** @param rutaCodificada parte de la URL tras {@code /imagenes/}, sin decodificar. */
	public Optional<ArchivoServible> imagen(String rutaCodificada, Authentication autenticacion) {
		Path base = RutasArchivos.directorioBase(directorioImagenesLinux, directorioImagenesWindows);
		return localizar(base, rutaCodificada).flatMap(u -> {
			if (RutasArchivos.esImagenPublica(u.relativa())) {
				return Optional.of(new ArchivoServible(u.real(), true));
			}
			if (!puedeLeerPrivado(u.relativa(), autenticacion) || !esImagenDeLaEscuela(base, u)) {
				return Optional.empty();
			}
			return Optional.of(new ArchivoServible(u.real(), false));
		});
	}

	/** @param rutaCodificada parte de la URL tras {@code /documentos/}, sin decodificar. */
	public Optional<ArchivoServible> documento(String rutaCodificada, Authentication autenticacion) {
		Path base = RutasArchivos.directorioBase(directorioDocumentosLinux, directorioDocumentosWindows);
		return localizar(base, rutaCodificada).flatMap(u -> {
			if (RutasArchivos.esDocumentoPublico(u.relativa())) {
				return Optional.of(new ArchivoServible(u.real(), true));
			}
			if (!puedeLeerPrivado(u.relativa(), autenticacion) || !esDocumentoDeLaEscuela(u)) {
				return Optional.empty();
			}
			return Optional.of(new ArchivoServible(u.real(), false));
		});
	}

	// ------------------------------------------------------------------ comprobaciones

	/** Fichero regular existente dentro de la raíz, como ruta real; vacío ante cualquier anomalía. */
	private Optional<Ubicacion> localizar(Path base, String rutaCodificada) {
		Optional<String> relativa = RutasArchivos.normalizarRutaSolicitada(rutaCodificada);
		if (relativa.isEmpty()) {
			return Optional.empty();
		}
		try {
			Path candidata = RutasArchivos.resolverDentroDe(base, relativa.get());
			if (!Files.isRegularFile(candidata) || !Files.isDirectory(base)) {
				return Optional.empty();
			}
			Path baseReal = base.toRealPath();
			Path real = candidata.toRealPath();
			if (!real.startsWith(baseReal) || real.equals(baseReal) || !Files.isRegularFile(real)) {
				return Optional.empty();
			}
			return Optional.of(new Ubicacion(real, baseReal.relativize(real).toString().replace(java.io.File.separatorChar, '/')));
		} catch (IOException | RuntimeException e) {
			logger.debug("Ruta de fichero rechazada: {}", e.getMessage());
			return Optional.empty();
		}
	}

	private boolean puedeLeerPrivado(String relativaReal, Authentication autenticacion) {
		if (!esPersonal(autenticacion)) {
			return false;
		}
		Long activa = TenantContext.get();
		if (activa == null) {
			return false;
		}
		Long deLaRuta = RutasArchivos.organizacionDeRuta(relativaReal);
		return deLaRuta != null ? deLaRuta.equals(activa) : activa.equals(resolutor.getOrganizacionPorDefecto());
	}

	private static boolean esPersonal(Authentication autenticacion) {
		if (autenticacion == null || !autenticacion.isAuthenticated()
				|| autenticacion instanceof AnonymousAuthenticationToken) {
			return false;
		}
		for (GrantedAuthority autoridad : autenticacion.getAuthorities()) {
			String nombre = autoridad.getAuthority();
			if (Roles.ROLE_ADMIN.toString().equals(nombre) || Roles.ROLE_MANAGER.toString().equals(nombre)) {
				return true;
			}
		}
		return false;
	}

	/** Hay una {@code Imagen} de la escuela activa cuyo fichero es {@code real}. */
	private boolean esImagenDeLaEscuela(Path base, Ubicacion u) {
		// Con prefijo org-<id>: ruta exacta (índice). Sin prefijo (heredada, ruta absoluta): por nombre.
		List<Imagen> candidatas = RutasArchivos.organizacionDeRuta(u.relativa()) != null
				? imagenes.findByRuta(u.relativa())
				: imagenes.findByRutaEndingWith(u.real().getFileName().toString());
		for (Imagen imagen : candidatas) {
			for (Path candidata : rutasDeImagen(base, imagen)) {
				if (mismoFichero(candidata, u.real())) {
					return true;
				}
			}
		}
		return false;
	}

	private List<Path> rutasDeImagen(Path base, Imagen imagen) {
		List<Path> rutas = new ArrayList<>(2);
		Path guardada = imagenService.resolverRutaArchivo(imagen);
		if (guardada != null) {
			rutas.add(guardada);
		}
		// Fila heredada con ruta absoluta de otro entorno: el fichero se movió a <raiz>/alumnos/<nombre>.
		if (RutasArchivos.esRutaAbsoluta(imagen.getRuta()) && imagen.getNombre() != null) {
			try {
				rutas.add(RutasArchivos.resolverDentroDe(base, RutasArchivos.CARPETA_ALUMNOS + "/" + imagen.getNombre()));
			} catch (IllegalArgumentException e) {
				// Nombre no válido: no aporta candidata.
			}
		}
		return rutas;
	}

	/** Hay un {@code Documento} de la escuela activa cuyo fichero es {@code real}. */
	private boolean esDocumentoDeLaEscuela(Ubicacion u) {
		List<Documento> candidatos = RutasArchivos.organizacionDeRuta(u.relativa()) != null
				? documentos.findByRuta(u.relativa())
				: documentos.findByRutaEndingWith(u.real().getFileName().toString());
		for (Documento documento : candidatos) {
			try {
				if (mismoFichero(documentoService.resolverRutaArchivo(documento), u.real())) {
					return true;
				}
			} catch (RuntimeException e) {
				logger.debug("Documento {} con ruta no resoluble: {}", documento.getId(), e.getMessage());
			}
		}
		return false;
	}

	private static boolean mismoFichero(Path candidata, Path real) {
		try {
			return candidata != null && Files.exists(candidata) && Files.isSameFile(candidata, real);
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}
}
