package com.taemoi.project.errores.fichero;

public class StorageFileNotFoundException extends StorageException {

	private static final long serialVersionUID = 7374731504531628342L;

	public StorageFileNotFoundException(String message) {
        super(message);
    }

    public StorageFileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

}