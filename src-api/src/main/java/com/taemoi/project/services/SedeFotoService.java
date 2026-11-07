package com.taemoi.project.services;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.archivos.RutasArchivos;
import com.taemoi.project.dtos.response.SedeDTO;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.exceptions.ArchivoNoValidoException;
import com.taemoi.project.repositories.ImagenRepository;
import com.taemoi.project.repositories.SedeRepository;

/**
 * Web por plantilla: las dos fotos de una sede de la escuela activa ({@code foto}, la principal, y {@code foto2}, la de
 * «cómo llegar»). Solo se ponen subiendo una imagen nueva, que {@link ImagenService#guardarFotoSede} escribe en la
 * carpeta pública de la web ({@code org-<id>/web/sedes/}): la API no acepta el id de una imagen que ya exista, así que
 * ninguna foto de alumno puede acabar en la web por aquí.
 *
 * <ul>
 * <li>Sede de otra escuela o inexistente: 404 ({@code Sede} es {@code @TenantId}), antes de procesar la imagen.</li>
 * <li>La sede se bloquea en la transacción: dos subidas a la vez al mismo hueco no dejan una imagen suelta.</li>
 * <li>Sustituir o quitar borra la fila de la imagen anterior y, tras confirmar, su fichero, como la galería de eventos;
 * solo si es de la carpeta de las sedes de la escuela. Otra imagen (puesta a mano) solo se suelta.</li>
 * <li>Si la transacción falla, el fichero recién escrito se borra.</li>
 * <li>Cada escritura correcta deja UNA fila en el registro ({@link RegistroAccionesSedes}, misma transacción), también
 * quitar sin foto ({@code sinCambios=SI}): por eso las dos rutas están en
 * {@code AuditoriaRequestFilter.EscrituraConRegistroPropio}.</li>
 * </ul>
 */
@Service
public class SedeFotoService {

	private static final Logger log = LoggerFactory.getLogger(SedeFotoService.class);

	static final String FOTO_NO_EXISTE = "La sede solo tiene la foto 1 y la 2.";
	static final String SIN_ARCHIVO = "Elige una foto.";
	private static final String RUTA = "/api/sedes/";

	/** Hueco de la foto: {@code FOTO} (1, la principal) y {@code FOTO2} (2, «cómo llegar»). */
	public enum Foto {
		FOTO, FOTO2;

		static Foto de(int numero) {
			return switch (numero) {
			case 1 -> FOTO;
			case 2 -> FOTO2;
			default -> throw new NoSuchElementException(FOTO_NO_EXISTE);
			};
		}

		Long de(Sede sede) {
			return this == FOTO ? sede.getFotoImagenId() : sede.getFoto2ImagenId();
		}

		void poner(Sede sede, Long imagen) {
			if (this == FOTO) {
				sede.setFotoImagenId(imagen);
			} else {
				sede.setFoto2ImagenId(imagen);
			}
		}

		int numero() {
			return ordinal() + 1;
		}
	}

	/** La sede tras el cambio y la imagen anterior cuya fila se borró (su fichero se borra tras confirmar). */
	private record Hecho(SedeDTO sede, Imagen borrada) {
	}

	private final SedeRepository sedes;
	private final ImagenRepository imagenes;
	private final ImagenService imagenService;
	private final RegistroAccionesSedes registro;
	private final TransactionTemplate transaccion;

	public SedeFotoService(SedeRepository sedes, ImagenRepository imagenes, ImagenService imagenService,
			RegistroAccionesSedes registro, PlatformTransactionManager transacciones) {
		this.sedes = sedes;
		this.imagenes = imagenes;
		this.imagenService = imagenService;
		this.registro = registro;
		this.transaccion = new TransactionTemplate(transacciones);
	}

	/**
	 * Pone o sustituye la foto {@code numero} con la imagen subida.
	 *
	 * @throws NoSuchElementException   (404) sede de otra escuela o inexistente, o {@code numero} no es 1 ni 2
	 * @throws ArchivoNoValidoException (400) sin fichero, HEIC, otro formato, ilegible o demasiado grande
	 */
	public SedeDTO cambiar(Long sedeId, int numero, MultipartFile archivo) throws IOException {
		Foto foto = Foto.de(numero);
		exigir(sedeId);
		if (archivo == null || archivo.isEmpty()) {
			throw new ArchivoNoValidoException(SIN_ARCHIVO);
		}
		Imagen nueva = imagenService.guardarFotoSede(archivo);
		Hecho hecho;
		try {
			hecho = transaccion.execute(estado -> poner(sedeId, foto, nueva));
		} catch (RuntimeException | Error e) {
			// Deshecho: el fichero recién escrito no lo apunta nadie.
			borrarFichero(nueva);
			throw e;
		}
		borrarFichero(hecho.borrada());
		return hecho.sede();
	}

	/**
	 * Quita la foto {@code numero}. Sin foto, 200 sin cambios.
	 *
	 * @throws NoSuchElementException (404) sede de otra escuela o inexistente, o {@code numero} no es 1 ni 2
	 */
	public SedeDTO quitar(Long sedeId, int numero) {
		Foto foto = Foto.de(numero);
		exigir(sedeId);
		Hecho hecho = transaccion.execute(estado -> {
			Sede sede = bloquear(sedeId);
			Long anterior = foto.de(sede);
			Map<String, Object> valores = valores(sede, foto);
			Imagen borrada = null;
			if (anterior == null) {
				valores.put("sinCambios", Boolean.TRUE);
			} else {
				foto.poner(sede, null);
				sede = sedes.saveAndFlush(sede);
				borrada = borrarFilaSiEsDeLaCarpeta(anterior);
				valores.put("imagen", anterior);
			}
			registro.registrar(RegistroAccionesSedes.Accion.SEDE_FOTO_QUITADA, ruta(sede, foto), valores);
			return new Hecho(SedeDTO.de(sede), borrada);
		});
		borrarFichero(hecho.borrada());
		return hecho.sede();
	}

	private Hecho poner(Long sedeId, Foto foto, Imagen nueva) {
		Sede sede = bloquear(sedeId);
		Long anterior = foto.de(sede);
		Imagen guardada = imagenes.save(nueva);
		foto.poner(sede, guardada.getId());
		sede = sedes.saveAndFlush(sede);
		Imagen borrada = anterior == null ? null : borrarFilaSiEsDeLaCarpeta(anterior);
		Map<String, Object> valores = valores(sede, foto);
		valores.put("imagen", guardada.getId());
		valores.put("anterior", anterior);
		registro.registrar(RegistroAccionesSedes.Accion.SEDE_FOTO_CAMBIADA, ruta(sede, foto), valores);
		return new Hecho(SedeDTO.de(sede), borrada);
	}

	/**
	 * Borra la fila de la imagen si es de la carpeta de las sedes de la escuela activa (las que sube esta API) y la
	 * devuelve para borrar después su fichero; otra imagen (puesta a mano) solo se suelta y devuelve {@code null}.
	 */
	private Imagen borrarFilaSiEsDeLaCarpeta(Long imagenId) {
		Imagen imagen = imagenes.findById(imagenId).orElse(null);
		if (imagen == null || !esDeLaCarpetaDeSedes(imagen)) {
			return null;
		}
		imagenes.delete(imagen);
		imagenes.flush();
		return imagen;
	}

	private static boolean esDeLaCarpetaDeSedes(Imagen imagen) {
		String carpeta = RutasArchivos.carpetaOrganizacion(RutasArchivos.organizacionActiva()) + "/"
				+ RutasArchivos.CARPETA_WEB_SEDES + "/";
		String ruta = imagen.getRuta();
		return ruta != null && Objects.equals(imagen.getOrganizacionId(), RutasArchivos.organizacionActiva())
				&& ruta.startsWith(carpeta) && ruta.indexOf('/', carpeta.length()) < 0
				&& RutasArchivos.esImagenPublica(ruta);
	}

	private void exigir(Long sedeId) {
		if (sedeId == null || !sedes.existeEnEscuelaActiva(sedeId)) {
			throw new NoSuchElementException(GestionSedesService.NO_EXISTE);
		}
	}

	private Sede bloquear(Long sedeId) {
		return sedes.bloquearEnEscuelaActiva(sedeId)
				.orElseThrow(() -> new NoSuchElementException(GestionSedesService.NO_EXISTE));
	}

	private static Map<String, Object> valores(Sede sede, Foto foto) {
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("sede", sede.getId());
		valores.put("foto", foto);
		return valores;
	}

	private static String ruta(Sede sede, Foto foto) {
		return RUTA + sede.getId() + "/fotos/" + foto.numero();
	}

	/** La fila ya no existe: si el fichero no se puede borrar se avisa, pero la petición no falla. */
	private void borrarFichero(Imagen imagen) {
		if (imagen == null) {
			return;
		}
		try {
			imagenService.eliminarImagenDeSistema(imagen);
		} catch (RuntimeException e) {
			// Sin el mensaje: lleva la ruta del fichero.
			log.warn("No se pudo borrar el fichero de una foto de sede (imagen {}): {}", imagen.getId(),
					e.getClass().getSimpleName());
		}
	}
}
