package com.taemoi.project.controladores;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.UsuarioDTO;
import com.taemoi.project.entidades.Alumno;
import com.taemoi.project.errores.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.errores.alumno.ListaAlumnosVaciaException;
import com.taemoi.project.errores.usuario.ListaUsuariosVaciaException;
import com.taemoi.project.servicios.AlumnoService;
import com.taemoi.project.servicios.UsuarioService;

/**
 * Controlador REST que maneja las operaciones relacionadas con administración.
 * Este controlador proporciona endpoints para administrar usuarios y alumnos en el sistema.
 * Todos los endpoints en este controlador requieren autorización con el rol ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
	private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

	/**
     * Inyección del servicio de usuario.
     */
	@Autowired
	private UsuarioService usuarioService;
	
	/**
     * Inyección del servicio de alumno.
     */
	@Autowired
	private AlumnoService alumnoService;

    /**
     * Obtiene una lista de alumnos paginada o no paginada según los parámetros proporcionados.
     *
     * @param page Número de página para paginación (opcional).
     * @param size Tamaño de la página para paginación (opcional).
     * @return ResponseEntity que contiene una lista de alumnos.
     * @throws ListaAlumnosVaciaException si no se encuentran alumnos en el sistema.
     */
	@GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> obtenerAlumnos(@RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            logger.info("## AdminController :: mostrarAlumnos paginados");
            Pageable pageable = PageRequest.of(page, size, Sort.by("nombre").ascending());
            Page<Alumno> alumnos = alumnoService.obtenerTodosLosAlumnos(pageable);
            if (alumnos.isEmpty()) {
                throw new ListaAlumnosVaciaException("No hay usuarios registrados en el sistema.");
            }
            return ResponseEntity.ok(alumnos);
        } else {
            logger.info("## AdminController :: mostrarTodosLosAlumnos");
            List<Alumno> alumnos = alumnoService.obtenerTodosLosAlumnos();
            if (alumnos.isEmpty()) {
                throw new ListaAlumnosVaciaException("No hay usuarios registrados en el sistema.");
            }
            return ResponseEntity.ok(alumnos);
        }
    }

    /**
     * Obtiene un alumno por su ID.
     *
     * @param id ID del alumno.
     * @return ResponseEntity que contiene el alumno encontrado.
     * @throws AlumnoNoEncontradoException si no se encuentra ningún alumno con el ID especificado.
     */
	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<Alumno> obtenerAlumnoPorId(@PathVariable Long id) {
		logger.info("## AdminController :: mostrarAlumnosPorId");
		Optional<Alumno> alumno = alumnoService.obtenerAlumnoPorId(id);
		return alumno.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
				.orElseThrow(() -> new AlumnoNoEncontradoException("Alumno no encontrado con ID: " + id));
	}

    /**
     * Obtiene una lista de usuarios en el sistema.
     *
     * @return ResponseEntity que contiene una lista de usuarios.
     * @throws ListaUsuariosVaciaException si no se encuentran usuarios en el sistema.
     */
	@GetMapping("/users")
	@PreAuthorize("hasAuthority('ROLE_ADMIN')")
	public ResponseEntity<List<UsuarioDTO>> mostrarUsuarios() {
		logger.info("## AuthorizationAdminController :: showUsers");
		List<UsuarioDTO> usuarios = usuarioService.obtenerTodos();
		if (usuarios.isEmpty()) {
			throw new ListaUsuariosVaciaException("No hay usuarios registrados en el sistema.");
		}
		return ResponseEntity.ok(usuarios);
	}
}
