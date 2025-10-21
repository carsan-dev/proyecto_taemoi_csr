package com.taemoi.project.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.taemoi.project.exceptions.alumno.AlumnoDuplicadoException;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoEnGrupoException;
import com.taemoi.project.exceptions.alumno.AlumnoNoEncontradoException;
import com.taemoi.project.exceptions.alumno.DatosAlumnoInvalidosException;
import com.taemoi.project.exceptions.alumno.FechaNacimientoInvalidaException;
import com.taemoi.project.exceptions.alumno.ListaAlumnosVaciaException;
import com.taemoi.project.exceptions.evento.EventoNoEncontradoException;
import com.taemoi.project.exceptions.grupo.GrupoNoEncontradoException;
import com.taemoi.project.exceptions.pago.PagoNotFoundException;
import com.taemoi.project.exceptions.producto.ProductoNoEncontradoException;
import com.taemoi.project.exceptions.turno.TurnoNoEncontradoException;
import com.taemoi.project.exceptions.usuario.ListaUsuariosVaciaException;

@ControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(AlumnoNoEncontradoException.class)
	public ResponseEntity<String> handleAlumnoNoEncontradoException(AlumnoNoEncontradoException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}

	@ExceptionHandler(AlumnoDuplicadoException.class)
	public ResponseEntity<String> handleAlumnoDuplicadoException(AlumnoDuplicadoException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
	}

	@ExceptionHandler(FechaNacimientoInvalidaException.class)
	public ResponseEntity<String> handleFechaNacimientoInvalidaException(FechaNacimientoInvalidaException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	}

	@ExceptionHandler(DatosAlumnoInvalidosException.class)
	public ResponseEntity<String> handleDatosAlumnoInvalidosException(DatosAlumnoInvalidosException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	}

	@ExceptionHandler(ListaUsuariosVaciaException.class)
	public ResponseEntity<String> handleListaUsuariosVaciaException(ListaUsuariosVaciaException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}

	@ExceptionHandler(ListaAlumnosVaciaException.class)
	public ResponseEntity<String> handleListaAlumnosVaciaException(ListaAlumnosVaciaException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}

	@ExceptionHandler(GrupoNoEncontradoException.class)
	public ResponseEntity<String> handleGrupoNoEncontradoException(GrupoNoEncontradoException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}

	@ExceptionHandler(TurnoNoEncontradoException.class)
	public ResponseEntity<String> handleTurnoNoEncontradoException(TurnoNoEncontradoException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}

	@ExceptionHandler(AlumnoNoEncontradoEnGrupoException.class)
	public ResponseEntity<String> handleAlumnoNoEncontradoEnGrupoException(AlumnoNoEncontradoEnGrupoException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}

	@ExceptionHandler(EventoNoEncontradoException.class)
	public ResponseEntity<String> handleEventoNoEncontradoException(EventoNoEncontradoException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}

	@ExceptionHandler(PagoNotFoundException.class)
	public ResponseEntity<String> handlePagoNotFoundException(PagoNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}

	@ExceptionHandler(ProductoNoEncontradoException.class)
	public ResponseEntity<String> handleProductoNoEncontradoException(ProductoNoEncontradoException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
	}
}