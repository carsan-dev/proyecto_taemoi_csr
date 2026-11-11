package com.taemoi.project.web;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.archivos.RutasArchivos;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.entities.WebBloque;
import com.taemoi.project.entities.WebContenido;
import com.taemoi.project.exceptions.ArchivoNoValidoException;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.repositories.DisciplinaRepository;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.repositories.WebBloqueRepository;
import com.taemoi.project.repositories.WebContenidoRepository;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.CobroService;
import com.taemoi.project.services.ImagenService;

/**
 * Pieza W12a (fase4-w12-plan.md): el contenido de la web de la escuela activa para el editor del propietario. Solo
 * escuelas de plantilla: Moiskimdo (web propia) responde 409, como «Web · Tema» (W3).
 *
 * <ul>
 * <li>Leer: todas las filas de {@code web_bloque} (también las ocultas), la versión y la constancia del permiso de cada
 * profesor (d521). Funciona sin fila en {@code web_escuela} (web aún sin publicar).</li>
 * <li>Guardar: reemplaza el contenido ENTERO en una transacción con la escuela bloqueada. Si la versión no es la
 * guardada, 409 sin escribir nada y sin decir quién guardó (d522, d530). Si algo no vale, 400 con la lista de claves y
 * nada guardado: todo se valida antes de escribir. Las fotos de bloques que dejan de usarse se borran (fila y, tras
 * confirmar, fichero): una foto quitada no sigue pública. Lo guardado sale en la web al momento (d486).</li>
 * <li>Imágenes: solo fotos de la carpeta pública de bloques de la escuela ({@code org-<id>/web/bloques/}), que solo
 * escribe {@link #subirImagen}: ningún id de otra imagen (de alumnos, de otra escuela, de eventos) llega a la web.</li>
 * <li>Profesores: cada uno necesita constancia del permiso de publicación. Si ya la tiene guardada (marcada en el
 * editor o dada en el alta, d526) y su nombre no cambia, se conserva; si no la tiene o el nombre cambia (puede ser otra
 * persona en el mismo puesto, d531), el cuerpo tiene que traer {@code permiso: true}. Siempre que llega
 * {@code permiso: true} se guarda una constancia nueva: quién y cuándo. La constancia va en columnas de
 * {@code web_bloque} que la API pública no lee.</li>
 * <li>Cada guardado deja UNA fila en el registro de la escuela (solo recuentos y números de profesor, nunca textos):
 * la ruta está en {@code AuditoriaRequestFilter.EscrituraConRegistroPropio}.</li>
 * </ul>
 */
@Service
public class ContenidoWebService {

	private static final Logger log = LoggerFactory.getLogger(ContenidoWebService.class);

	static final String RUTA = "/api/escuela/web/contenido";
	static final String NO_VALIDO = "Hay datos que no se pueden guardar. Tu web sigue como estaba.";
	static final String CONFLICTO = "Otra persona ha guardado cambios en la web mientras editabas. Recarga para verlos:"
			+ " tus cambios no se han guardado.";
	static final String SIN_ARCHIVO = "Elige una foto.";
	static final String SIN_VERSION = "Falta la versión del contenido.";
	static final String PERMISO_FALTA = "Marca la casilla si tienes su permiso. Sin él no puede salir en la web.";
	static final String PERMISO_NOMBRE_CAMBIADO = "Has cambiado el nombre: marca la casilla si tienes el permiso de esta"
			+ " persona. Sin él no puede salir en la web.";
	static final String PERMISO_PANEL = "PANEL";
	static final String PERMISO_ALTA = "ALTA";
	/** Más de lo que cabe en el catálogo con todos sus repetibles llenos de sentido. */
	static final int MAXIMO_BLOQUES = 1000;
	static final int MAXIMO_ORDEN = 9999;

	private final OrganizacionRepository organizaciones;
	private final WebBloqueRepository bloques;
	private final WebContenidoRepository contenidos;
	private final ImagenRepository imagenes;
	private final SedeRepository sedes;
	private final DisciplinaRepository disciplinas;
	private final UsuarioRepository usuarios;
	private final ImagenService imagenService;
	private final AuditoriaService auditoria;
	private final TransaccionEnEscuela enEscuela;

	public ContenidoWebService(OrganizacionRepository organizaciones, WebBloqueRepository bloques,
			WebContenidoRepository contenidos, ImagenRepository imagenes, SedeRepository sedes,
			DisciplinaRepository disciplinas, UsuarioRepository usuarios, ImagenService imagenService,
			AuditoriaService auditoria, TransaccionEnEscuela enEscuela) {
		this.organizaciones = organizaciones;
		this.bloques = bloques;
		this.contenidos = contenidos;
		this.imagenes = imagenes;
		this.sedes = sedes;
		this.disciplinas = disciplinas;
		this.usuarios = usuarios;
		this.imagenService = imagenService;
		this.auditoria = auditoria;
		this.enEscuela = enEscuela;
	}

	// ------------------------------------------------------------------ leer

	/**
	 * @throws NoSuchElementException (404) la escuela no existe
	 * @throws IllegalStateException  (409) la escuela tiene su web propia (Moiskimdo)
	 */
	public ContenidoWebDTO leer(Long organizacionId) {
		PublicacionWebService.comprobarId(organizacionId);
		return enEscuela.leer(organizacionId, () -> {
			PublicacionWebService.comprobarPlantilla(organizaciones.findById(organizacionId)
					.orElseThrow(() -> new NoSuchElementException(PublicacionWebService.NO_ENCONTRADA)));
			return dto(organizacionId);
		});
	}

	private ContenidoWebDTO dto(Long organizacionId) {
		long version = contenidos.findById(organizacionId).map(WebContenido::getVersion).orElse(0L);
		List<WebBloque> filas = bloques.findAllByOrderByOrdenAscClaveAsc();
		Map<Long, String> urls = imagenes
				.findAllById(filas.stream().map(WebBloque::getImagenId).filter(Objects::nonNull).distinct().toList())
				.stream().collect(Collectors.toMap(Imagen::getId, Imagen::getUrl, (a, b) -> a));
		Map<Long, String> nombres = usuarios
				.findAllById(filas.stream().map(WebBloque::getPermisoPor).filter(Objects::nonNull).distinct().toList())
				.stream().collect(Collectors.toMap(Usuario::getId, ContenidoWebService::nombre, (a, b) -> a));
		return new ContenidoWebDTO(version, filas.stream().map(b -> new ContenidoWebDTO.Bloque(b.getClave(),
				b.getTexto(), b.getImagenId(), b.getImagenId() == null ? null : urls.get(b.getImagenId()),
				b.getOrden() == null ? 0 : b.getOrden(), b.isOculto(),
				b.getPermisoOrigen() == null ? null
						: new ContenidoWebDTO.Permiso(b.getPermisoOrigen(),
								b.getPermisoPor() == null ? null : nombres.get(b.getPermisoPor()), b.getPermisoEn())))
				.toList());
	}

	private static String nombre(Usuario usuario) {
		String nombre = ((usuario.getNombre() == null ? "" : usuario.getNombre()) + " "
				+ (usuario.getApellidos() == null ? "" : usuario.getApellidos())).strip();
		return nombre.isEmpty() ? null : nombre;
	}

	// ------------------------------------------------------------------ guardar

	/** Lo que deja una transacción de guardado: la respuesta y las imágenes cuyo fichero se borra al confirmar. */
	private record Guardado(ContenidoWebDTO contenido, List<Imagen> borradas) {
	}

	/**
	 * Reemplaza el contenido entero.
	 *
	 * @throws ContenidoWebNoValidoException     (400) algo no vale; no se guarda nada
	 * @throws ContenidoWebEnConflictoException (409) otra persona guardó antes; no se guarda nada
	 * @throws NoSuchElementException            (404) la escuela no existe
	 * @throws IllegalStateException             (409) la escuela tiene su web propia (Moiskimdo)
	 */
	public ContenidoWebDTO guardar(Long organizacionId, ContenidoWebRequest solicitud) {
		PublicacionWebService.comprobarId(organizacionId);
		if (solicitud == null || solicitud.version() == null || solicitud.version() < 0) {
			throw new ContenidoWebNoValidoException(Map.of("version", SIN_VERSION));
		}
		List<ContenidoWebRequest.Bloque> pedidos = solicitud.bloques() == null ? List.of() : solicitud.bloques();
		if (pedidos.size() > MAXIMO_BLOQUES) {
			throw new ContenidoWebNoValidoException(Map.of("bloques", "Demasiados bloques."));
		}
		Guardado guardado = enEscuela.escribir(organizacionId, () -> {
			PublicacionWebService.comprobarPlantilla(organizaciones.findByIdParaActualizar(organizacionId)
					.orElseThrow(() -> new NoSuchElementException(PublicacionWebService.NO_ENCONTRADA)));
			WebContenido contenido = contenidos.findById(organizacionId).orElse(null);
			long actual = contenido == null ? 0L : contenido.getVersion();
			if (actual != solicitud.version()) {
				throw new ContenidoWebEnConflictoException();
			}
			List<WebBloque> existentes = bloques.findAllByOrderByOrdenAscClaveAsc();
			Map<String, WebBloque> porClave = new HashMap<>();
			existentes.forEach(b -> porClave.put(b.getClave(), b));
			List<Fila> filas = validar(organizacionId, pedidos, porClave);
			return escribir(organizacionId, filas, porClave, existentes, contenido, actual);
		});
		borrarFicheros(guardado.borradas());
		return guardado.contenido();
	}

	/** Una fila ya validada y normalizada. */
	private record Fila(ClavesWeb.Clave definicion, String texto, Long imagenId, int orden, boolean oculto,
			boolean permisoNuevo) {
	}

	private List<Fila> validar(Long organizacionId, List<ContenidoWebRequest.Bloque> pedidos,
			Map<String, WebBloque> existentes) {
		Map<String, String> errores = new LinkedHashMap<>();
		List<Fila> filas = new ArrayList<>();
		Set<String> vistas = new HashSet<>();
		Set<Long> imagenesPedidas = new HashSet<>();
		Set<String> codigos = null;
		Set<Long> idsSedes = null;
		for (ContenidoWebRequest.Bloque pedido : pedidos) {
			if (pedido == null || pedido.clave() == null) {
				errores.put("bloques", "Hay un bloque sin clave.");
				continue;
			}
			String clave = pedido.clave();
			ClavesWeb.Clave d = ClavesWeb.de(clave).orElse(null);
			if (d == null) {
				errores.put(clave, "Esta clave no existe en la web.");
				continue;
			}
			if (!vistas.add(clave)) {
				errores.put(clave, "Clave repetida.");
				continue;
			}
			String error = null;
			String texto = pedido.texto() == null ? null : pedido.texto().strip();
			int orden = pedido.orden() == null ? 0 : pedido.orden();
			boolean oculto = Boolean.TRUE.equals(pedido.oculto());
			boolean permiso = Boolean.TRUE.equals(pedido.permiso());
			if (orden < 0 || orden > MAXIMO_ORDEN) {
				error = "Orden no válido.";
			} else if ("galeria".equals(d.seccion()) && d.esImagen() && orden > ClavesWeb.MAXIMO_GALERIA) {
				// En la galería, orden = puesto de la foto (como mucho 6, como las fotos).
				error = "Puesto en la galería no válido.";
			} else if (oculto && (!d.esCabecera() || d.seccion() == null)) {
				error = d.esCabecera() ? "Esta sección no se puede ocultar." : "Solo se oculta una sección entera.";
			} else if (permiso && !d.esNombreDeProfesor()) {
				error = "El permiso va con el nombre del profesor.";
			} else if (d.esImagen() != (pedido.imagenId() != null)) {
				error = d.esImagen() ? "Añade la foto." : "Aquí no va una foto.";
			} else if (d.esCabecera()) {
				if (texto != null && !texto.isEmpty()) {
					error = "La cabecera de una sección no lleva texto.";
				}
				texto = null;
			} else {
				if (texto == null) {
					texto = "";
				}
				int largo = texto.codePointCount(0, texto.length());
				if (d.obligatorio() && texto.isEmpty()) {
					error = d.esImagen() ? "Describe la foto."
							: d.esNombreDeProfesor() ? "Escribe su nombre." : "Escribe el texto.";
				} else if (largo > d.maximo()) {
					error = "Máximo " + d.maximo() + " caracteres.";
				} else if (d.tipo() == ClavesWeb.Tipo.SEDES) {
					if (idsSedes == null) {
						idsSedes = sedes.findAllByOrderByIdAsc().stream().map(Sede::getId).collect(Collectors.toSet());
					}
					String normalizado = sedesDelProfesor(texto, idsSedes);
					if (normalizado == null) {
						error = "Elige sedes de tu escuela.";
					} else {
						texto = normalizado;
					}
				}
			}
			if (error == null && d.disciplina() != null) {
				if (codigos == null) {
					codigos = disciplinas.findAll().stream().map(Disciplina::getCodigo).filter(Objects::nonNull)
							.map(c -> c.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
				}
				if (!codigos.contains(d.disciplina())) {
					error = "Esa disciplina no es de tu escuela.";
				}
			}
			if (error != null) {
				errores.put(clave, error);
				continue;
			}
			if (pedido.imagenId() != null) {
				imagenesPedidas.add(pedido.imagenId());
			}
			filas.add(new Fila(d, texto, pedido.imagenId(), orden, oculto, permiso));
		}
		comprobarImagenes(organizacionId, filas, imagenesPedidas, errores);
		comprobarEstructura(filas, existentes, errores);
		if (!errores.isEmpty()) {
			throw new ContenidoWebNoValidoException(errores);
		}
		return filas;
	}

	/** Ids de sedes de la escuela separados por comas, sin repetir; normalizado o {@code null} si no vale. */
	private static String sedesDelProfesor(String texto, Set<Long> idsSedes) {
		if (texto.isEmpty()) {
			return "";
		}
		Set<Long> vistas = new java.util.LinkedHashSet<>();
		for (String parte : texto.split(",", -1)) {
			String limpia = parte.strip();
			if (!limpia.matches("[1-9][0-9]{0,17}")) {
				return null;
			}
			Long id = Long.valueOf(limpia);
			if (!idsSedes.contains(id) || !vistas.add(id)) {
				return null;
			}
		}
		return vistas.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	/** Solo fotos de la carpeta pública de bloques de la escuela activa (las de otra escuela ni se encuentran). */
	private void comprobarImagenes(Long organizacionId, List<Fila> filas, Set<Long> pedidas,
			Map<String, String> errores) {
		if (pedidas.isEmpty()) {
			return;
		}
		Map<Long, Imagen> encontradas = imagenes.findAllById(pedidas).stream()
				.collect(Collectors.toMap(Imagen::getId, Function.identity()));
		for (Fila fila : filas) {
			if (fila.imagenId() != null && !esDeLaCarpetaDeBloques(encontradas.get(fila.imagenId()), organizacionId)) {
				errores.put(fila.definicion().clave(), "La foto no es de la web de tu escuela.");
			}
		}
	}

	static boolean esDeLaCarpetaDeBloques(Imagen imagen, Long organizacionId) {
		if (imagen == null || imagen.getRuta() == null || !Objects.equals(imagen.getOrganizacionId(), organizacionId)) {
			return false;
		}
		String carpeta = RutasArchivos.carpetaOrganizacion(organizacionId) + "/" + RutasArchivos.CARPETA_WEB_BLOQUES
				+ "/";
		String ruta = imagen.getRuta();
		return ruta.startsWith(carpeta) && ruta.indexOf('/', carpeta.length()) < 0 && ruta.length() > carpeta.length()
				&& RutasArchivos.esImagenPublica(ruta);
	}

	/** Lo que depende de varias filas: portada, galería, profesores y bloques «Texto con foto». */
	private static void comprobarEstructura(List<Fila> filas, Map<String, WebBloque> existentes,
			Map<String, String> errores) {
		Map<String, Fila> porClave = new HashMap<>();
		filas.forEach(f -> porClave.put(f.definicion().clave(), f));
		if (!porClave.containsKey("portada.titular") && !errores.containsKey("portada.titular")) {
			errores.put("portada.titular", "Escribe el titular.");
		}
		if (!porClave.containsKey("portada.foto") && !errores.containsKey("portada.foto")) {
			errores.put("portada.foto", "Añade la foto de portada.");
		}
		long fotosGaleria = filas.stream()
				.filter(f -> "galeria".equals(f.definicion().seccion()) && f.definicion().esImagen()).count();
		if (fotosGaleria > ClavesWeb.MAXIMO_GALERIA) {
			errores.put("galeria", "La galería tiene como mucho " + ClavesWeb.MAXIMO_GALERIA + " fotos.");
		}
		Set<Integer> profesores = new TreeSet<>();
		Set<String> textosConFoto = new TreeSet<>();
		for (Fila fila : filas) {
			ClavesWeb.Clave d = fila.definicion();
			if (d.profesor() != null) {
				profesores.add(d.profesor());
			}
			if (d.seccion() != null && d.seccion().startsWith("texto_foto.")) {
				textosConFoto.add(d.seccion());
			}
		}
		for (Integer n : profesores) {
			String clave = "profesor." + n + ".nombre";
			Fila nombre = porClave.get(clave);
			if (nombre == null) {
				errores.putIfAbsent(clave, "Escribe su nombre.");
				continue;
			}
			if (!nombre.permisoNuevo() && !conservaLaConstancia(existentes.get(clave), nombre)) {
				WebBloque guardado = existentes.get(clave);
				errores.put("profesor." + n + ".permiso", guardado != null && guardado.getPermisoOrigen() != null
						? PERMISO_NOMBRE_CAMBIADO
						: PERMISO_FALTA);
			}
		}
		for (String seccion : textosConFoto) {
			if (!porClave.containsKey(seccion)) {
				errores.putIfAbsent(seccion, "Falta la cabecera del bloque.");
			}
			boolean conContenido = porClave.containsKey(seccion + ".foto")
					|| (porClave.containsKey(seccion + ".texto") && !porClave.get(seccion + ".texto").texto().isEmpty());
			if (conContenido && !porClave.containsKey(seccion + ".titulo")) {
				errores.putIfAbsent(seccion + ".titulo", "Escribe el título.");
			}
		}
	}

	/**
	 * d531: la constancia es de la persona, no del puesto {@code profesor.<n>}. Solo se conserva si la fila ya la tiene y
	 * el nombre no cambia; con otro nombre puede ser otra persona y hace falta marcar la casilla otra vez.
	 */
	private static boolean conservaLaConstancia(WebBloque guardado, Fila nombre) {
		return guardado != null && guardado.getPermisoOrigen() != null
				&& Objects.equals(guardado.getTexto() == null ? "" : guardado.getTexto().strip(), nombre.texto());
	}

	private Guardado escribir(Long organizacionId, List<Fila> filas, Map<String, WebBloque> porClave,
			List<WebBloque> existentes, WebContenido contenido, long actual) {
		Long usuario = CobroService.usuarioActualId();
		LocalDateTime ahora = LocalDateTime.now().withNano(0);
		Set<Long> imagenesAntes = existentes.stream().map(WebBloque::getImagenId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Set<String> quedan = new HashSet<>();
		List<Integer> permisosNuevos = new ArrayList<>();
		List<WebBloque> aGuardar = new ArrayList<>();
		for (Fila fila : filas) {
			String clave = fila.definicion().clave();
			quedan.add(clave);
			WebBloque bloque = porClave.get(clave);
			if (bloque == null) {
				bloque = new WebBloque();
				bloque.setClave(clave);
			}
			bloque.setTexto(fila.texto());
			bloque.setImagenId(fila.imagenId());
			bloque.setOrden(fila.orden());
			bloque.setOculto(fila.oculto());
			// d531: casilla marcada → constancia nueva (quién y cuándo), aunque el puesto ya tuviera una. La validación ya
			// exigió la casilla si no había constancia o si el nombre cambió.
			if (fila.definicion().esNombreDeProfesor() && (fila.permisoNuevo() || bloque.getPermisoOrigen() == null)) {
				bloque.setPermisoOrigen(PERMISO_PANEL);
				bloque.setPermisoPor(usuario);
				bloque.setPermisoEn(ahora);
				permisosNuevos.add(fila.definicion().profesor());
			}
			aGuardar.add(bloque);
		}
		List<WebBloque> quitados = existentes.stream().filter(b -> !quedan.contains(b.getClave())).toList();
		bloques.deleteAll(quitados);
		bloques.saveAll(aGuardar);
		bloques.flush();

		// Fotos de bloques que ya no usa nadie: fuera la fila (y, tras confirmar, el fichero). Una foto quitada no sigue
		// pública. Solo las de la carpeta de bloques de la escuela: otra imagen solo se suelta.
		Set<Long> imagenesDespues = filas.stream().map(Fila::imagenId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		List<Imagen> borradas = new ArrayList<>();
		for (Imagen imagen : imagenes.findAllById(
				imagenesAntes.stream().filter(id -> !imagenesDespues.contains(id)).toList())) {
			if (esDeLaCarpetaDeBloques(imagen, organizacionId)) {
				imagenes.delete(imagen);
				borradas.add(imagen);
			}
		}
		imagenes.flush();

		boolean primera = contenido == null;
		if (primera) {
			contenido = new WebContenido();
			contenido.setOrganizacionId(organizacionId);
		}
		contenido.setVersion(actual + 1);
		contenido.setGuardadoEn(ahora);
		contenido.setGuardadoPor(usuario);
		contenidos.saveAndFlush(contenido);

		// Solo recuentos y números de profesor: ningún texto ni nombre.
		long fotos = filas.stream().filter(f -> f.imagenId() != null).count();
		long ocultos = filas.stream().filter(Fila::oculto).count();
		long textos = filas.stream().filter(f -> !f.definicion().esCabecera() && !f.definicion().esImagen()).count();
		permisosNuevos.sort(Comparator.naturalOrder());
		// El método real (PUT) también en el primer guardado, que es un CREATE.
		auditoria.registrarAccesoSensible(primera ? AuditoriaAccion.CREATE : AuditoriaAccion.UPDATE, "PUT",
				PublicacionWebService.MODULO_AUDITORIA, RUTA, organizacionId,
				"WEB CONTENIDO_GUARDADO organizacion=#" + organizacionId + " version=" + (actual + 1) + " bloques="
						+ filas.size() + " textos=" + textos + " fotos=" + fotos + " ocultos=" + ocultos
						+ " quitados=" + quitados.size() + " fotosBorradas=" + borradas.size()
						+ " permisosNuevos=" + (permisosNuevos.isEmpty() ? "NINGUNO"
								: permisosNuevos.stream().map(String::valueOf).collect(Collectors.joining(","))));
		return new Guardado(dto(organizacionId), borradas);
	}

	// ------------------------------------------------------------------ imágenes

	/**
	 * Sube una foto para un bloque a la carpeta pública de la escuela ({@code org-<id>/web/bloques/}). No toca ningún
	 * bloque: queda suelta hasta que se guarda el contenido.
	 *
	 * @throws ArchivoNoValidoException (400) sin fichero, HEIC, otro formato, ilegible o demasiado grande
	 * @throws IllegalStateException    (409) la escuela tiene su web propia (Moiskimdo), antes de leer el fichero
	 */
	public ImagenWebDTO subirImagen(Long organizacionId, MultipartFile archivo) throws IOException {
		PublicacionWebService.comprobarId(organizacionId);
		enEscuela.leer(organizacionId, () -> {
			PublicacionWebService.comprobarPlantilla(organizaciones.findById(organizacionId)
					.orElseThrow(() -> new NoSuchElementException(PublicacionWebService.NO_ENCONTRADA)));
			return null;
		});
		if (archivo == null || archivo.isEmpty()) {
			throw new ArchivoNoValidoException(SIN_ARCHIVO);
		}
		Imagen nueva = imagenService.guardarFotoBloqueWeb(archivo);
		Imagen guardada;
		try {
			guardada = enEscuela.escribir(organizacionId, () -> imagenes.saveAndFlush(nueva));
		} catch (RuntimeException | Error e) {
			// Deshecho: el fichero recién escrito no lo apunta nadie.
			borrarFicheros(List.of(nueva));
			throw e;
		}
		return new ImagenWebDTO(guardada.getId(), guardada.getUrl());
	}

	/** Las filas ya no existen: si un fichero no se puede borrar se avisa, pero la petición no falla. */
	private void borrarFicheros(List<Imagen> borradas) {
		for (Imagen imagen : borradas) {
			try {
				imagenService.eliminarImagenDeSistema(imagen);
			} catch (RuntimeException e) {
				// Sin el mensaje: lleva la ruta del fichero.
				log.warn("No se pudo borrar el fichero de una foto de la web (imagen {}): {}", imagen.getId(),
						e.getClass().getSimpleName());
			}
		}
	}
}
