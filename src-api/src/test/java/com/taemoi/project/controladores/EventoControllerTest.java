package com.taemoi.project.controladores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.taemoi.project.controllers.EventoController;
import com.taemoi.project.entities.Documento;
import com.taemoi.project.entities.Evento;
import com.taemoi.project.services.DocumentoService;
import com.taemoi.project.services.EventoService;
import com.taemoi.project.services.ImagenService;

@ExtendWith(MockitoExtension.class)
class EventoControllerTest {

	@Mock
	private EventoService eventoService;

	@Mock
	private ImagenService imagenService;

	@Mock
	private DocumentoService documentoService;

	@InjectMocks
	private EventoController eventoController;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void descargarDocumento_eventoNoVisible_anonimo_devuelveNotFound() {
		Long eventoId = 10L;
		Long documentoId = 55L;
		Evento eventoNoVisible = new Evento();
		eventoNoVisible.setId(eventoId);
		eventoNoVisible.setVisible(false);

		Authentication anonymous = new AnonymousAuthenticationToken(
				"anonymous",
				"anonymousUser",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
		SecurityContextHolder.getContext().setAuthentication(anonymous);

		when(eventoService.obtenerEventoPorId(eventoId)).thenReturn(eventoNoVisible);

		ResponseEntity<Resource> response = eventoController.descargarDocumento(eventoId, documentoId, false);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		verify(eventoService, never()).obtenerDocumentoDeEvento(anyLong(), anyLong());
	}

	@Test
	void descargarDocumento_eventoNoVisible_manager_devuelveOk() {
		Long eventoId = 10L;
		Long documentoId = 55L;
		Documento documento = new Documento();
		documento.setId(documentoId);
		documento.setNombre("bases.pdf");
		documento.setTipo("application/pdf");

		Authentication manager = new UsernamePasswordAuthenticationToken(
				"manager@taemoi.com",
				"pass",
				List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
		SecurityContextHolder.getContext().setAuthentication(manager);

		when(eventoService.obtenerDocumentoDeEvento(eventoId, documentoId)).thenReturn(documento);
		when(documentoService.obtenerRecursoDocumento(documento)).thenReturn(new ByteArrayResource("pdf".getBytes()));

		ResponseEntity<Resource> response = eventoController.descargarDocumento(eventoId, documentoId, false);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		verify(eventoService, never()).obtenerEventoPorId(anyLong());
	}

	@Test
	void descargarDocumento_eventoVisible_anonimo_devuelveOk() {
		Long eventoId = 10L;
		Long documentoId = 55L;
		Evento eventoVisible = new Evento();
		eventoVisible.setId(eventoId);
		eventoVisible.setVisible(true);

		Documento documento = new Documento();
		documento.setId(documentoId);
		documento.setNombre("bases.pdf");
		documento.setTipo("application/pdf");

		Authentication anonymous = new AnonymousAuthenticationToken(
				"anonymous",
				"anonymousUser",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
		SecurityContextHolder.getContext().setAuthentication(anonymous);

		when(eventoService.obtenerEventoPorId(eventoId)).thenReturn(eventoVisible);
		when(eventoService.obtenerDocumentoDeEvento(eventoId, documentoId)).thenReturn(documento);
		when(documentoService.obtenerRecursoDocumento(documento)).thenReturn(new ByteArrayResource("pdf".getBytes()));

		ResponseEntity<Resource> response = eventoController.descargarDocumento(eventoId, documentoId, false);

		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	void descargarDocumento_tipoMimeNulo_devuelveOctetStream() {
		Long eventoId = 10L;
		Long documentoId = 55L;
		Documento documento = new Documento();
		documento.setId(documentoId);
		documento.setNombre("bases.pdf");
		documento.setTipo(null);

		Authentication manager = new UsernamePasswordAuthenticationToken(
				"manager@taemoi.com",
				"pass",
				List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
		SecurityContextHolder.getContext().setAuthentication(manager);

		when(eventoService.obtenerDocumentoDeEvento(eventoId, documentoId)).thenReturn(documento);
		when(documentoService.obtenerRecursoDocumento(documento)).thenReturn(new ByteArrayResource("pdf".getBytes()));

		ResponseEntity<Resource> response = eventoController.descargarDocumento(eventoId, documentoId, false);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
	}

	@Test
	void descargarDocumento_forceDownload_devuelveOctetStream() {
		Long eventoId = 11L;
		Long documentoId = 77L;
		Documento documento = new Documento();
		documento.setId(documentoId);
		documento.setNombre("reglamento.pdf");
		documento.setTipo("application/pdf");

		Authentication manager = new UsernamePasswordAuthenticationToken(
				"manager@taemoi.com",
				"pass",
				List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
		SecurityContextHolder.getContext().setAuthentication(manager);

		when(eventoService.obtenerDocumentoDeEvento(eventoId, documentoId)).thenReturn(documento);
		when(documentoService.obtenerRecursoDocumento(documento)).thenReturn(new ByteArrayResource("pdf".getBytes()));

		ResponseEntity<Resource> response = eventoController.descargarDocumento(eventoId, documentoId, true);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
	}
}
