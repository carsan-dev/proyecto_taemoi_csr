package com.taemoi.project.pdf;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.test.util.ReflectionTestUtils;

import com.taemoi.project.catalogo.CatalogoTaemoiEnMemoria;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.PlantillaPreinscripcion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.ProductoAlumno;
import com.taemoi.project.entities.Turno;

/**
 * Datos ficticios y deterministas para los tests de contenido de los PDF: cinco alumnos con inscripciones en las cuatro
 * disciplinas de Taemoi (grados, licencias en vigor, caducadas y sin licencia, competidores con y sin categoría, una
 * alumna inactiva), sus turnos y sus cargos (mensualidades pagadas, pendientes y canceladas, tarifa de competidor,
 * reservas de plaza y un producto suelto). «Hoy» es {@link ContenidoPdf#HOY}.
 */
final class DatosPdfPrueba {

	final CatalogoTaemoiEnMemoria catalogo = new CatalogoTaemoiEnMemoria();

	final Alumno ana = alumno(1L, "Ana", "Prueba Uno", 101, LocalDate.of(2015, 5, 10), true);
	final Alumno bruno = alumno(2L, "Bruno", "Prueba Dos", 102, LocalDate.of(1990, 2, 20), true);
	final Alumno carla = alumno(3L, "Carla", "Prueba Tres", 103, LocalDate.of(2013, 7, 7), false);
	final Alumno diana = alumno(4L, "Diana", "Prueba Cuatro", 104, LocalDate.of(1985, 11, 30), true);
	final Alumno eva = alumno(5L, "Eva", "Prueba Cinco", 105, LocalDate.of(2000, 4, 4), true);
	final List<Alumno> alumnos = List.of(ana, bruno, carla, diana, eva);

	// Inscripciones.
	final AlumnoDeporte anaTkd = inscripcion(ana, "TAEKWONDO", "AMARILLO", fecha(2026, 3, 1), 5001, fecha(2026, 1, 10),
			true, "INFANTIL", 28.0);
	final AlumnoDeporte brunoTkd = inscripcion(bruno, "TAEKWONDO", "AZUL", fecha(2025, 6, 1), 5002, fecha(2025, 1, 15),
			true, "ADULTO", 30.0);
	final AlumnoDeporte brunoKb = inscripcion(bruno, "KICKBOXING", "VERDE", fecha(2026, 2, 1), null, null, true,
			"KICKBOXING", 30.0);
	final AlumnoDeporte carlaTkd = inscripcion(carla, "TAEKWONDO", "BLANCO_AMARILLO", fecha(2025, 12, 1), null, null,
			true, "INFANTIL", 28.0);
	final AlumnoDeporte carlaDpf = inscripcion(carla, "DEFENSA_PERSONAL_FEMENINA", null, null, null, null, null,
			"DEFENSA_PERSONAL_FEMENINA", 25.0);
	final AlumnoDeporte dianaDpf = inscripcion(diana, "DEFENSA_PERSONAL_FEMENINA", null, null, null, null, null,
			"DEFENSA_PERSONAL_FEMENINA", 25.0);
	final AlumnoDeporte dianaPilates = inscripcion(diana, "PILATES", null, null, null, null, null, "PILATES", 30.0);
	final AlumnoDeporte evaTkd = inscripcion(eva, "TAEKWONDO", "NEGRO_1_DAN", fecha(2024, 5, 5), 5005,
			fecha(2026, 2, 2), false, "ADULTO", 30.0);
	final List<AlumnoDeporte> inscripciones = List.of(anaTkd, brunoTkd, brunoKb, carlaTkd, carlaDpf, dianaDpf,
			dianaPilates, evaTkd);

	// Turnos (ids 9 a 12).
	final Turno lunesInfantil = turno(9L, "Lunes", "17:00", "18:00", grupo(3L, "Taekwondo infantil", "TAEKWONDO"),
			ana, carla);
	final Turno miercolesKb = turno(10L, "Miércoles", "19:00", "20:00",
			grupo(4L, "Kickboxing adultos", "KICKBOXING"), bruno);
	final Turno lunesAdultos = turno(11L, "Lunes", "20:00", "21:00", grupo(5L, "Taekwondo adultos", "TAEKWONDO"),
			bruno, eva);
	final Turno martesPilates = turno(12L, "Martes", "10:00", "11:00", grupo(6L, "Pilates mañanas", "PILATES"),
			diana);
	final List<Turno> turnos = List.of(lunesInfantil, miercolesKb, lunesAdultos, martesPilates);

	// Cargos.
	final ProductoAlumno anaOctubre = cargo(11L, anaTkd, "MENSUALIDAD OCTUBRE 2026 - TAEKWONDO", 28.0, 1,
			fecha(2026, 10, 1), fecha(2026, 10, 5), "Pagado por Bizum");
	final ProductoAlumno anaSeptiembre = cargo(12L, anaTkd, "MENSUALIDAD SEPTIEMBRE 2026 - TAEKWONDO", 28.0, 1,
			fecha(2026, 9, 1), null, null);
	final ProductoAlumno brunoOctubreTkd = cargo(13L, brunoTkd, "MENSUALIDAD OCTUBRE 2026 - TAEKWONDO", 30.0, 1,
			fecha(2026, 10, 1), null, null);
	final ProductoAlumno brunoOctubreKb = cargo(14L, brunoKb, "MENSUALIDAD OCTUBRE 2026 - KICKBOXING", 30.0, 1,
			fecha(2026, 10, 1), fecha(2026, 10, 3), null);
	final ProductoAlumno brunoCompetidor = cargo(15L, brunoTkd, "TARIFA COMPETIDOR OCTUBRE 2026 - TAEKWONDO", 12.5, 2,
			fecha(2026, 10, 2), null, "Dos plazos");
	final ProductoAlumno carlaOctubreCancelada = cancelado(cargo(16L, carlaTkd, "MENSUALIDAD OCTUBRE 2026 - TAEKWONDO",
			28.0, 1, fecha(2026, 10, 1), null, null));
	final ProductoAlumno anaEnero = cargo(17L, anaTkd, "MENSUALIDAD ENERO 2026 - TAEKWONDO", 26.0, 1,
			fecha(2026, 1, 1), fecha(2026, 1, 8), null);
	final ProductoAlumno anaReserva = cargo(18L, anaTkd, "RESERVA DE PLAZA 2026/2027 - TAEKWONDO", 30.0, 1,
			fecha(2026, 6, 15), fecha(2026, 6, 16), null);
	final ProductoAlumno brunoReserva = cargo(19L, brunoKb, "RESERVA DE PLAZA 2026/2027 - KICKBOXING", 30.0, 1,
			fecha(2026, 6, 20), null, null);
	final ProductoAlumno dianaCamiseta = cargo(20L, dianaDpf, "Camiseta escuela", 15.0, 2, fecha(2026, 9, 10), null,
			"Talla M");

	/** Mensualidades y tarifas de competidor (lo que devuelve la consulta de mensualidades). */
	final List<ProductoAlumno> mensualidades = List.of(anaOctubre, anaSeptiembre, brunoOctubreTkd, brunoOctubreKb,
			brunoCompetidor, carlaOctubreCancelada, anaEnero);
	/** Cargos de octubre de 2026 (mensualidades y competidor). */
	final List<ProductoAlumno> octubre = List.of(anaOctubre, brunoOctubreTkd, brunoOctubreKb, brunoCompetidor,
			carlaOctubreCancelada);
	final List<ProductoAlumno> reservas = List.of(anaReserva, brunoReserva);
	/** Cargos sin pagar y sin cancelar. */
	final List<ProductoAlumno> impagados = List.of(anaSeptiembre, brunoOctubreTkd, brunoCompetidor, brunoReserva,
			dianaCamiseta);
	/** Cargos pagados (los cobros del certificado de Ana: enero, octubre y la reserva). */
	final List<ProductoAlumno> pagados = List.of(anaEnero, anaOctubre, anaReserva, brunoOctubreKb);

	final List<Producto> productos = List.of(producto("Mensualidad adulto", 30.0), producto("Camiseta escuela", 15.0),
			producto("Dobok", null), producto("Cinturón", 8.5));

	DatosPdfPrueba() {
		anaTkd.setCompetidor(true);
		anaTkd.setCategoria(categoria("Infantil -40 kg"));
		anaTkd.setPeso(38.5);
		anaTkd.setFechaPeso(fecha(2026, 9, 1));
		brunoTkd.setCompetidor(true);
		brunoTkd.setCategoria(categoria("Senior -80 kg"));
		brunoTkd.setPeso(79.0);
		brunoTkd.setFechaPeso(fecha(2026, 9, 2));
		brunoKb.setCompetidor(true);
		for (Alumno alumno : alumnos) {
			alumno.setDeportes(new ArrayList<>(inscripciones.stream().filter(i -> i.getAlumno() == alumno).toList()));
			alumno.setTurnos(new ArrayList<>(turnos.stream().filter(t -> t.getAlumnos().contains(alumno)).toList()));
		}
	}

	static Date fecha(int anio, int mes, int dia) {
		return Date.from(LocalDate.of(anio, mes, dia).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant());
	}

	private static Alumno alumno(long id, String nombre, String apellidos, int expediente, LocalDate nacimiento,
			boolean activo) {
		Alumno alumno = new Alumno();
		alumno.setId(id);
		alumno.setNombre(nombre);
		alumno.setApellidos(apellidos);
		alumno.setNumeroExpediente(expediente);
		alumno.setFechaNacimiento(java.sql.Date.valueOf(nacimiento));
		alumno.setActivo(activo);
		alumno.setSedePrincipalId(1L);
		return alumno;
	}

	private AlumnoDeporte inscripcion(Alumno alumno, String disciplina, String grado, Date fechaGrado, Integer licencia,
			Date fechaLicencia, Boolean apto, String tarifa, Double cuantia) {
		AlumnoDeporte inscripcion = new AlumnoDeporte();
		inscripcion.setAlumno(alumno);
		inscripcion.setDisciplina(catalogo.disciplina(disciplina));
		if (grado != null) {
			inscripcion.setGrado(catalogo.grado(disciplina, grado));
		}
		inscripcion.setFechaGrado(fechaGrado);
		inscripcion.setNumeroLicencia(licencia);
		inscripcion.setTieneLicencia(licencia != null);
		inscripcion.setFechaLicencia(fechaLicencia);
		inscripcion.setAptoParaExamen(apto);
		inscripcion.setActivo(true);
		inscripcion.setTarifa(CatalogoTaemoiEnMemoria.tarifaTaemoi(tarifa));
		inscripcion.setCuantiaTarifa(cuantia);
		return inscripcion;
	}

	private Grupo grupo(long id, String nombre, String disciplina) {
		Grupo grupo = new Grupo();
		ReflectionTestUtils.setField(grupo, "id", id);
		grupo.setNombre(nombre);
		grupo.setDisciplina(catalogo.disciplina(disciplina));
		return grupo;
	}

	private static Turno turno(long id, String dia, String inicio, String fin, Grupo grupo, Alumno... alumnos) {
		Turno turno = new Turno();
		ReflectionTestUtils.setField(turno, "id", id);
		turno.setDiaSemana(dia);
		turno.setHoraInicio(inicio);
		turno.setHoraFin(fin);
		turno.setGrupo(grupo);
		turno.setAlumnos(new ArrayList<>(List.of(alumnos)));
		grupo.setAlumnos(List.of(alumnos));
		return turno;
	}

	private static ProductoAlumno cargo(long id, AlumnoDeporte inscripcion, String concepto, double precio,
			int cantidad, Date asignacion, Date pago, String notas) {
		ProductoAlumno cargo = new ProductoAlumno();
		ReflectionTestUtils.setField(cargo, "id", id);
		cargo.setAlumno(inscripcion.getAlumno());
		cargo.setAlumnoDeporte(inscripcion);
		cargo.setConcepto(concepto);
		cargo.setPrecio(precio);
		cargo.setCantidad(cantidad);
		cargo.setFechaAsignacion(asignacion);
		cargo.setPagado(pago != null);
		cargo.setFechaPago(pago);
		cargo.setNotas(notas);
		return cargo;
	}

	private static ProductoAlumno cancelado(ProductoAlumno cargo) {
		cargo.setCanceladoEn(LocalDateTime.of(2026, 10, 4, 10, 0));
		cargo.setMotivoCancelacion("Baja temporal");
		return cargo;
	}

	private static Categoria categoria(String nombre) {
		Categoria categoria = new Categoria();
		categoria.setNombre(nombre);
		return categoria;
	}

	private static Producto producto(String concepto, Double precio) {
		Producto producto = new Producto();
		producto.setConcepto(concepto);
		producto.setPrecio(precio);
		return producto;
	}

	/** Solicitud de preinscripción firmada (datos ficticios; firma dibujada en el test). */
	Preinscripcion preinscripcion() throws IOException {
		Preinscripcion p = new Preinscripcion();
		p.setReferencia("PRE-2026-CONTENIDO");
		p.setTemporada("2026-2027");
		p.setDisciplina(catalogo.disciplina("TAEKWONDO"));
		p.setNombre("Lucía");
		p.setApellidos("Prueba Ficticia");
		p.setDni("12345678Z");
		p.setFechaNacimiento(LocalDate.of(2012, 4, 9));
		p.setDireccion("Calle Ficticia 1, Villaprueba");
		p.setTelefono("600000000");
		p.setTelefono2("611111111");
		p.setEmail("lucia@example.test");
		p.setAceptacionNormas(true);
		p.setConsentimientoFotografico(true);
		p.setFirmanteNombre("Marta Prueba Ficticia");
		p.setGrupoSnapshot("Taekwondo · 10-14 años · Lunes 17:00-18:00");
		PlantillaPreinscripcion plantilla = new PlantillaPreinscripcion();
		plantilla.setContenido("{\"contacto\":\"Calle Ficticia 1\",\"normas\":[\"Primera norma de prueba.\","
				+ "\"Segunda norma de prueba.\"]}");
		p.setPlantilla(plantilla);
		BufferedImage firma = new BufferedImage(80, 30, BufferedImage.TYPE_INT_RGB);
		var g = firma.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 80, 30);
		g.setColor(Color.BLUE);
		g.drawLine(5, 20, 70, 8);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(firma, "png", out);
		p.setFirma(out.toByteArray());
		return p;
	}
}
