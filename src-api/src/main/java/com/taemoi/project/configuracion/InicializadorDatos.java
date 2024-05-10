package com.taemoi.project.configuracion;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.github.javafaker.Faker;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.entidades.Categoria;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.Roles;
import com.taemoi.project.entidades.TipoCategoria;
import com.taemoi.project.entidades.TipoGrado;
import com.taemoi.project.entidades.TipoTarifa;
import com.taemoi.project.entidades.Usuario;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.CategoriaRepository;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.repositorios.UsuarioRepository;

/**
 * Componente encargado de inicializar datos en la base de datos al arrancar la aplicación.
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
     * Inyección del codificador de contraseñas.
     */
	@Autowired
	private PasswordEncoder passwordEncoder;

    /**
     * Método que se ejecuta al arrancar la aplicación para inicializar datos en la base de datos.
     *
     * @param args Argumentos de la línea de comandos.
     * @throws Exception Si ocurre un error durante la inicialización de datos.
     */
	@Override
	public void run(String... args) throws Exception {
		boolean borrarAlumnos = true;
		if (borrarAlumnos) {
			alumnoRepository.deleteAll();
		}

		generarGrados();

		try {
			if (usuarioRepository.findByEmail("moiskimdotaekwondo@gmail.com").isEmpty()) {
				Usuario moiskimdo = new Usuario();
				moiskimdo.setNombre("Moiskimdo");
				moiskimdo.setApellidos("Taekwondo");
				moiskimdo.setEmail("moiskimdotaekwondo@gmail.com");
				moiskimdo.setContrasena(passwordEncoder.encode("09012013"));
				moiskimdo.getRoles().add(Roles.ROLE_USER);
				usuarioRepository.save(moiskimdo);
			}

			if (usuarioRepository.findByEmail("crolyx16@gmail.com").isEmpty()) {
				Usuario admin = new Usuario();
				admin.setNombre("Carlos");
				admin.setApellidos("Sanchez Roman");
				admin.setEmail("crolyx16@gmail.com");
				admin.setContrasena(passwordEncoder.encode("17022003"));
				admin.getRoles().add(Roles.ROLE_ADMIN);
				usuarioRepository.save(admin);
			}
		} catch (Exception e) {

		}

		Faker faker = new Faker(new Locale("es"));

		// Crear y almacenar las categorías en la base de datos
		for (TipoCategoria tipoCategoria : TipoCategoria.values()) {
			Categoria categoriaExistente = categoriaRepository.findByNombre(tipoCategoria.getNombre());
			if (categoriaExistente == null) {
				Categoria categoria = new Categoria();
				categoria.setNombre(tipoCategoria.getNombre());
				categoriaRepository.save(categoria);
			}
		}

		// Asignar grados y guardar alumnos
		for (int i = 0; i < 20; i++) {
			Alumno alumno = generarAlumno(faker);
			alumnoRepository.save(alumno);
		}
	}

    /**
     * Genera los grados si no existen en la base de datos.
     */
	private void generarGrados() {
		for (TipoGrado tipoGrado : TipoGrado.values()) {
			Grado gradoExistente = gradoRepository.findByTipoGrado(tipoGrado);
			if (gradoExistente == null) {
				Grado nuevoGrado = new Grado();
				nuevoGrado.setTipoGrado(tipoGrado);
				gradoRepository.save(nuevoGrado);
			}
		}
	}

    /**
     * Genera un alumno aleatorio utilizando la biblioteca Faker.
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
		alumno.setNif(faker.idNumber().valid());
		alumno.setDireccion(faker.address().fullAddress());
		alumno.setTelefono(faker.number().numberBetween(100000000, 999999999));
		alumno.setEmail(faker.internet().emailAddress());
		alumno.setCuantiaTarifa(faker.number().randomDouble(2, 50, 200));
		alumno.setFechaAlta(faker.date().birthday());
		alumno.setFechaBaja(faker.date().birthday());
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
}