package com.taemoi.project.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.NombresGrupo;
import com.taemoi.project.entities.Producto;
import com.taemoi.project.entities.AuthProvider;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.TipoCategoria;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.CategoriaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.ProductoRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;
import com.taemoi.project.utils.EmailUtils;

/**
 * Componente encargado de inicializar datos esenciales en la base de datos al arrancar la aplicación.
 * Solo inicializa datos necesarios para el funcionamiento del sistema:
 * - Grados (TipoGrado) - si no existen
 * - Categorías - si no existen
 * - Usuarios administradores - si no existen
 * - Grupos y turnos - solo si no existen (normalmente vienen de la migración)
 * - Productos básicos (MENSUALIDAD, LICENCIA FEDERATIVA, RESERVA DE PLAZA) - si no existen
 *
 * NOTA: La mayoría de productos vienen de la migración (extraídos del sistema de pagos antiguo)
 */
@Component
public class InicializadorDatos implements CommandLineRunner {

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private GradoRepository gradoRepository;

	@Autowired
	private GrupoRepository grupoRepository;

	@Autowired
	private TurnoRepository turnoRepository;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	/**
	 * Método que se ejecuta al arrancar la aplicación para inicializar datos en la
	 * base de datos.
	 *
	 * @param args Argumentos de la línea de comandos.
	 * @throws Exception Si ocurre un error durante la inicialización de datos.
	 */
	@Override
	public void run(String... args) throws Exception {
		// Initialize essential data only (no test/fake data)

		// 1. Initialize grados (grades) if they don't exist
		if (gradoRepository.count() == 0) {
			generarGrados();
		}

		// 2. Initialize categorias if they don't exist
		if (categoriaRepository.count() == 0) {
			generarCategorias();
		}

		// 3. Initialize admin users if they don't exist
		if (usuarioRepository.count() == 0) {
			generarUsuarios();
		}

		// 4. Initialize basic products if they don't exist
		generarProductosBasicos();

		// 5. Initialize grupos (groups) if they don't exist - only if no migration was run
		if (grupoRepository.count() == 0) {
			Grupo taekwondoLunesMiercolesPrimerTurno = obtenerOcrearGrupo(
					NombresGrupo.TAEKWONDO_LUNES_MIERCOLES_PRIMER_TURNO, "Taekwondo");
			Grupo taekwondoLunesMiercolesSegundoTurno = obtenerOcrearGrupo(
					NombresGrupo.TAEKWONDO_LUNES_MIERCOLES_SEGUNDO_TURNO, "Taekwondo");
			Grupo taekwondoLunesMiercolesTercerTurno = obtenerOcrearGrupo(
					NombresGrupo.TAEKWONDO_LUNES_MIERCOLES_TERCER_TURNO, "Taekwondo");
			Grupo taekwondoMartesJuevesPrimerTurno = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_MARTES_JUEVES_PRIMER_TURNO,
					"Taekwondo");
			Grupo taekwondoMartesJuevesSegundoTurno = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_MARTES_JUEVES_SEGUNDO_TURNO,
					"Taekwondo");
			Grupo taekwondoMartesJuevesTercerTurno = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_MARTES_JUEVES_TERCER_TURNO,
					"Taekwondo");
			Grupo competicion = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_COMPETICION, "Taekwondo Competición");
			Grupo pilatesMartesJueves = obtenerOcrearGrupo(NombresGrupo.PILATES_MARTES_JUEVES, "Pilates");
			Grupo kickboxingLunesMiercoles = obtenerOcrearGrupo(NombresGrupo.KICKBOXING_LUNES_MIERCOLES, "Kickboxing");
			Grupo defensaPersonalFemeninaMartesViernes = obtenerOcrearGrupo(
					NombresGrupo.DEFENSA_PERSONAL_FEMENINA_MARTES_VIERNES, "Defensa Personal Femenina");

			// 6. Initialize turnos (schedules) if they don't exist
			if (turnoRepository.count() == 0) {
				crearTurno("Lunes", "17:00", "18:00", taekwondoLunesMiercolesPrimerTurno, "Taekwondo Primer Turno Lunes");
				crearTurno("Lunes", "18:00", "19:00", taekwondoLunesMiercolesSegundoTurno, "Taekwondo Segundo Turno Lunes");
				crearTurno("Lunes", "19:00", "20:00", taekwondoLunesMiercolesTercerTurno, "Taekwondo Tercer Turno Lunes");
				crearTurno("Lunes", "20:15", "21:15", kickboxingLunesMiercoles, "Kickboxing Lunes");

				crearTurno("Martes", "10:00", "11:15", pilatesMartesJueves, "Pilates Martes");
				crearTurno("Martes", "17:00", "18:00", taekwondoMartesJuevesPrimerTurno, "Taekwondo Primer Turno Martes");
				crearTurno("Martes", "18:00", "19:00", taekwondoMartesJuevesSegundoTurno, "Taekwondo Segundo Turno Martes");
				crearTurno("Martes", "19:00", "20:00", taekwondoMartesJuevesTercerTurno, "Taekwondo Tercer Turno Martes");
				crearTurno("Martes", "20:00", "21:00", defensaPersonalFemeninaMartesViernes,
						"Defensa Personal Femenina Martes");

				crearTurno("Miércoles", "17:00", "18:00", taekwondoLunesMiercolesPrimerTurno,
						"Taekwondo Primer Turno Miércoles");
				crearTurno("Miércoles", "18:00", "19:00", taekwondoLunesMiercolesSegundoTurno,
						"Taekwondo Segundo Turno Miércoles");
				crearTurno("Miércoles", "19:00", "20:30", taekwondoLunesMiercolesTercerTurno,
						"Taekwondo Tercer Turno Miércoles");
				crearTurno("Miércoles", "20:00", "21:00", kickboxingLunesMiercoles, "Kickboxing Miércoles");

				crearTurno("Jueves", "10:00", "11:15", pilatesMartesJueves, "Pilates Jueves");
				crearTurno("Jueves", "17:00", "18:00", taekwondoMartesJuevesPrimerTurno, "Taekwondo Primer Turno Jueves");
				crearTurno("Jueves", "18:00", "19:00", taekwondoMartesJuevesSegundoTurno, "Taekwondo Segundo Turno Jueves");
				crearTurno("Jueves", "19:00", "20:00", taekwondoMartesJuevesTercerTurno, "Taekwondo Tercer Turno Jueves");
				crearTurno("Jueves", "20:00", "21:30", competicion, "Taekwondo Competición");
				
				crearTurno("Viernes", "18:30", "19:30", defensaPersonalFemeninaMartesViernes, "Defensa Personal Femenina Viernes");
			}
		}

		// Note: Most products come from the migration (370+ products from the old payment system)
		// Only basic/essential products are initialized above
	}

	private Grupo obtenerOcrearGrupo(String nombreGrupo, String tipo) {
		return grupoRepository.findByNombre(nombreGrupo).orElseGet(() -> {
			Grupo nuevoGrupo = new Grupo();
			nuevoGrupo.setNombre(nombreGrupo);
			nuevoGrupo.setTipo(tipo);

			// Asignar deporte basado en el tipo del grupo
			nuevoGrupo.setDeporte(determinarDeportePorTipo(tipo));

			return grupoRepository.save(nuevoGrupo);
		});
	}

	/**
	 * Determina el deporte basándose en el tipo del grupo.
	 */
	private Deporte determinarDeportePorTipo(String tipo) {
		if (tipo == null) {
			return Deporte.TAEKWONDO;
		}

		String tipoLower = tipo.toLowerCase();

		if (tipoLower.contains("taekwondo")) {
			return Deporte.TAEKWONDO;
		} else if (tipoLower.contains("kickboxing")) {
			return Deporte.KICKBOXING;
		} else if (tipoLower.contains("pilates")) {
			return Deporte.PILATES;
		} else if (tipoLower.contains("defensa") || tipoLower.contains("femenina")) {
			return Deporte.DEFENSA_PERSONAL_FEMENINA;
		}

		// Por defecto, asignar Taekwondo
		return Deporte.TAEKWONDO;
	}

	private void generarUsuarios() {
		crearUsuarioSiNoExiste("moiskimdotaekwondo@gmail.com", "Moiskimdo", "Taekwondo", "09012013",
				Roles.ROLE_ADMIN);
		crearUsuarioSiNoExiste("crolyx16@gmail.com", "Carlos", "Sanchez Roman", "16082017", Roles.ROLE_ADMIN);
		crearUsuarioSiNoExiste("usuarioPrueba@gmail.com", "Prueba", "Standard User", "12345678", Roles.ROLE_USER);
	}

	private void crearUsuarioSiNoExiste(String email, String nombre, String apellidos, String contrasena, Roles rol) {
		String normalizedEmail = EmailUtils.normalizeEmail(email);
		if (usuarioRepository.findByEmailIgnoreCase(normalizedEmail).isEmpty()) {
			Usuario usuario = new Usuario();
			usuario.setNombre(nombre);
			usuario.setApellidos(apellidos);
			usuario.setEmail(normalizedEmail);
			usuario.setContrasena(passwordEncoder.encode(contrasena));
			usuario.setAuthProvider(AuthProvider.LOCAL);
			usuario.getRoles().add(rol);
			usuarioRepository.save(usuario);
		}
	}

	/**
	 * Genera los grados si no existen en la base de datos.
	 */
	private void generarGrados() {
		for (TipoGrado tipoGrado : TipoGrado.values()) {
			if (!gradoRepository.existsByTipoGrado(tipoGrado)) {
				Grado nuevoGrado = new Grado();
				nuevoGrado.setTipoGrado(tipoGrado);
				gradoRepository.save(nuevoGrado);
			}
		}
	}

	private void generarCategorias() {
		for (TipoCategoria tipoCategoria : TipoCategoria.values()) {
			if (!categoriaRepository.existsByNombre(tipoCategoria.getNombre())) {
				Categoria categoria = new Categoria();
				categoria.setNombre(tipoCategoria.getNombre());
				categoriaRepository.save(categoria);
			}
		}
	}

	private void crearTurno(String dia, String horaInicio, String horaFin, Grupo grupo, String tipo) {
		Turno nuevoTurno = new Turno();
		nuevoTurno.setDiaSemana(dia);
		nuevoTurno.setHoraInicio(horaInicio);
		nuevoTurno.setHoraFin(horaFin);
		nuevoTurno.setTipo(tipo);
		nuevoTurno.setGrupo(grupo);
		turnoRepository.save(nuevoTurno);
	}

	/**
	 * Genera los productos básicos necesarios para el funcionamiento del sistema.
	 * Estos productos son esenciales para las operaciones básicas:
	 * - MENSUALIDAD: Producto genérico para mensualidades (precio base 28€)
	 * - LICENCIA FEDERATIVA: Alta de licencia federativa (sin coste adicional, 0€)
	 * - RESERVA DE PLAZA: Reserva de plaza para nuevos alumnos (10€)
	 */
	private void generarProductosBasicos() {
		crearProductoSiNoExiste("MENSUALIDAD", 28.0);
		crearProductoSiNoExiste("LICENCIA FEDERATIVA", 0.0);
		crearProductoSiNoExiste("RESERVA DE PLAZA", 10.0);
		crearProductoSiNoExiste("TARIFA COMPETIDOR TAEKWONDO", 20.0);
		crearProductoSiNoExiste("TARIFA COMPETIDOR KICKBOXING", 20.0);
	}

	/**
	 * Crea un producto si no existe en la base de datos.
	 *
	 * @param concepto El concepto/nombre del producto.
	 * @param precio El precio del producto.
	 */
	private void crearProductoSiNoExiste(String concepto, Double precio) {
		if (productoRepository.findByConcepto(concepto).isEmpty()) {
			Producto producto = new Producto();
			producto.setConcepto(concepto);
			producto.setPrecio(precio);
			productoRepository.save(producto);
		}
	}

}
