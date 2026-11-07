package com.taemoi.project.controllers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.dtos.request.DatosEscuelaRequest;
import com.taemoi.project.dtos.response.DatosEscuelaDTO;
import com.taemoi.project.services.DatosEscuelaService;

import jakarta.validation.Valid;

/**
 * Pieza 394: «Escuela · Datos de la escuela» (boceto {@code docs/multi-escuela/boceto-escuela-datos.html}) de la escuela
 * ACTIVA. Solo el propietario ({@code ROLE_ADMIN}) y el administrador de plataforma ({@code ROLE_ADMIN_PLATAFORMA});
 * el gestor, el responsable de sede, el profesor y la familia, 403. Protegido dos veces: la regla de URL de
 * {@code SecurityConfiguration} (fuera de la lista blanca de la sesión solo-profesor) y {@code @PreAuthorize} aquí.
 */
@RestController
@RequestMapping("/api/escuela/datos")
@PreAuthorize(EscuelaDatosController.QUIEN)
public class EscuelaDatosController {

	static final String QUIEN = "hasAnyAuthority('ROLE_ADMIN', 'ROLE_ADMIN_PLATAFORMA')";

	/** Orden del formulario: el primer error de este orden va en {@code mensaje}. */
	private static final List<String> ORDEN = List.of("nombreLegal", "cif", "direccion", "emailContacto", "telefono",
			"firmanteNombre", "firmanteCargo", "firmanteNif", "diaLimiteMensualidad");
	static final String CUERPO_NO_VALIDO = "Faltan los datos de la escuela o no son válidos.";

	private final DatosEscuelaService datos;

	public EscuelaDatosController(DatosEscuelaService datos) {
		this.datos = datos;
	}

	@GetMapping
	public ResponseEntity<DatosEscuelaDTO> obtener() {
		return ResponseEntity.ok(datos.obtener());
	}

	/** Sustituye todos los datos; devuelve lo guardado. 400 con {@code {mensaje, errores{campo: mensaje}}} si no valen. */
	@PutMapping
	public ResponseEntity<DatosEscuelaDTO> guardar(@Valid @RequestBody DatosEscuelaRequest solicitud) {
		return ResponseEntity.ok(datos.guardar(solicitud));
	}

	/**
	 * Datos que no cumplen la validación: 400 con un mensaje por campo ({@code errores}) y el primero del formulario en
	 * {@code mensaje}. Atendido aquí, Spring no apunta en el log los valores rechazados (un NIF es un dato personal).
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> noValidos(MethodArgumentNotValidException error) {
		Map<String, String> porCampo = new LinkedHashMap<>();
		for (FieldError campo : error.getBindingResult().getFieldErrors()) {
			porCampo.putIfAbsent(campo.getField(), campo.getDefaultMessage());
		}
		Map<String, String> ordenados = new LinkedHashMap<>();
		ORDEN.stream().filter(porCampo::containsKey).forEach(c -> ordenados.put(c, porCampo.get(c)));
		porCampo.forEach(ordenados::putIfAbsent);
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("mensaje", ordenados.isEmpty() ? CUERPO_NO_VALIDO : ordenados.values().iterator().next());
		cuerpo.put("errores", ordenados);
		return ResponseEntity.badRequest().body(cuerpo);
	}

	/**
	 * Cuerpo que no se puede leer (JSON malformado, ninguno o un día que no es un número): 400 con {@code {mensaje}}. Sin
	 * apuntar la excepción, que cita el texto del cuerpo.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, String>> cuerpoNoLegible() {
		return ResponseEntity.badRequest().body(Map.of("mensaje", CUERPO_NO_VALIDO));
	}
}
