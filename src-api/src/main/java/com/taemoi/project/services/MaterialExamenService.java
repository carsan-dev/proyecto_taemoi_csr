package com.taemoi.project.services;

import java.nio.file.Path;
import java.util.List;

import com.taemoi.project.dtos.response.MaterialExamenDTO;

public interface MaterialExamenService {

	MaterialExamenDTO obtenerMaterialExamen(Long alumnoId, String deporte);

	MaterialExamenArchivo obtenerTemario(Long alumnoId, String deporte);

	MaterialExamenArchivo obtenerVideo(Long alumnoId, String deporte, String videoFile);

	MaterialExamenArchivo obtenerDocumento(Long alumnoId, String deporte, String documentoFile);

	/**
	 * Lo que hay en disco en un bloque de la escuela activa, con las mismas reglas de carpeta, título y orden que ven las
	 * familias. Solo lee: nunca crea ni mueve carpetas.
	 */
	BloqueEnDisco leerBloque(String disciplina, String bloqueId);

	/**
	 * {@link #leerBloque} para quien va a escribir. La lectura de las familias tolera un {@code index.json} dañado y sigue
	 * con el orden por nombre; reescribirlo desde ahí perdería los títulos.
	 *
	 * @throws com.taemoi.project.exceptions.MaterialExamenException 409 si {@code index.json} existe y no se puede leer
	 */
	BloqueEnDisco leerBloqueParaEscribir(String disciplina, String bloqueId);

	/** Datos de un fichero para servirlo; {@code mimeForzado} nulo, el detectado. */
	MaterialExamenArchivo describirArchivo(Path ruta, String mimeForzado);

	/** Título que un fichero recibe sin {@code index.json}. */
	String tituloDesdeArchivo(String fichero);

	/** La regla con la que los alumnos de grados superiores siguen viendo un vídeo de taekwondo. */
	boolean esVideoTaegukOPumse(String fichero, String titulo);

	record BloqueEnDisco(Path carpeta, Path temario, List<VideoEnDisco> videos, List<DocumentoEnDisco> documentos) {
	}

	record VideoEnDisco(String fichero, String titulo, int orden, Path ruta) {
	}

	record DocumentoEnDisco(String fichero, String titulo, String mime, Path ruta) {
	}

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
