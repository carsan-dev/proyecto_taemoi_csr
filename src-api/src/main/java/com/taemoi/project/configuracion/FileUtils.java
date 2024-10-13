package com.taemoi.project.configuracion;

import java.text.Normalizer;

public class FileUtils {
    // Método que limpia y formatea el nombre del archivo
    public static String limpiarNombreArchivo(String nombreArchivo) {
        // Normaliza el nombre del archivo (elimina acentos, tildes, etc.)
        String nombreLimpio = Normalizer.normalize(nombreArchivo, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", ""); // Elimina caracteres no ASCII
        
        // Reemplaza los espacios por guiones bajos
        nombreLimpio = nombreLimpio.replaceAll("\\s+", "_");
        
        // Elimina caracteres no válidos para nombres de archivos (mantiene solo letras, números, guiones bajos y puntos)
        nombreLimpio = nombreLimpio.replaceAll("[^a-zA-Z0-9._-]", "");
        
        return nombreLimpio;
    }
}
