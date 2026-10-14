package com.taemoi.project.controllers;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.taemoi.project.archivos.AccesoArchivos;
import com.taemoi.project.archivos.AccesoArchivos.ArchivoServible;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Sirve {@code /imagenes/**} y {@code /documentos/**} (GET y HEAD) sustituyendo a los manejadores de
 * recursos estáticos: el acceso lo decide {@link AccesoArchivos} según la escuela activa y la fila
 * {@code Imagen}/{@code Documento}. Spring Security sigue aplicando antes las reglas por rol de
 * {@code SecurityConfiguration}; esta capa añade escuela y fila. Toda denegación responde 404.
 */
@Controller
public class ArchivoEstaticoController {

	private static final String IMAGENES = "/imagenes/";
	private static final String DOCUMENTOS = "/documentos/";

	private final AccesoArchivos accesoArchivos;

	public ArchivoEstaticoController(AccesoArchivos accesoArchivos) {
		this.accesoArchivos = accesoArchivos;
	}

	@GetMapping("/imagenes/**")
	public ResponseEntity<Resource> imagen(HttpServletRequest request) throws IOException {
		return servir(request, IMAGENES, accesoArchivos::imagen);
	}

	@GetMapping("/documentos/**")
	public ResponseEntity<Resource> documento(HttpServletRequest request) throws IOException {
		return servir(request, DOCUMENTOS, accesoArchivos::documento);
	}

	private ResponseEntity<Resource> servir(HttpServletRequest request, String prefijo,
			BiFunction<String, Authentication, Optional<ArchivoServible>> acceso) throws IOException {
		// Ruta tal como llegó (codificada): la decodificación y validación las hace AccesoArchivos.
		String ruta = request.getRequestURI();
		String contexto = request.getContextPath();
		if (contexto != null && !contexto.isEmpty() && ruta.startsWith(contexto)) {
			ruta = ruta.substring(contexto.length());
		}
		if (!ruta.startsWith(prefijo)) {
			return ResponseEntity.notFound().build();
		}
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		Optional<ArchivoServible> archivo = acceso.apply(ruta.substring(prefijo.length()), autenticacion);
		if (archivo.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		ArchivoServible servible = archivo.get();
		Resource recurso = new FileSystemResource(servible.ruta());
		MediaType tipo = MediaTypeFactory.getMediaType(recurso).orElse(MediaType.APPLICATION_OCTET_STREAM);
		CacheControl cache = servible.publico()
				? CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic()
				: CacheControl.noStore().cachePrivate();
		return ResponseEntity.ok()
				.contentType(tipo)
				.cacheControl(cache)
				.lastModified(Files.getLastModifiedTime(servible.ruta()).toMillis())
				.body(recurso);
	}
}
