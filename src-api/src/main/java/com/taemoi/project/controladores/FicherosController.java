package com.taemoi.project.controladores;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.taemoi.project.servicios.FicheroService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FicherosController {
	
	private static final Logger logger = LoggerFactory.getLogger(FicherosController.class);

	@Autowired
	private FicheroService ficheroService;
	
	@GetMapping(value="/{filename:.+}")
	@ResponseBody
	public ResponseEntity<Resource> serveFile(@PathVariable String filename, HttpServletRequest request) {
		Resource file = ficheroService.loadAsResource(filename);
		
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(file.getFile().getAbsolutePath());
        } catch (IOException ex) {
            logger.info("Could not determine file type.");
        }

        if(contentType == null) {
            contentType = "application/octet-stream";
        }
		
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(contentType))
				.body(file);
	}
}
