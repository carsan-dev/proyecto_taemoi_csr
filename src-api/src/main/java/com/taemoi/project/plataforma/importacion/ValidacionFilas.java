package com.taemoi.project.plataforma.importacion;

import static com.taemoi.project.plataforma.importacion.ColumnasHoja.APELLIDOS;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.AUTORIZACION_IMAGEN;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.CODIGO_POSTAL;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.DIRECCION;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.DNI_NIE;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.EMAIL_FAMILIA;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.FECHA_LICENCIA;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.FECHA_NACIMIENTO;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.GRADO;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.GRUPO;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.LOCALIDAD;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.NOMBRE;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.NUMERO_FEDERACION;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.OBSERVACIONES;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.SEDE;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.TARIFA;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.TELEFONO;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.TELEFONO2;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.TUTOR_DNI;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.TUTOR_NOMBRE;
import static com.taemoi.project.plataforma.importacion.ColumnasHoja.TUTOR_PARENTESCO;
import static com.taemoi.project.plataforma.importacion.ContextoEscuela.clave;
import static com.taemoi.project.plataforma.importacion.ConversionGrados.citado;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.taemoi.project.entities.ParentescoTutor;
import com.taemoi.project.utils.EmailUtils;
import com.taemoi.project.utils.NifUtils;
import com.taemoi.project.utils.TelefonoUtils;

/**
 * Reglas por fila del importador (fase4-importacion-plan.md §4.2 y §4.3), sin base de datos: la escuela llega ya leída en
 * un {@link ContextoEscuela}. Los motivos solo citan valores de catálogo (sede, grupo, grado, tarifa), nunca datos
 * personales.
 */
final class ValidacionFilas {

	static final String DIRECCION_PENDIENTE = "Pendiente";

	static final String AVISO_SIN_DIRECCION = "Sin dirección: entrará como «Pendiente».";
	static final String AVISO_SIN_TARIFA = "Sin tarifa: no tendrá importe de mensualidad.";
	static final String AVISO_SIN_GRUPO = "Sin grupo: no estará en ningún turno.";
	static final String AVISO_SIN_CORREO = "Sin correo: la familia no podrá crear su cuenta.";
	static final String AVISO_ADULTO_SIN_DNI = "Adulto sin DNI/NIE: añádelo cuando lo tengas.";

	static final String FALTA_TELEFONO = "Falta el teléfono.";
	static final String ERROR_DE_FORMULA = "La celda tiene un error de fórmula: escribe el valor.";

	/** Las columnas que se leen; las de la federación ({@code nombre_federacion}, {@code menor}…) solo son de consulta. */
	private static final List<String> USADAS = List.of(NUMERO_FEDERACION, FECHA_NACIMIENTO, FECHA_LICENCIA, NOMBRE,
			APELLIDOS, DNI_NIE, GRADO, SEDE, GRUPO, EMAIL_FAMILIA, TELEFONO, TELEFONO2, DIRECCION, LOCALIDAD,
			CODIGO_POSTAL, TUTOR_NOMBRE, TUTOR_PARENTESCO, TUTOR_DNI, TARIFA, AUTORIZACION_IMAGEN, OBSERVACIONES);

	private static final Pattern DNI_NIE_VALIDO = Pattern.compile("[0-9XYZ][0-9]{7}[A-Z]");
	private static final Pattern CIFRAS = Pattern.compile("[0-9]{1,10}");
	/** Columnas VARCHAR(255) de alumno; {@code localidad} 100, {@code codigo_postal} 10, {@code tutor.nombre} 180. */
	private static final int MAXIMO_TEXTO = 255;
	private static final int MAXIMO_LOCALIDAD = 100;
	private static final int MAXIMO_CODIGO_POSTAL = 10;
	private static final int MAXIMO_TUTOR = 180;
	private static final int MAYORIA_DE_EDAD = 18;

	private final ContextoEscuela escuela;
	private final LocalDate hoy;

	ValidacionFilas(ContextoEscuela escuela, LocalDate hoy) {
		this.escuela = escuela;
		this.hoy = hoy;
	}

	List<FilaValidada> validar(List<FilaHoja> filas) {
		List<Revision> revisiones = filas.stream().map(this::revisar).toList();
		repetidas(revisiones, r -> r.numero == null ? null : r.numero.toString(), NUMERO_FEDERACION,
				"El número de federación está repetido en la fila ");
		repetidas(revisiones, r -> r.nif, DNI_NIE, "El DNI/NIE está repetido en la fila ");
		return revisiones.stream().map(Revision::cerrar).toList();
	}

	// ------------------------------------------------------------------ una fila

	private Revision revisar(FilaHoja fila) {
		Revision r = new Revision(fila);
		r.numero = numeroFederacion(r);
		if (r.numero != null && escuela.licenciasExistentes().contains(r.numero)) {
			r.yaCargada = true;
			return r;
		}
		for (String columna : USADAS) {
			if (r.conError(columna)) {
				r.error(columna, ERROR_DE_FORMULA);
			}
		}
		String nombre = obligatorio(r, NOMBRE, "Falta el nombre.");
		largo(r, NOMBRE, nombre, MAXIMO_TEXTO, "El nombre es demasiado largo: máximo 255 caracteres.");
		String apellidos = obligatorio(r, APELLIDOS, "Faltan los apellidos.");
		largo(r, APELLIDOS, apellidos, MAXIMO_TEXTO, "Los apellidos son demasiado largos: máximo 255 caracteres.");

		LocalDate nacimiento = fechaNacimiento(r);
		boolean menor = nacimiento != null && Period.between(nacimiento, hoy).getYears() < MAYORIA_DE_EDAD;

		r.nif = dni(r, DNI_NIE, "El DNI/NIE no es válido.");
		if (r.nif != null && escuela.nifsExistentes().contains(r.nif)) {
			r.error(DNI_NIE, "Ya hay otro alumno de la escuela con este DNI/NIE.");
		}
		if (r.nif == null && !r.conError(DNI_NIE) && nacimiento != null && !menor) {
			r.aviso(DNI_NIE, AVISO_ADULTO_SIN_DNI);
		}

		String grado = grado(r, menor);
		ContextoEscuela.SedeRef sede = sede(r);
		Long grupo = grupo(r, sede);

		String email = EmailUtils.normalizeEmail(r.texto(EMAIL_FAMILIA));
		if (email != null && email.isEmpty() && !r.conError(EMAIL_FAMILIA)) {
			r.aviso(EMAIL_FAMILIA, AVISO_SIN_CORREO);
		} else if (email != null && !email.isEmpty()
				&& (email.length() > MAXIMO_TEXTO || !escuela.esEmail().test(email))) {
			r.error(EMAIL_FAMILIA, "El correo de la familia no es válido.");
		}

		String telefono = obligatorio(r, TELEFONO, FALTA_TELEFONO);
		if (!telefono.isEmpty() && !TelefonoUtils.esValido(telefono)) {
			r.error(TELEFONO, "El teléfono no es válido: escribe de 9 a 15 cifras.");
		}
		String telefono2 = r.texto(TELEFONO2);
		if (!telefono2.isEmpty() && !TelefonoUtils.esValido(telefono2)) {
			r.error(TELEFONO2, "El segundo teléfono no es válido: escribe de 9 a 15 cifras.");
		}

		String direccion = r.texto(DIRECCION);
		if (direccion.isEmpty() && !r.conError(DIRECCION)) {
			r.aviso(DIRECCION, AVISO_SIN_DIRECCION);
			direccion = DIRECCION_PENDIENTE;
		}
		largo(r, DIRECCION, direccion, MAXIMO_TEXTO, "La dirección es demasiado larga: máximo 255 caracteres.");
		String localidad = r.texto(LOCALIDAD);
		largo(r, LOCALIDAD, localidad, MAXIMO_LOCALIDAD, "La localidad es demasiado larga: máximo 100 caracteres.");
		String codigoPostal = r.texto(CODIGO_POSTAL);
		largo(r, CODIGO_POSTAL, codigoPostal, MAXIMO_CODIGO_POSTAL,
				"El código postal es demasiado largo: máximo 10 caracteres.");

		String tutorNombre = r.texto(TUTOR_NOMBRE);
		largo(r, TUTOR_NOMBRE, tutorNombre, MAXIMO_TUTOR,
				"El nombre del tutor es demasiado largo: máximo 180 caracteres.");
		ParentescoTutor parentesco = parentesco(r);
		String tutorNif = dni(r, TUTOR_DNI, "El DNI del tutor no es válido.");
		if (tutorNombre.isEmpty() && !r.conError(TUTOR_NOMBRE)
				&& (!r.texto(TUTOR_PARENTESCO).isEmpty() || !r.texto(TUTOR_DNI).isEmpty())) {
			r.error(TUTOR_NOMBRE, "Falta el nombre del tutor.");
		}

		String tarifa = tarifa(r);
		DatosAlumno.UsoImagen usoImagen = usoImagen(r);
		LocalDate fechaLicencia = fechaOpcional(r, FECHA_LICENCIA,
				"La fecha de licencia no es válida: escríbela como DD/MM/AAAA.");
		String observaciones = r.texto(OBSERVACIONES);

		if (r.errores.isEmpty()) {
			r.datos = new DatosAlumno(r.numero, nombre, apellidos, nacimiento, menor, r.nif, TelefonoUtils.normalizar(
					telefono), TelefonoUtils.normalizar(telefono2), email == null || email.isEmpty() ? null : email,
					direccion, vacioANulo(localidad), vacioANulo(codigoPostal), vacioANulo(observaciones), sede.id(),
					grupo, grado, tarifa, fechaLicencia, vacioANulo(tutorNombre), parentesco, tutorNif, usoImagen);
		}
		return r;
	}

	private Integer numeroFederacion(Revision r) {
		String texto = obligatorio(r, NUMERO_FEDERACION, "Falta el número de federación.");
		if (texto.isEmpty()) {
			return null;
		}
		if (CIFRAS.matcher(texto).matches()) {
			long numero = Long.parseLong(texto);
			if (numero > 0 && numero <= Integer.MAX_VALUE) {
				return (int) numero;
			}
		}
		r.error(NUMERO_FEDERACION, "El número de federación no es válido: escribe un número entero positivo.");
		return null;
	}

	private LocalDate fechaNacimiento(Revision r) {
		Valor valor = r.valor(FECHA_NACIMIENTO);
		if (r.conError(FECHA_NACIMIENTO)) {
			return null;
		}
		if (valor.vacio()) {
			r.error(FECHA_NACIMIENTO, "Falta la fecha de nacimiento.");
			return null;
		}
		LocalDate fecha = CeldaExcel.fecha(valor);
		if (fecha == null) {
			r.error(FECHA_NACIMIENTO, "La fecha de nacimiento no es válida: escríbela como DD/MM/AAAA.");
			return null;
		}
		if (fecha.isAfter(hoy)) {
			r.error(FECHA_NACIMIENTO, "La fecha de nacimiento no puede ser futura.");
			return null;
		}
		return fecha;
	}

	private LocalDate fechaOpcional(Revision r, String columna, String noValida) {
		Valor valor = r.valor(columna);
		if (r.conError(columna) || valor.vacio()) {
			return null;
		}
		LocalDate fecha = CeldaExcel.fecha(valor);
		if (fecha == null) {
			r.error(columna, noValida);
		}
		return fecha;
	}

	/** DNI/NIE en forma canónica (mayúsculas, sin espacios ni guiones), o {@code null} si está vacío o no vale. */
	private static String dni(Revision r, String columna, String noValido) {
		String canonico = NifUtils.normalizeForComparison(r.texto(columna));
		if (canonico == null) {
			return null;
		}
		if (!DNI_NIE_VALIDO.matcher(canonico).matches()) {
			r.error(columna, noValido);
			return null;
		}
		return canonico;
	}

	private String grado(Revision r, boolean menor) {
		if (r.conError(GRADO)) {
			return null;
		}
		String texto = r.texto(GRADO);
		ConversionGrados.Resultado conversion = ConversionGrados.convertir(texto, menor);
		if (conversion.error() != null) {
			r.error(GRADO, conversion.error());
			return null;
		}
		if (!escuela.gradoExiste().test(conversion.codigo())) {
			r.error(GRADO, "El grado «" + citado(texto.isEmpty() ? conversion.codigo() : texto)
					+ "» no existe en esta disciplina.");
			return null;
		}
		if (conversion.aviso() != null) {
			r.aviso(GRADO, conversion.aviso());
		}
		return conversion.codigo();
	}

	private ContextoEscuela.SedeRef sede(Revision r) {
		String texto = obligatorio(r, SEDE, "Falta la sede.");
		if (texto.isEmpty()) {
			return null;
		}
		String buscada = clave(texto);
		for (ContextoEscuela.SedeRef sede : escuela.sedes()) {
			if (buscada.equals(clave(sede.nombre()))) {
				return sede;
			}
		}
		r.error(SEDE, "«" + citado(texto) + "» no es una sede de esta escuela.");
		return null;
	}

	private Long grupo(Revision r, ContextoEscuela.SedeRef sede) {
		if (r.conError(GRUPO)) {
			return null;
		}
		String texto = r.texto(GRUPO);
		if (texto.isEmpty()) {
			r.aviso(GRUPO, AVISO_SIN_GRUPO);
			return null;
		}
		if (sede == null) {
			return null; // la sede ya tiene su error
		}
		String buscado = clave(texto);
		List<Long> encontrados = escuela.grupos().stream()
				.filter(g -> sede.id().equals(g.sedeId()) && buscado.equals(clave(g.nombre())))
				.map(ContextoEscuela.GrupoRef::id).toList();
		if (encontrados.isEmpty()) {
			r.error(GRUPO, "No hay ningún grupo «" + citado(texto) + "» en " + sede.nombre() + ".");
			return null;
		}
		if (encontrados.size() > 1) {
			r.error(GRUPO, "Hay varios grupos «" + citado(texto) + "» en " + sede.nombre()
					+ ": deja solo uno con ese nombre.");
			return null;
		}
		return encontrados.get(0);
	}

	private ParentescoTutor parentesco(Revision r) {
		if (r.conError(TUTOR_PARENTESCO)) {
			return null;
		}
		String texto = r.texto(TUTOR_PARENTESCO);
		String limpio = texto.toUpperCase(Locale.ROOT).replaceAll("[\\s_]+", " ");
		switch (limpio) {
		case "":
		case "OTRO":
			return ParentescoTutor.OTRO;
		case "PADRE":
			return ParentescoTutor.PADRE;
		case "MADRE":
			return ParentescoTutor.MADRE;
		case "TUTOR LEGAL":
			return ParentescoTutor.TUTOR_LEGAL;
		default:
			r.error(TUTOR_PARENTESCO,
					"«" + citado(texto) + "» no es un parentesco válido: usa PADRE, MADRE, TUTOR LEGAL u OTRO.");
			return null;
		}
	}

	/** Código de la tarifa: por código y, si no, por nombre (sin distinguir mayúsculas ni espacios sobrantes). */
	private String tarifa(Revision r) {
		if (r.conError(TARIFA)) {
			return null;
		}
		String texto = r.texto(TARIFA);
		if (texto.isEmpty()) {
			r.aviso(TARIFA, AVISO_SIN_TARIFA);
			return null;
		}
		String buscada = clave(texto);
		List<ContextoEscuela.TarifaRef> porCodigo = escuela.tarifas().stream()
				.filter(t -> buscada.equals(clave(t.codigo()))).toList();
		List<ContextoEscuela.TarifaRef> candidatas = porCodigo.isEmpty()
				? escuela.tarifas().stream().filter(t -> buscada.equals(clave(t.nombre()))).toList()
				: porCodigo;
		if (candidatas.isEmpty()) {
			r.error(TARIFA, "«" + citado(texto) + "» no es una tarifa de esta escuela.");
			return null;
		}
		if (candidatas.size() > 1) {
			r.error(TARIFA, "Hay varias tarifas «" + citado(texto) + "»: usa su código.");
			return null;
		}
		ContextoEscuela.TarifaRef tarifa = candidatas.get(0);
		if (tarifa.disciplina() != null && !tarifa.disciplina().equals(escuela.disciplina())) {
			r.error(TARIFA, "La tarifa «" + citado(texto) + "» es de otra disciplina.");
			return null;
		}
		return tarifa.codigo();
	}

	/**
	 * SI, NO o SIN REGISTRAR (vacío = sin registrar). Sin registrar no lleva aviso: en la ficha de papel la imagen va en
	 * la misma firma que las normas y casi ninguna fila lo tendrá (repetirlo en cada fila taparía el resto del informe).
	 */
	private static DatosAlumno.UsoImagen usoImagen(Revision r) {
		if (r.conError(AUTORIZACION_IMAGEN)) {
			return null;
		}
		String texto = r.texto(AUTORIZACION_IMAGEN);
		String limpio = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
				.toUpperCase(Locale.ROOT).replaceAll("[\\s_]+", " ");
		return switch (limpio) {
		case "", "SIN REGISTRAR" -> DatosAlumno.UsoImagen.SIN_REGISTRAR;
		case "SI" -> DatosAlumno.UsoImagen.SI;
		case "NO" -> DatosAlumno.UsoImagen.NO;
		default -> {
			r.error(AUTORIZACION_IMAGEN,
					"«" + citado(texto) + "» no vale para el uso de imagen: usa SI, NO o SIN REGISTRAR.");
			yield null;
		}
		};
	}

	private static String obligatorio(Revision r, String columna, String falta) {
		String texto = r.texto(columna);
		if (texto.isEmpty() && !r.conError(columna)) {
			r.error(columna, falta);
		}
		return texto;
	}

	private static void largo(Revision r, String columna, String texto, int maximo, String motivo) {
		if (texto != null && texto.codePointCount(0, texto.length()) > maximo) {
			r.error(columna, motivo);
		}
	}

	private static String vacioANulo(String texto) {
		return texto == null || texto.isEmpty() ? null : texto;
	}

	// ------------------------------------------------------------------ repetidas dentro del fichero

	/** Cada fila con un valor repetido (fuera de las ya cargadas) cita otra fila con el mismo valor. */
	private static void repetidas(List<Revision> revisiones, Function<Revision, String> valor, String columna,
			String motivo) {
		Map<String, List<Revision>> porValor = new LinkedHashMap<>();
		for (Revision r : revisiones) {
			String v = r.yaCargada ? null : valor.apply(r);
			if (v != null) {
				porValor.computeIfAbsent(v, k -> new ArrayList<>()).add(r);
			}
		}
		for (List<Revision> grupo : porValor.values()) {
			if (grupo.size() < 2) {
				continue;
			}
			for (Revision r : grupo) {
				Revision otra = r == grupo.get(0) ? grupo.get(1) : grupo.get(0);
				r.error(columna, motivo + otra.fila.fila() + ".");
				r.datos = null;
			}
		}
	}

	// ------------------------------------------------------------------ estado de una fila

	private static final class Revision {
		private final FilaHoja fila;
		private final List<Incidencia> errores = new ArrayList<>();
		private final List<Incidencia> avisos = new ArrayList<>();
		private final Set<String> celdasConError = new HashSet<>();
		private Integer numero;
		private String nif;
		private boolean yaCargada;
		private DatosAlumno datos;

		Revision(FilaHoja fila) {
			this.fila = fila;
			for (Map.Entry<String, Valor> celda : fila.valores().entrySet()) {
				if (celda.getValue().error() && !ColumnasHoja.REVISAR.equals(celda.getKey())) {
					celdasConError.add(celda.getKey());
				}
			}
		}

		Valor valor(String columna) {
			return fila.valor(columna);
		}

		/** El texto de la celda; vacío si tiene un error de fórmula (apuntado aparte, una vez por celda). */
		String texto(String columna) {
			if (celdasConError.contains(columna)) {
				return "";
			}
			Valor valor = fila.valor(columna);
			return valor.fecha() != null ? valor.texto() : CeldaExcel.limpiar(valor.texto());
		}

		boolean conError(String columna) {
			return celdasConError.contains(columna);
		}

		void error(String columna, String motivo) {
			errores.add(new Incidencia(fila.fila(), numero, columna, motivo));
		}

		void aviso(String columna, String motivo) {
			avisos.add(new Incidencia(fila.fila(), numero, columna, motivo));
		}

		FilaValidada cerrar() {
			if (yaCargada) {
				return new FilaValidada(fila.fila(), numero, true, List.of(), List.of(), null);
			}
			return new FilaValidada(fila.fila(), numero, false, errores, avisos, errores.isEmpty() ? datos : null);
		}
	}
}
