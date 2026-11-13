package com.taemoi.project.plataforma.importacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.taemoi.project.dtos.AlumnoDTO;
import com.taemoi.project.dtos.request.AlumnoDeporteCreacionDTO;
import com.taemoi.project.dtos.request.ConsentimientoRequest;
import com.taemoi.project.dtos.request.TutorRequest;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AuditoriaAccion;
import com.taemoi.project.entities.Disciplina;
import com.taemoi.project.entities.OrigenDato;
import com.taemoi.project.entities.RelacionFirmante;
import com.taemoi.project.entities.Tarifa;
import com.taemoi.project.entities.TipoConsentimiento;
import com.taemoi.project.organizacion.TransaccionEnEscuela;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.repositories.TarifaRepository;
import com.taemoi.project.services.AlumnoService;
import com.taemoi.project.services.AuditoriaService;
import com.taemoi.project.services.CatalogoDisciplinasService;
import com.taemoi.project.services.ConsentimientoService;
import com.taemoi.project.services.ContadorService;
import com.taemoi.project.services.GrupoService;
import com.taemoi.project.services.OpcionesAltaAlumno;
import com.taemoi.project.services.ProgresionGradosService;
import com.taemoi.project.services.TarifasService;
import com.taemoi.project.services.TutorService;
import com.taemoi.project.utils.NifUtils;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;

/**
 * Fase 4, pieza I1 (decisiones d556 y d558): carga inicial de los alumnos de una escuela a partir de la hoja maestra, por
 * el superadmin. Tres operaciones con el MISMO código:
 * <ul>
 * <li><b>Ensayo</b>: valida y escribe como la carga y deshace SIEMPRE la transacción; devuelve el informe.</li>
 * <li><b>Carga</b>: todo o nada. Con algún error no escribe nada (422 con el informe).</li>
 * <li><b>Hoja con errores</b>: un ensayo y el mismo libro con la columna {@code revisar} rellena y las celdas con error
 * marcadas (d558.6).</li>
 * </ul>
 *
 * <p>Transacción y escuela: todo pasa dentro de {@link TransaccionEnEscuela#escribir}, abierta ANTES de tocar ningún
 * repositorio (Hibernate fija la escuela al abrir la sesión), con los servicios del alta de siempre. Este servicio NO es
 * {@code @Transactional}. El ensayo se deshace lanzando {@link Deshecha} con el informe dentro: la acción de
 * {@code TransaccionEnEscuela} es un {@code Supplier} sin acceso al estado de la transacción, y es el patrón de
 * {@code AltaEscuelaService#reservarId}; cualquier otra excepción sigue su camino (no se confunde con un ensayo).
 *
 * <p>Concurrencia: la primera sentencia de la transacción bloquea el contador de expediente de la escuela
 * ({@link ContadorService#bloquearNumeroExpediente}), que todo alta bloquea igual. Dos cargas de la misma escuela se
 * ordenan: la segunda espera, y como su instantánea de lectura se toma después del bloqueo, ve los alumnos de la primera
 * y los cuenta como ya cargados. Sin migración (no hay índice único sobre la licencia).
 *
 * <p>Datos personales: ni el informe, ni el log, ni la auditoría llevan nombres, apellidos, DNI, teléfonos ni correos.
 * Un fallo al escribir una fila se informa sin su mensaje (podría citar un DNI).
 */
@Service
public class ImportacionAlumnosService {

	private static final Logger log = LoggerFactory.getLogger(ImportacionAlumnosService.class);

	static final String MODULO_AUDITORIA = "plataforma";
	static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
	static final String ESCUELA_NO_ENCONTRADA = "Escuela no encontrada.";
	static final String FILA_NO_ESCRITA = "No se ha podido crear el alumno de esta fila. Avisa al administrador.";

	private final LectorHojaAlumnos lector;
	private final TransaccionEnEscuela enEscuela;
	private final OrganizacionRepository organizaciones;
	private final ContadorService contadores;
	private final CatalogoDisciplinasService disciplinas;
	private final ProgresionGradosService grados;
	private final SedeRepository sedes;
	private final GrupoRepository grupos;
	private final TarifaRepository tarifas;
	private final TarifasService tarifasService;
	private final AlumnoRepository alumnos;
	private final AlumnoDeporteRepository inscripciones;
	private final AlumnoService altas;
	private final TutorService tutores;
	private final ConsentimientoService consentimientos;
	private final GrupoService grupoService;
	private final AuditoriaService auditoria;
	private final Validator validador;

	public ImportacionAlumnosService(LectorHojaAlumnos lector, TransaccionEnEscuela enEscuela,
			OrganizacionRepository organizaciones, ContadorService contadores, CatalogoDisciplinasService disciplinas,
			ProgresionGradosService grados, SedeRepository sedes, GrupoRepository grupos, TarifaRepository tarifas,
			TarifasService tarifasService, AlumnoRepository alumnos, AlumnoDeporteRepository inscripciones,
			AlumnoService altas, TutorService tutores, ConsentimientoService consentimientos, GrupoService grupoService,
			AuditoriaService auditoria, Validator validador) {
		this.lector = lector;
		this.enEscuela = enEscuela;
		this.organizaciones = organizaciones;
		this.contadores = contadores;
		this.disciplinas = disciplinas;
		this.grados = grados;
		this.sedes = sedes;
		this.grupos = grupos;
		this.tarifas = tarifas;
		this.tarifasService = tarifasService;
		this.alumnos = alumnos;
		this.inscripciones = inscripciones;
		this.altas = altas;
		this.tutores = tutores;
		this.consentimientos = consentimientos;
		this.grupoService = grupoService;
		this.auditoria = auditoria;
		this.validador = validador;
	}

	/** Resultado de una carga: el informe y si se rechazó por errores (422, nada escrito). */
	public record ResultadoCarga(InformeImportacion informe, boolean rechazada) {
	}

	/**
	 * Ensayo: el informe de lo que haría la carga; no deja nada escrito.
	 *
	 * @throws IllegalArgumentException (400) el fichero no es la hoja de alumnos, o la disciplina no vale
	 * @throws NoSuchElementException   (404) la escuela no existe
	 */
	public InformeImportacion ensayar(Long organizacionId, MultipartFile fichero, String disciplina) {
		HojaAlumnos hoja = lector.leer(fichero);
		InformeImportacion informe = ensayar(organizacionId, hoja, disciplina);
		registrarConsulta(organizacionId, "ensayo", "PLATAFORMA IMPORTACION_ALUMNOS_ENSAYO", informe);
		return informe;
	}

	/**
	 * Carga: todo o nada. Sin errores, crea los alumnos y deja un apunte de auditoría en la escuela; con alguno, no
	 * escribe nada y {@link ResultadoCarga#rechazada()}.
	 *
	 * @throws IllegalArgumentException (400) el fichero no es la hoja de alumnos, o la disciplina no vale
	 * @throws NoSuchElementException   (404) la escuela no existe
	 */
	public ResultadoCarga cargar(Long organizacionId, MultipartFile fichero, String disciplina) {
		HojaAlumnos hoja = lector.leer(fichero);
		exigirEscuela(organizacionId);
		try {
			InformeImportacion informe = enEscuela.escribir(organizacionId, () -> {
				Procesado procesado = procesar(hoja, disciplina, true);
				InformeImportacion hecho = procesado.informe();
				if (hecho.conErrores()) {
					throw new Deshecha(hecho);
				}
				auditoria.registrarAccesoSensible(AuditoriaAccion.CREATE, MODULO_AUDITORIA,
						ruta(organizacionId, "carga"), organizacionId,
						"PLATAFORMA IMPORTACION_ALUMNOS organizacion=#" + organizacionId + " disciplina="
								+ procesado.disciplina() + " filas=" + hecho.filas() + " creados=" + hecho.creados()
								+ " yaCargados=" + hecho.yaCargados() + " avisos=" + hecho.avisos().size());
				return hecho;
			});
			registrarEnLog(organizacionId, "carga", informe);
			return new ResultadoCarga(informe, false);
		} catch (Deshecha rechazo) {
			registrarEnLog(organizacionId, "carga rechazada", rechazo.informe);
			return new ResultadoCarga(rechazo.informe, true);
		}
	}

	/**
	 * La hoja subida con la columna {@code revisar} y las celdas con error marcadas, a partir de un ensayo. No deja nada
	 * escrito ni guardado.
	 */
	public HojaRevisada hojaConErrores(Long organizacionId, MultipartFile fichero, String disciplina) {
		HojaAlumnos hoja = lector.leer(fichero);
		InformeImportacion informe = ensayar(organizacionId, hoja, disciplina);
		HojaRevisada revisada = HojaRevisada.de(hoja, informe);
		registrarConsulta(organizacionId, "hoja-con-errores", "PLATAFORMA IMPORTACION_ALUMNOS_HOJA_REVISAR", informe);
		return revisada;
	}

	// ------------------------------------------------------------------ ensayo y proceso

	private InformeImportacion ensayar(Long organizacionId, HojaAlumnos hoja, String disciplina) {
		exigirEscuela(organizacionId);
		try {
			enEscuela.escribir(organizacionId, () -> {
				throw new Deshecha(procesar(hoja, disciplina, false).informe());
			});
		} catch (Deshecha ensayo) {
			registrarEnLog(organizacionId, "ensayo", ensayo.informe);
			return ensayo.informe;
		}
		throw new IllegalStateException("El ensayo no se ha deshecho.");
	}

	private record Procesado(InformeImportacion informe, String disciplina) {
	}

	/**
	 * Dentro de la transacción de la escuela: bloquea, lee la escuela, valida y, sin errores, escribe cada fila lista.
	 * En el ensayo ({@code carga = false}) se escribe igual (así salen también los fallos de escritura) y el informe
	 * lleva {@code creados = 0}.
	 */
	private Procesado procesar(HojaAlumnos hoja, String disciplinaPedida, boolean carga) {
		// PRIMERA sentencia: el bloqueo (ver la cabecera). Nada se lee antes.
		contadores.bloquearNumeroExpediente();
		String disciplina = disciplina(disciplinaPedida);
		Map<String, Tarifa> tarifasPorCodigo = new HashMap<>();
		ContextoEscuela escuela = contexto(disciplina, tarifasPorCodigo);
		LocalDate hoy = LocalDate.now(ZONA);
		List<FilaValidada> validadas = new ValidacionFilas(escuela, hoy).validar(hoja.filas());

		List<Incidencia> errores = validadas.stream().flatMap(f -> f.errores().stream()).toList();
		List<Incidencia> avisos = validadas.stream().flatMap(f -> f.avisos().stream()).toList();
		int listas = (int) validadas.stream().filter(FilaValidada::lista).count();
		List<InformeImportacion.FilaYaCargada> yaCargadas = validadas.stream().filter(FilaValidada::yaCargada)
				.map(f -> new InformeImportacion.FilaYaCargada(f.fila(), f.numeroFederacion())).toList();
		InformeImportacion informe = new InformeImportacion(validadas.size(), listas, 0, yaCargadas.size(), errores,
				avisos, yaCargadas);
		if (!errores.isEmpty()) {
			return new Procesado(informe, disciplina);
		}
		int creados = 0;
		for (FilaValidada fila : validadas) {
			if (fila.lista()) {
				escribir(fila, disciplina, tarifasPorCodigo, hoy, informe);
				creados++;
			}
		}
		return new Procesado(carga ? informe.conCreados(creados) : informe, disciplina);
	}

	/**
	 * La disciplina pedida o, sin pedirla, la única activa de la escuela.
	 *
	 * @throws IllegalArgumentException (400) no existe, no tiene grados, o la escuela tiene varias y no se ha dicho cuál
	 */
	private String disciplina(String pedida) {
		String codigo;
		if (pedida != null && !pedida.isBlank()) {
			String limpia = pedida.strip();
			codigo = disciplinas.buscarPorCodigo(limpia).map(Disciplina::getCodigo).orElseThrow(
					() -> new IllegalArgumentException("La disciplina «" + ConversionGrados.citado(limpia)
							+ "» no existe en esta escuela."));
		} else {
			List<Disciplina> activas = disciplinas.listarActivas();
			if (activas.isEmpty()) {
				throw new IllegalArgumentException("La escuela no tiene ninguna disciplina activa.");
			}
			if (activas.size() > 1) {
				throw new IllegalArgumentException("La escuela tiene varias disciplinas: indica cuál con el parámetro "
						+ "disciplina (" + activas.stream().map(Disciplina::getCodigo).collect(Collectors.joining(", "))
						+ ").");
			}
			codigo = activas.get(0).getCodigo();
		}
		if (!grados.tieneProgresion(codigo)) {
			throw new IllegalArgumentException("La disciplina «" + codigo + "» no tiene sistema de grados.");
		}
		return codigo;
	}

	/** La escuela destino tal como la ve su transacción ({@code @TenantId}): nada de otra escuela. */
	private ContextoEscuela contexto(String disciplina, Map<String, Tarifa> tarifasPorCodigo) {
		List<ContextoEscuela.SedeRef> activas = sedes.findByActivaTrueOrderByIdAsc().stream()
				.map(s -> new ContextoEscuela.SedeRef(s.getId(), s.getNombre())).toList();
		List<ContextoEscuela.GrupoRef> deLaEscuela = grupos.findAll().stream()
				.map(g -> new ContextoEscuela.GrupoRef(g.getId(), g.getNombre(), g.getSedeId())).toList();
		List<ContextoEscuela.TarifaRef> tarifasRef = tarifas.findAllByOrderByOrdenAscIdAsc().stream().map(t -> {
			tarifasPorCodigo.put(t.getCodigo(), t);
			return new ContextoEscuela.TarifaRef(t.getCodigo(), t.getNombre(),
					t.getDisciplina() == null ? null : t.getDisciplina().getCodigo(), !Boolean.FALSE.equals(t.getActiva()),
					tarifasService.requiereRolFamiliar(t) || tarifasService.requiereGrupoFamiliar(t));
		}).toList();
		Map<String, Boolean> gradosVistos = new HashMap<>();
		Set<String> nifs = alumnos.nifsEnOrganizacionActiva().stream().map(NifUtils::normalizeForComparison)
				.filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
		Set<Integer> licencias = new HashSet<>(inscripciones.numerosLicenciaDeDisciplina(disciplina));
		return new ContextoEscuela(disciplina, activas, deLaEscuela, tarifasRef,
				codigo -> gradosVistos.computeIfAbsent(codigo, c -> grados.buscarGrado(disciplina, c).isPresent()),
				nifs, licencias, this::esEmail);
	}

	/** Una fila lista, con el camino del alta de siempre (sin consentimiento automático ni mensualidad). */
	private void escribir(FilaValidada fila, String disciplina, Map<String, Tarifa> tarifasPorCodigo, LocalDate hoy,
			InformeImportacion informe) {
		DatosAlumno d = fila.datos();
		try {
			AlumnoDTO alumno = new AlumnoDTO();
			alumno.setNombre(d.nombre());
			alumno.setApellidos(d.apellidos());
			alumno.setFechaNacimiento(java.sql.Date.valueOf(d.fechaNacimiento()));
			alumno.setNif(d.nif());
			alumno.setTelefono(d.telefono());
			alumno.setTelefono2(d.telefono2());
			alumno.setEmail(d.email());
			alumno.setDireccion(d.direccion());
			alumno.setLocalidad(d.localidad());
			alumno.setCodigoPostal(d.codigoPostal());
			alumno.setObservaciones(d.observaciones());
			alumno.setSedePrincipalId(d.sedeId());
			alumno.setFechaAlta(java.sql.Date.valueOf(hoy));
			// Sin registrar = sin autorización; SI/NO lo fija después su consentimiento.
			alumno.setAutorizacionWeb(d.usoImagen() == DatosAlumno.UsoImagen.SI);

			AlumnoDeporteCreacionDTO deporte = new AlumnoDeporteCreacionDTO();
			deporte.setDeporte(disciplina);
			deporte.setGrado(d.grado());
			deporte.setFechaAlta(java.sql.Date.valueOf(hoy));
			deporte.setTieneLicencia(true);
			deporte.setNumeroLicencia(d.numeroLicencia());
			deporte.setFechaLicencia(d.fechaLicencia() == null ? null : java.sql.Date.valueOf(d.fechaLicencia()));
			if (d.tarifa() != null) {
				Tarifa tarifa = tarifasPorCodigo.get(d.tarifa());
				// El importe lo calcula el servidor (en el alta del panel lo calcula el formulario).
				BigDecimal importe = tarifasService.calcularImporte(tarifa, null);
				deporte.setCodigoTarifa(tarifa.getCodigo());
				deporte.setCuantiaTarifa(importe == null ? null : importe.doubleValue());
			}
			alumno.setDeportesInicial(List.of(deporte));

			Alumno creado = altas.crearAlumnoDesdeDTO(alumno, OpcionesAltaAlumno.IMPORTACION);

			if (d.tutorNombre() != null) {
				tutores.crear(creado.getId(), new TutorRequest(d.tutorNombre(), null, d.tutorNif(), d.tutorParentesco(),
						null, null, d.menor(), null));
			}
			if (d.usoImagen() != DatosAlumno.UsoImagen.SIN_REGISTRAR) {
				consentimientos.registrar(creado.getId(),
						new ConsentimientoRequest(TipoConsentimiento.USO_IMAGEN, d.usoImagen() == DatosAlumno.UsoImagen.SI,
								hoy, true, null, d.menor() ? RelacionFirmante.TUTOR : RelacionFirmante.PROPIO,
								OrigenDato.FICHA_PAPEL, null));
			}
			if (d.grupoId() != null) {
				grupoService.agregarAlumnoAGrupo(d.grupoId(), creado.getId());
			}
		} catch (AccessDeniedException | ConcurrencyFailureException e) {
			// 403 y 409 de siempre (bloqueo agotado, interbloqueo): no son un problema de la fila.
			throw e;
		} catch (RuntimeException e) {
			// Sin el mensaje: el de un alta duplicada, por ejemplo, cita el NIF.
			log.warn("Importación de alumnos: no se pudo escribir la fila {} ({})", fila.fila(),
					e.getClass().getSimpleName());
			throw new Deshecha(informe.conError(new Incidencia(fila.fila(), fila.numeroFederacion(), null,
					FILA_NO_ESCRITA)));
		}
	}

	// ------------------------------------------------------------------ apoyo

	/** Para comprobar el {@code @Email} de Bean Validation (el mismo que {@code alumno.email}). */
	private record Correo(@Email String valor) {
	}

	private boolean esEmail(String valor) {
		return validador.validate(new Correo(valor)).isEmpty();
	}

	/** @throws NoSuchElementException (404) la escuela no existe */
	private void exigirEscuela(Long organizacionId) {
		if (organizacionId == null || organizacionId <= 0
				|| !enEscuela.leer(organizacionId, () -> organizaciones.existsById(organizacionId))) {
			throw new NoSuchElementException(ESCUELA_NO_ENCONTRADA);
		}
	}

	/**
	 * El ensayo y la hoja con errores no escriben, pero leen datos de menores: su acceso queda en la escuela destino como
	 * lectura (READ), solo con recuentos, en una transacción propia tras deshacer el ensayo.
	 */
	private void registrarConsulta(Long organizacionId, String operacion, String accion, InformeImportacion informe) {
		enEscuela.escribir(organizacionId, () -> {
			auditoria.registrarAccesoSensible(AuditoriaAccion.READ, "POST", MODULO_AUDITORIA,
					ruta(organizacionId, operacion), organizacionId,
					accion + " organizacion=#" + organizacionId + " filas=" + informe.filas() + " listas="
							+ informe.listas() + " yaCargados=" + informe.yaCargados() + " errores="
							+ informe.errores().size() + " avisos=" + informe.avisos().size());
			return null;
		});
	}

	private static void registrarEnLog(Long organizacionId, String operacion, InformeImportacion informe) {
		log.info("Importación de alumnos ({}) en la escuela {}: filas={} listas={} creados={} yaCargados={} errores={} "
				+ "avisos={}", operacion, organizacionId, informe.filas(), informe.listas(), informe.creados(),
				informe.yaCargados(), informe.errores().size(), informe.avisos().size());
	}

	static String ruta(Long organizacionId, String operacion) {
		return "/api/plataforma/escuelas/" + organizacionId + "/importacion/alumnos/" + operacion;
	}

	/** Lleva el informe fuera de la transacción, que así se deshace (ensayo, carga con errores o fila que falla). */
	private static final class Deshecha extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final transient InformeImportacion informe;

		Deshecha(InformeImportacion informe) {
			super(null, null, false, false);
			this.informe = informe;
		}
	}
}
