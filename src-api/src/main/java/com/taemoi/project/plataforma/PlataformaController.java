package com.taemoi.project.plataforma;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.taemoi.project.marca.MarcaPanelService;
import com.taemoi.project.marca.ReglaContraste;
import com.taemoi.project.web.EstadoWebDTO;
import com.taemoi.project.web.PublicacionWebService;
import com.taemoi.project.web.TemasWeb;

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
	private final AltaEscuelaService altas;
	private final PublicacionWebService webs;

	public PlataformaController(ErroresPlataformaService errores, LogoEscuelaService logos, MarcaPanelService marcas,
			AltaEscuelaService altas, PublicacionWebService webs) {
		this.errores = errores;
		this.logos = logos;
		this.marcas = marcas;
		this.altas = altas;
		this.webs = webs;
	}

	/** Mensaje del 400 de {@code PUT …/marca} sin la clave {@code colorPrimario}. */
	static final String FALTA_COLOR = "Falta colorPrimario: un color #RRGGBB, o null para quitarlo.";

	/** Mensaje del 400 de un cuerpo que no se puede leer (el mismo texto que {@code MiembroController}). */
	static final String CUERPO_NO_VALIDO = "El cuerpo de la petición no es válido.";

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
	 * Fase 4, pieza F4-2 (decisiones 390, 392 y 444): da de alta una escuela con su primera sede, su propietario
	 * (invitación, o solo el rol si el email ya tiene cuenta) y el catálogo de las disciplinas elegidas copiado de la
	 * plantilla, con las tarifas desactivadas. 201 con {@code id}, {@code nombre} e {@code invitacionEnviadaA} (null sin
	 * invitación); 400 datos que no valen o disciplina fuera de la plantilla; 409 nombre repetido. Todo o nada.
	 */
	@PostMapping("/escuelas")
	public ResponseEntity<AltaEscuelaDTO> crearEscuela(@RequestBody(required = false) AltaEscuelaRequest solicitud) {
		return ResponseEntity.status(HttpStatus.CREATED).body(altas.crear(solicitud));
	}

	/** Pieza F4-2: las disciplinas de la plantilla para el alta, en el orden del catálogo, con lo que copia cada una. */
	@GetMapping("/plantilla/disciplinas")
	public ResponseEntity<List<DisciplinaPlantillaDTO>> obtenerDisciplinasDeLaPlantilla() {
		return ResponseEntity.ok(altas.disciplinasDeLaPlantilla());
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
	 * Pieza W3 de la web por plantilla: estado de la web de una escuela (tema publicado, si está publicada, cuándo y los
	 * temas disponibles). 404 si la escuela no existe; 409 si tiene su web propia (Moiskimdo).
	 */
	@GetMapping("/escuelas/{organizacionId}/web")
	public ResponseEntity<EstadoWebDTO> obtenerWeb(@PathVariable Long organizacionId) {
		return ResponseEntity.ok(webs.estado(organizacionId));
	}

	/**
	 * Pieza W3: publica la web de una escuela con el tema del cuerpo ({@code {"tema": "chandal"}}), como su
	 * propietario. Se registra en la escuela DESTINO. 200 con el estado; 400 tema que no vale; 404 escuela que no
	 * existe; 409 Moiskimdo o publicación a la vez.
	 */
	@PutMapping("/escuelas/{organizacionId}/web/publicacion")
	public ResponseEntity<EstadoWebDTO> publicarWeb(@PathVariable Long organizacionId,
			@RequestBody(required = false) JsonNode cuerpo) {
		return ResponseEntity.ok(webs.publicar(organizacionId, TemasWeb.temaDe(cuerpo),
				"/api/plataforma/escuelas/" + organizacionId + "/web/publicacion"));
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

	/**
	 * Cuerpo que no se puede leer (JSON malformado o con otra forma, p. ej. {@code {"sede": []}} en el alta): 400 con
	 * {@code {"mensaje"}}, en cualquier endpoint de este controlador, como {@code MiembroController}. Atendido aquí,
	 * Spring no apunta en el log el error de Jackson, que cita el texto del cuerpo (en el alta, nombres y emails); por
	 * eso tampoco se registra nada de la excepción.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, String>> cuerpoNoLegible() {
		return ResponseEntity.badRequest().body(Map.of("mensaje", CUERPO_NO_VALIDO));
	}
}
