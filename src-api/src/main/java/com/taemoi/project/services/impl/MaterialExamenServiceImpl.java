package com.taemoi.project.services.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.config.ExamMaterialBlockConfig;
import com.taemoi.project.dtos.response.MaterialExamenDTO;
import com.taemoi.project.dtos.response.MaterialExamenTemarioDTO;
import com.taemoi.project.dtos.response.MaterialExamenVideoDTO;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.services.AlumnoDeporteService;
import com.taemoi.project.services.MaterialExamenService;

@Service
public class MaterialExamenServiceImpl implements MaterialExamenService {

	private static final Logger logger = LoggerFactory.getLogger(MaterialExamenServiceImpl.class);
	private static final Pattern ORDEN_VIDEO_PATTERN = Pattern.compile("^(\\d{1,3})[_\\-.\\s]+(.+)$");
	private static final int ORDEN_POR_DEFECTO = 10_000;

	@Value("${app.documentos.directorio.linux}")
	private String directorioDocumentosLinux;

	@Value("${app.documentos.directorio.windows}")
	private String directorioDocumentosWindows;

	@Autowired
	private AlumnoDeporteService alumnoDeporteService;

	@Autowired
	private ExamMaterialBlockConfig examMaterialBlockConfig;

	@Autowired
	private ObjectMapper objectMapper;

	@Override
	public MaterialExamenDTO obtenerMaterialExamen(Long alumnoId, Deporte deporte) {
		MaterialContext contexto = resolverContexto(alumnoId, deporte);

		MaterialExamenDTO response = new MaterialExamenDTO();
		response.setDeporte(deporte != null ? deporte.name() : null);
		response.setGradoActual(contexto.gradoActual());
		response.setBloqueId(contexto.bloqueId());

		if (contexto.temario() != null) {
			response.setTemario(new MaterialExamenTemarioDTO(
					contexto.temario().getFileName().toString(),
					"/api/alumnos/" + alumnoId + "/deportes/" + deporte.name() + "/material-examen/temario"));
		}

		List<MaterialExamenVideoDTO> videos = contexto.videos().stream()
				.map(video -> new MaterialExamenVideoDTO(
						video.fileName(),
						video.title(),
						video.order(),
						"/api/alumnos/" + alumnoId + "/deportes/" + deporte.name()
								+ "/material-examen/videos/"
								+ UriUtils.encodePathSegment(video.fileName(), StandardCharsets.UTF_8)))
				.toList();
		response.setVideos(videos);

		return response;
	}

	@Override
	public MaterialExamenArchivo obtenerTemario(Long alumnoId, Deporte deporte) {
		MaterialContext contexto = resolverContexto(alumnoId, deporte);
		if (contexto.temario() == null) {
			throw new NoSuchElementException("No hay temario para el bloque de material actual");
		}

		Path temarioPath = contexto.temario();
		return crearArchivo(temarioPath, "application/pdf");
	}

	@Override
	public MaterialExamenArchivo obtenerVideo(Long alumnoId, Deporte deporte, String videoFile) {
		String nombreVideo = sanitizarNombreVideo(videoFile);
		MaterialContext contexto = resolverContexto(alumnoId, deporte);

		Optional<VideoFileEntry> match = contexto.videos().stream()
				.filter(video -> video.fileName().equalsIgnoreCase(nombreVideo))
				.findFirst();

		if (match.isEmpty()) {
			throw new NoSuchElementException("No se encontro el video solicitado en el bloque de material actual");
		}

		return crearArchivo(match.get().path(), null);
	}

	private MaterialContext resolverContexto(Long alumnoId, Deporte deporte) {
		if (alumnoId == null || alumnoId <= 0) {
			throw new IllegalArgumentException("El alumnoId es obligatorio");
		}
		if (deporte == null) {
			throw new IllegalArgumentException("El deporte es obligatorio");
		}

		List<AlumnoDeporte> deportesActivos = alumnoDeporteService.obtenerDeportesActivosDelAlumno(alumnoId);
		AlumnoDeporte deporteSeleccionado = deportesActivos.stream()
				.filter(alumnoDeporte -> alumnoDeporte.getDeporte() == deporte)
				.findFirst()
				.orElse(null);

		String gradoActual = null;
		String bloqueId = null;
		if (deporteSeleccionado != null && deporteSeleccionado.getGrado() != null
				&& deporteSeleccionado.getGrado().getTipoGrado() != null) {
			TipoGrado tipoGrado = deporteSeleccionado.getGrado().getTipoGrado();
			gradoActual = tipoGrado.name();
			bloqueId = examMaterialBlockConfig.obtenerBloque(deporte, tipoGrado);
		}

		if (bloqueId == null || bloqueId.isBlank()) {
			return new MaterialContext(gradoActual, null, null, List.of());
		}

		Path carpetaBloque = obtenerRutaBaseDocumentos()
				.resolve("Materiales_Examen")
				.resolve(deporte.name().toLowerCase(Locale.ROOT))
				.resolve(bloqueId)
				.normalize();

		if (!Files.isDirectory(carpetaBloque)) {
			logger.info("Bloque de material no encontrado en disco: {}", carpetaBloque);
			return new MaterialContext(gradoActual, bloqueId, null, List.of());
		}

		Path temario = resolverTemario(carpetaBloque.resolve("temario"));
		List<VideoFileEntry> videos = resolverVideos(carpetaBloque.resolve("videos"), carpetaBloque.resolve("index.json"));
		return new MaterialContext(gradoActual, bloqueId, temario, videos);
	}

	private Path resolverTemario(Path carpetaTemario) {
		if (!Files.isDirectory(carpetaTemario)) {
			return null;
		}

		Path temarioPreferido = carpetaTemario.resolve("temario.pdf");
		if (Files.isRegularFile(temarioPreferido)) {
			return temarioPreferido;
		}

		try (var stream = Files.list(carpetaTemario)) {
			return stream
					.filter(Files::isRegularFile)
					.filter(path -> extension(path).equals("pdf"))
					.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
					.findFirst()
					.orElse(null);
		} catch (IOException e) {
			logger.error("Error al listar temarios en {}", carpetaTemario, e);
			return null;
		}
	}

	private List<VideoFileEntry> resolverVideos(Path carpetaVideos, Path indexPath) {
		if (!Files.isDirectory(carpetaVideos)) {
			return List.of();
		}

		Map<String, IndexVideoEntry> indexByFile = cargarIndex(indexPath);
		List<VideoFileEntry> videos = new ArrayList<>();

		try (var stream = Files.list(carpetaVideos)) {
			stream
					.filter(Files::isRegularFile)
					.filter(this::esVideoPermitido)
					.forEach(path -> {
						String fileName = path.getFileName().toString();
						IndexVideoEntry metadata = indexByFile.get(fileName.toLowerCase(Locale.ROOT));

						Integer order = metadata != null && metadata.order() != null
								? metadata.order()
								: extraerOrden(fileName);
						String title = metadata != null && metadata.title() != null && !metadata.title().isBlank()
								? metadata.title().trim()
								: generarTituloDesdeArchivo(fileName);

						videos.add(new VideoFileEntry(fileName, title, order, path));
					});
		} catch (IOException e) {
			logger.error("Error al listar videos en {}", carpetaVideos, e);
			return List.of();
		}

		videos.sort(
				Comparator.comparing(VideoFileEntry::order)
						.thenComparing(video -> video.fileName().toLowerCase(Locale.ROOT)));
		return videos;
	}

	private Map<String, IndexVideoEntry> cargarIndex(Path indexPath) {
		if (!Files.isRegularFile(indexPath)) {
			return Map.of();
		}

		try {
			IndexFileDto indexFile = objectMapper.readValue(indexPath.toFile(), IndexFileDto.class);
			if (indexFile == null || indexFile.getVideos() == null) {
				return Map.of();
			}

			Map<String, IndexVideoEntry> map = new HashMap<>();
			for (IndexVideoDto video : indexFile.getVideos()) {
				if (video == null || video.getFile() == null || video.getFile().isBlank()) {
					continue;
				}
				map.put(
						video.getFile().trim().toLowerCase(Locale.ROOT),
						new IndexVideoEntry(video.getTitle(), video.getOrder()));
			}
			return map;
		} catch (Exception e) {
			logger.warn("No se pudo leer index.json de materiales en {}. Se usara orden por nombre.", indexPath);
			return Map.of();
		}
	}

	private int extraerOrden(String fileName) {
		String base = sinExtension(fileName);
		Matcher matcher = ORDEN_VIDEO_PATTERN.matcher(base);
		if (matcher.matches()) {
			try {
				return Integer.parseInt(matcher.group(1));
			} catch (NumberFormatException ignored) {
				return ORDEN_POR_DEFECTO;
			}
		}
		return ORDEN_POR_DEFECTO;
	}

	private String generarTituloDesdeArchivo(String fileName) {
		String base = sinExtension(fileName);
		Matcher matcher = ORDEN_VIDEO_PATTERN.matcher(base);
		String limpio = matcher.matches() ? matcher.group(2) : base;
		return limpio.replace('_', ' ').replace('-', ' ').trim();
	}

	private String sanitizarNombreVideo(String videoFile) {
		if (videoFile == null || videoFile.isBlank()) {
			throw new IllegalArgumentException("El nombre de video es obligatorio");
		}

		String cleaned = videoFile.trim();
		if (cleaned.contains("..") || cleaned.contains("/") || cleaned.contains("\\") || cleaned.contains("%2f")
				|| cleaned.contains("%2F") || cleaned.contains("%5c") || cleaned.contains("%5C")) {
			throw new IllegalArgumentException("Nombre de video invalido");
		}
		return cleaned;
	}

	private boolean esVideoPermitido(Path path) {
		String ext = extension(path);
		return ext.equals("mp4") || ext.equals("webm") || ext.equals("m4v") || ext.equals("mov");
	}

	private String extension(Path path) {
		return extension(path.getFileName().toString());
	}

	private String extension(String fileName) {
		int idx = fileName.lastIndexOf('.');
		if (idx < 0 || idx >= fileName.length() - 1) {
			return "";
		}
		return fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
	}

	private String sinExtension(String fileName) {
		int idx = fileName.lastIndexOf('.');
		if (idx <= 0) {
			return fileName;
		}
		return fileName.substring(0, idx);
	}

	private MaterialExamenArchivo crearArchivo(Path path, String forcedMimeType) {
		try {
			long size = Files.size(path);
			String mime = forcedMimeType != null && !forcedMimeType.isBlank()
					? forcedMimeType
					: detectarMime(path);
			return new MaterialExamenArchivo(path, path.getFileName().toString(), mime, size);
		} catch (IOException e) {
			throw new RuntimeException("No se pudo acceder al archivo solicitado", e);
		}
	}

	private String detectarMime(Path path) {
		try {
			String detected = Files.probeContentType(path);
			if (detected != null && !detected.isBlank()) {
				return detected;
			}
		} catch (IOException ignored) {
			// Fall through to extension based detection.
		}

		String ext = extension(path);
		return switch (ext) {
			case "mp4", "m4v" -> "video/mp4";
			case "webm" -> "video/webm";
			case "mov" -> "video/quicktime";
			case "pdf" -> "application/pdf";
			default -> "application/octet-stream";
		};
	}

	private Path obtenerRutaBaseDocumentos() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		String baseDir;
		if (os.contains("win")) {
			String userProfile = System.getenv("USERPROFILE");
			if (userProfile == null || userProfile.isBlank()) {
				userProfile = System.getProperty("user.home", "");
			}
			baseDir = directorioDocumentosWindows.replace("%USERPROFILE%", userProfile);
		} else {
			baseDir = directorioDocumentosLinux;
		}
		return Path.of(baseDir).normalize();
	}

	private record MaterialContext(String gradoActual, String bloqueId, Path temario, List<VideoFileEntry> videos) {
	}

	private record VideoFileEntry(String fileName, String title, Integer order, Path path) {
	}

	private record IndexVideoEntry(String title, Integer order) {
	}

	public static class IndexFileDto {
		private List<IndexVideoDto> videos;

		public List<IndexVideoDto> getVideos() {
			return videos;
		}

		public void setVideos(List<IndexVideoDto> videos) {
			this.videos = videos;
		}
	}

	public static class IndexVideoDto {
		private String file;
		private String title;
		private Integer order;

		public String getFile() {
			return file;
		}

		public void setFile(String file) {
			this.file = file;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public Integer getOrder() {
			return order;
		}

		public void setOrder(Integer order) {
			this.order = order;
		}
	}
}
