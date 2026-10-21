package com.taemoi.project.plataforma;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.taemoi.project.marca.MarcaPanelService;
import com.taemoi.project.marca.ReglaContraste;

/**
 * Configuración y consultas de plataforma (todas las escuelas). Protegido dos veces con la misma autoridad: la
 * regla de {@code /api/plataforma/**} de {@code SecurityConfiguration} y {@code @PreAuthorize} aquí.
 */
@RestController
@RequestMapping("/api/plataforma")
@PreAuthorize("hasAuthority('ROLE_ADMIN_PLATAFORMA')")
public class PlataformaController {

	private final ErroresPlataformaService errores;
	private final LogoEscuelaService logos;
	private final MarcaPanelService marcas;

	public PlataformaController(ErroresPlataformaService errores, LogoEscuelaService logos, MarcaPanelService marcas) {
		this.errores = errores;
		this.logos = logos;
		this.marcas = marcas;
	}

	/** Mensaje del 400 de {@code PUT …/marca} sin la clave {@code colorPrimario}. */
	static final String FALTA_COLOR = "Falta colorPrimario: un color #RRGGBB, o null para quitarlo.";

	/** Errores técnicos (4xx/5xx o sin sesión) de todas las escuelas, del más reciente al más antiguo. */
	@GetMapping("/errores")
	public ResponseEntity<Page<ErrorPlataformaDTO>> obtenerErrores(
			@RequestParam(required = false) Long organizacionId,
			@RequestParam(required = false) String estado,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(defaultValue = "1") Integer page,
			@RequestParam(defaultValue = "25") Integer size) {
		return ResponseEntity.ok(errores.buscar(organizacionId, estado, desde, hasta, page, size));
	}

	/**
	 * Todas las escuelas ordenadas por nombre (id, nombre, tieneLogo, marcaHeredada): el filtro de escuela de la
	 * pantalla de errores y la lista de la pantalla del logo (E7-7b).
	 */
	@GetMapping("/escuelas")
	public ResponseEntity<List<EscuelaPlataformaDTO>> obtenerEscuelas() {
		return ResponseEntity.ok(errores.escuelas());
	}

	/**
	 * Pieza E7-7a (decisión 212): sube o sustituye el logo de una escuela (campo multipart {@code archivo}: PNG, JPG o
	 * SVG de hasta 1 MB, validado por contenido). 200 con la escuela; 400 si el fichero no vale; 404 si la escuela no
	 * existe. También la escuela con la marca heredada (Moiskimdo, E7-8): solo cambia el logo de sus documentos.
	 */
	@PutMapping(value = "/escuelas/{organizacionId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<EscuelaPlataformaDTO> subirLogo(@PathVariable Long organizacionId,
			@RequestParam("archivo") MultipartFile archivo) {
		return ResponseEntity.ok(logos.subir(organizacionId, archivo));
	}

	/** Quita el logo de una escuela (sale solo con su nombre; Moiskimdo, con el de siempre). 200 con la escuela; 404. */
	@DeleteMapping("/escuelas/{organizacionId}/logo")
	public ResponseEntity<EscuelaPlataformaDTO> quitarLogo(@PathVariable Long organizacionId) {
		return ResponseEntity.ok(logos.quitar(organizacionId));
	}

	/**
	 * El logo subido (PNG) para previsualizarlo; Moiskimdo sin logo subido, su logo de siempre (E7-8); 404 (JSON con
	 * {@code mensaje}) si la escuela no existe o no tiene logo.
	 * Sin {@code produces}: así el error sale como JSON aunque la petición acepte solo imágenes.
	 */
	@GetMapping("/escuelas/{organizacionId}/logo")
	public ResponseEntity<byte[]> obtenerLogo(@PathVariable Long organizacionId) {
		return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
				.body(logos.logo(organizacionId));
	}

	/**
	 * Pieza 8b-T (decisiones 135, 136, 212 y 257): guarda ({@code #RRGGBB}) o quita ({@code null}) el color de la
	 * marca de una escuela. 200 con la escuela y el tema calculado; 400 si el formato no vale; 404 si la escuela no
	 * existe. Se registra en la escuela DESTINO, sin el valor del color. La clave {@code colorPrimario} es obligatoria
	 * (con {@code null} explícito para quitar): sin ella ({@code {}}, otra clave o sin cuerpo), 400 y nada cambia.
	 */
	@PutMapping("/escuelas/{organizacionId}/marca")
	public ResponseEntity<EscuelaPlataformaConTemaDTO> cambiarMarca(@PathVariable Long organizacionId,
			@RequestBody(required = false) JsonNode cuerpo) {
		return ResponseEntity.ok(marcas.cambiarColor(organizacionId, colorPrimario(cuerpo)));
	}

	/**
	 * El valor de {@code colorPrimario}: texto o {@code null} explícito.
	 *
	 * @throws IllegalArgumentException (400) sin la clave, o con un valor que no es texto ni {@code null}
	 */
	static String colorPrimario(JsonNode cuerpo) {
		if (cuerpo == null || !cuerpo.isObject() || !cuerpo.has("colorPrimario")) {
			throw new IllegalArgumentException(FALTA_COLOR);
		}
		JsonNode valor = cuerpo.get("colorPrimario");
		if (valor.isNull()) {
			return null;
		}
		if (!valor.isTextual()) {
			throw new IllegalArgumentException(ReglaContraste.MENSAJE_FORMATO);
		}
		return valor.asText();
	}
}
