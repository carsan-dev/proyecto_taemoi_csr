package com.taemoi.project.services;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.entities.Evento;
import com.taemoi.project.entities.EventoFoto;
import com.taemoi.project.entities.Imagen;
import com.taemoi.project.exceptions.evento.EventoNoEncontradoException;
import com.taemoi.project.exceptions.evento.FotoEventoNoEncontradaException;
import com.taemoi.project.exceptions.evento.TopeFotosEventoException;
import com.taemoi.project.repositories.EventoFotoRepository;
import com.taemoi.project.repositories.EventoRepository;
import com.taemoi.project.repositories.ImagenRepository;

/**
 * Decisión 342: galería de fotos de un evento. Quién puede ver qué se decide en {@code EventoFotoController}.
 *
 * <p>Toda escritura bloquea antes la fila del evento: se ordenan entre sí, y el recuento del tope va DESPUÉS del bloqueo
 * para que, en REPEATABLE READ, vea lo que confirmó la subida anterior. El bloqueo debe ser la primera sentencia de la
 * transacción.
 *
 * <p>Fichero y fila no pueden ir juntos en una transacción. Al subir, el fichero se escribe fuera (procesar una imagen
 * con la fila bloqueada pararía el resto) y se borra si la transacción falla. Al borrar, el fichero se va tras confirmar,
 * así un fallo no deja una fila sin su foto. Por eso se usa {@link TransactionTemplate} y no {@code @Transactional}:
 * estos métodos no deben llamarse dentro de otra transacción.
 */
@Service
public class EventoFotoService {

	public static final int TOPE_POR_EVENTO = 40;

	private static final Logger log = LoggerFactory.getLogger(EventoFotoService.class);

	private final EventoRepository eventos;
	private final EventoFotoRepository fotos;
	private final ImagenRepository imagenes;
	private final ImagenService imagenService;
	private final TransactionTemplate transaccion;

	public EventoFotoService(EventoRepository eventos, EventoFotoRepository fotos, ImagenRepository imagenes,
			ImagenService imagenService, PlatformTransactionManager transacciones) {
		this.eventos = eventos;
		this.fotos = fotos;
		this.imagenes = imagenes;
		this.imagenService = imagenService;
		this.transaccion = new TransactionTemplate(transacciones);
	}

	public List<EventoFoto> listar(Long eventoId) {
		return fotos.listarDeEvento(eventoId);
	}

	/** @throws FotoEventoNoEncontradaException si no existe, es de otra escuela o no es de ese evento */
	public EventoFoto obtenerConImagen(Long eventoId, Long fotoId) {
		return fotos.buscarConImagen(eventoId, fotoId)
				.orElseThrow(() -> new FotoEventoNoEncontradaException("La foto no existe."));
	}

	/**
	 * @throws EventoNoEncontradoException si el evento no existe o es de otra escuela
	 * @throws TopeFotosEventoException    si ya tiene {@value #TOPE_POR_EVENTO} fotos
	 */
	public EventoFoto subir(Long eventoId, MultipartFile archivo) throws IOException {
		// Sin bloquear y antes de procesar la imagen, para no gastar en lo que ya se sabe que no entra. El tope que vale
		// es el de dentro de la transacción.
		if (!eventos.existsById(eventoId)) {
			throw eventoNoEncontrado(eventoId);
		}
		comprobarTope(eventoId);
		Imagen imagen = imagenService.guardarImagenFotoEvento(archivo);
		try {
			return transaccion.execute(estado -> registrar(eventoId, imagen));
		} catch (RuntimeException | Error e) {
			// Deshecho: el fichero recién escrito no lo apunta nadie.
			borrarFichero(imagen);
			throw e;
		}
	}

	/**
	 * @param ids exactamente las fotos del evento, en el orden nuevo
	 * @return las fotos en ese orden
	 * @throws IllegalArgumentException si es {@code null} o no son exactamente las fotos del evento
	 */
	public List<EventoFoto> reordenar(Long eventoId, List<Long> ids) {
		return transaccion.execute(estado -> {
			bloquear(eventoId);
			Map<Long, EventoFoto> porId = fotos.listarDeEvento(eventoId).stream()
					.collect(Collectors.toMap(EventoFoto::getId, Function.identity()));
			if (ids == null) {
				throw listaNoValida();
			}
			Set<Long> pedidas = new HashSet<>(ids);
			if (pedidas.size() != ids.size() || !pedidas.equals(porId.keySet())) {
				throw listaNoValida();
			}
			int orden = 1;
			for (Long id : ids) {
				porId.get(id).setOrden(orden++);
			}
			return ids.stream().map(porId::get).toList();
		});
	}

	/**
	 * Borra la fila, la imagen y, tras confirmar, el fichero.
	 *
	 * @throws EventoNoEncontradoException     si el evento no existe o es de otra escuela
	 * @throws FotoEventoNoEncontradaException si la foto no es de ese evento
	 */
	public void eliminar(Long eventoId, Long fotoId) {
		Imagen imagen = transaccion.execute(estado -> {
			bloquear(eventoId);
			EventoFoto foto = obtenerConImagen(eventoId, fotoId);
			fotos.delete(foto);
			imagenes.delete(foto.getImagen());
			return foto.getImagen();
		});
		borrarFichero(imagen);
	}

	/**
	 * Último paso de borrar un evento: el evento y sus fotos a la vez, para que una subida simultánea no quede a medias.
	 * Los ficheros se van tras confirmar.
	 *
	 * @throws EventoNoEncontradoException si el evento no existe o es de otra escuela
	 */
	public void eliminarEventoConFotos(Long eventoId) {
		List<Imagen> borradas = transaccion.execute(estado -> {
			Evento evento = bloquear(eventoId);
			List<EventoFoto> suyas = fotos.listarDeEventoConImagen(eventoId);
			List<Imagen> suyasImagenes = suyas.stream().map(EventoFoto::getImagen).toList();
			fotos.deleteAll(suyas);
			imagenes.deleteAll(suyasImagenes);
			eventos.delete(evento);
			return suyasImagenes;
		});
		borradas.forEach(this::borrarFichero);
	}

	private EventoFoto registrar(Long eventoId, Imagen imagen) {
		Evento evento = bloquear(eventoId);
		comprobarTope(eventoId);
		Imagen guardada = imagenes.save(imagen);
		return fotos.save(new EventoFoto(evento, guardada, fotos.ultimoOrden(eventoId) + 1));
	}

	private Evento bloquear(Long eventoId) {
		return eventos.findByIdParaActualizar(eventoId).orElseThrow(() -> eventoNoEncontrado(eventoId));
	}

	private void comprobarTope(Long eventoId) {
		if (fotos.contarDeEvento(eventoId) >= TOPE_POR_EVENTO) {
			throw new TopeFotosEventoException("Un evento no puede tener más de " + TOPE_POR_EVENTO + " fotos.");
		}
	}

	private static IllegalArgumentException listaNoValida() {
		return new IllegalArgumentException("La lista debe contener exactamente las fotos del evento, sin repetir ninguna.");
	}

	private static EventoNoEncontradoException eventoNoEncontrado(Long eventoId) {
		return new EventoNoEncontradoException("No se encontró el evento con ID: " + eventoId);
	}

	/** La fila ya no existe: si el fichero no se puede borrar se avisa, pero la petición no falla. Queda en carpeta privada. */
	private void borrarFichero(Imagen imagen) {
		try {
			imagenService.eliminarImagenDeSistema(imagen);
		} catch (RuntimeException e) {
			// Sin el mensaje: lleva la ruta del fichero.
			log.warn("No se pudo borrar el fichero de una foto de evento (imagen {}): {}", imagen.getId(),
					e.getClass().getSimpleName());
		}
	}
}
