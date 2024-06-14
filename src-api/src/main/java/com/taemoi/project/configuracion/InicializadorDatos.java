package com.taemoi.project.configuracion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.github.javafaker.Faker;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Categoria;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.Roles;
import com.taemoi.project.entidades.TipoCategoria;
import com.taemoi.project.entidades.TipoGrado;
import com.taemoi.project.entidades.TipoTarifa;
import com.taemoi.project.entidades.Usuario;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.CategoriaRepository;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.repositorios.GrupoRepository;
import com.taemoi.project.repositorios.UsuarioRepository;
import com.taemoi.project.servicios.GrupoService;

/**
 * Componente encargado de inicializar datos en la base de datos al arrancar la
 * aplicación.
 */
@Component
public class InicializadorDatos implements CommandLineRunner {

	/**
	 * Inyección del repositorio de alumno.
	 */
	@Autowired
	private AlumnoRepository alumnoRepository;

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

	/**
	 * Inyección del codificador de contraseñas.
	 */
	@Autowired
	private PasswordEncoder passwordEncoder;
	
	@Autowired
    private DataSource dataSource;

	/**
	 * Método que se ejecuta al arrancar la aplicación para inicializar datos en la
	 * base de datos.
	 *
	 * @param args Argumentos de la línea de comandos.
	 * @throws Exception Si ocurre un error durante la inicialización de datos.
	 */
	@Override
	public void run(String... args) throws Exception {
		
        String sqlScript = new String(Files.readAllBytes(Paths.get("taemoidb.sql")), StandardCharsets.UTF_8);
        String[] sqlStatements = sqlScript.split(";");

        try (Connection conn = dataSource.getConnection()) {
            for (String sql : sqlStatements) {
                if (!sql.trim().isEmpty()) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(sql.trim());
                    }
                }
            }
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

	private void generarAlumnos() {
		Faker faker = new Faker(new Locale("es"));
		for (int i = 0; i < 20; i++) {
			Alumno alumno = generarAlumno(faker);
			if (!alumnoRepository.existsByEmail(alumno.getEmail())) {
				alumnoRepository.save(alumno);
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
		alumno.setNumeroExpediente(faker.number().numberBetween(10000000, 99999999));
		alumno.setFechaNacimiento(faker.date().birthday());
		alumno.setNif(generarNif(faker));
		alumno.setDireccion(faker.address().fullAddress());
		alumno.setTelefono(faker.number().numberBetween(100000000, 999999999));
		alumno.setEmail(faker.internet().emailAddress());
		alumno.setCuantiaTarifa(faker.number().randomDouble(2, 50, 200));
		alumno.setFechaAlta(faker.date().birthday());
		alumno.setFechaBaja(null);
		TipoTarifa tipoTarifa = TipoTarifa.values()[faker.number().numberBetween(0, TipoTarifa.values().length)];
		alumno.setTipoTarifa(tipoTarifa);

		double cuantiaTarifa = asignarCuantiaTarifa(tipoTarifa);
		alumno.setCuantiaTarifa(cuantiaTarifa);

		LocalDate fechaNacimiento = alumno.getFechaNacimiento().toInstant().atZone(ZoneId.systemDefault())
				.toLocalDate();
		int edad = Period.between(fechaNacimiento, LocalDate.now()).getYears();

		alumno.setCategoria(asignarCategoriaSegunEdad(edad));
		alumno.setGrado(asignarGradoSegunEdad(edad));

		return alumno;
	}
	
    private String generarNif(Faker faker) {
        String nif = faker.idNumber().valid();
        if (nif.length() > 9) {
            nif = nif.substring(0, 9);
        }
        return nif;
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
	 * Asigna la categoría según la edad del alumno.
	 *
	 * @param edad Edad del alumno.
	 * @return La categoría asignada.
	 */
	private Categoria asignarCategoriaSegunEdad(int edad) {
		TipoCategoria tipoCategoria;
		if (edad >= 3 && edad <= 7) {
			tipoCategoria = TipoCategoria.PRETKD;
		} else if (edad >= 8 && edad <= 9) {
			tipoCategoria = TipoCategoria.INFANTIL;
		} else if (edad >= 10 && edad <= 11) {
			tipoCategoria = TipoCategoria.PRECADETE;
		} else if (edad >= 12 && edad <= 14) {
			tipoCategoria = TipoCategoria.CADETE;
		} else if (edad >= 15 && edad <= 17) {
			tipoCategoria = TipoCategoria.JUNIOR;
		} else if (edad >= 16 && edad <= 20) {
			tipoCategoria = TipoCategoria.SUB21;
		} else {
			tipoCategoria = TipoCategoria.SENIOR;
		}

		return categoriaRepository.findByNombre(tipoCategoria.getNombre());
	}

	/**
	 * Asigna el grado según la edad del alumno.
	 *
	 * @param edad Edad del alumno.
	 * @return El grado asignado.
	 */
	private Grado asignarGradoSegunEdad(int edad) {
		List<TipoGrado> tiposGradoDisponibles;
		if (edad > 15) {
			tiposGradoDisponibles = Arrays.asList(TipoGrado.BLANCO, TipoGrado.AMARILLO, TipoGrado.NARANJA,
					TipoGrado.VERDE, TipoGrado.AZUL, TipoGrado.ROJO, TipoGrado.NEGRO);
		} else {
			tiposGradoDisponibles = Arrays.asList(TipoGrado.BLANCO, TipoGrado.BLANCO_AMARILLO, TipoGrado.AMARILLO,
					TipoGrado.AMARILLO_NARANJA, TipoGrado.NARANJA, TipoGrado.NARANJA_VERDE, TipoGrado.VERDE,
					TipoGrado.VERDE_AZUL, TipoGrado.AZUL, TipoGrado.AZUL_ROJO, TipoGrado.ROJO, TipoGrado.ROJO_NEGRO);
		}

		Random random = new Random();
		TipoGrado tipoGradoSeleccionado = tiposGradoDisponibles.get(random.nextInt(tiposGradoDisponibles.size()));

		return gradoRepository.findByTipoGrado(tipoGradoSeleccionado);
	}

	/**
	 * Asigna todos los alumnos a un grupo aleatorio.
	 *
	 * @param alumnos Lista de alumnos.
	 */
	@SuppressWarnings("null")
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