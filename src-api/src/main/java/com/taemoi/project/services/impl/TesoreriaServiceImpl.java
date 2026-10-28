package com.taemoi.project.services.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.pdmodel.interactive.form.PDVariableText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.taemoi.project.dtos.response.TesoreriaMovimientoDTO;
import com.taemoi.project.dtos.response.TesoreriaResumenDTO;
import com.taemoi.project.dtos.response.TesoreriaResumenSedeDTO;
import com.taemoi.project.acceso.FiltroSedesTesoreria;
import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.entities.Sede;
import com.taemoi.project.repositories.SedeRepository;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.TipoProducto;
import com.taemoi.project.services.CatalogoLegadoTaemoi;
import com.taemoi.project.services.MarcaEscuela;
import com.taemoi.project.services.MarcaEscuelaService;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.ProductoAlumnoRepository;
import com.taemoi.project.services.TesoreriaService;
import com.taemoi.project.utils.ImporteCargo;

@Service
public class TesoreriaServiceImpl implements TesoreriaService {

	private static final int DEFAULT_PAGE_SIZE = 25;
	private static final int MAX_PAGE_SIZE = 200;
	private static final DateTimeFormatter FECHA_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter FECHA_CERTIFICADO_FORMATTER =
			DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
	private static final Pattern ANO_PATTERN = Pattern.compile("\\b(20\\d{2})\\b");
	private static final Pattern MATRICULA_PATTERN = Pattern.compile("\\s*MATR[IÍ]CULA\\b");
	private static final String RESPONSABLE_NOMBRE = "DÑA. DOLORES MARIA ROMAN RUIZ";
	private static final String RESPONSABLE_DNI = "28756368C";
	private static final String CLUB_NOMBRE_LEGAL = "Club Deportivo Moi's Kim do Taekwondo";
	private static final String CLUB_CIF = "G90341173";
	private static final String[] MESES_ES = {
			"ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
			"JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"
	};
	/** Hueco de un dato legal que la escuela no tiene en {@code organizacion} (certificado de otra escuela). */
	private static final String DATO_LEGAL_VACIO = "________";

	/**
	 * Pieza E7-7a (decisión 211): logo y datos legales del certificado de la escuela activa. Moiskimdo, los de siempre
	 * (las constantes de arriba); otra escuela, los suyos de {@code organizacion} o un hueco, nunca los de Moiskimdo.
	 */
	@Autowired
	private MarcaEscuelaService marcaEscuela;

	/** Textos legales del certificado de cobros. */
	private record DatosCertificado(String nombreLegal, String cif, String responsable, String responsableNif,
			String cargo, String clubFirma, String localidad) {
	}

	@Autowired
	private com.taemoi.project.services.ComportamientoLegadoService comportamientoLegado;

	@Autowired
	private ProductoAlumnoRepository productoAlumnoRepository;

	@Autowired
	private AlumnoRepository alumnoRepository;

	@Autowired
	private com.taemoi.project.services.CatalogoDisciplinasService catalogoDisciplinas;

	/** Entrega 6-3 (D6-12): sedes de la tesorería de la petición (gestión: todas; responsable: las suyas). */
	@Autowired
	private GestionPorSede gestionPorSede;

	@Autowired
	private SedeRepository sedeRepository;

	/** Pieza 6c-6 (D6c-12): lo devuelto de los cargos (devoluciones enlazadas activas). */
	@Autowired
	private com.taemoi.project.repositories.MovimientoCajaRepository movimientoCajaRepository;

	@Override
	public TesoreriaResumenDTO obtenerResumen(Integer mes, Integer ano, String deporte, Boolean soloActivos,
			Long sedeId) {
		FiltroSedesTesoreria sedes = gestionPorSede.filtroTesoreria(sedeId);
		FiltroTesoreria filtro = construirFiltro(mes, ano, deporte);
		String anoTexto = obtenerAnoTexto(filtro.ano);
		String mesNombre = obtenerMesNombre(filtro.mes);
		Boolean soloActivosNormalizado = normalizarSoloActivos(soloActivos);

		if (!filtro.tieneFiltroPeriodo()) {
			long totalMovimientos = valorSeguro(productoAlumnoRepository.contarMovimientosTesoreria(
					filtro.deporte,
					filtro.deporteNombre,
					null,
					soloActivosNormalizado,
					filtro.ano,
					anoTexto,
					filtro.mes,
					mesNombre,
					legado(),
					sedes.todas(),
					sedes.parametro()));
			long totalPagados = valorSeguro(productoAlumnoRepository.contarMovimientosTesoreria(
					filtro.deporte,
					filtro.deporteNombre,
					true,
					soloActivosNormalizado,
					filtro.ano,
					anoTexto,
					filtro.mes,
					mesNombre,
					legado(),
					sedes.todas(),
					sedes.parametro()));
			long totalPendientes = valorSeguro(productoAlumnoRepository.contarMovimientosTesoreria(
					filtro.deporte,
					filtro.deporteNombre,
					false,
					soloActivosNormalizado,
					filtro.ano,
					anoTexto,
					filtro.mes,
					mesNombre,
					legado(),
					sedes.todas(),
					sedes.parametro()));
			double importeTotal = valorSeguro(productoAlumnoRepository.sumarImporteMovimientosTesoreria(
					filtro.deporte,
					filtro.deporteNombre,
					null,
					soloActivosNormalizado,
					filtro.ano,
					anoTexto,
					filtro.mes,
					mesNombre,
					legado(),
					sedes.todas(),
					sedes.parametro()));
			double importePagado = valorSeguro(productoAlumnoRepository.sumarImporteMovimientosTesoreria(
					filtro.deporte,
					filtro.deporteNombre,
					true,
					soloActivosNormalizado,
					filtro.ano,
					anoTexto,
					filtro.mes,
					mesNombre,
					legado(),
					sedes.todas(),
					sedes.parametro()));
			double importePendiente = valorSeguro(productoAlumnoRepository.sumarImporteMovimientosTesoreria(
					filtro.deporte,
					filtro.deporteNombre,
					false,
					soloActivosNormalizado,
					filtro.ano,
					anoTexto,
					filtro.mes,
					mesNombre,
					legado(),
					sedes.todas(),
					sedes.parametro()));
			long alumnosConPendientes = valorSeguro(productoAlumnoRepository.contarAlumnosConPendientesTesoreria(
					filtro.deporte,
					filtro.deporteNombre,
					soloActivosNormalizado,
					filtro.ano,
					anoTexto,
					filtro.mes,
					mesNombre,
					legado(),
					sedes.todas(),
					sedes.parametro()));
			TesoreriaResumenDTO resumen = construirResumen(
					filtro,
					totalMovimientos,
					totalPagados,
					totalPendientes,
					importeTotal,
					importePagado,
					importePendiente,
					alumnosConPendientes);
			resumen.setPorSede(porSedeDesdeConsultas(filtro, soloActivosNormalizado, anoTexto, mesNombre, sedes));
			java.math.BigDecimal devuelto = productoAlumnoRepository.sumarImporteDevueltoTesoreria(
					filtro.deporte,
					filtro.deporteNombre,
					soloActivosNormalizado,
					filtro.ano,
					anoTexto,
					filtro.mes,
					mesNombre,
					legado(),
					sedes.todas(),
					sedes.parametro());
			resumen.setImporteDevuelto(devuelto == null ? 0.0 : devuelto.doubleValue());
			return resumen;
		}

		List<ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection> movimientosBase =
				obtenerMovimientosPeriodoBase(filtro, null, null, soloActivosNormalizado, sedes);
		List<ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection> movimientos =
				filtrarMovimientosPeriodoBase(movimientosBase, filtro);
		long totalMovimientos = movimientos.size();
		long totalPagados = movimientos.stream()
				.filter(pa -> Boolean.TRUE.equals(pa.getPagado()))
				.count();
		long totalPendientes = totalMovimientos - totalPagados;
		double importeTotal = movimientos.stream()
				.mapToDouble(pa -> ImporteCargo.sinRedondear(pa.getPrecio(), pa.getCantidad()))
				.sum();
		double importePagado = movimientos.stream()
				.filter(pa -> Boolean.TRUE.equals(pa.getPagado()))
				.mapToDouble(pa -> ImporteCargo.sinRedondear(pa.getPrecio(), pa.getCantidad()))
				.sum();
		double importePendiente = movimientos.stream()
				.filter(pa -> !Boolean.TRUE.equals(pa.getPagado()))
				.mapToDouble(pa -> ImporteCargo.sinRedondear(pa.getPrecio(), pa.getCantidad()))
				.sum();
		long alumnosConPendientes = movimientos.stream()
				.filter(pa -> !Boolean.TRUE.equals(pa.getPagado()))
				.map(ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection::getAlumnoId)
				.filter(Objects::nonNull)
				.distinct()
				.count();
		TesoreriaResumenDTO resumen = construirResumen(
				filtro,
				totalMovimientos,
				totalPagados,
				totalPendientes,
				importeTotal,
				importePagado,
				importePendiente,
				alumnosConPendientes);
		resumen.setPorSede(porSedeDesdeMovimientos(movimientos, sedes));
		resumen.setImporteDevuelto(devueltoDeCargos(movimientos.stream()
				.map(ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection::getId).toList()).values().stream()
				.mapToDouble(java.math.BigDecimal::doubleValue).sum());
		return resumen;
	}

	@Override
	public Page<TesoreriaMovimientoDTO> obtenerMovimientos(
			Integer mes,
			Integer ano,
			String deporte,
			Boolean pagado,
			String texto,
			Boolean soloActivos,
			Integer page,
			Integer size,
			Long sedeId) {
		FiltroSedesTesoreria sedes = gestionPorSede.filtroTesoreria(sedeId);
		FiltroTesoreria filtro = construirFiltro(mes, ano, deporte);
		int pageNumber = page == null || page < 1 ? 1 : page;
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
		Pageable pageable = PageRequest.of(pageNumber - 1, pageSize);
		String textoNormalizado = normalizarTextoBusqueda(texto);
		Boolean soloActivosNormalizado = normalizarSoloActivos(soloActivos);

		if (!filtro.tieneFiltroPeriodo()) {
			return marcarDevueltos(obtenerPaginaMovimientosFiltrados(
					filtro,
					pagado,
					textoNormalizado,
					soloActivosNormalizado,
					pageable,
					sedes)
					.map(conversorAMovimientoDTO()));
		}

		List<ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection> movimientosBase =
				obtenerMovimientosPeriodoBase(filtro, pagado, textoNormalizado, soloActivosNormalizado, sedes);
		List<Long> idsFiltrados = filtrarMovimientosPeriodoBase(movimientosBase, filtro).stream()
				.map(ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection::getId)
				.collect(Collectors.toList());

		int fromIndex = Math.min((int) pageable.getOffset(), idsFiltrados.size());
		int toIndex = Math.min(fromIndex + pageable.getPageSize(), idsFiltrados.size());
		List<Long> idsPagina = idsFiltrados.subList(fromIndex, toIndex);

		List<ProductoAlumno> movimientosPagina = idsPagina.isEmpty()
				? List.of()
				: obtenerMovimientosPaginaPorIds(idsPagina);

		Page<ProductoAlumno> pagina = new PageImpl<>(
				movimientosPagina,
				pageable,
				idsFiltrados.size());

		return marcarDevueltos(pagina.map(conversorAMovimientoDTO()));
	}

	/** Pieza 6c-6 (D6c-12): marca las filas devueltas de la página (una consulta). */
	private Page<TesoreriaMovimientoDTO> marcarDevueltos(Page<TesoreriaMovimientoDTO> pagina) {
		Map<Long, java.math.BigDecimal> devueltos = devueltoDeCargos(pagina.getContent().stream()
				.map(TesoreriaMovimientoDTO::getProductoAlumnoId).toList());
		pagina.getContent().forEach(fila -> fila.setDevuelto(devueltos.containsKey(fila.getProductoAlumnoId())));
		return pagina;
	}

	/**
	 * Pieza 6c-6 (D6c-12): lo devuelto de cada cargo devuelto de la lista (cargo → Σ −importe de su devolución activa),
	 * en tandas de 1000 ids. Sin repositorio (pruebas unitarias), nada.
	 */
	private Map<Long, java.math.BigDecimal> devueltoDeCargos(List<Long> cargoIds) {
		Map<Long, java.math.BigDecimal> devueltos = new HashMap<>();
		List<Long> ids = cargoIds.stream().filter(Objects::nonNull).distinct().toList();
		if (movimientoCajaRepository == null || ids.isEmpty()) {
			return devueltos;
		}
		for (int i = 0; i < ids.size(); i += 1000) {
			for (Object[] fila : movimientoCajaRepository.devueltoDeCargos(ids.subList(i, Math.min(i + 1000,
					ids.size())))) {
				devueltos.put((Long) fila[0], (java.math.BigDecimal) fila[1]);
			}
		}
		return devueltos;
	}

	@Override
	public List<Integer> obtenerAniosDisponibles() {
		TreeSet<Integer> anios = new TreeSet<>();
		anios.addAll(productoAlumnoRepository.findAniosDistintosTesoreriaConFecha());
		aniadirAniosDesdeConceptos(anios, productoAlumnoRepository.findConceptosTesoreriaConPeriodoPotencial(legado()));

		List<String> conceptosSinFecha = productoAlumnoRepository.findConceptosTesoreriaSinFecha();
		aniadirAniosDesdeConceptos(anios, conceptosSinFecha);

		if (anios.isEmpty()) {
			anios.add(LocalDate.now().getYear());
		}

		return new ArrayList<>(anios);
	}

	private void aniadirAniosDesdeConceptos(TreeSet<Integer> anios, List<String> conceptos) {
		for (String concepto : conceptos) {
			PeriodoMovimiento periodo = extraerPeriodoDesdeConcepto(concepto);
			Integer ano = periodo != null ? periodo.ano : null;
			if (ano != null) {
				anios.add(ano);
			}
		}
	}

	@Override
	public byte[] exportarMovimientosCSV(
			Integer mes,
			Integer ano,
			String deporte,
			Boolean pagado,
			String texto,
			Boolean soloActivos,
			Long sedeId) {
		FiltroSedesTesoreria sedes = gestionPorSede.filtroTesoreria(sedeId);
		FiltroTesoreria filtro = construirFiltro(mes, ano, deporte);
		String textoNormalizado = normalizarTextoBusqueda(texto);
		Boolean soloActivosNormalizado = normalizarSoloActivos(soloActivos);
		List<TesoreriaMovimientoDTO> movimientos = obtenerMovimientosFiltrados(
				filtro,
				pagado,
				textoNormalizado,
				soloActivosNormalizado,
				sedes).stream()
				.map(conversorAMovimientoDTO())
				.collect(Collectors.toList());

		StringBuilder csv = new StringBuilder();
		csv.append("Alumno,Deporte,Concepto,Categoria,Fecha cargo,Estado,Fecha pago,Importe,Notas\n");

		for (TesoreriaMovimientoDTO movimiento : movimientos) {
			csv.append(escapeCSV(movimiento.getAlumnoNombreCompleto())).append(",");
			csv.append(escapeCSV(movimiento.getDeporte())).append(",");
			csv.append(escapeCSV(movimiento.getConcepto())).append(",");
			csv.append(escapeCSV(movimiento.getCategoria())).append(",");
			csv.append(escapeCSV(formatearFecha(movimiento.getFechaAsignacion()))).append(",");
			csv.append(escapeCSV(Boolean.TRUE.equals(movimiento.getPagado()) ? "PAGADO" : "PENDIENTE")).append(",");
			csv.append(escapeCSV(formatearFecha(movimiento.getFechaPago()))).append(",");
			csv.append(movimiento.getPrecio() != null ? String.format(Locale.ROOT, "%.2f", movimiento.getPrecio()) : "0.00")
					.append(",");
			csv.append(escapeCSV(movimiento.getNotas())).append("\n");
		}

		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public byte[] exportarMovimientosPDF(
			Integer mes,
			Integer ano,
			String deporte,
			Boolean pagado,
			String texto,
			Boolean soloActivos,
			Long sedeId) {
		FiltroSedesTesoreria sedes = gestionPorSede.filtroTesoreria(sedeId);
		FiltroTesoreria filtro = construirFiltro(mes, ano, deporte);
		String textoNormalizado = normalizarTextoBusqueda(texto);
		Boolean soloActivosNormalizado = normalizarSoloActivos(soloActivos);
		List<TesoreriaMovimientoDTO> movimientos = obtenerMovimientosFiltrados(
				filtro,
				pagado,
				textoNormalizado,
				soloActivosNormalizado,
				sedes).stream()
				.map(conversorAMovimientoDTO())
				.collect(Collectors.toList());
		String html = generarHtmlInformeTesoreria(movimientos, filtro, pagado);

		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = EstiloPdf.builder();
			builder.withHtmlContent(html, null);
			builder.toStream(outputStream);
			builder.run();
			return outputStream.toByteArray();
		} catch (Exception ex) {
			throw new RuntimeException("Error al generar el informe PDF de tesoreria", ex);
		}
	}

	@Override
	public byte[] generarCertificadoCobrosAlumno(Long alumnoId, Integer ano) {
		validarCertificadoCobros(alumnoId, ano);
		// D6-3: el responsable, solo de alumnos de su sede principal. El certificado es del alumno: todos sus cobros.
		gestionPorSede.exigirAlumnosConSedePrincipalPropia(List.of(alumnoId));
		Alumno alumno = alumnoRepository.findById(alumnoId)
				.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado: " + alumnoId));
		CertificadoCobrosData certificado = construirCertificadoCobros(alumno, ano);
		return generarPdfCertificadoCobros(certificado);
	}

	@Override
	public byte[] generarCertificadosCobrosZip(List<Long> alumnoIds, Integer ano) {
		if (alumnoIds == null || alumnoIds.isEmpty()) {
			throw new IllegalArgumentException("Debes seleccionar al menos un alumno.");
		}
		if (ano == null || ano < 1900 || ano > 2200) {
			throw new IllegalArgumentException("El año no es válido.");
		}
		gestionPorSede.exigirAlumnosConSedePrincipalPropia(alumnoIds);

		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
			for (Long alumnoId : alumnoIds.stream().filter(Objects::nonNull).distinct().toList()) {
				Alumno alumno = alumnoRepository.findById(alumnoId)
						.orElseThrow(() -> new IllegalArgumentException("Alumno no encontrado: " + alumnoId));
				byte[] pdfBytes = generarPdfCertificadoCobros(construirCertificadoCobros(alumno, ano));
				ZipEntry entry = new ZipEntry(construirNombreSeguroCertificado(alumno, ano) + ".pdf");
				zipOutputStream.putNextEntry(entry);
				zipOutputStream.write(pdfBytes);
				zipOutputStream.closeEntry();
			}
			zipOutputStream.finish();
			return outputStream.toByteArray();
		} catch (IOException ex) {
			throw new RuntimeException("Error al generar el ZIP de certificados de cobros", ex);
		}
	}

	private void validarCertificadoCobros(Long alumnoId, Integer ano) {
		if (alumnoId == null) {
			throw new IllegalArgumentException("Debes seleccionar un alumno.");
		}
		if (ano == null || ano < 1900 || ano > 2200) {
			throw new IllegalArgumentException("El año no es válido.");
		}
	}

	private CertificadoCobrosData construirCertificadoCobros(Alumno alumno, Integer ano) {
		FiltroTesoreria filtro = construirFiltro(null, ano, "TODOS");
		List<ProductoAlumno> movimientos = obtenerMovimientosFiltrados(filtro, true, null, false,
				FiltroSedesTesoreria.TODAS).stream()
				.filter(this::esConceptoCertificable)
				.filter(pa -> Objects.equals(obtenerAlumnoId(pa), alumno.getId()))
				.collect(Collectors.toList());

		double[] importesPorMes = new double[12];
		for (ProductoAlumno movimiento : movimientos) {
			PeriodoMovimiento periodo = obtenerPeriodoMovimiento(
					movimiento.getConcepto(),
					tipoProducto(movimiento),
					movimiento.getFechaAsignacion(),
					movimiento.getFechaPago());
			if (periodo != null && Objects.equals(periodo.ano, ano) && periodo.mes != null && periodo.mes >= 1 && periodo.mes <= 12) {
				importesPorMes[periodo.mes - 1] += ImporteCargo.sinRedondear(movimiento);
			}
		}

		return new CertificadoCobrosData(alumno, ano, importesPorMes);
	}

	/**
	 * Cobros del certificado: los conceptos que contaba el código hasta V27 y, además, los cargos de
	 * productos del catálogo de mensualidad, competidor o reserva de plaza (nombres de otra organización).
	 */
	private boolean esConceptoCertificable(ProductoAlumno productoAlumno) {
		String concepto = productoAlumno.getConcepto() != null
				? productoAlumno.getConcepto().toUpperCase(Locale.ROOT)
				: "";
		if (concepto.startsWith("MENSUALIDAD")
				|| concepto.startsWith("TARIFA COMPETIDOR")
				|| concepto.startsWith("RESERVA DE PLAZA")
				|| concepto.startsWith("MATRICULA")
				|| concepto.startsWith("MATRÍCULA")) {
			return true;
		}
		TipoProducto tipo = tipoProducto(productoAlumno);
		return tipo == TipoProducto.MENSUALIDAD || tipo == TipoProducto.COMPETIDOR
				|| tipo == TipoProducto.RESERVA_PLAZA;
	}

	/**
	 * Tipo del catálogo del producto cobrado, solo si su código es PROPIO (otras escuelas). NULL para los
	 * códigos heredados de Taemoi (se clasifican por concepto, como antes de V27), los productos sin
	 * clasificar y los cargos sin producto (producto borrado).
	 */
	private TipoProducto tipoProducto(ProductoAlumno productoAlumno) {
		return CatalogoLegadoTaemoi.tipoDeCatalogo(productoAlumno.getProducto(), legado());
	}

	/**
	 * Comportamiento heredado de Taemoi en la organización activa (V28): solo entonces los productos con
	 * códigos de V24 se clasifican por concepto; en las demás escuelas decide el tipo del catálogo.
	 */
	private boolean legado() {
		return comportamientoLegado.activo();
	}

	/**
	 * Certificado de cobros en PDFBox directo, editable (AcroForm con 16 campos), con la estética común de los PDF
	 * (decisión 315, {@link EstiloPdf}): cabecera con logo, nombre de la escuela, título, contexto y filete en el color de
	 * la escuela; tabla de meses con el estilo de tablas común; pie «Escuela · Certificado de cobros» y «Página 1 de 1».
	 *
	 * <p>Fuentes: las del panel, embebidas. Public Sans Regular va ENTERA (sin subconjunto) porque es también la de los
	 * campos (recurso DR del formulario y DA de cada campo): quien edite un campo puede escribir cualquier carácter de la
	 * fuente, salga o no en el documento. Public Sans SemiBold y Montserrat Bold, solo como subconjunto. Las apariencias de
	 * los campos se generan aquí ({@code NeedAppearances} a falso, como antes).
	 */
	private byte[] generarPdfCertificadoCobros(CertificadoCobrosData certificado) {
		try (PDDocument document = new PDDocument();
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);

			FuentesCertificado fuentes = new FuentesCertificado(
					PDType0Font.load(document, EstiloPdf.fuente("PublicSans-Regular.ttf"), false),
					PDType0Font.load(document, EstiloPdf.fuente("PublicSans-SemiBold.ttf"), true),
					PDType0Font.load(document, EstiloPdf.fuente("Montserrat-Bold.ttf"), true));

			PDAcroForm acroForm = new PDAcroForm(document);
			PDResources resources = new PDResources();
			resources.put(COSName.getPDFName(FUENTE_CAMPOS), fuentes.regular());
			acroForm.setDefaultResources(resources);
			acroForm.setDefaultAppearance(aparienciaCampo(CUERPO_CERTIFICADO));
			acroForm.setNeedAppearances(false);
			document.getDocumentCatalog().setAcroForm(acroForm);

			MarcaEscuela marca = marcaEscuela.deActiva();
			DatosCertificado legal = datosCertificado(marca);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				// El pie primero, como en los PDF HTML: el texto del documento acaba en los datos legales de la firma.
				dibujarPieCertificado(content, page, fuentes, marca);
				float inicioCuerpo = dibujarCabeceraCertificado(document, content, page, fuentes, marca,
						certificado.ano, legal);
				dibujarTextoCertificado(content, acroForm, page, fuentes, certificado, legal, inicioCuerpo);
			}

			acroForm.refreshAppearances();
			document.save(outputStream);
			return outputStream.toByteArray();
		} catch (IOException ex) {
			throw new RuntimeException("Error al generar el certificado PDF editable", ex);
		}
	}

	/** Fuentes del certificado, cargadas en su documento. */
	private record FuentesCertificado(PDType0Font regular, PDType0Font semiBold, PDType0Font titulos) {
	}

	/** Puntos por milímetro. */
	private static final float MM = 72f / 25.4f;
	/** Márgenes de la página, los de {@code EstiloPdf.pie}: 14 mm y 16 mm abajo (donde va el pie). */
	private static final float MARGEN_CERTIFICADO = 14 * MM;
	private static final float MARGEN_INFERIOR_CERTIFICADO = 16 * MM;
	/** Nombre de la fuente de los campos en el recurso DR del formulario. */
	private static final String FUENTE_CAMPOS = "PubSans";
	private static final float CUERPO_CERTIFICADO = 10f;
	private static final float INTERLINEA_CERTIFICADO = 21f;
	private static final float ALTO_CAMPO = 17f;
	/** Relleno interior de la apariencia que genera PDFBox (1 pt más el borde de 0,4 pt). */
	private static final float RELLENO_CAMPO = 1.4f;
	private static final String TITULO_CERTIFICADO = "Certificado de cobros";

	/** DA de un campo: Public Sans Regular a ese tamaño, en la tinta del panel. */
	private static String aparienciaCampo(float tamano) {
		float[] tinta = rgb(EstiloPdf.TINTA);
		String tamanoTexto = tamano == (int) tamano ? String.valueOf((int) tamano) : String.valueOf(tamano);
		return String.format(Locale.ROOT, "/%s %s Tf %.3f %.3f %.3f rg", FUENTE_CAMPOS, tamanoTexto, tinta[0],
				tinta[1], tinta[2]);
	}

	/** Componentes de un color {@code #rrggbb} entre 0 y 1. */
	private static float[] rgb(String hex) {
		int valor = Integer.parseInt(hex.substring(1), 16);
		return new float[] { ((valor >> 16) & 0xFF) / 255f, ((valor >> 8) & 0xFF) / 255f, (valor & 0xFF) / 255f };
	}

	private static void relleno(PDPageContentStream content, String hex) throws IOException {
		float[] color = rgb(hex);
		content.setNonStrokingColor(color[0], color[1], color[2]);
	}

	private static void trazo(PDPageContentStream content, String hex) throws IOException {
		float[] color = rgb(hex);
		content.setStrokingColor(color[0], color[1], color[2]);
	}

	/** Ancho de un texto en puntos. */
	private static float ancho(PDFont fuente, float tamano, String texto) throws IOException {
		return fuente.getStringWidth(texto) / 1000f * tamano;
	}

	/**
	 * Línea base de un texto en una caja de línea de CSS: la caja empieza en {@code arriba} y mide {@code alto}; el
	 * texto (ascendente más descendente de la fuente) va centrado en ella.
	 */
	private static float lineaBase(float arriba, float alto, PDFont fuente, float tamano) {
		float ascendente = fuente.getFontDescriptor().getAscent() / 1000f * tamano;
		float descendente = -fuente.getFontDescriptor().getDescent() / 1000f * tamano;
		return arriba - (alto - ascendente - descendente) / 2 - ascendente;
	}

	/** Línea base que centra las mayúsculas en una franja (como PDFBox centra el texto de un campo). */
	private static float lineaBaseCentrada(float abajo, float alto, PDFont fuente, float tamano) {
		float mayusculas = fuente.getFontDescriptor().getCapHeight() / 1000f * tamano;
		return abajo + (alto - mayusculas) / 2;
	}

	private void escribirTexto(PDPageContentStream content, PDFont fuente, float tamano, String color, float x,
			float y, String texto) throws IOException {
		relleno(content, color);
		content.beginText();
		content.setFont(fuente, tamano);
		content.newLineAtOffset(x, y);
		content.showText(sanitizarPdfText(texto));
		content.endText();
	}

	private void escribirTextoDerecha(PDPageContentStream content, PDFont fuente, float tamano, String color,
			float derecha, float y, String texto) throws IOException {
		escribirTexto(content, fuente, tamano, color, derecha - ancho(fuente, tamano, sanitizarPdfText(texto)), y,
				texto);
	}

	/** Pie de {@code EstiloPdf.pie}: filete de 0,5 pt; «Escuela · Certificado de cobros» y «Página 1 de 1», 7 pt. */
	private void dibujarPieCertificado(PDPageContentStream content, PDPage page, FuentesCertificado fuentes,
			MarcaEscuela marca) throws IOException {
		float derecha = page.getMediaBox().getWidth() - MARGEN_CERTIFICADO;
		float borde = MARGEN_INFERIOR_CERTIFICADO;
		trazo(content, EstiloPdf.FILETE);
		content.setLineWidth(0.5f);
		content.moveTo(MARGEN_CERTIFICADO, borde - 0.25f);
		content.lineTo(derecha, borde - 0.25f);
		content.stroke();

		float tamano = 7f;
		float base = lineaBase(borde - 0.5f - 1.6f * MM, tamano * 1.35f, fuentes.regular(), tamano);
		String escuela = marca.cabeceraInformes();
		String izquierda = escuela == null || escuela.isBlank() ? TITULO_CERTIFICADO
				: escuela + " · " + TITULO_CERTIFICADO;
		escribirTexto(content, fuentes.regular(), tamano, EstiloPdf.GRIS_PIE, MARGEN_CERTIFICADO, base, izquierda);
		escribirTextoDerecha(content, fuentes.regular(), tamano, EstiloPdf.GRIS_PIE, derecha, base, "Página 1 de 1");
	}

	/**
	 * Cabecera de {@code EstiloPdf.cabecera}: logo de 14 mm a la izquierda (si lo hay), nombre de la escuela
	 * (Montserrat Bold 13 pt), título (Public Sans SemiBold 10,5 pt) y contexto en gris (8 pt: el ejercicio y el
	 * nombre legal, el dato que llevaba la cabecera anterior); debajo, el filete de 2 pt en el color de la escuela.
	 *
	 * @return la altura (desde abajo) donde empieza el cuerpo, 5 mm bajo el filete
	 */
	private float dibujarCabeceraCertificado(PDDocument document, PDPageContentStream content, PDPage page,
			FuentesCertificado fuentes, MarcaEscuela marca, Integer ano, DatosCertificado legal) throws IOException {
		float anchoPagina = page.getMediaBox().getWidth();
		float arriba = page.getMediaBox().getHeight() - MARGEN_CERTIFICADO;

		float altoEscuela = 13f * 1.2f;
		float altoTitulo = 10.5f * 1.3f;
		float altoContexto = 8f * 1.3f;
		float altoTextos = altoEscuela + 0.8f * MM + altoTitulo + 0.6f * MM + altoContexto;

		PDImageXObject logo = logoCertificado(document, marca);
		float altoLogo = 14 * MM;
		float altoFila = Math.max(altoTextos, logo != null ? altoLogo : 0);
		float x = MARGEN_CERTIFICADO;
		if (logo != null) {
			float anchoLogo = altoLogo * logo.getWidth() / logo.getHeight();
			content.drawImage(logo, x, arriba - (altoFila + altoLogo) / 2, anchoLogo, altoLogo);
			x += anchoLogo + 3.5f * MM;
		}

		float linea = arriba - (altoFila - altoTextos) / 2;
		escribirTexto(content, fuentes.titulos(), 13f, EstiloPdf.TINTA, x,
				lineaBase(linea, altoEscuela, fuentes.titulos(), 13f), marca.cabeceraInformes());
		linea -= altoEscuela + 0.8f * MM;
		escribirTexto(content, fuentes.semiBold(), 10.5f, EstiloPdf.TINTA, x,
				lineaBase(linea, altoTitulo, fuentes.semiBold(), 10.5f), TITULO_CERTIFICADO + " · " + ano);
		linea -= altoTitulo + 0.6f * MM;
		escribirTexto(content, fuentes.regular(), 8f, EstiloPdf.GRIS, x,
				lineaBase(linea, altoContexto, fuentes.regular(), 8f), "Ejercicio " + ano + " - " + legal.nombreLegal());

		float filete = arriba - altoFila - 2.4f * MM - 2f;
		relleno(content, marca.colorPrimario());
		content.addRect(MARGEN_CERTIFICADO, filete, anchoPagina - 2 * MARGEN_CERTIFICADO, 2f);
		content.fill();
		return filete - 5 * MM;
	}

	/** Logo de la escuela (Moiskimdo: Logo_nuevo_sin_vectorizar.jpeg, como siempre); sin logo, nulo. */
	private PDImageXObject logoCertificado(PDDocument document, MarcaEscuela marca) throws IOException {
		byte[] logoBytes = marca.logoCertificado();
		if (logoBytes == null) {
			return null;
		}
		return PDImageXObject.createFromByteArray(document, logoBytes,
				marca.legado() ? "Logo_nuevo_sin_vectorizar.jpeg" : "logo.png");
	}

	private static DatosCertificado datosCertificado(MarcaEscuela marca) {
		if (marca.legado()) {
			return new DatosCertificado(CLUB_NOMBRE_LEGAL, CLUB_CIF, RESPONSABLE_NOMBRE, RESPONSABLE_DNI, "Presidenta",
					"C.D. MOI'S KIMDO TAEKWONDO", "Umbrete");
		}
		MarcaEscuela.DatosLegales legal = marca.legal();
		String nombreLegal = lleno(legal.nombreLegal()) ? legal.nombreLegal().trim() : marca.nombre();
		return new DatosCertificado(nombreLegal, datoLegal(legal.cif()), datoLegal(legal.firmanteNombre()),
				datoLegal(legal.firmanteNif()), datoLegal(legal.firmanteCargo()),
				nombreLegal.toUpperCase(new Locale("es", "ES")), lleno(legal.localidad()) ? legal.localidad().trim() : null);
	}

	private static boolean lleno(String valor) {
		return valor != null && !valor.isBlank();
	}

	private static String datoLegal(String valor) {
		return lleno(valor) ? valor.trim() : DATO_LEGAL_VACIO;
	}

	/**
	 * Cuerpo del certificado: los mismos textos y campos de siempre, en el mismo orden, en Public Sans 10 pt; la tabla
	 * de meses con el estilo de tablas común; la fecha (campo) y la firma.
	 */
	private void dibujarTextoCertificado(
			PDPageContentStream content,
			PDAcroForm acroForm,
			PDPage page,
			FuentesCertificado fuentes,
			CertificadoCobrosData certificado,
			DatosCertificado legal,
			float inicioCuerpo) throws IOException {
		String alumnoNombre = obtenerNombreCompletoAlumno(certificado.alumno);
		String fecha = FECHA_CERTIFICADO_FORMATTER.format(LocalDate.now());
		String fechaGeneracion = legal.localidad() != null ? "En " + legal.localidad() + ", a " + fecha + "."
				: "A " + fecha + ".";
		PDType0Font texto = fuentes.regular();
		float cuerpo = CUERPO_CERTIFICADO;
		float x = MARGEN_CERTIFICADO;
		String tinta = EstiloPdf.TINTA;

		float y = inicioCuerpo - 30f;
		escribirTexto(content, texto, cuerpo, tinta, x, y, legal.responsable() + ", CON DNI " + legal.responsableNif()
				+ ", en calidad de " + legal.cargo() + " del");
		y -= INTERLINEA_CERTIFICADO;
		escribirTexto(content, texto, cuerpo, tinta, x, y, legal.nombreLegal() + " con C.I.F.: " + legal.cif()
				+ ", hago constar que ha recibido de");
		y -= INTERLINEA_CERTIFICADO;
		escribirTexto(content, texto, cuerpo, tinta, x, y, "D/Dña.");
		float xPagador = x + ancho(texto, cuerpo, "D/Dña. ");
		aniadirCampoTexto(acroForm, page, "pagador", alumnoNombre.toUpperCase(Locale.ROOT), xPagador - RELLENO_CAMPO,
				y - 5f, 250f, ALTO_CAMPO, cuerpo, false);
		float xCantidad = xPagador + 250f + 6f;
		escribirTexto(content, texto, cuerpo, tinta, xCantidad, y, "la cantidad de");
		float xTotal = xCantidad + ancho(texto, cuerpo, "la cantidad de ");
		aniadirCampoTexto(acroForm, page, "totalImporte", formatearEuros(certificado.total), xTotal - RELLENO_CAMPO,
				y - 5f, 80f, ALTO_CAMPO, cuerpo, false);
		y -= INTERLINEA_CERTIFICADO;
		escribirTexto(content, texto, cuerpo, tinta, x, y, "durante el ejercicio " + certificado.ano
				+ ", en concepto de matricula, mensualidades o reserva de plaza del menor");
		y -= INTERLINEA_CERTIFICADO;
		aniadirCampoTexto(acroForm, page, "alumno", alumnoNombre.toUpperCase(Locale.ROOT), x - RELLENO_CAMPO, y - 5f,
				330f, ALTO_CAMPO, cuerpo, false);
		escribirTexto(content, texto, cuerpo, tinta, x + 330f, y, ".");

		y -= 36f;
		escribirTexto(content, texto, cuerpo, tinta, x, y, "Se desglosa el importe total del ejercicio "
				+ certificado.ano + " en los siguientes importes:");

		float finTabla = dibujarTablaMeses(content, acroForm, page, fuentes, certificado, y - 10f);

		y = finTabla - 50f;
		aniadirCampoTexto(acroForm, page, "fechaGeneracion", fechaGeneracion, x - RELLENO_CAMPO, y - 5f, 300f,
				ALTO_CAMPO, cuerpo, false);
		y -= 55f;
		escribirTexto(content, fuentes.semiBold(), cuerpo, tinta, x, y, "Firmado:");
		y -= INTERLINEA_CERTIFICADO;
		escribirTexto(content, texto, cuerpo, tinta, x, y, legal.responsable());
		y -= 55f;
		escribirTexto(content, texto, cuerpo, tinta, x, y, legal.cargo().toUpperCase(new Locale("es", "ES")));
		y -= INTERLINEA_CERTIFICADO;
		escribirTexto(content, texto, cuerpo, tinta, x, y, legal.clubFirma());
		y -= INTERLINEA_CERTIFICADO;
		escribirTexto(content, texto, cuerpo, tinta, x, y, "C.I.F.: " + legal.cif());
	}

	/**
	 * Importes por mes en dos tablas (enero a junio y julio a diciembre) con el estilo de tablas común: cabecera
	 * («Mes», «Importe») en gris sobre {@code #fafaf8} con filete de 1 pt, filas con filete fino, importes a la derecha.
	 * Cada importe es un campo editable ({@code mes_1} a {@code mes_12}).
	 *
	 * @param arriba altura del borde superior de las tablas
	 * @return altura del borde inferior
	 */
	private float dibujarTablaMeses(PDPageContentStream content, PDAcroForm acroForm, PDPage page,
			FuentesCertificado fuentes, CertificadoCobrosData certificado, float arriba) throws IOException {
		float anchoUtil = page.getMediaBox().getWidth() - 2 * MARGEN_CERTIFICADO;
		float separacion = 8 * MM;
		float anchoTabla = (anchoUtil - separacion) / 2;
		float rellenoCelda = 2 * MM;
		float tamanoCabecera = 7.5f;
		float tamanoCelda = 8.5f;
		float altoCabecera = tamanoCabecera * 1.25f + 2 * 1.5f * MM;
		float altoFila = tamanoCelda * 1.35f + 2 * 1.4f * MM;
		float altoCampo = 14f;
		float abajo = arriba - altoCabecera - 6 * altoFila;

		for (int tabla = 0; tabla < 2; tabla++) {
			float izquierda = MARGEN_CERTIFICADO + tabla * (anchoTabla + separacion);
			float derecha = izquierda + anchoTabla;

			relleno(content, EstiloPdf.FONDO_CABECERA_TABLA);
			content.addRect(izquierda, arriba - altoCabecera, anchoTabla, altoCabecera);
			content.fill();
			trazo(content, EstiloPdf.FILETE_CABECERA_TABLA);
			content.setLineWidth(1f);
			content.moveTo(izquierda, arriba - altoCabecera + 0.5f);
			content.lineTo(derecha, arriba - altoCabecera + 0.5f);
			content.stroke();

			trazo(content, EstiloPdf.FILETE);
			content.setLineWidth(0.5f);
			for (int fila = 0; fila < 6; fila++) {
				float pie = arriba - altoCabecera - (fila + 1) * altoFila;
				content.moveTo(izquierda, pie + 0.25f);
				content.lineTo(derecha, pie + 0.25f);
			}
			content.stroke();
		}

		// Cabeceras de columna (vertical-align: bottom) y meses de las dos tablas fila a fila (enero y julio, febrero y
		// agosto…), como en el certificado anterior.
		float baseCabecera = arriba - altoCabecera + 1.5f * MM
				- fuentes.semiBold().getFontDescriptor().getDescent() / 1000f * tamanoCabecera;
		for (int tabla = 0; tabla < 2; tabla++) {
			float izquierda = MARGEN_CERTIFICADO + tabla * (anchoTabla + separacion);
			escribirTexto(content, fuentes.semiBold(), tamanoCabecera, EstiloPdf.GRIS, izquierda + rellenoCelda,
					baseCabecera, "Mes");
			escribirTextoDerecha(content, fuentes.semiBold(), tamanoCabecera, EstiloPdf.GRIS,
					izquierda + anchoTabla - rellenoCelda, baseCabecera, "Importe");
		}
		for (int fila = 0; fila < 6; fila++) {
			float pie = arriba - altoCabecera - (fila + 1) * altoFila;
			float base = lineaBaseCentrada(pie, altoFila, fuentes.regular(), tamanoCelda);
			for (int tabla = 0; tabla < 2; tabla++) {
				int mes = tabla * 6 + fila;
				float izquierda = MARGEN_CERTIFICADO + tabla * (anchoTabla + separacion);
				float derecha = izquierda + anchoTabla;
				escribirTexto(content, fuentes.regular(), tamanoCelda, EstiloPdf.TINTA, izquierda + rellenoCelda, base,
						nombreMes(mes));
				float anchoCampo = anchoTabla / 2;
				aniadirCampoTexto(acroForm, page, "mes_" + (mes + 1), formatearEuros(certificado.importesPorMes[mes]),
						derecha - rellenoCelda + RELLENO_CAMPO - anchoCampo, pie + (altoFila - altoCampo) / 2,
						anchoCampo, altoCampo, tamanoCelda, true);
			}
		}
		return abajo;
	}

	/** «Enero», «Febrero»… */
	private static String nombreMes(int indice) {
		String mes = MESES_ES[indice];
		return mes.charAt(0) + mes.substring(1).toLowerCase(new Locale("es", "ES"));
	}

	private void aniadirCampoTexto(
			PDAcroForm acroForm,
			PDPage page,
			String nombre,
			String valor,
			float x,
			float y,
			float width,
			float height,
			float tamano,
			boolean derecha) throws IOException {
		PDTextField field = new PDTextField(acroForm);
		field.setPartialName(nombre);
		field.setDefaultAppearance(aparienciaCampo(tamano));
		if (derecha) {
			field.setQ(PDVariableText.QUADDING_RIGHT);
		}

		PDAnnotationWidget widget = field.getWidgets().get(0);
		widget.setRectangle(new PDRectangle(x, y, width, height));
		widget.setPage(page);
		widget.setPrinted(true);
		PDBorderStyleDictionary borderStyle = new PDBorderStyleDictionary();
		borderStyle.setWidth(0.4f);
		widget.setBorderStyle(borderStyle);
		page.getAnnotations().add(widget);
		acroForm.getFields().add(field);
		field.setValue(sanitizarPdfText(valor));
	}

	private String formatearEuros(double importe) {
		return String.format(Locale.forLanguageTag("es-ES"), "%.2f €", importe);
	}

	private String sanitizarPdfText(String value) {
		if (value == null) {
			return "";
		}
		return value;
	}

	private String construirNombreSeguroCertificado(Alumno alumno, Integer ano) {
		String base = obtenerNombreCompletoAlumno(alumno).toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "_")
				.replaceAll("^_+|_+$", "");
		return "certificado_cobros_" + (base.isBlank() ? alumno.getId() : base) + "_" + ano;
	}

	private List<ProductoAlumno> obtenerMovimientosFiltrados(
			FiltroTesoreria filtro,
			Boolean pagado,
			String texto,
			Boolean soloActivos,
			FiltroSedesTesoreria sedes) {
		List<ProductoAlumno> movimientosBase = obtenerMovimientosFiltradosBase(filtro, pagado, texto, soloActivos,
				sedes);
		if (!filtro.tieneFiltroPeriodo()) {
			return movimientosBase;
		}

		return movimientosBase.stream()
				.filter(pa -> coincideConFiltroPeriodo(pa, filtro))
				.collect(Collectors.toList());
	}

	private List<ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection> obtenerMovimientosPeriodoBase(
			FiltroTesoreria filtro,
			Boolean pagado,
			String texto,
			Boolean soloActivos,
			FiltroSedesTesoreria sedes) {
		String anoTexto = obtenerAnoTexto(filtro.ano);
		String mesNombre = obtenerMesNombre(filtro.mes);

		return productoAlumnoRepository.findMovimientosTesoreriaPeriodoBase(
				filtro.deporte,
				filtro.deporteNombre,
				pagado,
				texto,
				soloActivos,
				filtro.ano,
				anoTexto,
				filtro.mes,
				mesNombre,
					legado(),
				sedes.todas(),
				sedes.parametro());
	}

	private List<ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection> filtrarMovimientosPeriodoBase(
			List<ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection> movimientosBase,
			FiltroTesoreria filtro) {
		if (!filtro.tieneFiltroPeriodo()) {
			return movimientosBase;
		}

		return movimientosBase.stream()
				.filter(pa -> coincideConFiltroPeriodo(pa, filtro))
				.collect(Collectors.toList());
	}

	private List<ProductoAlumno> obtenerMovimientosFiltradosBase(
			FiltroTesoreria filtro,
			Boolean pagado,
			String texto,
			Boolean soloActivos,
			FiltroSedesTesoreria sedes) {
		String anoTexto = obtenerAnoTexto(filtro.ano);
		String mesNombre = obtenerMesNombre(filtro.mes);

		return productoAlumnoRepository.findMovimientosTesoreriaFiltrados(
				filtro.deporte,
				filtro.deporteNombre,
				pagado,
				texto,
				soloActivos,
				filtro.ano,
				anoTexto,
				filtro.mes,
				mesNombre,
					legado(),
				sedes.todas(),
				sedes.parametro());
	}

	private Page<ProductoAlumno> obtenerPaginaMovimientosFiltrados(
			FiltroTesoreria filtro,
			Boolean pagado,
			String texto,
			Boolean soloActivos,
			Pageable pageable,
			FiltroSedesTesoreria sedes) {
		String anoTexto = obtenerAnoTexto(filtro.ano);
		String mesNombre = obtenerMesNombre(filtro.mes);

		return productoAlumnoRepository.findMovimientosTesoreriaPaginados(
				filtro.deporte,
				filtro.deporteNombre,
				pagado,
				texto,
				soloActivos,
				filtro.ano,
				anoTexto,
				filtro.mes,
				mesNombre,
				pageable,
					legado(),
				sedes.todas(),
				sedes.parametro());
	}

	private List<ProductoAlumno> obtenerMovimientosPaginaPorIds(List<Long> idsOrdenados) {
		List<ProductoAlumno> movimientos = productoAlumnoRepository.findMovimientosTesoreriaByIds(idsOrdenados);
		Map<Long, ProductoAlumno> movimientosPorId = new HashMap<>();
		for (ProductoAlumno movimiento : movimientos) {
			movimientosPorId.put(movimiento.getId(), movimiento);
		}
		List<ProductoAlumno> ordenados = new ArrayList<>();
		for (Long id : idsOrdenados) {
			ProductoAlumno movimiento = movimientosPorId.get(id);
			if (movimiento != null) {
				ordenados.add(movimiento);
			}
		}
		return ordenados;
	}

	/**
	 * Entrega 6-3 (D6-12), resumen sin filtro de período: los mismos contadores y sumas agrupados por la sede del
	 * cargo (dos consultas con el mismo {@code WHERE} que las de los totales).
	 */
	private List<TesoreriaResumenSedeDTO> porSedeDesdeConsultas(FiltroTesoreria filtro, Boolean soloActivos,
			String anoTexto, String mesNombre, FiltroSedesTesoreria sedes) {
		Map<Long, TesoreriaResumenSedeDTO> filas = new HashMap<>();
		for (ProductoAlumnoRepository.TesoreriaResumenPorSedeProjection fila : productoAlumnoRepository
				.resumirMovimientosTesoreriaPorSede(filtro.deporte, filtro.deporteNombre, null, soloActivos,
						filtro.ano, anoTexto, filtro.mes, mesNombre, legado(), sedes.todas(), sedes.parametro())) {
			TesoreriaResumenSedeDTO dto = filaVacia(fila.getSedeId());
			dto.setTotalMovimientos(valorSeguro(fila.getTotalMovimientos()));
			dto.setTotalPagados(valorSeguro(fila.getTotalPagados()));
			dto.setTotalPendientes(valorSeguro(fila.getTotalPendientes()));
			dto.setImporteTotal(valorSeguro(fila.getImporteTotal()));
			dto.setImportePagado(valorSeguro(fila.getImportePagado()));
			dto.setImportePendiente(valorSeguro(fila.getImportePendiente()));
			filas.put(fila.getSedeId(), dto);
		}
		for (ProductoAlumnoRepository.TesoreriaAlumnosPendientesPorSedeProjection fila : productoAlumnoRepository
				.contarAlumnosConPendientesTesoreriaPorSede(filtro.deporte, filtro.deporteNombre, soloActivos,
						filtro.ano, anoTexto, filtro.mes, mesNombre, legado(), sedes.todas(), sedes.parametro())) {
			filas.computeIfAbsent(fila.getSedeId(), this::filaVacia)
					.setAlumnosConPendientes(valorSeguro(fila.getAlumnosConPendientes()));
		}
		return ordenarPorSede(filas, sedes);
	}

	/** Entrega 6-3 (D6-12), resumen con filtro de período: los mismos movimientos del total, agrupados por sede. */
	private List<TesoreriaResumenSedeDTO> porSedeDesdeMovimientos(
			List<ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection> movimientos, FiltroSedesTesoreria sedes) {
		Map<Long, List<ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection>> porSede = new HashMap<>();
		for (ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection movimiento : movimientos) {
			porSede.computeIfAbsent(movimiento.getSedeId(), k -> new ArrayList<>()).add(movimiento);
		}
		Map<Long, TesoreriaResumenSedeDTO> filas = new HashMap<>();
		porSede.forEach((sedeId, deLaSede) -> {
			TesoreriaResumenSedeDTO dto = filaVacia(sedeId);
			long pagados = deLaSede.stream().filter(pa -> Boolean.TRUE.equals(pa.getPagado())).count();
			dto.setTotalMovimientos((long) deLaSede.size());
			dto.setTotalPagados(pagados);
			dto.setTotalPendientes(deLaSede.size() - pagados);
			dto.setImporteTotal(deLaSede.stream()
					.mapToDouble(pa -> ImporteCargo.sinRedondear(pa.getPrecio(), pa.getCantidad())).sum());
			dto.setImportePagado(deLaSede.stream().filter(pa -> Boolean.TRUE.equals(pa.getPagado()))
					.mapToDouble(pa -> ImporteCargo.sinRedondear(pa.getPrecio(), pa.getCantidad())).sum());
			dto.setImportePendiente(deLaSede.stream().filter(pa -> !Boolean.TRUE.equals(pa.getPagado()))
					.mapToDouble(pa -> ImporteCargo.sinRedondear(pa.getPrecio(), pa.getCantidad())).sum());
			dto.setAlumnosConPendientes(deLaSede.stream().filter(pa -> !Boolean.TRUE.equals(pa.getPagado()))
					.map(ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection::getAlumnoId).filter(Objects::nonNull)
					.distinct().count());
			filas.put(sedeId, dto);
		});
		return ordenarPorSede(filas, sedes);
	}

	/**
	 * Una fila por cada sede del filtro (gestión sin filtro: todas las de la escuela), aunque no tenga movimientos, por
	 * id; después la fila «Sin sede» (solo la gestión sin filtro), si tiene movimientos.
	 */
	private List<TesoreriaResumenSedeDTO> ordenarPorSede(Map<Long, TesoreriaResumenSedeDTO> filas,
			FiltroSedesTesoreria sedes) {
		List<TesoreriaResumenSedeDTO> resultado = new ArrayList<>();
		java.util.Set<Long> incluidas = new java.util.HashSet<>();
		for (Sede sede : sedeRepository.findAllByOrderByIdAsc()) {
			if (!sedes.incluye(sede.getId())) {
				continue;
			}
			TesoreriaResumenSedeDTO fila = filas.getOrDefault(sede.getId(), filaVacia(sede.getId()));
			fila.setNombre(sede.getNombre());
			resultado.add(fila);
			incluidas.add(sede.getId());
		}
		filas.keySet().stream().filter(Objects::nonNull).filter(id -> !incluidas.contains(id)).sorted()
				.forEach(id -> resultado.add(filas.get(id)));
		TesoreriaResumenSedeDTO sinSede = filas.get(null);
		if (sinSede != null && sinSede.getTotalMovimientos() > 0) {
			sinSede.setNombre("Sin sede");
			resultado.add(sinSede);
		}
		return resultado;
	}

	private TesoreriaResumenSedeDTO filaVacia(Long sedeId) {
		TesoreriaResumenSedeDTO fila = new TesoreriaResumenSedeDTO();
		fila.setSedeId(sedeId);
		fila.setTotalMovimientos(0L);
		fila.setTotalPagados(0L);
		fila.setTotalPendientes(0L);
		fila.setImporteTotal(0.0);
		fila.setImportePagado(0.0);
		fila.setImportePendiente(0.0);
		fila.setAlumnosConPendientes(0L);
		return fila;
	}

	private TesoreriaResumenDTO construirResumen(
			FiltroTesoreria filtro,
			long totalMovimientos,
			long totalPagados,
			long totalPendientes,
			double importeTotal,
			double importePagado,
			double importePendiente,
			long alumnosConPendientes) {
		TesoreriaResumenDTO resumen = new TesoreriaResumenDTO();
		resumen.setMes(filtro.mes);
		resumen.setAno(filtro.ano);
		resumen.setDeporte(filtro.deporte != null ? filtro.deporte : "TODOS");
		resumen.setTotalMovimientos(totalMovimientos);
		resumen.setTotalPagados(totalPagados);
		resumen.setTotalPendientes(totalPendientes);
		resumen.setImporteTotal(importeTotal);
		resumen.setImportePagado(importePagado);
		resumen.setImportePendiente(importePendiente);
		resumen.setAlumnosConPendientes(alumnosConPendientes);
		return resumen;
	}

	private long valorSeguro(Long valor) {
		return valor != null ? valor : 0L;
	}

	private double valorSeguro(Double valor) {
		return valor != null ? valor : 0.0;
	}

	private String obtenerAnoTexto(Integer ano) {
		return ano != null ? String.valueOf(ano) : null;
	}

	private String obtenerMesNombre(Integer mes) {
		return mes != null ? MESES_ES[mes - 1] : null;
	}

	private String normalizarTextoBusqueda(String texto) {
		if (texto == null) {
			return null;
		}

		String limpio = texto.trim();
		if (limpio.isEmpty()) {
			return null;
		}

		return "%" + limpio.toUpperCase(Locale.ROOT) + "%";
	}

	private Boolean normalizarSoloActivos(Boolean soloActivos) {
		return soloActivos == null ? Boolean.TRUE : soloActivos;
	}

	private PeriodoMovimiento obtenerPeriodoMovimiento(String concepto, TipoProducto tipo, Date fechaAsignacion,
			Date fechaPago) {
		Date fechaReferencia = fechaAsignacion != null ? fechaAsignacion : fechaPago;
		PeriodoMovimiento periodoFecha = obtenerPeriodoDesdeFecha(fechaReferencia);
		PeriodoMovimiento periodoConcepto = extraerPeriodoDesdeConcepto(concepto);

		if (esCategoriaConPeriodo(concepto, tipo)) {
			if (periodoConcepto != null) {
				Integer ano = periodoConcepto.ano != null
						? periodoConcepto.ano
						: (periodoFecha != null ? periodoFecha.ano : null);
				Integer mes = periodoConcepto.mes != null
						? periodoConcepto.mes
						: (periodoFecha != null ? periodoFecha.mes : null);
				if (ano != null || mes != null) {
					return new PeriodoMovimiento(ano, mes);
				}
			}
			return periodoFecha;
		}

		return periodoFecha != null ? periodoFecha : periodoConcepto;
	}

	private PeriodoMovimiento obtenerPeriodoDesdeFecha(Date fecha) {
		if (fecha == null) {
			return null;
		}
		LocalDate localDate = fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		return new PeriodoMovimiento(localDate.getYear(), localDate.getMonthValue());
	}

	/**
	 * Cobros con periodo mensual en el concepto: los que reconocía el código hasta V27 por el concepto y
	 * los de productos del catálogo de mensualidad o competidor (misma regla que las consultas).
	 */
	private boolean esCategoriaConPeriodo(String concepto, TipoProducto tipo) {
		String conceptoUpper = concepto != null ? concepto.toUpperCase(Locale.ROOT) : "";
		return conceptoUpper.startsWith("MENSUALIDAD")
				|| conceptoUpper.startsWith("TARIFA COMPETIDOR")
				|| tipo == TipoProducto.MENSUALIDAD
				|| tipo == TipoProducto.COMPETIDOR;
	}

	private PeriodoMovimiento extraerPeriodoDesdeConcepto(String concepto) {
		if (concepto == null || concepto.isBlank()) {
			return null;
		}

		String conceptoUpper = concepto.toUpperCase(Locale.ROOT);
		Matcher matcherAno = ANO_PATTERN.matcher(conceptoUpper);
		Integer ano = matcherAno.find() ? Integer.parseInt(matcherAno.group(1)) : null;

		Integer mes = null;
		for (int i = 0; i < MESES_ES.length; i++) {
			if (conceptoUpper.contains(MESES_ES[i])) {
				mes = i + 1;
				break;
			}
		}

		if (ano == null && mes == null) {
			return null;
		}

		return new PeriodoMovimiento(ano, mes);
	}

	private boolean coincideConFiltroPeriodo(ProductoAlumno productoAlumno, FiltroTesoreria filtro) {
		return coincideConFiltroPeriodo(
				productoAlumno.getConcepto(),
				tipoProducto(productoAlumno),
				productoAlumno.getFechaAsignacion(),
				productoAlumno.getFechaPago(),
				filtro);
	}

	private boolean coincideConFiltroPeriodo(
			ProductoAlumnoRepository.TesoreriaPeriodoBaseProjection productoAlumno,
			FiltroTesoreria filtro) {
		return coincideConFiltroPeriodo(
				productoAlumno.getConcepto(),
				CatalogoLegadoTaemoi.tipoDeCatalogo(productoAlumno.getProductoCodigo(), productoAlumno.getProductoTipo(), legado()),
				productoAlumno.getFechaAsignacion(),
				productoAlumno.getFechaPago(),
				filtro);
	}

	private boolean coincideConFiltroPeriodo(
			String concepto,
			TipoProducto tipo,
			Date fechaAsignacion,
			Date fechaPago,
			FiltroTesoreria filtro) {
		PeriodoMovimiento periodo = obtenerPeriodoMovimiento(concepto, tipo, fechaAsignacion, fechaPago);
		if (periodo == null) {
			return false;
		}
		if (filtro.ano != null && !filtro.ano.equals(periodo.ano)) {
			return false;
		}
		if (filtro.mes != null && !filtro.mes.equals(periodo.mes)) {
			return false;
		}
		return true;
	}

	/**
	 * Conversor de una consulta: las disciplinas (para los productos con código propio) se cargan una sola
	 * vez, y solo si algún movimiento las necesita.
	 */
	private java.util.function.Function<ProductoAlumno, TesoreriaMovimientoDTO> conversorAMovimientoDTO() {
		Map<Long, String> disciplinasPorId = new HashMap<>();
		boolean[] cargadas = { false };
		java.util.function.Supplier<Map<Long, String>> disciplinas = () -> {
			if (!cargadas[0]) {
				cargadas[0] = true;
				if (catalogoDisciplinas != null) {
					catalogoDisciplinas.listarTodas().forEach(d -> disciplinasPorId.put(d.getId(), d.getCodigo()));
				}
			}
			return disciplinasPorId;
		};
		return productoAlumno -> convertirAMovimientoDTO(productoAlumno, disciplinas);
	}

	private TesoreriaMovimientoDTO convertirAMovimientoDTO(ProductoAlumno productoAlumno,
			java.util.function.Supplier<Map<Long, String>> disciplinas) {
		Alumno alumno = obtenerAlumno(productoAlumno);
		TesoreriaMovimientoDTO dto = new TesoreriaMovimientoDTO();
		dto.setProductoAlumnoId(productoAlumno.getId());
		dto.setAlumnoId(alumno != null ? alumno.getId() : null);
		dto.setAlumnoNombreCompleto(obtenerNombreCompletoAlumno(alumno));
		dto.setAlumnoActivo(alumno != null && !Boolean.FALSE.equals(alumno.getActivo()));
		dto.setAlumnoEliminado(alumno != null && alumno.estaAnonimizado());
		dto.setDeporte(obtenerDeporte(productoAlumno, disciplinas));
		dto.setConcepto(productoAlumno.getConcepto());
		dto.setCategoria(obtenerCategoria(productoAlumno.getConcepto(), tipoProducto(productoAlumno)));
		dto.setFechaAsignacion(productoAlumno.getFechaAsignacion());
		dto.setPagado(Boolean.TRUE.equals(productoAlumno.getPagado()));
		dto.setFechaPago(productoAlumno.getFechaPago());
		// Decisión 297: «precio» del movimiento es su importe (precio × cantidad); con cantidad 1, el precio como antes.
		dto.setPrecio(ImporteCargo.sinRedondear(productoAlumno));
		dto.setNotas(productoAlumno.getNotas());
		dto.setSedeId(productoAlumno.getSedeId());
		return dto;
	}

	private Alumno obtenerAlumno(ProductoAlumno productoAlumno) {
		if (productoAlumno.getAlumnoDeporte() != null && productoAlumno.getAlumnoDeporte().getAlumno() != null) {
			return productoAlumno.getAlumnoDeporte().getAlumno();
		}
		return productoAlumno.getAlumno();
	}

	private Long obtenerAlumnoId(ProductoAlumno productoAlumno) {
		Alumno alumno = obtenerAlumno(productoAlumno);
		return alumno != null ? alumno.getId() : null;
	}

	private String obtenerNombreCompletoAlumno(Alumno alumno) {
		if (alumno == null) {
			return "Sin alumno asociado";
		}

		String nombre = alumno.getNombre() != null ? alumno.getNombre().trim() : "";
		String apellidos = alumno.getApellidos() != null ? alumno.getApellidos().trim() : "";
		String nombreCompleto = (nombre + " " + apellidos).trim();
		return nombreCompleto.isEmpty() ? "Sin nombre" : nombreCompleto;
	}

	private String obtenerDeporte(ProductoAlumno productoAlumno,
			java.util.function.Supplier<Map<Long, String>> disciplinas) {
		if (productoAlumno.getAlumnoDeporte() != null && productoAlumno.getAlumnoDeporte().getDeporte() != null) {
			return productoAlumno.getAlumnoDeporte().getDeporte();
		}

		String concepto = productoAlumno.getConcepto() != null
				? productoAlumno.getConcepto().toUpperCase(Locale.ROOT)
				: "";
		if (concepto.contains(CatalogoLegadoTaemoi.TAEKWONDO)) {
			return CatalogoLegadoTaemoi.TAEKWONDO;
		}
		if (concepto.contains("KICKBOXING")) {
			return CatalogoLegadoTaemoi.KICKBOXING;
		}
		if (concepto.contains("PILATES")) {
			return CatalogoLegadoTaemoi.PILATES;
		}
		if (concepto.contains("DEFENSA") || concepto.contains("PERSONAL")) {
			return CatalogoLegadoTaemoi.DEFENSA_PERSONAL_FEMENINA;
		}

		// Sin pista en el concepto: la disciplina del producto del catálogo, si la tiene.
		String disciplinaProducto = disciplinaDelProducto(productoAlumno, disciplinas);
		return disciplinaProducto != null ? disciplinaProducto : "GENERAL";
	}

	/**
	 * Código de la disciplina del producto cobrado, solo para productos con código PROPIO (los heredados de
	 * Taemoi dan GENERAL, como antes de V27). Por id (el enlace es perezoso y aquí no hay sesión abierta).
	 */
	private String disciplinaDelProducto(ProductoAlumno productoAlumno,
			java.util.function.Supplier<Map<Long, String>> disciplinas) {
		Producto producto = productoAlumno.getProducto();
		if (CatalogoLegadoTaemoi.tipoDeCatalogo(producto, legado()) == null || producto.getDisciplina() == null) {
			return null;
		}
		return disciplinas.get().get(producto.getDisciplina().getId());
	}

	/**
	 * Categoría del cobro: la regla por concepto del código hasta V27 y, si no reconoce el concepto
	 * (OTRO), el tipo del producto del catálogo (nombres propios de otra organización).
	 */
	private String obtenerCategoria(String concepto, TipoProducto tipo) {
		String porConcepto = obtenerCategoriaPorConcepto(concepto);
		if (!"OTRO".equals(porConcepto) || tipo == null) {
			return porConcepto;
		}
		return switch (tipo) {
		case MENSUALIDAD -> "MENSUALIDAD";
		case COMPETIDOR -> "TARIFA_COMPETIDOR";
		case LICENCIA -> "LICENCIA";
		case RESERVA_PLAZA -> "RESERVA_PLAZA";
		case EXAMEN, RECOMPENSA -> "EXAMEN";
		case OTRO -> "OTRO";
		};
	}

	private String obtenerCategoriaPorConcepto(String concepto) {
		String conceptoUpper = concepto != null ? concepto.toUpperCase(Locale.ROOT) : "";

		if (conceptoUpper.startsWith("MENSUALIDAD")) {
			return "MENSUALIDAD";
		}
		if (conceptoUpper.startsWith("TARIFA COMPETIDOR")) {
			return "TARIFA_COMPETIDOR";
		}
		if (MATRICULA_PATTERN.matcher(conceptoUpper).lookingAt()) {
			return "MATRICULA";
		}
		if (conceptoUpper.contains("LICENCIA")) {
			return "LICENCIA";
		}
		if (conceptoUpper.startsWith("RESERVA DE PLAZA")) {
			return "RESERVA_PLAZA";
		}
		if (conceptoUpper.contains("EXAMEN") || conceptoUpper.contains("RECOMPENSA")) {
			return "EXAMEN";
		}
		return "OTRO";
	}

	private FiltroTesoreria construirFiltro(Integer mes, Integer ano, String deporte) {
		if (mes != null && (mes < 1 || mes > 12)) {
			throw new IllegalArgumentException("El mes debe estar entre 1 y 12.");
		}
		if (ano != null && (ano < 1900 || ano > 2200)) {
			throw new IllegalArgumentException("El año no es válido.");
		}
		if (mes != null && ano == null) {
			throw new IllegalArgumentException("Para filtrar por mes, también debes indicar el año.");
		}

		String deporteEnum = parsearDeporte(deporte);
		String deporteNombre = deporteEnum != null ? deporteEnum : null;
		return new FiltroTesoreria(deporteEnum, deporteNombre, mes, ano);
	}

	private String parsearDeporte(String deporte) {
		if (deporte == null || deporte.isBlank() || "TODOS".equalsIgnoreCase(deporte)) {
			return null;
		}

		try {
			return catalogoDisciplinas.validarCodigo(deporte.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Deporte no válido: " + deporte);
		}
	}

	private String generarHtmlInformeTesoreria(
			List<TesoreriaMovimientoDTO> movimientos,
			FiltroTesoreria filtro,
			Boolean pagado) {
		double totalImporte = movimientos.stream()
				.mapToDouble(m -> m.getPrecio() != null ? m.getPrecio() : 0.0)
				.sum();
		long totalPagados = movimientos.stream().filter(m -> Boolean.TRUE.equals(m.getPagado())).count();
		long totalPendientes = movimientos.size() - totalPagados;

		String titulo = "Informe de Tesorería";
		String estadoLabel = pagado == null ? "Todos" : (Boolean.TRUE.equals(pagado) ? "Pagados" : "Pendientes");
		String deporteLabel = filtro.deporte != null ? filtro.deporte : "TODOS";
		String periodoLabel = obtenerPeriodoLabel(filtro.mes, filtro.ano);

		// Decisiones 309 y 315: cabecera, pie y tablas comunes, con logo, nombre de la escuela y los filtros. A4 apaisado:
		// en vertical, un cargo de DEFENSA_PERSONAL_FEMENINA (palabra sin cortes) desbordaba la tabla y se perdían las
		// columnas «Fecha pago» e «Importe»; en apaisado caben las ocho columnas sin partir ni cambiar ningún dato.
		MarcaEscuela marca = marcaEscuela.deActiva();
		String filtros = "<strong>Periodo:</strong> " + escapeHtml(periodoLabel) + " | <strong>Deporte:</strong> "
				+ escapeHtml(deporteLabel) + " | <strong>Estado:</strong> " + escapeHtml(estadoLabel);

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html><html><head><meta charset='UTF-8' />");
		html.append("<style>");
		html.append(EstiloPdf.pie(marca.cabeceraInformes(), titulo));
		html.append(EstiloPdf.cssComun());
		html.append("@page { size: A4 landscape; }");
		html.append(".resumen { margin: 0 0 3mm; }");
		html.append(".resumen span { display: inline-block; margin-right: 6mm; font-weight: 600; }");
		html.append("</style></head><body>");
		html.append(EstiloPdf.cabeceraInforme(marca, escapeHtml(titulo), filtros));
		html.append("<div class='resumen'>");
		html.append("<span>Total movimientos: ").append(movimientos.size()).append("</span>");
		html.append("<span>Pagados: ").append(totalPagados).append("</span>");
		html.append("<span>Pendientes: ").append(totalPendientes).append("</span>");
		html.append("<span>Importe total: ").append(String.format(Locale.ROOT, "%.2f", totalImporte)).append(" EUR</span>");
		html.append("</div>");

		html.append("<table><thead><tr>");
		html.append("<th>Alumno</th><th>Deporte</th><th>Concepto</th><th>Categoria</th>");
		html.append("<th class='num'>Fecha cargo</th><th>Estado</th><th class='num'>Fecha pago</th>");
		html.append("<th class='num'>Importe</th>");
		html.append("</tr></thead><tbody>");

		if (movimientos.isEmpty()) {
			html.append("<tr><td colspan='8' class='vacio'>No hay movimientos para los filtros seleccionados.</td></tr>");
		} else {
			for (TesoreriaMovimientoDTO movimiento : movimientos) {
				html.append("<tr>");
				html.append("<td>").append(escapeHtml(movimiento.getAlumnoNombreCompleto())).append("</td>");
				html.append("<td>").append(escapeHtml(movimiento.getDeporte())).append("</td>");
				html.append("<td>").append(escapeHtml(movimiento.getConcepto())).append("</td>");
				html.append("<td>").append(escapeHtml(movimiento.getCategoria())).append("</td>");
				html.append("<td class='num'>").append(escapeHtml(formatearFecha(movimiento.getFechaAsignacion())))
						.append("</td>");
				// Decisión 315: el cargo pendiente de cobro, en ocre; el pagado, neutro.
				html.append(Boolean.TRUE.equals(movimiento.getPagado()) ? "<td><span class='estado'>PAGADO</span></td>"
						: "<td><span class='pendiente-dinero'>PENDIENTE</span></td>");
				html.append("<td class='num'>").append(escapeHtml(formatearFecha(movimiento.getFechaPago())))
						.append("</td>");
				html.append("<td class='num'>")
						.append(String.format(Locale.ROOT, "%.2f", movimiento.getPrecio() != null ? movimiento.getPrecio() : 0.0))
						.append(" EUR</td>");
				html.append("</tr>");
			}
		}

		html.append("</tbody></table>");
		html.append("</body></html>");
		return html.toString();
	}

	private String formatearFecha(Date fecha) {
		if (fecha == null) {
			return "-";
		}
		LocalDate localDate = fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		return FECHA_FORMATTER.format(localDate);
	}

	private String obtenerPeriodoLabel(Integer mes, Integer ano) {
		if (ano == null) {
			return "Todos los años";
		}
		if (mes == null) {
			return "Año " + ano;
		}
		return MESES_ES[mes - 1] + " " + ano;
	}

	private String escapeCSV(String value) {
		if (value == null) {
			return "";
		}
		String escaped = value.replace("\"", "\"\"");
		if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") || escaped.contains("\"")) {
			return "\"" + escaped + "\"";
		}
		return escaped;
	}

	private String escapeHtml(String value) {
		if (value == null) {
			return "";
		}
		return value
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	private static class CertificadoCobrosData {
		private final Alumno alumno;
		private final Integer ano;
		private final double[] importesPorMes;
		private final double total;

		private CertificadoCobrosData(Alumno alumno, Integer ano, double[] importesPorMes) {
			this.alumno = alumno;
			this.ano = ano;
			this.importesPorMes = importesPorMes;
			double suma = 0.0;
			for (double importe : importesPorMes) {
				suma += importe;
			}
			this.total = suma;
		}
	}

	private static class FiltroTesoreria {
		private final String deporte;
		private final String deporteNombre;
		private final Integer mes;
		private final Integer ano;

		private FiltroTesoreria(String deporte, String deporteNombre, Integer mes, Integer ano) {
			this.deporte = deporte;
			this.deporteNombre = deporteNombre;
			this.mes = mes;
			this.ano = ano;
		}

		private boolean tieneFiltroPeriodo() {
			return this.mes != null || this.ano != null;
		}
	}

	private static class PeriodoMovimiento {
		private final Integer ano;
		private final Integer mes;

		private PeriodoMovimiento(Integer ano, Integer mes) {
			this.ano = ano;
			this.mes = mes;
		}
	}
}
