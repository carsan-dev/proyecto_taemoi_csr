package com.taemoi.project.configuracion;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.github.javafaker.Faker;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Turno;
import com.taemoi.project.entidades.Categoria;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.NombresGrupo;
import com.taemoi.project.entidades.Roles;
import com.taemoi.project.entidades.TipoCategoria;
import com.taemoi.project.entidades.TipoGrado;
import com.taemoi.project.entidades.TipoTarifa;
import com.taemoi.project.entidades.Usuario;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.CategoriaRepository;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.repositorios.GrupoRepository;
import com.taemoi.project.repositorios.TurnoRepository;
import com.taemoi.project.repositorios.UsuarioRepository;
import com.taemoi.project.servicios.AlumnoService;
import com.taemoi.project.servicios.GrupoService;

/**
 * Componente encargado de inicializar datos en la base de datos al arrancar la
 * aplicación.
 */
@Component
public class InicializadorDatos implements CommandLineRunner {
    private static final String LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";

	/**
	 * Inyección del repositorio de alumno.
	 */
	@Autowired
	private AlumnoRepository alumnoRepository;
	
	@Autowired
	private AlumnoService alumnoService;

	/**
	 * Inyección del repositorio de usuario.
	 */
	@Autowired
	private UsuarioRepository usuarioRepository;

	/**
	 * Inyección del repositorio de categoría.
	 */
	@Autowired
	private CategoriaRepository categoriaRepository;

	/**
	 * Inyección del repositorio de grado.
	 */
	@Autowired
	private GradoRepository gradoRepository;

	/**
	 * Inyección del repositorio de grupo.
	 */
	@Autowired
	private GrupoRepository grupoRepository;

	/**
	 * Inyección del servicio de grupo.
	 */
	@Autowired
	private GrupoService grupoService;

	@Autowired
	private TurnoRepository turnoRepository;

	/**
	 * Inyección del codificador de contraseñas.
	 */
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

	    Grupo lunesMiercolesInfantil = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_LUNES_MIERCOLES_INFANTIL);
	    Grupo lunesMiercolesJoven = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_LUNES_MIERCOLES_JOVEN);
	    Grupo lunesMiercolesAdulto = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_LUNES_MIERCOLES_ADULTO);
	    Grupo martesJuevesInfantil = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_MARTES_JUEVES_INFANTIL);
	    Grupo martesJuevesJoven = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_MARTES_JUEVES_JOVEN);
	    Grupo martesJuevesAdulto = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_MARTES_JUEVES_ADULTO);
	    Grupo competicion = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_COMPETICION);
	    Grupo pilates = obtenerOcrearGrupo(NombresGrupo.PILATES);
	    Grupo kickboxing = obtenerOcrearGrupo(NombresGrupo.KICKBOXING);
		
	    if (turnoRepository.count() == 0) {
			crearTurno("Lunes", "17:00", "18:00", lunesMiercolesInfantil, "Taekwondo Infantil");
			crearTurno("Lunes", "18:00", "19:00", lunesMiercolesJoven, "Taekwondo Joven");
			crearTurno("Lunes", "19:00", "20:30", lunesMiercolesAdulto, "Taekwondo Adulto");
			crearTurno("Lunes", "20:30", "21:30", kickboxing, "Kickboxing");
			
			crearTurno("Martes", "10:00", "11:15", pilates, "Pilates");
			crearTurno("Martes", "17:00", "18:00", martesJuevesInfantil, "Taekwondo Infantil");
			crearTurno("Martes", "18:00", "19:00", martesJuevesJoven, "Taekwondo Joven");
			crearTurno("Martes", "19:00", "20:00", martesJuevesAdulto, "Taekwondo Adulto");
			
			crearTurno("Miércoles", "17:00", "18:00", lunesMiercolesInfantil, "Taekwondo Infantil");
			crearTurno("Miércoles", "18:00", "19:00", lunesMiercolesJoven, "Taekwondo Joven");
			crearTurno("Miércoles", "19:00", "20:30", lunesMiercolesAdulto, "Taekwondo Adulto");
			crearTurno("Miércoles", "20:30", "21:30", kickboxing, "Kickboxing");
			
			crearTurno("Jueves", "10:00", "11:15", pilates, "Pilates");
			crearTurno("Jueves", "17:00", "18:00", martesJuevesInfantil, "Taekwondo Infantil");
			crearTurno("Jueves", "18:00", "19:00", martesJuevesJoven, "Taekwondo Joven");
			crearTurno("Jueves", "19:00", "20:00", martesJuevesAdulto, "Taekwondo Adulto");
			crearTurno("Jueves", "20:00", "21:30", competicion, "Taekwondo Competición");
	    }
        if (gradoRepository.count() == 0) {
            generarGrados();
        }

        if (usuarioRepository.count() == 0) {
            generarUsuarios();
        }

        if (categoriaRepository.count() == 0) {
            generarCategorias();
        }

        if (alumnoRepository.count() == 0) {
            generarAlumnos();
        }

        asignarAlumnosAGrupoAleatorio();
        asignarAlumnosAGrupoYTurnos();
	}
	
	private Grupo obtenerOcrearGrupo(String nombreGrupo) {
	    return grupoRepository.findByNombre(nombreGrupo)
	            .orElseGet(() -> {
	                Grupo nuevoGrupo = new Grupo();
	                nuevoGrupo.setNombre(nombreGrupo);
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
	
    private void asignarAlumnosAGrupoYTurnos() {
        List<Alumno> alumnos = alumnoRepository.findAll();
        for (Alumno alumno : alumnos) {
            if (alumno.getTurnos().isEmpty()) {
                List<Grupo> grupos = alumno.getGrupos();
                for (Grupo grupo : grupos) {
                    List<Turno> turnos = turnoRepository.findByGrupo(grupo);
                    for (Turno turno : turnos) {
                        alumno.addTurno(turno);
                    }
                }
                alumnoRepository.save(alumno);
            }
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

	private void generarAlumnos() {
		Faker faker = new Faker(new Locale("es"));
		for (int i = 0; i < 20; i++) {
			Alumno alumno = generarAlumno(faker);
			if (!alumnoRepository.existsByEmail(alumno.getEmail())) {
				alumno = alumnoService.crearAlumno(alumno);
				crearUsuarioParaAlumno(alumno);
			}
		}
	}

	public void crearUsuarioParaAlumno(Alumno alumno) {
		Usuario usuario = new Usuario();
		usuario.setNombre(alumno.getNombre());
		usuario.setApellidos(alumno.getApellidos());
		usuario.setEmail(alumno.getEmail());
		String nombreMinusculas = alumno.getNombre().toLowerCase().replaceAll("\\s", "");
		String apellidosMinusculas = alumno.getApellidos().toLowerCase().replaceAll("\\s", "");
		String nombreCompleto = nombreMinusculas + apellidosMinusculas;
		String contrasenaCodificada = passwordEncoder.encode(nombreCompleto);
		usuario.setContrasena(contrasenaCodificada);
		usuario.getRoles().add(Roles.ROLE_USER);
		usuario.setAlumno(alumno);
		usuarioRepository.save(usuario);
	}

	/**
	 * Genera un alumno aleatorio utilizando la biblioteca Faker y le crea y asigna
	 * un usuario.
	 *
	 * @param faker Objeto Faker para generar datos aleatorios.
	 * @return El alumno generado.
	 */
	private Alumno generarAlumno(Faker faker) {
	    Alumno alumno = new Alumno();
	    alumno.setNombre(faker.name().firstName());
	    alumno.setApellidos(faker.name().lastName());
	    alumno.setFechaNacimiento(faker.date().birthday());
	    alumno.setNif(generarNif(faker));
	    alumno.setDireccion(faker.address().fullAddress());
	    alumno.setTelefono(faker.number().numberBetween(100000000, 999999999));
	    alumno.setEmail(faker.internet().emailAddress());
	    alumno.setCuantiaTarifa(faker.number().randomDouble(2, 50, 200));
	    alumno.setFechaAlta(faker.date().birthday());
	    alumno.setFechaBaja(null);

	    // Asignar tipo de tarifa aleatoria
	    TipoTarifa tipoTarifa = TipoTarifa.values()[faker.number().numberBetween(0, TipoTarifa.values().length)];
	    alumno.setTipoTarifa(tipoTarifa);

	    // Asignar cuantía de la tarifa según el tipo
	    double cuantiaTarifa = asignarCuantiaTarifa(tipoTarifa);
	    alumno.setCuantiaTarifa(cuantiaTarifa);

	    // Convertir la fecha de nacimiento de alumno a LocalDate
	    LocalDate fechaNacimiento = alumno.getFechaNacimiento().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	    int edad = Period.between(fechaNacimiento, LocalDate.now()).getYears();

	    // Llamar al método asignarGradoSegunEdad que requiere la edad y la fecha de nacimiento
	    alumno.setGrado(asignarGradoSegunEdad(edad, fechaNacimiento));
	    
	    alumno.setFechaGrado(new Date());

	    return alumno;
	}


	private void crearTurno(String dia, String horaInicio, String horaFin, Grupo grupo, String tipo ) {
		Turno nuevoTurno = new Turno();
		nuevoTurno.setDiaSemana(dia);
		nuevoTurno.setHoraInicio(horaInicio);
		nuevoTurno.setHoraFin(horaFin);
		nuevoTurno.setTipo(tipo);
		nuevoTurno.setGrupo(grupo);
		turnoRepository.save(nuevoTurno);
	}

    private static String generarNif(Faker faker) {
        String numbers = String.format("%08d", faker.number().numberBetween(0, 100000000));
        int index = Integer.parseInt(numbers) % 23;
        char letter = LETTERS.charAt(index);
        return numbers + letter;
    }

	/**
	 * Asigna la cuantía de la tarifa según el tipo de tarifa.
	 *
	 * @param tipoTarifa Tipo de tarifa.
	 * @return La cuantía de la tarifa asignada.
	 */
	private double asignarCuantiaTarifa(TipoTarifa tipoTarifa) {
		switch (tipoTarifa) {
		case ADULTO:
			return 30.0;
		case ADULTO_GRUPO:
			return 20.0;
		case FAMILIAR:
			return 0.0;
		case INFANTIL:
			return 25.0;
		case INFANTIL_GRUPO:
			return 20.0;
		case HERMANOS:
			return 23.0;
		case PADRES_HIJOS:
			return 0.0;
		default:
			throw new IllegalArgumentException("Tipo de tarifa no válido: " + tipoTarifa);
		}
	}

	/**
	 * Asigna el grado según la edad del alumno.
	 *
	 * @param edad Edad del alumno.
	 * @return El grado asignado.
	 */
	private Grado asignarGradoSegunEdad(int edad, LocalDate fechaNacimiento) {
	    LocalDate fechaActual = LocalDate.now();

	    // Verificar si cumple 14 años en el año actual
	    boolean cumpleCatorceEsteAno = fechaNacimiento.plusYears(14).getYear() == fechaActual.getYear();

	    // Se considera menor si tiene menos de 13 años o tiene 13 pero no cumple 14 este año
	    boolean esMenor = edad < 13 || (edad == 13 && !cumpleCatorceEsteAno);

	    List<TipoGrado> gradosDisponibles;

	    // Si el alumno es menor, asignar grados correspondientes a menores
	    if (esMenor) {
	        gradosDisponibles = Arrays.asList(TipoGrado.BLANCO, TipoGrado.BLANCO_AMARILLO, TipoGrado.AMARILLO,
	                TipoGrado.AMARILLO_NARANJA, TipoGrado.NARANJA, TipoGrado.NARANJA_VERDE, TipoGrado.VERDE,
	                TipoGrado.VERDE_AZUL, TipoGrado.AZUL, TipoGrado.AZUL_ROJO, TipoGrado.ROJO,
	                TipoGrado.ROJO_NEGRO_1º_PUM, TipoGrado.ROJO_NEGRO_2º_PUM, TipoGrado.ROJO_NEGRO_3º_PUM);
	    } else {
	        // Si el alumno es adulto, asignar grados correspondientes a adultos
	        gradosDisponibles = Arrays.asList(TipoGrado.BLANCO, TipoGrado.AMARILLO, TipoGrado.NARANJA, TipoGrado.VERDE,
	                TipoGrado.AZUL, TipoGrado.ROJO, TipoGrado.NEGRO_1º_DAN, TipoGrado.NEGRO_2º_DAN,
	                TipoGrado.NEGRO_3º_DAN, TipoGrado.NEGRO_4º_DAN, TipoGrado.NEGRO_5º_DAN);
	    }

	    // Asignar un grado aleatoriamente de la lista de grados disponibles
	    TipoGrado tipoGradoAsignado = gradosDisponibles.get(new Random().nextInt(gradosDisponibles.size()));

	    // Buscar si el grado ya existe en la base de datos
	    Grado gradoExistente = gradoRepository.findByTipoGrado(tipoGradoAsignado);
	    if (gradoExistente != null) {
	        return gradoExistente;
	    }

	    // Si no existe, crear un nuevo grado y guardarlo
	    Grado nuevoGrado = new Grado();
	    nuevoGrado.setTipoGrado(tipoGradoAsignado);
	    return gradoRepository.save(nuevoGrado);
	}


	/**
	 * Asigna todos los alumnos a un grupo aleatorio.
	 *
	 * @param alumnos Lista de alumnos.
	 */
	private void asignarAlumnosAGrupoAleatorio() {
		List<Alumno> alumnosSinGrupo = alumnoRepository.findAlumnosSinGrupo();
		List<Grupo> grupos = grupoRepository.findAll();

		if (grupos.isEmpty()) {
			throw new IllegalStateException("No hay grupos disponibles.");
		}

		Random random = new Random();
		for (Alumno alumno : alumnosSinGrupo) {
			Grupo grupoAleatorio = grupos.get(random.nextInt(grupos.size()));
			grupoService.agregarAlumnoAGrupo(grupoAleatorio.getId(), alumno.getId());
		}
	}

}