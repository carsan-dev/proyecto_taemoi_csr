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
import com.taemoi.project.entidades.Categoria;
import com.taemoi.project.entidades.Grado;
import com.taemoi.project.entidades.Grupo;
import com.taemoi.project.entidades.NombresGrupo;
import com.taemoi.project.entidades.Producto;
import com.taemoi.project.entidades.Roles;
import com.taemoi.project.entidades.TipoCategoria;
import com.taemoi.project.entidades.TipoGrado;
import com.taemoi.project.entidades.TipoTarifa;
import com.taemoi.project.entidades.Turno;
import com.taemoi.project.entidades.Usuario;
import com.taemoi.project.repositorios.AlumnoRepository;
import com.taemoi.project.repositorios.CategoriaRepository;
import com.taemoi.project.repositorios.GradoRepository;
import com.taemoi.project.repositorios.GrupoRepository;
import com.taemoi.project.repositorios.ProductoRepository;
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
	
	@Autowired
	private ProductoRepository productoRepository;

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

	    Grupo taekwondoLunesMiercolesPrimerTurno = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_LUNES_MIERCOLES_PRIMER_TURNO);
	    Grupo taekwondoLunesMiercolesSegundoTurno = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_LUNES_MIERCOLES_SEGUNDO_TURNO);
	    Grupo taekwondoLunesMiercolesTercerTurno = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_LUNES_MIERCOLES_TERCER_TURNO);
	    Grupo taekwondoMartesJuevesPrimerTurno = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_MARTES_JUEVES_PRIMER_TURNO);
	    Grupo taekwondoMartesJuevesSegundoTurno = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_MARTES_JUEVES_SEGUNDO_TURNO);
	    Grupo taekwondoMartesJuevesTercerTurno = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_MARTES_JUEVES_TERCER_TURNO);
	    Grupo competicion = obtenerOcrearGrupo(NombresGrupo.TAEKWONDO_COMPETICION);
	    Grupo pilatesMartesJueves = obtenerOcrearGrupo(NombresGrupo.PILATES_MARTES_JUEVES);
	    Grupo kickboxingLunesMiercoles = obtenerOcrearGrupo(NombresGrupo.KICKBOXING_LUNES_MIERCOLES);
		
	    if (turnoRepository.count() == 0) {
			crearTurno("Lunes", "17:00", "18:00", taekwondoLunesMiercolesPrimerTurno, "Taekwondo Primer Turno Lunes");
			crearTurno("Lunes", "18:00", "19:00", taekwondoLunesMiercolesSegundoTurno, "Taekwondo Segundo Turno Lunes");
			crearTurno("Lunes", "19:00", "20:30", taekwondoLunesMiercolesTercerTurno, "Taekwondo Tercer Turno Lunes");
			crearTurno("Lunes", "20:30", "21:30", kickboxingLunesMiercoles, "Kickboxing Lunes");
			
			crearTurno("Martes", "10:00", "11:15", pilatesMartesJueves, "Pilates Martes");
			crearTurno("Martes", "17:00", "18:00", taekwondoMartesJuevesPrimerTurno, "Taekwondo Primer Turno Martes");
			crearTurno("Martes", "18:00", "19:00", taekwondoMartesJuevesSegundoTurno, "Taekwondo Segundo Turno Martes");
			crearTurno("Martes", "19:00", "20:00", taekwondoMartesJuevesTercerTurno, "Taekwondo Tercer Turno Martes");
			
			crearTurno("Miércoles", "17:00", "18:00", taekwondoLunesMiercolesPrimerTurno, "Taekwondo Primer Turno Miércoles");
			crearTurno("Miércoles", "18:00", "19:00", taekwondoLunesMiercolesSegundoTurno, "Taekwondo Segundo Turno Miércoles");
			crearTurno("Miércoles", "19:00", "20:30", taekwondoLunesMiercolesTercerTurno, "Taekwondo Tercer Turno Miércoles");
			crearTurno("Miércoles", "20:30", "21:30", kickboxingLunesMiercoles, "Kickboxing Miércoles");
			
			crearTurno("Jueves", "10:00", "11:15", pilatesMartesJueves, "Pilates Jueves");
			crearTurno("Jueves", "17:00", "18:00", taekwondoMartesJuevesPrimerTurno, "Taekwondo Primer Turno Jueves");
			crearTurno("Jueves", "18:00", "19:00", taekwondoMartesJuevesSegundoTurno, "Taekwondo Segundo Turno Jueves");
			crearTurno("Jueves", "19:00", "20:00", taekwondoMartesJuevesTercerTurno, "Taekwondo Tercer Turno Jueves");
			crearTurno("Jueves", "20:00", "21:30", competicion, "Taekwondo Competición");
	    }

	    inicializarProductos();
	    
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
	
	private void inicializarProductos() {
	    if (productoRepository.count() == 0) {  // Solo cargar si no hay productos
	        List<Producto> productos = Arrays.asList(
	            new Producto("CINTURÓN BLANCO INFANTIL", 3.2, ""),
	            new Producto("CINTURÓN BLANCO JUNIOR", 4.2, ""),
	            new Producto("CINTURÓN BLANCO ADULTO", 4.2, ""),
	            new Producto("CINTURÓN BLANCO-AMARILLO INFANTIL", 4.8, ""),
	            new Producto("CINTURÓN BLANCO-AMARILLO ADULTO", 6.2, ""),
	            new Producto("CINTURÓN AMARILLO INFANTIL", 4.8, ""),
	            new Producto("CINTURÓN AMARILLO JUNIOR", 4.8, ""),
	            new Producto("CINTURÓN AMARILLO ADULTO", 5.2, ""),
	            new Producto("CINTURÓN AMARILLO-NARANJA INFANTIL", 4.8, ""),
	            new Producto("CINTURÓN AMARILLO-NARANJA ADULTO", 6.2, ""),
	            new Producto("CINTURÓN NARANJA INFANTIL", 4.8, ""),
	            new Producto("CINTURÓN NARANJA JUNIOR", 4.8, ""),
	            new Producto("CINTURÓN NARANJA ADULTO", 5.2, ""),
	            new Producto("CINTURÓN NARANJA-VERDE INFANTIL", 4.8, ""),
	            new Producto("CINTURÓN NARANJA-VERDE ADULTO", 6.2, ""),
	            new Producto("CINTURÓN VERDE INFANTIL", 4.8, ""),
	            new Producto("CINTURÓN VERDE JUNIOR", 4.8, ""),
	            new Producto("CINTURÓN VERDE ADULTO", 5.2, ""),
	            new Producto("CINTURÓN VERDE-AZUL INFANTIL", 4.8, ""),
	            new Producto("CINTURÓN VERDE-AZUL ADULTO", 6.2, ""),
	            new Producto("CINTURÓN AZUL INFANTIL", 4.8, ""),
	            new Producto("CINTURÓN AZUL JUNIOR", 4.8, ""),
	            new Producto("CINTURÓN AZUL ADULTO", 5.2, ""),
	            new Producto("CINTURÓN AZUL-ROJO INFANTIL", 4.8, ""),
	            new Producto("CINTURÓN AZUL-ROJO ADULTO", 6.2, ""),
	            new Producto("CINTURÓN ROJO INFANTIL", 4.8, ""),
	            new Producto("CINTURÓN ROJO JUNIOR", 4.8, ""),
	            new Producto("CINTURÓN ROJO ADULTO", 5.2, ""),
	            new Producto("ANTEBRACERA CONFORT", 17.75, ""),
	            new Producto("ANTEBRACERA WTF", 19.85, ""),
	            new Producto("ANTEBRACERA SILVER FIT", 21.0, ""),
	            new Producto("ANTEBRACERA ADIDAS", 24.0, ""),
	            new Producto("ANTEBRACERA CON CODERA PROTEC BLANCA", 21.6, ""),
	            new Producto("ANTEBRACERA CON CODERA PROTEC ULTRA LIGHT", 21.6, ""),
	            new Producto("ANTEBRACERA DE ESPUMA", 8.35, ""),
	            new Producto("BUCAL SIMPLE", 3.5, ""),
	            new Producto("BUCAL ORTODONCIA DOBLE SHOCK DOCTOR", 29.9, ""),
	            new Producto("BUCAL DE GEL BLANCO", 10.0, ""),
	            new Producto("BORDADO NOMBRE EN DOBOK", 15.0, ""),
	            new Producto("BORDADO LETRA PARA FALD N", 1.27, ""),
	            new Producto("CASCO DAEDO BLANCO", 36.0, ""),
	            new Producto("CASCO DAEDO COLOR", 36.5, ""),
	            new Producto("CASCO CON MÁSCARA INTEGRADA DAEDO", 48.0, ""),
	            new Producto("CASCO PROTEC INSPIRE BLANCO", 32.4, ""),
	            new Producto("CHÁNDAL COMPLETO EQUIPO COMPETIDORES", 85.0, ""),
	            new Producto("CHAQUETA DEL CHÁNDAL COMPETIDORES", 30.63, ""),
	            new Producto("PANTALÓN CHÁNDAL COMPETIDORES", 19.69, ""),
	            new Producto("CAMISETA COMPETIDORES", 17.19, ""),
	            new Producto("BERMUDAS COMPETIDORES", 12.5, ""),
	            new Producto("CINTURÓN NEGRO LISO ANCHO 5CMS", 17.0, ""),
	            new Producto("CINTURÓN DE COLOR LARGO 330 CMS", 7.2, ""),
	            new Producto("COQUILLA + PORTA COQUILLA", 12.0, ""),
	            new Producto("COQUILLA FEMENINA COMPLETA", 14.5, ""),
	            new Producto("COQUILLA MASCULINA COMPLETA", 16.7, ""),
	            new Producto("BANDERITA PEQUEÑA PARA CINTURÓN", 1.2, ""),
	            new Producto("DERECHOS DE EXAMEN 1º DAN", 200, ""),
	            new Producto("DERECHOS DE EXAMEN 1º PUM", 130, ""),
	            new Producto("DERECHOS DE EXAMEN 2º DAN", 220, ""),
	            new Producto("DERECHOS DE EXAMEN 2º PUM", 140, ""),
	            new Producto("DERECHOS DE EXAMEN 3º DAN", 270, ""),
	            new Producto("DERECHOS DE EXAMEN 3º PUM", 155, ""),
	            new Producto("DERECHOS DE EXAMEN 4º DAN", 360, ""),
	            new Producto("DERECHOS DE EXAMEN 5º DAN", 410, ""),
	            new Producto("DERECHOS DE EXAMEN CINTURÓN ROJO BORDADO", 50, ""),
	            new Producto("DERECHOS DE EXAMEN ROJO", 30, ""),
	            new Producto("DERECHOS DE EXAMEN AZUL/ROJO", 30, ""),
	            new Producto("DERECHOS DE EXAMEN AZUL", 30, ""),
	            new Producto("DERECHOS DE EXAMEN VERDE/AZUL", 30, ""),
	            new Producto("DERECHOS DE EXAMEN VERDE", 30, ""),
	            new Producto("DERECHOS DE EXAMEN NARANJA/VERDE", 30, ""),
	            new Producto("DERECHOS DE EXAMEN NARANJA", 30, ""),
	            new Producto("DERECHOS DE EXAMEN AMARILLO/NARANJA", 30, ""),
	            new Producto("DERECHOS DE EXAMEN AMARILLO", 30, ""),
	            new Producto("DERECHOS DE EXAMEN BLANCO/AMARILLO", 30, ""),
	            new Producto("DOBOK DAEDO CUELLO BLANCO TALLA 0000 (100 CMS)", 35.2, ""),
	            new Producto("DOBOK DAEDO CUELLO BLANCO TALLA 000 (110 CMS)", 35.2, ""),
	            new Producto("DOBOK DAEDO CUELLO BLANCO TALLA 00 (120 CMS)", 36.2, ""),
	            new Producto("DOBOK DAEDO CUELLO BLANCO TALLA 0 (130 CMS)", 36.2, ""),
	            new Producto("DOBOK DAEDO CUELLO BLANCO TALLA 1 (140 CMS)", 37.2, ""),
	            new Producto("DOBOK DAEDO CUELLO BLANCO TALLA 2 (150 CMS)", 39.2, ""),
	            new Producto("DOBOK DAEDO CUELLO BLANCO TALLA 3 (160 CMS)", 41.2, ""),
	            new Producto("DOBOK DAEDO CUELLO BLANCO TALLA 4 (170 CMS)", 42.2, ""),
	            new Producto("DOBOK DAEDO CUELLO BLANCO TALLA 5 (180 CMS)", 43.2, ""),
	            new Producto("DOBOK DAEDO CUELLO BLANCO TALLA 6 (190 CMS)", 44.2, ""),
	            new Producto("DOBOK DAEDO CUELLO BLANCO TALLA 7 (200 CMS)", 45.2, ""),
	            new Producto("DOBOK DAEDO CUELLO BLANCO TALLA 8 (210 CMS)", 46.2, ""),
	            new Producto("DOBOK DAEDO CUELLO NEGRO TALLA 0000 (100 CMS)", 36.2, ""),
	            new Producto("DOBOK DAEDO CUELLO NEGRO TALLA 000 (110 CMS)", 36.2, ""),
	            new Producto("DOBOK DAEDO CUELLO NEGRO TALLA 00 (120 CMS)", 37.2, ""),
	            new Producto("DOBOK DAEDO CUELLO NEGRO TALLA 0 (130 CMS)", 37.2, ""),
	            new Producto("DOBOK DAEDO CUELLO NEGRO TALLA 1 (140 CMS)", 38.2, ""),
	            new Producto("DOBOK DAEDO CUELLO NEGRO TALLA 2 (150 CMS)", 40.2, ""),
	            new Producto("DOBOK DAEDO CUELLO NEGRO TALLA 3 (160 CMS)", 42.2, ""),
	            new Producto("DOBOK DAEDO CUELLO NEGRO TALLA 4 (170 CMS)", 43.2, ""),
	            new Producto("DOBOK DAEDO CUELLO NEGRO TALLA 5 (180 CMS)", 44.2, ""),
	            new Producto("DOBOK DAEDO CUELLO NEGRO TALLA 6 (190 CMS)", 45.2, ""),
	            new Producto("DOBOK DAEDO CUELLO NEGRO TALLA 7 (200 CMS)", 46.2, ""),
	            new Producto("DOBOK DAEDO CUELLO NEGRO TALLA 8 (210 CMS)", 47.2, ""),
	            new Producto("DOBOK ADIDAS CUELLO BLANCO TALLA 0000 (100 CMS)", 32.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO BLANCO TALLA 000 (110 CMS)", 32.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO BLANCO TALLA 00 (120 CMS)", 32.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO BLANCO TALLA 0 (130 CMS)", 37.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO BLANCO TALLA 1 (140 CMS)", 37.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO BLANCO TALLA 2 (150 CMS)", 37.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO BLANCO TALLA 3 (160 CMS)", 37.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO BLANCO TALLA 4 (170 CMS)", 39.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO BLANCO TALLA 5 (180 CMS)", 42.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO BLANCO TALLA 6 (190 CMS)", 42.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO NEGRO TALLA 0000 (100 CMS)", 36.5, ""),
	            new Producto("DOBOK ADIDAS CUELLO NEGRO TALLA 000 (110 CMS)", 36.5, ""),
	            new Producto("DOBOK ADIDAS CUELLO NEGRO TALLA 00 (120 CMS)", 36.5, ""),
	            new Producto("DOBOK ADIDAS CUELLO NEGRO TALLA 0 (130 CMS)", 36.5, ""),
	            new Producto("DOBOK ADIDAS CUELLO NEGRO TALLA 1 (140 CMS)", 40.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO NEGRO TALLA 2 (150 CMS)", 40.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO NEGRO TALLA 3 (160 CMS)", 40.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO NEGRO TALLA 4 (170 CMS)", 43.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO NEGRO TALLA 5 (180 CMS)", 46.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO NEGRO TALLA 6 (190 CMS)", 48.9, ""),
	            new Producto("DOBOK ADIDAS CUELLO NEGRO TALLA 7 (200 CMS)", 48.9, ""),
	            new Producto("DOBOK POOMSAE DAN MASCULINO TALLA 1,30 y 1,40", 58.0, ""),
	            new Producto("DOBOK POOMSAE DAN MASCULINO TALLA 1,50", 58.0, ""),
	            new Producto("DOBOK POOMSAE DAN MASCULINO TALLA 1,60", 64.0, ""),
	            new Producto("DOBOK POOMSAE DAN MASCULINO TALLA 1,70", 64.0, ""),
	            new Producto("DOBOK POOMSAE DAN MASCULINO TALLA 1,80", 64.0, ""),
	            new Producto("DOBOK POOMSAE DAN MASCULINO TALLA 1,90", 68.0, ""),
	            new Producto("DOBOK POOMSAE DAN MASCULINO TALLA 2,00", 68.0, ""),
	            new Producto("DOBOK POOMSAE DAN MASCULINO TALLA 2,10", 68.0, ""),
	            new Producto("DOBOK POOMSAE DAN FEMENINO TALLA 1,30 y 1,40", 58.0, ""),
	            new Producto("DOBOK POOMSAE DAN FEMENINO TALLA 1,50", 58.0, ""),
	            new Producto("DOBOK POOMSAE DAN FEMENINO TALLA 1,60", 64.0, ""),
	            new Producto("DOBOK POOMSAE DAN FEMENINO TALLA 1,70", 64.0, ""),
	            new Producto("DOBOK POOMSAE DAN FEMENINO TALLA 1,80", 64.0, ""),
	            new Producto("DOBOK POOMSAE DAN FEMENINO TALLA 1,90", 68.0, ""),
	            new Producto("DOBOK POOMSAE DAN FEMENINO TALLA 2,00", 68.0, ""),
	            new Producto("DOBOK POOMSAE DAN FEMENINO TALLA 2,10", 68.0, ""),
	            new Producto("ESPINILLERAS CONFORT", 20.9, ""),
	            new Producto("ESPINILLERAS WTF", 24.0, ""),
	            new Producto("ESPINILLERAS SILVER FIT", 30.0, ""),
	            new Producto("ESPINILLERAS ADIDAS", 25.2, ""),
	            new Producto("ESPINILLERAS ADIDAS CON RODILLERAS", 30.6, ""),
	            new Producto("GUANTILLAS WTF", 28.2, ""),
	            new Producto("INCIENSO", 2.5, ""),
	            new Producto("MACUTO DAEDO ROJO-NEGRO", 31.5, ""),
	            new Producto("MACUTO ADIDAS ROJO-AZUL", 44.9, ""),
	            new Producto("MÁSCARA DESMONTABLE PARA CASCO", 25.0, ""),
	            new Producto("PANTALÓN SUELTO DAEDO TALLA 000 (1,10CMS)", 13.0, ""),
	            new Producto("PANTALÓN SUELTO DAEDO TALLA 00 (1,20CMS)", 13.0, ""),
	            new Producto("PANTALÓN SUELTO DAEDO TALLA 0 (1,30CMS)", 13.0, ""),
	            new Producto("PANTALÓN SUELTO DAEDO TALLA 1 (1,40CMS)", 16.0, ""),
	            new Producto("PANTALÓN SUELTO DAEDO TALLA 2 (1,50CMS)", 16.0, ""),
	            new Producto("PANTALÓN SUELTO DAEDO TALLA 3 (1,60CMS)", 16.0, ""),
	            new Producto("PANTALÓN SUELTO DAEDO TALLA 4 (1,70CMS)", 16.0, ""),
	            new Producto("PANTALÓN SUELTO DAEDO TALLA 5 (1,80CMS)", 16.5, ""),
	            new Producto("PANTALÓN SUELTO DAEDO TALLA 6 (1,90CMS)", 16.5, ""),
	            new Producto("PANTALÓN SUELTO DAEDO TALLA 7 (2,00CMS)", 16.5, ""),
	            new Producto("PANTALÓN SUELTO DAEDO TALLA 8 (2,10CMS)", 18.0, ""),
	            new Producto("PATUCOS ELECTRÓNICOS NICO G2", 55.0, ""),
	            new Producto("PATUCO DAEDO NO ELECTRÓNICO NICO SIN TALÓN", 28.2, ""),
	            new Producto("PETO DAEDO NORMAL HOMOLOGADO", 28.2, ""),
	            new Producto("PETO DAEDO WTF", 38.6, ""),
	            new Producto("PROTECTOR PARA SENOS BASIC", 22.95, ""),
	            new Producto("PROTECTOR PARA SENOS", 32.0, ""),
	            new Producto("PROTECTOR FEMENINO ECONO GUARD", 36.45, ""),
	            new Producto("BOTINES DAEDO NUEVAS ZAPATILLAS ACTION", 45.5, ""),
	            new Producto("BOTINES DAAEDO ANTIGUAS", 36.0, ""),
	            new Producto("BOTINES DAEDO CONFORT", 23.0, ""),
	            new Producto("BOTINES ADIDAS", 72.95, ""),
	            new Producto("LETRAS GRANDES BORDADAS CINTURÓN", 0.42, ""),
	            new Producto("LETRAS PEQUEÑAS BORDADAS CINTURÓN", 0.24, ""),
	            new Producto("PAO IRAN CON CAVO PROTEC", 34.2, ""),
	            new Producto("KICKING MITT CUADRADO DAEDO", 28.5, ""),
	            new Producto("PAO PIEL OVALADO", 62.6, ""),
	            new Producto("PAO IRAN CON CAVO ADIDAS 2", 52.9, ""),
	            new Producto("PAO ESCUDO PEQUEÑO 42X20X15 CMS", 41.75, ""),
	            new Producto("PAO ESCUDO MEDIANO 60X30X15 CMS", 62.6, ""),
	            new Producto("PAO ESCUDO GRANDE 70X33X20 CMS", 83.5, ""),
	            new Producto("MIT DE BRAZO", 35.0, ""),
	            new Producto("MIT SIMPLE ADIDAS", 23.2, ""),
	            new Producto("MIT DOBLE DAEDO", 22.95, ""),
	            new Producto("MIT SIMPLE DAEDO", 19.0, ""),
	            new Producto("MIT DOBLE DAEDO PARA NIÑOS", 17.0, "")
	        );
	        productoRepository.saveAll(productos);
	    }
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
	                TipoGrado.ROJO_NEGRO_1_PUM, TipoGrado.ROJO_NEGRO_2_PUM, TipoGrado.ROJO_NEGRO_3_PUM);
	    } else {
	        // Si el alumno es adulto, asignar grados correspondientes a adultos
	        gradosDisponibles = Arrays.asList(TipoGrado.BLANCO, TipoGrado.AMARILLO, TipoGrado.NARANJA, TipoGrado.VERDE,
	                TipoGrado.AZUL, TipoGrado.ROJO, TipoGrado.NEGRO_1_DAN, TipoGrado.NEGRO_2_DAN,
	                TipoGrado.NEGRO_3_DAN, TipoGrado.NEGRO_4_DAN, TipoGrado.NEGRO_5_DAN);
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