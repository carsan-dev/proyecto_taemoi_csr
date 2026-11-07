package com.taemoi.project.web;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.WebEscuela;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.WebEscuelaRepository;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.CobroService;

/**
 * Pieza W3 de la web por plantilla (fase4-web-plan.md §5): el estado de la web de una escuela para el panel y
 * publicarla con un tema. Lo llaman el propietario (su escuela activa) y el superadmin (la escuela por id); quién puede,
 * lo deciden los controladores.
 *
 * <p>Solo se guarda lo publicado: el tema elegido y aún no publicado vive en la pantalla (§9, n.º 2). No toca los
 * bloques ni el contenido. Moiskimdo (marca heredada, la 1) tiene su web propia: no tiene «Web · Tema» (§9, n.º 8) y
 * aquí se rechaza con 409.
 *
 * <p>Todo en una transacción NUEVA con la escuela fijada ({@link TransaccionEnEscuela}), como el logo y el color del
 * superadmin: el registro de acciones va siempre a la escuela de la web. La escuela se bloquea mientras se publica: dos
 * publicaciones a la vez se ordenan (también la primera, que crea la fila); si la espera se agota, 409 con el mensaje
 * de concurrencia de siempre ({@code GlobalExceptionHandler}).
 */
@Service
public class PublicacionWebService {

	static final String MODULO_AUDITORIA = "web";
	static final String WEB_PROPIA = "Esta escuela tiene su web propia: no usa la web por plantilla.";
	private static final String NO_ENCONTRADA = "Escuela no encontrada.";

	private final OrganizacionRepository organizaciones;
	private final WebEscuelaRepository webs;
	private final AuditoriaService auditoria;
	private final TransaccionEnEscuela enEscuela;

	public PublicacionWebService(OrganizacionRepository organizaciones, WebEscuelaRepository webs,
			AuditoriaService auditoria, TransaccionEnEscuela enEscuela) {
		this.organizaciones = organizaciones;
		this.webs = webs;
		this.auditoria = auditoria;
		this.enEscuela = enEscuela;
	}

	/**
	 * Tema publicado, si está publicada, cuándo y los temas disponibles.
	 *
	 * @throws NoSuchElementException (404) la escuela no existe
	 * @throws IllegalStateException  (409) la escuela tiene su web propia (Moiskimdo)
	 */
	public EstadoWebDTO estado(Long organizacionId) {
		comprobarId(organizacionId);
		return enEscuela.leer(organizacionId, () -> {
			comprobarPlantilla(organizaciones.findById(organizacionId)
					.orElseThrow(() -> new NoSuchElementException(NO_ENCONTRADA)));
			return dto(webs.findById(organizacionId).orElse(null));
		});
	}

	/**
	 * Publica la web con {@code tema}: crea la fila la primera vez; si ya estaba publicada con ese tema, no cambia nada
	 * (ni la fecha ni el autor). Todo 2xx deja una fila en el registro de acciones de la escuela
	 * ({@code WEB TEMA_PUBLICADO organizacion=#n tema=CHANDAL primera=SI|NO}, más {@code sinCambios=SI} si no cambió).
	 *
	 * @param endpoint ruta llamada, para el registro
	 * @throws IllegalArgumentException (400) tema que no es uno de {@link TemasWeb#TODOS}
	 * @throws NoSuchElementException   (404) la escuela no existe
	 * @throws IllegalStateException    (409) la escuela tiene su web propia (Moiskimdo)
	 */
	public EstadoWebDTO publicar(Long organizacionId, String tema, String endpoint) {
		if (!TemasWeb.esValido(tema)) {
			throw new IllegalArgumentException(TemasWeb.TEMA_NO_VALIDO);
		}
		comprobarId(organizacionId);
		return enEscuela.escribir(organizacionId, () -> {
			comprobarPlantilla(organizaciones.findByIdParaActualizar(organizacionId)
					.orElseThrow(() -> new NoSuchElementException(NO_ENCONTRADA)));
			WebEscuela web = webs.findById(organizacionId).orElse(null);
			boolean primera = web == null;
			boolean sinCambios = !primera && web.isPublicada() && tema.equals(web.getTema());
			if (primera) {
				web = new WebEscuela();
				web.setOrganizacionId(organizacionId);
			}
			if (!sinCambios) {
				web.setTema(tema);
				web.setPublicada(true);
				web.setPublicadaEn(LocalDateTime.now().withNano(0));
				web.setPublicadaPor(CobroService.usuarioActualId());
				web = webs.save(web);
			}
			// Solo ids, el tema (de la lista cerrada) y sí/no.
			auditoria.registrarAccesoSensible(primera ? AuditoriaAccion.CREATE : AuditoriaAccion.UPDATE,
					MODULO_AUDITORIA, endpoint, organizacionId,
					"WEB TEMA_PUBLICADO organizacion=#" + organizacionId + " tema=" + TemasWeb.paraRegistro(tema)
							+ " primera=" + (primera ? "SI" : "NO") + (sinCambios ? " sinCambios=SI" : ""));
			return dto(web);
		});
	}

	private static void comprobarId(Long organizacionId) {
		if (organizacionId == null || organizacionId <= 0) {
			throw new NoSuchElementException(NO_ENCONTRADA);
		}
	}

	private static void comprobarPlantilla(Organizacion escuela) {
		if (Boolean.TRUE.equals(escuela.getComportamientoLegadoTaemoi())) {
			throw new IllegalStateException(WEB_PROPIA);
		}
	}

	private static EstadoWebDTO dto(WebEscuela web) {
		return web == null ? new EstadoWebDTO(null, false, null, TemasWeb.TODOS)
				: new EstadoWebDTO(web.getTema(), web.isPublicada(), web.getPublicadaEn(), TemasWeb.TODOS);
	}
}
