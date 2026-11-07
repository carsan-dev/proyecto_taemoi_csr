package com.taemoi.project.plataforma;

import java.sql.SQLException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.Membresia;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.RolMembresia;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.repositories.MembresiaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.InvitacionService;
import com.taemoi.project.services.RegistroAccionesMiembros;
import com.taemoi.project.utils.EmailUtils;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;

/**
 * Fase 4, pieza F4-2 (decisiones 390, 392 y 444): el superadmin da de alta una escuela: la escuela, su primera sede, su
 * propietario (invitación; si el email ya tiene cuenta, el rol, y si esa cuenta sigue con la invitación sin aceptar, de
 * esta escuela o de otra, además se le reenvía con esta escuela, como el reenvío del panel de miembros) y el catálogo de las disciplinas elegidas copiado
 * de la escuela plantilla ({@code app.plataforma.plantilla-organizacion-id}, por defecto 1). Cambia D3: el superadmin,
 * sin membresía, también crea escuelas; queda registrado en la escuela NUEVA.
 *
 * <p>TODO O NADA, en UNA transacción. Hibernate fija la escuela de una sesión al ABRIRLA ({@code ResolutorOrganizacion}),
 * y las filas de la escuela nueva ({@code @TenantId}) deben escribirse con su id: por eso el id se RESERVA antes
 * ({@link #reservarId}: un INSERT que se deshace; InnoDB no reutiliza un valor de AUTO_INCREMENT consumido, ni tras un
 * reinicio en MySQL 8) y la transacción del alta se abre ya con esa escuela ({@link TransaccionEnEscuela#escribir}).
 * Dentro: la escuela con ese id, la sede, la cuenta y la membresía, el catálogo y el registro. Cualquier fallo la deshace
 * entera y el correo de invitación, que sale tras confirmar ({@code InvitacionCorreoListener}), no se envía. Un id
 * reservado y no usado solo deja un hueco en la numeración.
 *
 * <p>Concurrencia: dos altas que calculan el mismo slug (mismo nombre, o nombres distintos con el mismo slug) o que crean
 * la misma cuenta de propietario chocan en un índice único: la segunda espera a que la primera confirme y falla, ya
 * deshecha. Entonces se repite el alta UNA vez ({@link #crear}), con otro id reservado: el mismo nombre da 409, otro
 * nombre recibe el slug con sufijo y el propietario, ya con cuenta, recibe el rol (y, si sigue pendiente, la invitación
 * reenviada con su escuela). Si la repetición vuelve a chocar
 * en un índice único (tres o más altas a la vez), 409 {@value #ALTA_A_LA_VEZ}: no se repite más.
 */
@Service
public class AltaEscuelaService {

	static final String RUTA = "/api/plataforma/escuelas";
	static final String MODULO_AUDITORIA = "plataforma";

	static final String NOMBRE_REPETIDO = "Ya hay una escuela con ese nombre.";
	static final String ALTA_A_LA_VEZ = "Otra alta de escuela ha coincidido con esta. Vuelve a intentarlo.";
	static final String NOMBRE_OBLIGATORIO = "El nombre de la escuela es obligatorio.";
	static final String NOMBRE_LARGO = "El nombre de la escuela no puede pasar de 150 caracteres.";
	static final String EMAIL_ESCUELA_OBLIGATORIO = "El email de la escuela es obligatorio.";
	static final String EMAIL_ESCUELA_NO_VALIDO = "El email de la escuela no es válido.";
	static final String SEDE_OBLIGATORIA = "El nombre de la sede es obligatorio.";
	static final String SEDE_LARGA = "El nombre de la sede no puede pasar de 100 caracteres.";
	static final String EMAIL_PROPIETARIO_OBLIGATORIO = "El email del propietario es obligatorio.";
	static final String EMAIL_PROPIETARIO_NO_VALIDO = "El email del propietario no es válido.";
	/** El mismo texto que {@code InvitacionService} para un alta sin cuenta sin nombre y apellidos. */
	static final String NOMBRE_Y_APELLIDOS = "Indica el nombre y los apellidos.";

	/** Columnas {@code organizacion.nombre}, {@code organizacion.email_contacto}, {@code sede.nombre} y {@code usuario.email}. */
	private static final int MAXIMO_NOMBRE = 150;
	private static final int MAXIMO_EMAIL_ESCUELA = 180;
	private static final int MAXIMO_SEDE = 100;
	private static final int MAXIMO_EMAIL = 255;
	/** MySQL {@code ER_DUP_ENTRY}: el choque con un índice único ({@code uk_organizacion_slug}, email, id). */
	private static final int ER_DUP_ENTRY = 1062;

	private final TransaccionEnEscuela enEscuela;
	private final PlantillaCatalogoLector plantillas;
	private final CopiaCatalogo copia;
	private final OrganizacionRepository organizaciones;
	private final SedeRepository sedes;
	private final UsuarioRepository usuarios;
	private final MembresiaRepository membresias;
	private final InvitacionService invitaciones;
	private final RegistroAccionesMiembros registroMiembros;
	private final AuditoriaService auditoria;
	private final Validator validador;
	private final Long plantillaId;

	public AltaEscuelaService(TransaccionEnEscuela enEscuela, PlantillaCatalogoLector plantillas, CopiaCatalogo copia,
			OrganizacionRepository organizaciones, SedeRepository sedes, UsuarioRepository usuarios,
			MembresiaRepository membresias, InvitacionService invitaciones, RegistroAccionesMiembros registroMiembros,
			AuditoriaService auditoria, Validator validador,
			@Value("${app.plataforma.plantilla-organizacion-id:1}") Long plantillaId) {
		this.enEscuela = enEscuela;
		this.plantillas = plantillas;
		this.copia = copia;
		this.organizaciones = organizaciones;
		this.sedes = sedes;
		this.usuarios = usuarios;
		this.membresias = membresias;
		this.invitaciones = invitaciones;
		this.registroMiembros = registroMiembros;
		this.auditoria = auditoria;
		this.validador = validador;
		this.plantillaId = plantillaId;
	}

	/** Las disciplinas que ofrece la plantilla, en el orden de su catálogo, con lo que se copiaría de cada una. */
	public List<DisciplinaPlantillaDTO> disciplinasDeLaPlantilla() {
		return plantillas.leer(plantillaId).resumen();
	}

	/**
	 * Da de alta la escuela.
	 *
	 * @throws IllegalArgumentException (400) datos que no valen o disciplina que no está en la plantilla
	 * @throws IllegalStateException    (409) ya hay una escuela con ese nombre (sin distinguir mayúsculas, tildes ni
	 *                                  espacios de los extremos), o la repetición tras un choque con otra alta a la vez
	 *                                  vuelve a chocar en un índice único
	 */
	public AltaEscuelaDTO crear(AltaEscuelaRequest solicitud) {
		Datos datos = validar(solicitud);
		PlantillaCatalogo plantilla = plantillas.leer(plantillaId);
		PlantillaCatalogo.Seleccion seleccion = plantilla.seleccionar(datos.disciplinas());
		try {
			return intentar(datos, plantilla, seleccion);
		} catch (DataIntegrityViolationException carrera) {
			// Choque con otra alta que confirmó mientras tanto (ya deshecho entero, sin correo): se repite UNA vez.
			try {
				return intentar(datos, plantilla, seleccion);
			} catch (DataIntegrityViolationException otraVez) {
				// La repetición choca otra vez (tres o más altas a la vez con el mismo slug o el mismo propietario
				// nuevo): 409 en vez de 500, ya deshecha y sin correo. Otro fallo de integridad se propaga como antes.
				if (esClaveRepetida(otraVez)) {
					throw new IllegalStateException(ALTA_A_LA_VEZ, otraVez);
				}
				throw otraVez;
			}
		}
	}

	/** Si el fallo de integridad es una clave única repetida (y no, por ejemplo, una clave ajena o un dato que no cabe). */
	private static boolean esClaveRepetida(DataIntegrityViolationException error) {
		if (error instanceof DuplicateKeyException) {
			return true;
		}
		for (Throwable causa = error; causa != null; causa = causa.getCause()) {
			if (causa instanceof SQLException sql && sql.getErrorCode() == ER_DUP_ENTRY) {
				return true;
			}
			if (causa.getCause() == causa) {
				break;
			}
		}
		return false;
	}

	/**
	 * Un intento completo: id reservado nuevo y UNA transacción. Un {@link DataIntegrityViolationException} sale con el
	 * intento ya deshecho; lo provocan otra alta que confirmó antes con el mismo slug (índice {@code uk_organizacion_slug})
	 * o con el mismo propietario nuevo (índice único de {@code usuario.email}), o un id reservado que ya existe (un
	 * contador de AUTO_INCREMENT movido entre la reserva y el alta). La repetición vuelve a mirar el nombre (409 solo si de
	 * verdad existe), recalcula el slug ({@code -2}…) y encuentra la cuenta del propietario (camino de la cuenta existente,
	 * sin correo). Si la repetición vuelve a chocar en un índice único, 409 (ver {@link #crear}).
	 */
	private AltaEscuelaDTO intentar(Datos datos, PlantillaCatalogo plantilla, PlantillaCatalogo.Seleccion seleccion) {
		Long id = reservarId();
		return enEscuela.escribir(id, () -> crearEn(id, datos, plantilla, seleccion));
	}

	/** Dentro de la transacción del alta, con la escuela {@code id} fijada. */
	private AltaEscuelaDTO crearEn(Long id, Datos datos, PlantillaCatalogo plantilla,
			PlantillaCatalogo.Seleccion seleccion) {
		String clave = clave(datos.nombre());
		if (organizaciones.findAll().stream().anyMatch(o -> clave.equals(clave(o.getNombre())))) {
			throw new IllegalStateException(NOMBRE_REPETIDO);
		}
		String slug = SlugEscuela.libre(datos.nombre(), s -> organizaciones.findBySlug(s).isPresent());
		// Un slug o un id ya ocupados por otra alta confirmada entretanto: DataIntegrityViolationException (ver intentar).
		organizaciones.insertarConId(id, slug, datos.nombre(), datos.emailContacto(), new Organizacion().getZonaHoraria());

		Sede sede = new Sede();
		sede.setNombre(datos.sede());
		sede.setActiva(true);
		sede = sedes.saveAndFlush(sede);

		// Propietario, como el alta de miembros (GestionMiembrosService#alta) pero sin exigir sesión de propietario.
		Usuario cuenta = usuarios.findByEmailIgnoreCase(datos.emailPropietario()).orElse(null);
		InvitacionService.Invitado invitado = null;
		if (cuenta == null) {
			invitado = InvitacionService.invitadoDe(datos.nombrePropietario(), datos.apellidosPropietario())
					.orElseThrow(() -> new IllegalArgumentException(NOMBRE_Y_APELLIDOS));
			cuenta = invitaciones.crearCuentaPendiente(datos.emailPropietario(), invitado, id, RolMembresia.PROPIETARIO);
		} else {
			usuarios.bloquearPorId(cuenta.getId());
		}
		membresias.saveAndFlush(new Membresia(cuenta.getId(), id, RolMembresia.PROPIETARIO));
		if (invitado != null) {
			registroMiembros.registrar(RegistroAccionesMiembros.Accion.MIEMBRO_INVITADO, RUTA,
					valoresRol(cuenta.getId()));
		}
		registroMiembros.registrar(RegistroAccionesMiembros.Accion.ROL_DADO, RUTA, valoresRol(cuenta.getId()));
		// Cuenta que aún no ha aceptado su invitación (de otra escuela, o de una alta a la vez con el mismo propietario):
		// sin contraseña no entra, y el rol nuevo no le llegaría. Como reenviar en el panel de miembros
		// (GestionMiembrosService#reenviarInvitacion): token nuevo con ESTA escuela (el anterior deja de valer) y el correo
		// tras confirmar. El UPDATE está condicionado a que siga pendiente: si la aceptó entretanto, solo el rol.
		boolean reenviada = invitado == null && cuenta.invitacionPendiente()
				&& invitaciones.renovarToken(cuenta, id, RolMembresia.PROPIETARIO);
		if (reenviada) {
			Map<String, Object> valores = new LinkedHashMap<>();
			valores.put("usuario", cuenta.getId());
			registroMiembros.registrar(RegistroAccionesMiembros.Accion.INVITACION_REENVIADA, RUTA, valores);
		}

		copia.copiar(plantilla, seleccion);

		// Solo ids, sí/no y códigos de disciplina: nunca el nombre ni los emails.
		auditoria.registrarAccesoSensible(AuditoriaAccion.CREATE, MODULO_AUDITORIA, RUTA, id,
				"PLATAFORMA ESCUELA_CREADA organizacion=#" + id + " sede=#" + sede.getId() + " propietario=#"
						+ cuenta.getId() + " invitado=" + (invitado != null ? "SI" : "NO") + " disciplinas="
						+ seleccion.disciplinas().stream().map(PlantillaCatalogo.Disciplina::codigo)
								.collect(Collectors.joining(",")));
		return new AltaEscuelaDTO(id, datos.nombre(),
				invitado != null || reenviada ? datos.emailPropietario() : null);
	}

	/**
	 * Reserva el id de la escuela nueva: inserta una fila provisional en una transacción propia y la deshace. El valor de
	 * AUTO_INCREMENT queda consumido (InnoDB no lo devuelve), así que nadie más lo recibe.
	 */
	private Long reservarId() {
		try {
			enEscuela.escribir(plantillaId, () -> {
				Organizacion provisional = new Organizacion();
				provisional.setSlug("reserva-" + UUID.randomUUID().toString().replace("-", ""));
				provisional.setNombre("Reserva de id");
				throw new IdReservado(organizaciones.saveAndFlush(provisional).getId());
			});
		} catch (IdReservado reservado) {
			return reservado.id;
		}
		throw new IllegalStateException("No se pudo reservar el id de la escuela.");
	}

	/** Lanza el id fuera de la transacción de la reserva, que así se deshace. */
	private static final class IdReservado extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final Long id;

		IdReservado(Long id) {
			super(null, null, false, false);
			this.id = id;
		}
	}

	// ------------------------------------------------------------------ validación

	private record Datos(String nombre, String emailContacto, String sede, String nombrePropietario,
			String apellidosPropietario, String emailPropietario, List<String> disciplinas) {
	}

	/** Para comprobar el {@code @Email} de Bean Validation (el mismo que {@code usuario.email}). */
	private record Correo(@Email String valor) {
	}

	private Datos validar(AltaEscuelaRequest solicitud) {
		AltaEscuelaRequest s = solicitud != null ? solicitud : new AltaEscuelaRequest(null, null, null, null, null);
		String nombre = recortado(s.nombre());
		if (nombre.isEmpty()) {
			throw new IllegalArgumentException(NOMBRE_OBLIGATORIO);
		}
		if (largo(nombre) > MAXIMO_NOMBRE) {
			throw new IllegalArgumentException(NOMBRE_LARGO);
		}
		String emailContacto = recortado(s.emailContacto());
		if (emailContacto.isEmpty()) {
			throw new IllegalArgumentException(EMAIL_ESCUELA_OBLIGATORIO);
		}
		if (largo(emailContacto) > MAXIMO_EMAIL_ESCUELA || !esEmail(emailContacto)) {
			throw new IllegalArgumentException(EMAIL_ESCUELA_NO_VALIDO);
		}
		String sede = recortado(s.sede() == null ? null : s.sede().nombre());
		if (sede.isEmpty()) {
			throw new IllegalArgumentException(SEDE_OBLIGATORIA);
		}
		if (largo(sede) > MAXIMO_SEDE) {
			throw new IllegalArgumentException(SEDE_LARGA);
		}
		AltaEscuelaRequest.Propietario propietario = s.propietario() != null ? s.propietario()
				: new AltaEscuelaRequest.Propietario(null, null, null);
		String email = EmailUtils.normalizeEmail(propietario.email());
		if (email == null || email.isEmpty()) {
			throw new IllegalArgumentException(EMAIL_PROPIETARIO_OBLIGATORIO);
		}
		if (largo(email) > MAXIMO_EMAIL || !esEmail(email)) {
			throw new IllegalArgumentException(EMAIL_PROPIETARIO_NO_VALIDO);
		}
		return new Datos(nombre, emailContacto, sede, propietario.nombre(), propietario.apellidos(), email,
				s.disciplinas());
	}

	private boolean esEmail(String valor) {
		return validador.validate(new Correo(valor)).isEmpty();
	}

	private static String recortado(String valor) {
		return valor == null ? "" : valor.strip();
	}

	private static int largo(String valor) {
		return valor.codePointCount(0, valor.length());
	}

	/**
	 * Nombre para comparar: sin espacios en los extremos, sin tildes ni diéresis ({@code ñ} cuenta como {@code n}, igual
	 * que en el slug) y en minúsculas. Así «Ñandú» y «Nandu» son el mismo nombre. No se hace en SQL: la columna no tiene
	 * índice único y la comparación se hace aquí, sobre los nombres ya guardados.
	 */
	private static String clave(String nombre) {
		String sinTildes = Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD).replaceAll("\\p{M}+",
				"");
		return sinTildes.strip().toLowerCase(Locale.ROOT);
	}

	private static Map<String, Object> valoresRol(Long usuarioId) {
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("usuario", usuarioId);
		valores.put("rol", RolMembresia.PROPIETARIO);
		return valores;
	}
}
