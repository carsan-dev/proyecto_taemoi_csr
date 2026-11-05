package com.taemoi.project.plataforma;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
 * propietario (invitación o, si el email ya tiene cuenta, solo el rol) y el catálogo de las disciplinas elegidas copiado
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
 * <p>Concurrencia: dos altas con el mismo nombre calculan el mismo slug; el índice único {@code uk_organizacion_slug}
 * hace esperar a la segunda hasta que la primera confirma y entonces falla: 409 como el nombre repetido.
 */
@Service
public class AltaEscuelaService {

	static final String RUTA = "/api/plataforma/escuelas";
	static final String MODULO_AUDITORIA = "plataforma";

	static final String NOMBRE_REPETIDO = "Ya hay una escuela con ese nombre.";
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
	 * @throws IllegalStateException    (409) ya hay una escuela con ese nombre (sin distinguir mayúsculas ni espacios
	 *                                  de los extremos)
	 */
	public AltaEscuelaDTO crear(AltaEscuelaRequest solicitud) {
		Datos datos = validar(solicitud);
		PlantillaCatalogo plantilla = plantillas.leer(plantillaId);
		PlantillaCatalogo.Seleccion seleccion = plantilla.seleccionar(datos.disciplinas());
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
		try {
			organizaciones.insertarConId(id, slug, datos.nombre(), datos.emailContacto(),
					new Organizacion().getZonaHoraria());
		} catch (DataIntegrityViolationException e) {
			// Otra alta con el mismo slug confirmó mientras tanto.
			throw new IllegalStateException(NOMBRE_REPETIDO);
		}

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

		copia.copiar(plantilla, seleccion);

		// Solo ids, sí/no y códigos de disciplina: nunca el nombre ni los emails.
		auditoria.registrarAccesoSensible(AuditoriaAccion.CREATE, MODULO_AUDITORIA, RUTA, id,
				"PLATAFORMA ESCUELA_CREADA organizacion=#" + id + " sede=#" + sede.getId() + " propietario=#"
						+ cuenta.getId() + " invitado=" + (invitado != null ? "SI" : "NO") + " disciplinas="
						+ seleccion.disciplinas().stream().map(PlantillaCatalogo.Disciplina::codigo)
								.collect(Collectors.joining(",")));
		return new AltaEscuelaDTO(id, datos.nombre(), invitado != null ? datos.emailPropietario() : null);
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

	/** Nombre para comparar: sin espacios en los extremos y en minúsculas. */
	private static String clave(String nombre) {
		return nombre == null ? "" : nombre.strip().toLowerCase(Locale.ROOT);
	}

	private static Map<String, Object> valoresRol(Long usuarioId) {
		Map<String, Object> valores = new LinkedHashMap<>();
		valores.put("usuario", usuarioId);
		valores.put("rol", RolMembresia.PROPIETARIO);
		return valores;
	}
}
