package com.taemoi.project.services.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.taemoi.project.config.ValidacionRelojConfig;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Cobro;
import com.taemoi.project.entities.EstadoCobro;
import com.taemoi.project.entities.EstadoMovimientoCaja;
import com.taemoi.project.entities.MetodoPago;
import com.taemoi.project.entities.Organizacion;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.entities.TipoMovimientoCaja;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.CobroRepository;
import com.taemoi.project.repositories.MovimientoCajaRepository;
import com.taemoi.project.repositories.OrganizacionRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.services.MarcaEscuela;
import com.taemoi.project.services.MarcaEscuelaService;
import com.taemoi.project.services.ReciboPagoService;
import com.taemoi.project.utils.FormatoImporte;
import com.taemoi.project.utils.ImporteCargo;

/**
 * Pieza 279: recibo de pago en PDF del cobro ACTIVO de un cargo, generado al momento (sin tabla ni fichero) con la
 * estética común ({@link EstiloPdf}).
 *
 * <p>Contenido (opción A del propietario, los mínimos datos personales): cabecera de la escuela (logo, nombre y, cada uno
 * solo si existe, razón social, CIF, dirección, código postal y localidad, provincia, correo y teléfono de
 * {@code organizacion}); «Recibo» y su número; la fecha del cobro; el alumno (SOLO nombre y apellidos); el concepto, la
 * forma de pago, la sede (si la hay) y el importe, de la instantánea del cobro; y el pie («No es una factura» y la fecha
 * de emisión, hoy en Europe/Madrid según el {@link Clock}). No sale quién cobró. No se registra nada en los logs.
 *
 * <p>Sin recibo (409): cargo sin cobro ACTIVO (sin cobrar, o con el cobro deshecho: ANULADO) y cobro ACTIVO con una
 * DEVOLUCION ACTIVA enlazada en la caja (devuelto, pieza 6c; decisión del propietario: «cobro deshecho o devuelto → no
 * se genera»).
 *
 * <p>Cargo de legado (decisión del propietario): V31_5 no rellenó los cobros, así que los cargos pagados antes no
 * tienen NINGUNA fila de {@code cobro}. Para un cargo pagado sin ninguna fila de cobro el recibo sale del propio cargo
 * ({@code producto_alumno}): fecha de pago, alumno, concepto, precio × cantidad ({@link ImporteCargo}) y sede, sin
 * forma de pago (el cargo no la guarda), con la serie {@code R-{año del pago}-C{id del cargo}}. Cancelado, devuelto o
 * sin fecha de pago → 409. Misma tarjeta: los datos se reúnen en {@code DatosRecibo} y se pintan igual.
 *
 * <p>Escuela: los repositorios filtran por la organización activa ({@code @TenantId}): un cargo de otra escuela no
 * existe para esta (404).
 */
@Service
public class ReciboPagoServiceImpl implements ReciboPagoService {

	static final String CARGO_NO_ENCONTRADO = "Cargo no encontrado.";
	static final String SIN_COBRO = "Este cargo no tiene ningún cobro registrado: no se puede emitir el recibo.";
	static final String COBRO_ANULADO = "El cobro de este cargo está anulado: no se puede emitir el recibo.";
	static final String COBRO_DEVUELTO = "El cobro de este cargo está devuelto: no se puede emitir el recibo.";
	static final String CARGO_CANCELADO = "Este cargo está cancelado: no tiene recibo.";
	static final String CARGO_DEVUELTO = "Este cargo está devuelto: no tiene recibo.";
	static final String SIN_FECHA_PAGO = "Este cargo no tiene fecha de pago: no se puede emitir el recibo.";
	static final String AVISO = "Este documento justifica el pago del concepto indicado. No es una factura.";
	private static final String TITULO = "Recibo";

	private static final Locale ES = Locale.of("es", "ES");
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy", ES);

	private final ProductoAlumnoRepository cargos;
	private final CobroRepository cobros;
	private final MovimientoCajaRepository movimientosCaja;
	private final AlumnoRepository alumnos;
	private final SedeRepository sedes;
	private final OrganizacionRepository organizaciones;
	private final MarcaEscuelaService marcaEscuela;
	private final Clock reloj;

	public ReciboPagoServiceImpl(ProductoAlumnoRepository cargos, CobroRepository cobros,
			MovimientoCajaRepository movimientosCaja, AlumnoRepository alumnos, SedeRepository sedes,
			OrganizacionRepository organizaciones, MarcaEscuelaService marcaEscuela, Clock reloj) {
		this.cargos = cargos;
		this.cobros = cobros;
		this.movimientosCaja = movimientosCaja;
		this.alumnos = alumnos;
		this.sedes = sedes;
		this.organizaciones = organizaciones;
		this.marcaEscuela = marcaEscuela;
		this.reloj = reloj;
	}

	@Override
	@Transactional(readOnly = true)
	public ReciboPago generar(Long productoAlumnoId) {
		// existsById es una consulta JPQL: con el filtro por organización, un cargo de otra escuela no existe.
		if (productoAlumnoId == null || !cargos.existsById(productoAlumnoId)) {
			throw new NoSuchElementException(CARGO_NO_ENCONTRADO);
		}
		DatosRecibo datos = cobros.findFirstByProductoAlumnoIdAndEstado(productoAlumnoId, EstadoCobro.ACTIVO)
				.map(this::delCobro).orElseGet(() -> sinCobroActivo(productoAlumnoId));

		MarcaEscuela marca = marcaEscuela.deActiva();
		Organizacion escuela = organizaciones.findById(marca.organizacionId()).orElse(null);
		LocalDate emision = LocalDate.ofInstant(reloj.instant(), ValidacionRelojConfig.ZONA);
		return new ReciboPago(datos.numero(), renderizar(html(marca, escuela, datos, emision)));
	}

	// ------------------------------------------------------------------ datos del recibo

	/**
	 * Lo que pinta la tarjeta, salga del cobro o del cargo de legado. {@code formaDePago} nula: sin la fila; {@code sede}
	 * nula o vacía: sin la fila.
	 */
	private record DatosRecibo(String numero, LocalDate fecha, String alumno, String concepto, String formaDePago,
			String sede, BigDecimal importe) {
	}

	/** Recibo del cobro ACTIVO (su instantánea): 409 si tiene una DEVOLUCION ACTIVA enlazada. */
	private DatosRecibo delCobro(Cobro cobro) {
		if (movimientosCaja.existsByCobroIdAndTipoAndEstado(cobro.getId(), TipoMovimientoCaja.DEVOLUCION,
				EstadoMovimientoCaja.ACTIVO)) {
			throw new IllegalStateException(COBRO_DEVUELTO);
		}
		LocalDate fecha = fechaCobro(cobro.getFechaCobro());
		return new DatosRecibo(numero(fecha, cobro.getId()), fecha, nombreAlumno(cobro.getAlumnoId()),
				cobro.getConcepto(), formaDePago(cobro.getMetodo()), nombreSede(cobro.getSedeId()), cobro.getImporte());
	}

	/**
	 * Cargo sin cobro ACTIVO. Con algún cobro (todos ANULADOS) → 409 como siempre. Sin NINGUNA fila de cobro: los cargos
	 * pagados antes de V31_5 (sin relleno) son de legado y el recibo sale del propio cargo; sin pagar → 409.
	 */
	private DatosRecibo sinCobroActivo(Long productoAlumnoId) {
		if (!cobros.findByProductoAlumnoIdOrderByIdAsc(productoAlumnoId).isEmpty()) {
			throw new IllegalStateException(COBRO_ANULADO);
		}
		ProductoAlumno cargo = cargos.buscarEnEscuelaActiva(productoAlumnoId)
				.orElseThrow(() -> new NoSuchElementException(CARGO_NO_ENCONTRADO));
		if (!Boolean.TRUE.equals(cargo.getPagado())) {
			throw new IllegalStateException(SIN_COBRO);
		}
		return delCargoDeLegado(cargo);
	}

	/**
	 * Recibo de un cargo pagado de legado (sin ninguna fila de cobro): fecha = {@code fecha_pago}; importe = precio ×
	 * cantidad ({@link ImporteCargo#de(ProductoAlumno)}, lo mismo que cobraría hoy {@code CobroService}); alumno del
	 * cargo como la tesorería ({@link ProductoAlumno#alumnoEfectivo()}); sede del cargo si la tiene; sin forma de pago
	 * (el cargo no la guarda). Cancelado, devuelto (DEVOLUCION ACTIVA enlazada al cargo en la caja) o sin fecha de pago
	 * → 409.
	 */
	private DatosRecibo delCargoDeLegado(ProductoAlumno cargo) {
		if (cargo.estaCancelado()) {
			throw new IllegalStateException(CARGO_CANCELADO);
		}
		if (movimientosCaja.existsByProductoAlumnoIdAndTipoAndEstado(cargo.getId(), TipoMovimientoCaja.DEVOLUCION,
				EstadoMovimientoCaja.ACTIVO)) {
			throw new IllegalStateException(CARGO_DEVUELTO);
		}
		if (cargo.getFechaPago() == null) {
			throw new IllegalStateException(SIN_FECHA_PAGO);
		}
		LocalDate fecha = fechaCobro(cargo.getFechaPago());
		return new DatosRecibo(numeroDeCargo(fecha, cargo.getId()), fecha, nombreAlumno(cargo.alumnoEfectivo()),
				cargo.getConcepto(), null, nombreSede(cargo.getSedeId()), ImporteCargo.de(cargo));
	}

	// ------------------------------------------------------------------ formatos

	/** Número del recibo: {@code R-{año del cobro}-{id del cobro con al menos 6 cifras}} (R-2026-000123). */
	public static String numero(LocalDate fechaCobro, Long cobroId) {
		return String.format(Locale.ROOT, "R-%d-%06d", fechaCobro.getYear(), cobroId);
	}

	/**
	 * Número del recibo de un cargo de legado, en otra serie para no chocar con la de los cobros: {@code R-{año del
	 * pago}-C{id del cargo con al menos 6 cifras}} (R-2025-C014192).
	 */
	public static String numeroDeCargo(LocalDate fechaPago, Long cargoId) {
		return String.format(Locale.ROOT, "R-%d-C%06d", fechaPago.getYear(), cargoId);
	}

	/** Importe en formato es-ES, sin depender del locale de la JVM: «28,00 €», «1.234,56 €». */
	public static String importe(BigDecimal importe) {
		return FormatoImporte.eur(importe);
	}

	/** Forma de pago legible, con los rótulos del panel («Confirmar cobro»). */
	public static String formaDePago(MetodoPago metodo) {
		if (metodo == null) {
			return "Sin indicar";
		}
		return switch (metodo) {
			case EFECTIVO -> "Efectivo";
			case TRANSFERENCIA -> "Transferencia";
			case TARJETA -> "Tarjeta";
			case BIZUM -> "Bizum";
			case SIN_INDICAR -> "Sin indicar";
		};
	}

	/**
	 * Día del cobro. {@code fecha_cobro} es un DATETIME sin zona que Hibernate lee en la zona de la JVM: se vuelve a esa
	 * zona para recuperar el día guardado, como la tesorería. {@code getTime()} vale también para {@code java.sql.Date}.
	 * Vale igual para {@code producto_alumno.fecha_pago} (mismo tipo) en los cargos de legado.
	 */
	static LocalDate fechaCobro(Date fecha) {
		return Instant.ofEpochMilli(fecha.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
	}

	/** Nombre y apellidos del alumno del cobro; {@code null} si no tiene o ya no existe. */
	private String nombreAlumno(Long alumnoId) {
		if (alumnoId == null) {
			return null;
		}
		return alumnos.findById(alumnoId).map(ReciboPagoServiceImpl::nombreAlumno).orElse(null);
	}

	/** SOLO nombre y apellidos; {@code null} si no hay alumno o no tiene ninguno de los dos. */
	private static String nombreAlumno(Alumno alumno) {
		if (alumno == null) {
			return null;
		}
		String nombre = unir(" ", alumno.getNombre(), alumno.getApellidos());
		return nombre.isEmpty() ? null : nombre;
	}

	/** Nombre de la sede; {@code null} si no hay o ya no existe. */
	private String nombreSede(Long sedeId) {
		return sedeId == null ? null : sedes.findById(sedeId).map(Sede::getNombre).orElse(null);
	}

	// ------------------------------------------------------------------ PDF

	/**
	 * Tarjeta A5 apaisada de una sola página: banda superior (a la izquierda logo, nombre y líneas legales; a la derecha
	 * «Recibo», el número y la fecha del cobro) con el filete en el color de la escuela; cuerpo con las filas
	 * etiqueta/valor a la izquierda y el importe destacado a la derecha; y el pie de la tarjeta («No es una factura» y
	 * la fecha de emisión). Las columnas son tablas: OpenHTMLtoPDF no maqueta con flex.
	 */
	private static String html(MarcaEscuela marca, Organizacion escuela, DatosRecibo datos, LocalDate emision) {
		StringBuilder cuerpo = new StringBuilder();
		cuerpo.append("<div class='cab' style='border-bottom-color: ").append(marca.colorPrimario()).append(";'>");
		cuerpo.append("<table class='banda'><tr>");
		String logo = marca.logoInformes();
		if (logo != null && !logo.isEmpty()) {
			cuerpo.append("<td class='banda-logo'><img src='").append(logo).append("' alt='Logo' /></td>");
		}
		cuerpo.append("<td class='banda-escuela'><div class='cab-escuela'>")
				.append(marca.cabeceraInformesHtml() == null ? "" : marca.cabeceraInformesHtml()).append("</div>");
		String legales = datosEscuelaHtml(escuela);
		if (lleno(legales)) {
			cuerpo.append("<div class='legales'>").append(legales).append("</div>");
		}
		cuerpo.append("</td><td class='banda-recibo'>");
		cuerpo.append("<div class='titulo'>").append(TITULO).append("</div>");
		cuerpo.append("<div class='numero'>").append(EstiloPdf.esc(datos.numero())).append("</div>");
		cuerpo.append("<div class='fecha'>Fecha del cobro ").append(datos.fecha().format(FECHA)).append("</div>");
		cuerpo.append("</td></tr></table></div>");

		cuerpo.append("<table class='cuerpo'><tr><td class='cuerpo-datos'><table class='datos'>");
		fila(cuerpo, "Alumno", datos.alumno());
		fila(cuerpo, "Concepto", datos.concepto());
		if (datos.formaDePago() != null) {
			fila(cuerpo, "Forma de pago", datos.formaDePago());
		}
		if (lleno(datos.sede())) {
			fila(cuerpo, "Sede", datos.sede());
		}
		cuerpo.append("</table></td><td class='cuerpo-importe'><div class='importe'>");
		cuerpo.append("<div class='importe-etiqueta'>Importe</div>");
		cuerpo.append("<div class='importe-valor'>").append(EstiloPdf.esc(importe(datos.importe()))).append("</div>");
		cuerpo.append("</div></td></tr></table>");

		cuerpo.append("<div class='pie-tarjeta'>");
		cuerpo.append("<p class='aviso'>").append(EstiloPdf.esc(AVISO)).append("</p>");
		cuerpo.append("<p class='emision'>Emitido el ").append(emision.format(FECHA)).append("</p>");
		cuerpo.append("</div>");

		return EstiloPdf.documento(marca.cabeceraInformes(), TITULO + " " + datos.numero(), estilos(),
				cuerpo.toString());
	}

	/**
	 * Líneas legales de la escuela, compactas y cada pieza solo si existe: «razón social · CIF: X», «dirección, CP
	 * localidad (provincia)» y «correo · teléfono». Sin separadores colgando: se unen solo las piezas llenas.
	 */
	private static String datosEscuelaHtml(Organizacion escuela) {
		if (escuela == null) {
			return null;
		}
		String cif = lleno(escuela.getCif()) ? "CIF: " + escuela.getCif().trim() : null;
		String lugar = unir(", ", escuela.getDireccion(), unir(" ", escuela.getCodigoPostal(), escuela.getLocalidad()));
		if (lleno(escuela.getProvincia())) {
			String provincia = escuela.getProvincia().trim();
			lugar = lugar.isEmpty() ? provincia : lugar + " (" + provincia + ")";
		}
		List<String> lineas = new ArrayList<>();
		anadir(lineas, unir(" · ", escuela.getNombreLegal(), cif));
		anadir(lineas, lugar);
		anadir(lineas, unir(" · ", escuela.getEmailContacto(), escuela.getTelefono()));
		return String.join("<br />", lineas.stream().map(EstiloPdf::esc).toList());
	}

	private static void fila(StringBuilder html, String etiqueta, String valor) {
		html.append("<tr><td class='etiqueta'>").append(EstiloPdf.esc(etiqueta)).append("</td>");
		html.append("<td class='valor'>").append(EstiloPdf.esc(valor)).append("</td></tr>");
	}

	/**
	 * Estilos de la tarjeta. La regla {@code @page} va detrás de la del pie común ({@link EstiloPdf#pie}): cambia el
	 * tamaño a A5 apaisado y los márgenes, y deja en blanco «Página X de Y» (una sola página; el filete y «Escuela ·
	 * Recibo» se quedan). No toca a los demás PDF.
	 */
	private static String estilos() {
		return " @page { size: A5 landscape; margin: 10mm 12mm 13mm 12mm; @bottom-right { content: \"\"; } }"
				+ " .cab { padding-bottom: 3.2mm; margin-bottom: 5mm; }"
				// Banda: escuela a la izquierda, número del recibo a la derecha.
				+ " table.banda { width: 100%; border-collapse: collapse; margin: 0; }"
				+ " table.banda td { border: none; padding: 0; vertical-align: top; }"
				+ " table.banda td.banda-logo { width: 1%; padding-right: 3.5mm; }"
				+ " table.banda td.banda-logo img { height: 13mm; width: auto; display: block; }"
				+ " table.banda td.banda-recibo { width: 1%; padding-left: 6mm; text-align: right; white-space: nowrap; }"
				+ " .legales { font-size: 7.5pt; line-height: 1.35; color: " + EstiloPdf.GRIS + "; margin-top: 1.2mm; }"
				+ " .titulo { font-size: 8pt; font-weight: 600; color: " + EstiloPdf.GRIS + "; line-height: 1.2; }"
				+ " .numero { font-family: " + EstiloPdf.TITULOS + "; font-weight: 700; font-size: 15pt;"
				+ " line-height: 1.15; color: " + EstiloPdf.TINTA + "; margin-top: 0.6mm; }"
				+ " .fecha { font-size: 8.5pt; color: " + EstiloPdf.TINTA + "; margin-top: 1.2mm; }"
				// Cuerpo: filas a la izquierda (≈ 2/3), importe destacado a la derecha (≈ 1/3).
				+ " table.cuerpo { width: 100%; border-collapse: collapse; margin: 0; }"
				+ " table.cuerpo td.cuerpo-datos, table.cuerpo td.cuerpo-importe { border: none; padding: 0;"
				+ " vertical-align: top; }"
				+ " table.cuerpo td.cuerpo-datos { width: 64%; padding-right: 7mm; }"
				+ " table.cuerpo td.cuerpo-importe { width: 36%; }"
				+ " table.datos { margin: 0; }"
				+ " table.datos td { font-size: 9.5pt; line-height: 1.35; padding: 2mm 0; vertical-align: top; }"
				+ " table.datos td.etiqueta { width: 31mm; padding-right: 3mm; color: " + EstiloPdf.GRIS
				+ "; font-weight: 600; }"
				+ " table.datos tr:last-child td { border-bottom: none; }"
				+ " .importe { background-color: " + EstiloPdf.FONDO_CABECERA_TABLA + "; border: 0.5pt solid "
				+ EstiloPdf.FILETE_CABECERA_TABLA + "; border-radius: 1.6mm; padding: 4.5mm 5mm; text-align: right; }"
				+ " .importe-etiqueta { font-size: 8.5pt; font-weight: 600; color: " + EstiloPdf.GRIS + "; }"
				+ " .importe-valor { font-family: " + EstiloPdf.TITULOS + "; font-weight: 700; font-size: 20pt;"
				+ " line-height: 1.15; color: " + EstiloPdf.TINTA + "; margin-top: 1.5mm; white-space: nowrap; }"
				// Pie de la tarjeta.
				+ " .pie-tarjeta { margin-top: 7mm; page-break-inside: avoid; }"
				+ " .aviso { margin: 0 0 1mm 0; font-size: 8.5pt; }"
				+ " .emision { margin: 0; font-size: 8pt; color: " + EstiloPdf.GRIS + "; }";
	}

	private static byte[] renderizar(String html) {
		try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = EstiloPdf.builder();
			builder.withHtmlContent(html, null);
			builder.toStream(salida);
			builder.run();
			return salida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo generar el recibo de pago", e);
		}
	}

	// ------------------------------------------------------------------ textos

	private static boolean lleno(String valor) {
		return valor != null && !valor.isBlank();
	}

	private static void anadir(List<String> lineas, String valor) {
		if (lleno(valor)) {
			lineas.add(valor.trim());
		}
	}

	/** Los valores llenos, recortados y unidos con el separador; vacío si no hay ninguno. */
	private static String unir(String separador, String... valores) {
		List<String> llenos = new ArrayList<>();
		for (String valor : valores) {
			anadir(llenos, valor);
		}
		return String.join(separador, llenos);
	}
}
