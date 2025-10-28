package com.taemoi.project.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.taemoi.project.entities.Categoria;
import com.taemoi.project.entities.Grado;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.NombresGrupo;
import com.taemoi.project.entities.Roles;
import com.taemoi.project.entities.TipoCategoria;
import com.taemoi.project.entities.TipoGrado;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.entities.Usuario;
import com.taemoi.project.repositories.CategoriaRepository;
import com.taemoi.project.repositories.GradoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.repositories.UsuarioRepository;

/**
 * Componente encargado de inicializar datos esenciales en la base de datos al arrancar la aplicación.
 * Solo inicializa datos necesarios para el funcionamiento del sistema:
 * - Grados (TipoGrado) - si no existen
 * - Categorías - si no existen
 * - Usuarios administradores - si no existen
 * - Grupos y turnos - solo si no existen (normalmente vienen de la migración)
 *
 * NOTA: Los productos vienen de la migración (extraídos del sistema de pagos antiguo)
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

		// 4. Initialize grupos (groups) if they don't exist - only if no migration was run
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
			Grupo defensaPersonalFemeninaLunesMiercoles = obtenerOcrearGrupo(
					NombresGrupo.DEFENSA_PERSONAL_FEMENINA_LUNES_MIERCOLES, "Defensa Personal Femenina");

			// 5. Initialize turnos (schedules) if they don't exist
			if (turnoRepository.count() == 0) {
				crearTurno("Lunes", "09:30", "10:30", defensaPersonalFemeninaLunesMiercoles,
						"Defensa Personal Femenina Lunes");
				crearTurno("Lunes", "17:00", "18:00", taekwondoLunesMiercolesPrimerTurno, "Taekwondo Primer Turno Lunes");
				crearTurno("Lunes", "18:00", "19:00", taekwondoLunesMiercolesSegundoTurno, "Taekwondo Segundo Turno Lunes");
				crearTurno("Lunes", "19:00", "20:30", taekwondoLunesMiercolesTercerTurno, "Taekwondo Tercer Turno Lunes");
				crearTurno("Lunes", "20:30", "21:30", kickboxingLunesMiercoles, "Kickboxing Lunes");

				crearTurno("Martes", "10:00", "11:15", pilatesMartesJueves, "Pilates Martes");
				crearTurno("Martes", "17:00", "18:00", taekwondoMartesJuevesPrimerTurno, "Taekwondo Primer Turno Martes");
				crearTurno("Martes", "18:00", "19:00", taekwondoMartesJuevesSegundoTurno, "Taekwondo Segundo Turno Martes");
				crearTurno("Martes", "19:00", "20:00", taekwondoMartesJuevesTercerTurno, "Taekwondo Tercer Turno Martes");

				crearTurno("Miércoles", "09:30", "10:30", defensaPersonalFemeninaLunesMiercoles,
						"Defensa Personal Femenina Miércoles");
				crearTurno("Miércoles", "17:00", "18:00", taekwondoLunesMiercolesPrimerTurno,
						"Taekwondo Primer Turno Miércoles");
				crearTurno("Miércoles", "18:00", "19:00", taekwondoLunesMiercolesSegundoTurno,
						"Taekwondo Segundo Turno Miércoles");
				crearTurno("Miércoles", "19:00", "20:30", taekwondoLunesMiercolesTercerTurno,
						"Taekwondo Tercer Turno Miércoles");
				crearTurno("Miércoles", "20:30", "21:30", kickboxingLunesMiercoles, "Kickboxing Miércoles");

				crearTurno("Jueves", "10:00", "11:15", pilatesMartesJueves, "Pilates Jueves");
				crearTurno("Jueves", "17:00", "18:00", taekwondoMartesJuevesPrimerTurno, "Taekwondo Primer Turno Jueves");
				crearTurno("Jueves", "18:00", "19:00", taekwondoMartesJuevesSegundoTurno, "Taekwondo Segundo Turno Jueves");
				crearTurno("Jueves", "19:00", "20:00", taekwondoMartesJuevesTercerTurno, "Taekwondo Tercer Turno Jueves");
				crearTurno("Jueves", "20:00", "21:30", competicion, "Taekwondo Competición");
			}
		}

		// Note: Products are NOT initialized here because they come from the migration
		// The migration imports real products from the old payment system (370+ products)
	}

	private Grupo obtenerOcrearGrupo(String nombreGrupo, String tipo) {
		return grupoRepository.findByNombre(nombreGrupo).orElseGet(() -> {
			Grupo nuevoGrupo = new Grupo();
			nuevoGrupo.setNombre(nombreGrupo);
			nuevoGrupo.setTipo(tipo);
			return grupoRepository.save(nuevoGrupo);
		});
	}

	private void generarUsuarios() {
		crearUsuarioSiNoExiste("moiskimdotaekwondo@gmail.com", "Moiskimdo", "Taekwondo", "09012013",
				Roles.ROLE_MANAGER);
		crearUsuarioSiNoExiste("crolyx16@gmail.com", "Carlos", "Sanchez Roman", "16082017", Roles.ROLE_ADMIN);
		crearUsuarioSiNoExiste("usuarioPrueba@gmail.com", "Prueba", "Standard User", "12345678", Roles.ROLE_USER);
	}

	private void crearUsuarioSiNoExiste(String email, String nombre, String apellidos, String contrasena, Roles rol) {
		if (usuarioRepository.findByEmail(email).isEmpty()) {
			Usuario usuario = new Usuario();
			usuario.setNombre(nombre);
			usuario.setApellidos(apellidos);
			usuario.setEmail(email);
			usuario.setContrasena(passwordEncoder.encode(contrasena));
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

}