package com.taemoi.project.services;

import java.nio.file.Path;

import com.taemoi.project.dtos.response.MaterialExamenDTO;
import com.taemoi.project.entities.Deporte;

public interface MaterialExamenService {

	MaterialExamenDTO obtenerMaterialExamen(Long alumnoId, Deporte deporte);

	MaterialExamenArchivo obtenerTemario(Long alumnoId, Deporte deporte);

	MaterialExamenArchivo obtenerVideo(Long alumnoId, Deporte deporte, String videoFile);

	MaterialExamenArchivo obtenerDocumento(Long alumnoId, Deporte deporte, String documentoFile);

	class MaterialExamenArchivo {
		private final Path path;
		private final String fileName;
		private final String mimeType;
		private final long size;

		public MaterialExamenArchivo(Path path, String fileName, String mimeType, long size) {
			this.path = path;
			this.fileName = fileName;
			this.mimeType = mimeType;
			this.size = size;
		}

		public Path getPath() {
			return path;
		}

		public String getFileName() {
			return fileName;
		}

		public String getMimeType() {
			return mimeType;
		}

		public long getSize() {
			return size;
		}
	}
}
