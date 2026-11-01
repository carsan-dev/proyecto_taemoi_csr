package com.taemoi.project.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.archivos.CarpetasMaterial;
import com.taemoi.project.archivos.CuerpoCrudo;
import com.taemoi.project.archivos.RecepcionCruda;
import com.taemoi.project.archivos.RutasArchivos;
import com.taemoi.project.config.FileUtils;
import com.taemoi.project.dtos.response.MaterialGestionDTO;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.exceptions.MaterialExamenException;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.services.MaterialExamenService.BloqueEnDisco;
import com.taemoi.project.services.MaterialExamenService.DocumentoEnDisco;
import com.taemoi.project.services.MaterialExamenService.MaterialExamenArchivo;
import com.taemoi.project.services.MaterialExamenService.VideoEnDisco;
import com.taemoi.project.utils.DocumentoSecurityUtils;

/**
 * Pieza R1 (fase3-e8c-plan.md): el propietario y el gestor administran el material de examen de su escuela. El material
 * sigue en disco, sin tabla: título y orden van en el {@code index.json} del bloque.
 *
 * <p>Moiskimdo tiene su material en la carpeta heredada y sus familias deben seguir viendo exactamente lo mismo. Por eso
 * nunca se crea una carpeta propia vacía, que la ocultaría: la primera escritura mueve la carpeta heredada entera con un
 * movimiento atómico, y solo si eso sale bien se escribe. Las lecturas nunca mueven nada.
 *
 * <p>El bloqueo es por escuela, disciplina y bloque, dentro de la JVM: el backend es una sola instancia
 * ({@code container_name} fijo en docker-compose.production.yml). El streaming va fuera del bloqueo, que solo cubre
 * adoptar al empezar y mover el fichero y escribir el índice al final.
 *
 * <p>El catálogo se lee con {@link TransaccionEnEscuela#leer}: esa sesión suelta su conexión al terminar. Con la sesión de
 * la petición (open-in-view), una subida de minutos retendría una conexión del pool hasta acabar.
 */
@Service
public class MaterialExamenGestionService {

	private static final Logger logger = LoggerFactory.getLogger(MaterialExamenGestionService.class);

	static final String MODULO_AUDITORIA = "materiales";
	public static final long MAX_BYTES_VIDEO = 500L * 1024 * 1024;
	public static final long MAX_BYTES_DOCUMENTO = 50L * 1024 * 1024;
	private static final long MARGEN_LIBRE_BYTES = 2L * 1024 * 1024 * 1024;
	private static final long HORAS_HUERFANO = 24;
	private static final int MAX_TITULO = 120;
	private static final int MAX_NOMBRE = 180;
	private static final String PREFIJO_VIDEO_ANTERIOR = "anterior__";
	private static final String INDICE = "index.json";
	private static final String TEMARIO = "temario.pdf";
	private static final String CARPETA_TEMARIO = "temario";
	private static final String CARPETA_VIDEOS = "videos";
	private static final String CARPETA_DOCUMENTOS = "documentacion";
	private static final Set<String> EXTENSIONES_VIDEO = Set.of("mp4", "webm", "m4v", "mov");
	private static final String CABECERA_VIDEO_FALSA = "El fichero no es un vídeo válido.";
	private static final String CABECERA_PDF_FALSA = "El fichero no es un PDF válido.";

	private static final Map<String, String> NOMBRES_BLOQUE = Map.ofEntries(
			Map.entry("b01_inicio_a_amarillo", "Inicio a amarillo"),
			Map.entry("b02_amarillo_a_naranja", "Amarillo a naranja"),
			Map.entry("b03_naranja_a_verde", "Naranja a verde"),
			Map.entry("b04_verde_a_azul", "Verde a azul"),
			Map.entry("b05_azul_a_rojo", "Azul a rojo"),
			Map.entry("b06_rojo_a_negro_1", "Rojo a negro"),
			Map.entry("b07_negro_1_a_negro_2", "Negro 1.º a 2.º"),
			Map.entry("b08_negro_2_a_negro_3", "Negro 2.º a 3.º"),
			Map.entry("b09_negro_3_a_negro_4", "Negro 3.º a 4.º"),
			Map.entry("b10_negro_4_a_negro_5", "Negro 4.º a 5.º"),
			Map.entry("b11_negro_5_a_maximo", "Negro 5.º en adelante"));

	private final CatalogoDisciplinasService catalogo;
	private final ProgresionGradosService progresion;
	private final MaterialExamenService lectura;
	private final AuditoriaService auditoria;
	private final TransaccionEnEscuela enEscuela;
	private final ObjectMapper json;
	private final String directorioLinux;
	private final String directorioWindows;
	private final Long organizacionPorDefecto;
	private final Map<String, ReentrantLock> cerrojos = new ConcurrentHashMap<>();

	public MaterialExamenGestionService(CatalogoDisciplinasService catalogo, ProgresionGradosService progresion,
			MaterialExamenService lectura, AuditoriaService auditoria, TransaccionEnEscuela enEscuela, ObjectMapper json,
			@Value("${app.documentos.directorio.linux}") String directorioLinux,
			@Value("${app.documentos.directorio.windows}") String directorioWindows,
			@Value("${app.organizacion.por-defecto:1}") Long organizacionPorDefecto) {
		this.catalogo = catalogo;
		this.progresion = progresion;
		this.lectura = lectura;
		this.auditoria = auditoria;
		this.enEscuela = enEscuela;
		this.json = json;
		this.directorioLinux = directorioLinux;
		this.directorioWindows = directorioWindows;
		this.organizacionPorDefecto = organizacionPorDefecto;
	}

	// ------------------------------------------------------------------ lecturas

	public MaterialGestionDTO.Listado listar() {
		Long organizacion = RutasArchivos.organizacionActiva();
		List<DisciplinaDeCatalogo> disciplinas = enEscuela.leer(organizacion, () -> catalogo.listarTodas().stream()
				.map(d -> new DisciplinaDeCatalogo(d.getCodigo(), d.getNombre(), gradosPorBloque(d.getCodigo())))
				.filter(d -> !d.bloques().isEmpty())
				.toList());

		List<MaterialGestionDTO.DisciplinaConBloques> resultado = new ArrayList<>();
		Totales global = new Totales();
		for (DisciplinaDeCatalogo disciplina : disciplinas) {
			Totales totales = new Totales();
			List<MaterialGestionDTO.BloqueResumen> bloques = new ArrayList<>();
			disciplina.bloques().forEach((id, grados) -> {
				BloqueEnDisco disco = lectura.leerBloque(disciplina.codigo(), id);
				long bytes = bytesDe(disco);
				bloques.add(new MaterialGestionDTO.BloqueResumen(id, nombreDeBloque(id), grados, disco.temario() != null,
						disco.videos().size(), bytes));
				totales.sumar(disco.temario() != null ? 1 : 0, disco.videos().size(), bytes);
			});
			global.sumar(totales.temarios, totales.videos, totales.bytes);
			resultado.add(new MaterialGestionDTO.DisciplinaConBloques(disciplina.codigo(), disciplina.nombre(), bloques,
					totales.dto()));
		}
		return new MaterialGestionDTO.Listado(resultado, global.dto());
	}

	public MaterialGestionDTO.Detalle detalle(String disciplina, String bloqueId) {
		return detalle(bloque(disciplina, bloqueId));
	}

	public MaterialExamenArchivo temario(String disciplina, String bloqueId) {
		BloqueDeCatalogo bloque = bloque(disciplina, bloqueId);
		Path temario = lectura.leerBloque(bloque.disciplina(), bloque.id()).temario();
		if (temario == null) {
			throw new NoSuchElementException("Este bloque no tiene temario.");
		}
		return lectura.describirArchivo(temario, "application/pdf");
	}

	public MaterialExamenArchivo video(String disciplina, String bloqueId, String fichero) {
		BloqueDeCatalogo bloque = bloque(disciplina, bloqueId);
		comprobarNombreGuardado(fichero);
		return lectura.describirArchivo(exigirVideo(leer(bloque), fichero).ruta(), null);
	}

	public MaterialExamenArchivo documento(String disciplina, String bloqueId, String fichero) {
		BloqueDeCatalogo bloque = bloque(disciplina, bloqueId);
		comprobarNombreGuardado(fichero);
		return lectura.describirArchivo(exigirDocumento(leer(bloque), fichero).ruta(), null);
	}

	/** Nombre del boceto; para otro id, el id sin prefijo y legible. */
	public static String nombreDeBloque(String id) {
		String nombre = NOMBRES_BLOQUE.get(id);
		if (nombre != null) {
			return nombre;
		}
		String legible = id.replaceFirst("^b\\d+_", "").replace('_', ' ').trim();
		if (legible.isEmpty()) {
			return id;
		}
		return legible.substring(0, 1).toUpperCase(Locale.ROOT) + legible.substring(1);
	}

	// ------------------------------------------------------------------ vídeos

	public MaterialGestionDTO.Subida subirVideo(String disciplina, String bloqueId, String nombre, CuerpoCrudo cuerpo) {
		BloqueDeCatalogo bloque = bloque(disciplina, bloqueId);
		String limpio = nombreDeSubida(nombre);
		if (!EXTENSIONES_VIDEO.contains(extension(limpio))) {
			throw new MaterialExamenException(HttpStatus.BAD_REQUEST, "Solo se admiten vídeos mp4, webm, m4v o mov.");
		}
		comprobarCuerpo(cuerpo, MAX_BYTES_VIDEO);

		Path carpeta = bajoBloqueo(bloque, () -> {
			entradasDe(leerParaEscribir(bloque));
			return subcarpeta(adoptar(bloque), CARPETA_VIDEOS);
		});
		Path temporal = RecepcionCruda.temporalEn(carpeta);
		try {
			String extension = extension(limpio);
			RecepcionCruda.guardar(cuerpo, temporal, MAX_BYTES_VIDEO, c -> RecepcionCruda.cabeceraDeVideo(extension, c),
					CABECERA_VIDEO_FALSA);
			String fichero = bajoBloqueo(bloque, () -> {
				String destino = nombreLibre(carpeta, limpio);
				List<EntradaIndice> entradas = entradasDe(leerParaEscribir(bloque));
				int orden = entradas.stream().mapToInt(EntradaIndice::order).max().orElse(0) + 1;
				entradas.add(new EntradaIndice(destino, tituloDeSubida(nombre, destino), orden));
				Path definitivo = carpeta.resolve(destino);
				Files.move(temporal, definitivo, StandardCopyOption.ATOMIC_MOVE);
				try {
					escribirIndice(carpeta.getParent(), entradas);
				} catch (IOException e) {
					// Un vídeo sin entrada cambiaría de sitio al regenerar el índice: la subida falla entera.
					Files.deleteIfExists(definitivo);
					throw e;
				}
				return destino;
			});
			auditar(AuditoriaAccion.CREATE, ruta(bloque, "/videos"),
					"Vídeo " + fichero + " subido a " + referencia(bloque) + " (" + cuerpo.longitud() + " bytes)");
			return new MaterialGestionDTO.Subida(fichero, detalle(bloque));
		} finally {
			borrarSinFallar(temporal);
		}
	}

	/** Sustituye el contenido; el nombre, el título y el orden no cambian. */
	public MaterialGestionDTO.Detalle sustituirVideo(String disciplina, String bloqueId, String fichero,
			CuerpoCrudo cuerpo) {
		BloqueDeCatalogo bloque = bloque(disciplina, bloqueId);
		comprobarNombreGuardado(fichero);
		comprobarCuerpo(cuerpo, MAX_BYTES_VIDEO);
		BloqueEnDisco inicial = leerParaEscribir(bloque);
		String extension = extension(exigirVideo(inicial, fichero).fichero());
		comprobarIndiceAsegurable(inicial);

		Path carpeta = bajoBloqueo(bloque, () -> subcarpeta(adoptar(bloque), CARPETA_VIDEOS));
		Path temporal = RecepcionCruda.temporalEn(carpeta);
		try {
			RecepcionCruda.guardar(cuerpo, temporal, MAX_BYTES_VIDEO, c -> RecepcionCruda.cabeceraDeVideo(extension, c),
					CABECERA_VIDEO_FALSA);
			String guardado = bajoBloqueo(bloque, () -> {
				BloqueEnDisco disco = leerParaEscribir(bloque);
				VideoEnDisco actual = exigirVideo(disco, fichero);
				asegurarIndice(disco);
				Files.move(temporal, actual.ruta(), StandardCopyOption.ATOMIC_MOVE);
				return actual.fichero();
			});
			auditar(AuditoriaAccion.UPDATE, ruta(bloque, "/videos/" + guardado + "/contenido"),
					"Vídeo " + guardado + " sustituido en " + referencia(bloque) + " (" + cuerpo.longitud() + " bytes)");
			return detalle(bloque);
		} finally {
			borrarSinFallar(temporal);
		}
	}

	public MaterialGestionDTO.Detalle cambiarTitulo(String disciplina, String bloqueId, String fichero, String titulo) {
		BloqueDeCatalogo bloque = bloque(disciplina, bloqueId);
		comprobarNombreGuardado(fichero);
		String nuevo = tituloValido(titulo);
		String guardado = bajoBloqueo(bloque, () -> {
			BloqueEnDisco inicial = leerParaEscribir(bloque);
			exigirVideo(inicial, fichero);
			entradasDe(inicial);
			Path carpeta = adoptar(bloque);
			BloqueEnDisco disco = leerParaEscribir(bloque);
			String actual = exigirVideo(disco, fichero).fichero();
			escribirIndice(carpeta, entradasDe(disco).stream()
					.map(e -> e.file().equals(actual) ? new EntradaIndice(e.file(), nuevo, e.order()) : e)
					.toList());
			return actual;
		});
		auditar(AuditoriaAccion.UPDATE, ruta(bloque, "/videos/" + guardado),
				"Título del vídeo " + guardado + " cambiado en " + referencia(bloque));
		return detalle(bloque);
	}

	/** {@code ficheros} debe ser una permutación exacta de los vídeos del bloque; si no, 409 y no cambia nada. */
	public MaterialGestionDTO.Detalle reordenarVideos(String disciplina, String bloqueId, List<String> ficheros) {
		BloqueDeCatalogo bloque = bloque(disciplina, bloqueId);
		bajoBloqueo(bloque, () -> {
			BloqueEnDisco inicial = leerParaEscribir(bloque);
			comprobarPermutacion(inicial, ficheros);
			entradasDe(inicial);
			Path carpeta = adoptar(bloque);
			BloqueEnDisco disco = leerParaEscribir(bloque);
			comprobarPermutacion(disco, ficheros);
			Map<String, EntradaIndice> porFichero = new TreeMap<>();
			entradasDe(disco).forEach(e -> porFichero.put(e.file(), e));
			List<EntradaIndice> nuevas = new ArrayList<>();
			for (String fichero : ficheros) {
				EntradaIndice entrada = porFichero.get(fichero);
				nuevas.add(new EntradaIndice(fichero, entrada.title(), nuevas.size() + 1));
			}
			escribirIndice(carpeta, nuevas);
			return null;
		});
		auditar(AuditoriaAccion.UPDATE, ruta(bloque, "/videos/orden"), "Orden de vídeos cambiado en " + referencia(bloque));
		return detalle(bloque);
	}

	public MaterialGestionDTO.Detalle eliminarVideo(String disciplina, String bloqueId, String fichero) {
		BloqueDeCatalogo bloque = bloque(disciplina, bloqueId);
		comprobarNombreGuardado(fichero);
		String guardado = bajoBloqueo(bloque, () -> {
			BloqueEnDisco inicial = leerParaEscribir(bloque);
			exigirVideo(inicial, fichero);
			entradasDe(inicial);
			Path carpeta = adoptar(bloque);
			BloqueEnDisco disco = leerParaEscribir(bloque);
			VideoEnDisco video = exigirVideo(disco, fichero);
			List<EntradaIndice> todas = entradasDe(disco);
			// Primero el índice: si no se puede borrar el fichero, se restaura y el bloque queda como estaba.
			escribirIndice(carpeta, todas.stream().filter(e -> !e.file().equals(video.fichero())).toList());
			try {
				Files.delete(video.ruta());
			} catch (IOException e) {
				escribirIndice(carpeta, todas);
				throw e;
			}
			return video.fichero();
		});
		auditar(AuditoriaAccion.DELETE, ruta(bloque, "/videos/" + guardado),
				"Vídeo " + guardado + " eliminado de " + referencia(bloque));
		return detalle(bloque);
	}

	// ------------------------------------------------------------------ temario

	/** Deja {@code temario.pdf} y retira el PDF anterior si tenía otro nombre. */
	public MaterialGestionDTO.Detalle subirTemario(String disciplina, String bloqueId, CuerpoCrudo cuerpo) {
		BloqueDeCatalogo bloque = bloque(disciplina, bloqueId);
		DocumentoSecurityUtils.detectarMimePermitido(TEMARIO, cuerpo.tipo());
		comprobarCuerpo(cuerpo, MAX_BYTES_DOCUMENTO);

		Path carpeta = bajoBloqueo(bloque, () -> {
			comprobarIndiceAsegurable(leerParaEscribir(bloque));
			return subcarpeta(adoptar(bloque), CARPETA_TEMARIO);
		});
		Path temporal = RecepcionCruda.temporalEn(carpeta);
		try {
			RecepcionCruda.guardar(cuerpo, temporal, MAX_BYTES_DOCUMENTO, RecepcionCruda::cabeceraDePdf, CABECERA_PDF_FALSA);
			bajoBloqueo(bloque, () -> {
				BloqueEnDisco disco = leerParaEscribir(bloque);
				Path anterior = disco.temario();
				asegurarIndice(disco);
				Files.move(temporal, carpeta.resolve(TEMARIO), StandardCopyOption.ATOMIC_MOVE);
				if (anterior != null && !anterior.getFileName().toString().equalsIgnoreCase(TEMARIO)) {
					// Con temario.pdf en su sitio, el anterior ya no se sirve: un fallo aquí no invalida la subida.
					borrarSinFallar(anterior);
				}
				return null;
			});
			auditar(AuditoriaAccion.UPDATE, ruta(bloque, "/temario"),
					"Temario sustituido en " + referencia(bloque) + " (" + cuerpo.longitud() + " bytes)");
			return detalle(bloque);
		} finally {
			borrarSinFallar(temporal);
		}
	}

	// ------------------------------------------------------------------ documentación

	public MaterialGestionDTO.Subida subirDocumento(String disciplina, String bloqueId, String nombre, CuerpoCrudo cuerpo) {
		BloqueDeCatalogo bloque = bloque(disciplina, bloqueId);
		String limpio = nombreDeSubida(nombre);
		DocumentoSecurityUtils.detectarMimePermitido(limpio, cuerpo.tipo());
		comprobarCuerpo(cuerpo, MAX_BYTES_DOCUMENTO);

		Path carpeta = bajoBloqueo(bloque, () -> {
			comprobarIndiceAsegurable(leerParaEscribir(bloque));
			return subcarpeta(adoptar(bloque), CARPETA_DOCUMENTOS);
		});
		Path temporal = RecepcionCruda.temporalEn(carpeta);
		try {
			RecepcionCruda.guardar(cuerpo, temporal, MAX_BYTES_DOCUMENTO, cabeceraDeDocumento(limpio), CABECERA_PDF_FALSA);
			String fichero = bajoBloqueo(bloque, () -> {
				asegurarIndice(leerParaEscribir(bloque));
				String destino = nombreLibre(carpeta, limpio);
				Files.move(temporal, carpeta.resolve(destino), StandardCopyOption.ATOMIC_MOVE);
				return destino;
			});
			auditar(AuditoriaAccion.CREATE, ruta(bloque, "/documentacion"),
					"Documento " + fichero + " subido a " + referencia(bloque) + " (" + cuerpo.longitud() + " bytes)");
			return new MaterialGestionDTO.Subida(fichero, detalle(bloque));
		} finally {
			borrarSinFallar(temporal);
		}
	}

	public MaterialGestionDTO.Detalle sustituirDocumento(String disciplina, String bloqueId, String fichero,
			CuerpoCrudo cuerpo) {
		BloqueDeCatalogo bloque = bloque(disciplina, bloqueId);
		comprobarNombreGuardado(fichero);
		BloqueEnDisco inicial = leerParaEscribir(bloque);
		String existente = exigirDocumento(inicial, fichero).fichero();
		DocumentoSecurityUtils.detectarMimePermitido(existente, cuerpo.tipo());
		comprobarCuerpo(cuerpo, MAX_BYTES_DOCUMENTO);
		comprobarIndiceAsegurable(inicial);

		Path carpeta = bajoBloqueo(bloque, () -> subcarpeta(adoptar(bloque), CARPETA_DOCUMENTOS));
		Path temporal = RecepcionCruda.temporalEn(carpeta);
		try {
			RecepcionCruda.guardar(cuerpo, temporal, MAX_BYTES_DOCUMENTO, cabeceraDeDocumento(existente),
					CABECERA_PDF_FALSA);
			String guardado = bajoBloqueo(bloque, () -> {
				BloqueEnDisco disco = leerParaEscribir(bloque);
				DocumentoEnDisco actual = exigirDocumento(disco, fichero);
				asegurarIndice(disco);
				Files.move(temporal, actual.ruta(), StandardCopyOption.ATOMIC_MOVE);
				return actual.fichero();
			});
			auditar(AuditoriaAccion.UPDATE, ruta(bloque, "/documentacion/" + guardado + "/contenido"),
					"Documento " + guardado + " sustituido en " + referencia(bloque) + " (" + cuerpo.longitud() + " bytes)");
			return detalle(bloque);
		} finally {
			borrarSinFallar(temporal);
		}
	}

	public MaterialGestionDTO.Detalle eliminarDocumento(String disciplina, String bloqueId, String fichero) {
		BloqueDeCatalogo bloque = bloque(disciplina, bloqueId);
		comprobarNombreGuardado(fichero);
		String guardado = bajoBloqueo(bloque, () -> {
			BloqueEnDisco inicial = leerParaEscribir(bloque);
			exigirDocumento(inicial, fichero);
			comprobarIndiceAsegurable(inicial);
			adoptar(bloque);
			BloqueEnDisco disco = leerParaEscribir(bloque);
			DocumentoEnDisco documento = exigirDocumento(disco, fichero);
			asegurarIndice(disco);
			Files.delete(documento.ruta());
			return documento.fichero();
		});
		auditar(AuditoriaAccion.DELETE, ruta(bloque, "/documentacion/" + guardado),
				"Documento " + guardado + " eliminado de " + referencia(bloque));
		return detalle(bloque);
	}

	// ------------------------------------------------------------------ adopción y bloqueo

	/**
	 * Devuelve la carpeta propia del bloque, lista para escribir. Debe llamarse bajo el bloqueo. Si es la escuela por
	 * defecto, no hay propia y sí heredada, crea solo los padres y mueve la heredada entera; un fallo deja el material donde
	 * estaba. Con las dos carpetas no mezcla: la escritura falla.
	 */
	private Path adoptar(BloqueDeCatalogo bloque) {
		Path base = base();
		Path propia = CarpetasMaterial.propia(base, bloque.organizacion(), bloque.disciplina(), bloque.id());
		try {
			if (bloque.organizacion().equals(organizacionPorDefecto)) {
				Path legada = CarpetasMaterial.legada(base, bloque.disciplina(), bloque.id());
				boolean hayPropia = Files.isDirectory(propia);
				boolean hayLegada = Files.isDirectory(legada);
				if (hayPropia && hayLegada) {
					logger.error("Material de examen con dos carpetas: organizacion={} disciplina={} bloque={}",
							bloque.organizacion(), bloque.disciplina(), bloque.id());
					throw new MaterialExamenException(HttpStatus.CONFLICT,
							"Este bloque tiene dos carpetas de material y no se puede cambiar. Avisa al administrador.");
				}
				if (hayLegada) {
					Files.createDirectories(propia.getParent());
					moverCarpeta(legada, propia);
					logger.info("Material de examen adoptado: organizacion={} disciplina={} bloque={}",
							bloque.organizacion(), bloque.disciplina(), bloque.id());
				}
			}
			Files.createDirectories(propia);
			limpiarHuerfanos(propia);
			return propia;
		} catch (IOException e) {
			logger.error("No se pudo preparar la carpeta del material: organizacion={} disciplina={} bloque={} causa={}",
					bloque.organizacion(), bloque.disciplina(), bloque.id(), e.getClass().getSimpleName());
			throw new MaterialExamenException(HttpStatus.INTERNAL_SERVER_ERROR,
					"No se ha podido preparar la carpeta del bloque. El material sigue intacto.");
		}
	}

	/** Movimiento atómico o nada: {@code ATOMIC_MOVE} no copia nunca entre sistemas de ficheros. */
	protected void moverCarpeta(Path origen, Path destino) throws IOException {
		Files.move(origen, destino, StandardCopyOption.ATOMIC_MOVE);
	}

	private <T> T bajoBloqueo(BloqueDeCatalogo bloque, AccionConFicheros<T> accion) {
		ReentrantLock cerrojo = cerrojos.computeIfAbsent(
				bloque.organizacion() + "|" + bloque.disciplina() + "|" + bloque.id(), clave -> new ReentrantLock());
		cerrojo.lock();
		try {
			return accion.ejecutar();
		} catch (IOException e) {
			logger.error("Error al escribir material de examen: organizacion={} disciplina={} bloque={} causa={}",
					bloque.organizacion(), bloque.disciplina(), bloque.id(), e.getClass().getSimpleName());
			throw new MaterialExamenException(HttpStatus.INTERNAL_SERVER_ERROR,
					"No se ha podido guardar el cambio en el servidor.");
		} finally {
			cerrojo.unlock();
		}
	}

	/** Bytes libres del disco del material. {@code File} no depende de la tabla de montajes, que varía en contenedores. */
	protected long espacioLibre() {
		return base().toFile().getUsableSpace();
	}

	private static void limpiarHuerfanos(Path carpeta) {
		Instant limite = Instant.now().minus(HORAS_HUERFANO, ChronoUnit.HOURS);
		for (Path donde : List.of(carpeta, carpeta.resolve(CARPETA_TEMARIO), carpeta.resolve(CARPETA_VIDEOS),
				carpeta.resolve(CARPETA_DOCUMENTOS))) {
			if (!Files.isDirectory(donde)) {
				continue;
			}
			try (Stream<Path> ficheros = Files.list(donde)) {
				ficheros.filter(f -> f.getFileName().toString().startsWith(RecepcionCruda.PREFIJO_TEMPORAL))
						.filter(f -> anteriorA(f, limite))
						.forEach(MaterialExamenGestionService::borrarSinFallar);
			} catch (IOException e) {
				logger.warn("No se pudieron revisar los temporales del material: causa={}", e.getClass().getSimpleName());
			}
		}
	}

	private static boolean anteriorA(Path fichero, Instant limite) {
		try {
			return Files.getLastModifiedTime(fichero).toInstant().isBefore(limite);
		} catch (IOException e) {
			return false;
		}
	}

	// ------------------------------------------------------------------ índice

	/**
	 * Lo que las familias ya ven, con su título y su orden: la primera escritura lo fija en el índice. Toda reescritura del
	 * índice sale de aquí. El lector funde en una entrada los nombres que solo difieren en mayúsculas, así que un bloque con
	 * dos de ellos no se puede reescribir sin dar el título o el orden de uno al otro: 409 y no se escribe nada. Las escrituras
	 * lo llaman también antes de adoptar, para que el material ni se mueva.
	 */
	private static List<EntradaIndice> entradasDe(BloqueEnDisco disco) {
		Set<String> nombres = new HashSet<>();
		List<EntradaIndice> entradas = new ArrayList<>();
		for (VideoEnDisco video : disco.videos()) {
			if (!nombres.add(video.fichero().toLowerCase(Locale.ROOT))) {
				logger.error("Material de examen con nombres que solo difieren en mayúsculas: carpeta={}", disco.carpeta());
				throw new MaterialExamenException(HttpStatus.CONFLICT,
						"Este bloque tiene vídeos cuyo nombre solo se distingue por las mayúsculas y no se puede cambiar. "
								+ "Avisa al administrador.");
			}
			entradas.add(new EntradaIndice(video.fichero(), video.titulo(), video.orden()));
		}
		return entradas;
	}

	private void asegurarIndice(BloqueEnDisco disco) throws IOException {
		if (!hayIndice(disco)) {
			escribirIndice(disco.carpeta(), entradasDe(disco));
		}
	}

	/** Lo que {@link #asegurarIndice} exigirá, sin escribir nada. */
	private static void comprobarIndiceAsegurable(BloqueEnDisco disco) {
		if (!hayIndice(disco)) {
			entradasDe(disco);
		}
	}

	private static boolean hayIndice(BloqueEnDisco disco) {
		return Files.isRegularFile(disco.carpeta().resolve(INDICE));
	}

	private void escribirIndice(Path carpeta, List<EntradaIndice> entradas) throws IOException {
		Path temporal = RecepcionCruda.temporalEn(carpeta);
		try {
			Files.write(temporal, json.writerWithDefaultPrettyPrinter().writeValueAsBytes(new Indice(entradas)),
					StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			Files.move(temporal, carpeta.resolve(INDICE), StandardCopyOption.ATOMIC_MOVE);
		} finally {
			borrarSinFallar(temporal);
		}
	}

	// ------------------------------------------------------------------ comprobaciones

	private BloqueDeCatalogo bloque(String disciplina, String bloqueId) {
		Long organizacion = RutasArchivos.organizacionActiva();
		String codigo = disciplina == null ? "" : disciplina.toUpperCase(Locale.ROOT);
		return enEscuela.leer(organizacion, () -> {
			Disciplina encontrada = catalogo.buscarPorCodigo(codigo)
					.orElseThrow(() -> new NoSuchElementException("Esa disciplina no existe en esta escuela."));
			List<MaterialGestionDTO.GradoDelBloque> grados = gradosPorBloque(encontrada.getCodigo()).get(bloqueId);
			if (grados == null) {
				throw new NoSuchElementException("Ese bloque no existe en esta disciplina.");
			}
			return new BloqueDeCatalogo(organizacion, encontrada.getCodigo(), bloqueId, grados);
		});
	}

	/** Bloques de la disciplina por id; los grados sin orden no son seleccionables y no se listan. */
	private Map<String, List<MaterialGestionDTO.GradoDelBloque>> gradosPorBloque(String disciplina) {
		Map<String, List<MaterialGestionDTO.GradoDelBloque>> bloques = new TreeMap<>();
		progresion.listarGrados(disciplina).stream()
				.filter(g -> g.getBloqueTemario() != null && !g.getBloqueTemario().isBlank())
				.sorted(Comparator.comparing(Grado::getOrden, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(Grado::getId, Comparator.nullsLast(Comparator.naturalOrder())))
				.forEach(g -> {
					List<MaterialGestionDTO.GradoDelBloque> grados = bloques.computeIfAbsent(g.getBloqueTemario(),
							id -> new ArrayList<>());
					if (g.getOrden() != null) {
						grados.add(new MaterialGestionDTO.GradoDelBloque(g.getCodigo(), g.getNombre()));
					}
				});
		return bloques;
	}

	private void comprobarCuerpo(CuerpoCrudo cuerpo, long maximo) {
		if (cuerpo.longitud() < 0) {
			throw new MaterialExamenException(HttpStatus.LENGTH_REQUIRED,
					"No se ha podido conocer el tamaño del fichero. Vuelve a intentarlo.");
		}
		if (cuerpo.longitud() > maximo) {
			throw new MaterialExamenException(HttpStatus.PAYLOAD_TOO_LARGE,
					"El fichero supera el máximo de " + (maximo / (1024 * 1024)) + " MB.");
		}
		if (cuerpo.longitud() == 0) {
			throw new MaterialExamenException(HttpStatus.BAD_REQUEST, "El fichero está vacío.");
		}
		String tipo = cuerpo.tipo() == null ? "" : cuerpo.tipo().toLowerCase(Locale.ROOT);
		if (tipo.startsWith("multipart/") || tipo.startsWith("application/x-www-form-urlencoded")) {
			throw new MaterialExamenException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
					"El fichero debe enviarse tal cual, sin formulario.");
		}
		if (espacioLibre() < cuerpo.longitud() + MARGEN_LIBRE_BYTES) {
			throw new MaterialExamenException(HttpStatus.INSUFFICIENT_STORAGE,
					"No hay espacio suficiente en el servidor para este fichero.");
		}
	}

	/** Nombre recibido en una subida: rechaza lo que no sea un nombre simple y devuelve la versión limpia. */
	private static String nombreDeSubida(String nombre) {
		comprobarNombreSimple(nombre);
		String limpio = FileUtils.limpiarNombreArchivo(nombre.trim());
		// «..» y el prefijo sintético de los vídeos anteriores: la ruta de las familias rechazaría o confundiría ese fichero.
		if (limpio.isBlank() || limpio.startsWith(".") || limpio.contains("..") || limpio.startsWith(PREFIJO_VIDEO_ANTERIOR)
				|| limpio.length() > MAX_NOMBRE || extension(limpio).isEmpty()) {
			throw nombreNoValido();
		}
		return limpio;
	}

	/** Nombre de un fichero ya guardado: se busca en la lista del bloque, nunca se concatena a una ruta. */
	private static void comprobarNombreGuardado(String fichero) {
		comprobarNombreSimple(fichero);
	}

	private static void comprobarNombreSimple(String nombre) {
		if (nombre == null || nombre.isBlank()) {
			throw nombreNoValido();
		}
		String recortado = nombre.trim();
		if (recortado.startsWith(".") || recortado.contains("..") || recortado.contains("/")
				|| recortado.contains("\\")) {
			throw nombreNoValido();
		}
		for (int i = 0; i < recortado.length(); i++) {
			if (Character.isISOControl(recortado.charAt(i))) {
				throw nombreNoValido();
			}
		}
	}

	private static MaterialExamenException nombreNoValido() {
		return new MaterialExamenException(HttpStatus.BAD_REQUEST, "El nombre del fichero no es válido.");
	}

	private static String tituloValido(String titulo) {
		String limpio = titulo == null ? "" : titulo.trim();
		if (limpio.isEmpty() || limpio.length() > MAX_TITULO) {
			throw new MaterialExamenException(HttpStatus.BAD_REQUEST,
					"El título es obligatorio y admite hasta " + MAX_TITULO + " caracteres.");
		}
		return limpio;
	}

	/** Desde el nombre original, que conserva los acentos; sin nada legible, el nombre guardado. */
	private String tituloDeSubida(String nombre, String guardado) {
		String titulo = lectura.tituloDesdeArchivo(nombre.trim());
		if (titulo.isBlank()) {
			titulo = sinExtension(guardado);
		}
		return titulo.length() > MAX_TITULO ? titulo.substring(0, MAX_TITULO).trim() : titulo;
	}

	private static void comprobarPermutacion(BloqueEnDisco disco, List<String> ficheros) {
		List<String> actuales = disco.videos().stream().map(VideoEnDisco::fichero).toList();
		if (ficheros == null || ficheros.size() != actuales.size() || !new HashSet<>(ficheros).equals(new HashSet<>(actuales))) {
			throw new MaterialExamenException(HttpStatus.CONFLICT,
					"La lista de vídeos ha cambiado. Recarga la página y vuelve a ordenarlos.");
		}
	}

	private static VideoEnDisco exigirVideo(BloqueEnDisco disco, String fichero) {
		return porNombre(disco.videos(), VideoEnDisco::fichero, fichero)
				.orElseThrow(() -> new NoSuchElementException("Ese vídeo no existe en este bloque."));
	}

	private static DocumentoEnDisco exigirDocumento(BloqueEnDisco disco, String fichero) {
		return porNombre(disco.documentos(), DocumentoEnDisco::fichero, fichero)
				.orElseThrow(() -> new NoSuchElementException("Ese documento no existe en este bloque."));
	}

	/**
	 * Con dos ficheros que solo difieren en mayúsculas, borrar o sustituir el que no era sería irreparable: primero el
	 * nombre exacto; sin él, el único que coincida sin mayúsculas; si son varios, 409.
	 */
	private static <T> Optional<T> porNombre(List<T> ficheros, Function<T, String> nombre, String fichero) {
		String buscado = fichero.trim();
		Optional<T> exacto = ficheros.stream().filter(f -> nombre.apply(f).equals(buscado)).findFirst();
		if (exacto.isPresent()) {
			return exacto;
		}
		List<T> parecidos = ficheros.stream().filter(f -> nombre.apply(f).equalsIgnoreCase(buscado)).toList();
		if (parecidos.size() > 1) {
			throw new MaterialExamenException(HttpStatus.CONFLICT,
					"Hay varios ficheros con ese nombre que solo se distinguen por las mayúsculas. Usa el nombre exacto.");
		}
		return parecidos.stream().findFirst();
	}

	private static Predicate<byte[]> cabeceraDeDocumento(String fichero) {
		return "pdf".equals(extension(fichero)) ? RecepcionCruda::cabeceraDePdf : cabecera -> true;
	}

	/** Sin distinguir mayúsculas: el índice y las rutas de lectura tampoco las distinguen. */
	private static String nombreLibre(Path carpeta, String deseado) throws IOException {
		Set<String> ocupados = new HashSet<>();
		try (Stream<Path> ficheros = Files.list(carpeta)) {
			ficheros.forEach(f -> ocupados.add(f.getFileName().toString().toLowerCase(Locale.ROOT)));
		}
		if (!ocupados.contains(deseado.toLowerCase(Locale.ROOT))) {
			return deseado;
		}
		String base = sinExtension(deseado);
		String extension = extension(deseado);
		int sufijo = 2;
		String candidato;
		do {
			candidato = base + "_" + sufijo++ + "." + extension;
		} while (ocupados.contains(candidato.toLowerCase(Locale.ROOT)));
		return candidato;
	}

	// ------------------------------------------------------------------ apoyo

	private MaterialGestionDTO.Detalle detalle(BloqueDeCatalogo bloque) {
		BloqueEnDisco disco = leer(bloque);
		boolean taekwondo = CatalogoLegadoTaemoi.TAEKWONDO.equals(bloque.disciplina());
		MaterialGestionDTO.TemarioDetalle temario = null;
		if (disco.temario() != null) {
			Atributos atributos = atributos(disco.temario());
			temario = new MaterialGestionDTO.TemarioDetalle(disco.temario().getFileName().toString(), atributos.bytes(),
					atributos.fecha());
		}
		List<MaterialGestionDTO.VideoDetalle> videos = disco.videos().stream().map(v -> {
			Atributos atributos = atributos(v.ruta());
			return new MaterialGestionDTO.VideoDetalle(v.fichero(), v.titulo(), v.orden(), atributos.bytes(),
					atributos.fecha(), taekwondo && lectura.esVideoTaegukOPumse(v.fichero(), v.titulo()));
		}).toList();
		List<MaterialGestionDTO.DocumentoDetalle> documentos = disco.documentos().stream().map(d -> {
			Atributos atributos = atributos(d.ruta());
			return new MaterialGestionDTO.DocumentoDetalle(d.fichero(), d.titulo(), d.mime(), atributos.bytes(),
					atributos.fecha());
		}).toList();
		return new MaterialGestionDTO.Detalle(bloque.disciplina(), bloque.id(), nombreDeBloque(bloque.id()), bloque.grados(),
				temario, videos, documentos);
	}

	private BloqueEnDisco leer(BloqueDeCatalogo bloque) {
		return lectura.leerBloque(bloque.disciplina(), bloque.id());
	}

	private BloqueEnDisco leerParaEscribir(BloqueDeCatalogo bloque) {
		return lectura.leerBloqueParaEscribir(bloque.disciplina(), bloque.id());
	}

	private Path base() {
		return RutasArchivos.directorioBase(directorioLinux, directorioWindows);
	}

	private static Path subcarpeta(Path carpeta, String nombre) throws IOException {
		return Files.createDirectories(carpeta.resolve(nombre));
	}

	private static long bytesDe(BloqueEnDisco disco) {
		long bytes = disco.temario() != null ? atributos(disco.temario()).bytes() : 0;
		for (VideoEnDisco video : disco.videos()) {
			bytes += atributos(video.ruta()).bytes();
		}
		for (DocumentoEnDisco documento : disco.documentos()) {
			bytes += atributos(documento.ruta()).bytes();
		}
		return bytes;
	}

	/** Un fichero que desaparece mientras se lista cuenta como vacío: la lista no falla por eso. */
	private static Atributos atributos(Path fichero) {
		try {
			BasicFileAttributes leidos = Files.readAttributes(fichero, BasicFileAttributes.class);
			FileTime fecha = leidos.lastModifiedTime();
			return new Atributos(leidos.size(), fecha.toInstant().truncatedTo(ChronoUnit.SECONDS).toString());
		} catch (IOException e) {
			return new Atributos(0, null);
		}
	}

	private void auditar(AuditoriaAccion accion, String ruta, String resumen) {
		try {
			enEscuela.escribir(RutasArchivos.organizacionActiva(), () -> {
				auditoria.registrarAccesoSensible(accion, MODULO_AUDITORIA, ruta, null, resumen);
				return null;
			});
		} catch (RuntimeException e) {
			// El fichero ya está en su sitio: decir que falló sería falso.
			logger.error("No se pudo registrar una escritura de material de examen: causa={}", e.getClass().getSimpleName());
		}
	}

	private static String ruta(BloqueDeCatalogo bloque, String sufijo) {
		return "/api/material-examen/" + bloque.disciplina() + "/" + bloque.id() + sufijo;
	}

	private static String referencia(BloqueDeCatalogo bloque) {
		return bloque.disciplina() + "/" + bloque.id();
	}

	private static void borrarSinFallar(Path fichero) {
		try {
			Files.deleteIfExists(fichero);
		} catch (IOException e) {
			logger.warn("No se pudo borrar un fichero de material: causa={}", e.getClass().getSimpleName());
		}
	}

	private static String extension(String fichero) {
		int punto = fichero.lastIndexOf('.');
		return punto < 0 || punto == fichero.length() - 1 ? "" : fichero.substring(punto + 1).toLowerCase(Locale.ROOT);
	}

	private static String sinExtension(String fichero) {
		int punto = fichero.lastIndexOf('.');
		return punto <= 0 ? fichero : fichero.substring(0, punto);
	}

	@FunctionalInterface
	private interface AccionConFicheros<T> {
		T ejecutar() throws IOException;
	}

	private record BloqueDeCatalogo(Long organizacion, String disciplina, String id,
			List<MaterialGestionDTO.GradoDelBloque> grados) {
	}

	private record DisciplinaDeCatalogo(String codigo, String nombre,
			Map<String, List<MaterialGestionDTO.GradoDelBloque>> bloques) {
	}

	private record Atributos(long bytes, String fecha) {
	}

	/** Formato de {@code index.json}: {@code {"videos":[{"file","title","order"}]}}. */
	private record Indice(List<EntradaIndice> videos) {
	}

	private record EntradaIndice(String file, String title, int order) {
	}

	private static final class Totales {
		private int temarios;
		private int videos;
		private long bytes;

		void sumar(int temariosMas, int videosMas, long bytesMas) {
			temarios += temariosMas;
			videos += videosMas;
			bytes += bytesMas;
		}

		MaterialGestionDTO.Totales dto() {
			return new MaterialGestionDTO.Totales(temarios, videos, bytes);
		}
	}
}
