package com.taemoi.project.entidades;

public enum TipoCategoria {
	INFANTIL("Infantil", 8, 9), PRECADETE("Precadete", 10, 11), CADETE("Cadete", 12, 14),
	JUNIOR("Junior", 14, 16), SUB21("Sub 21", 16, 20), SENIOR("Senior", 16, Integer.MAX_VALUE);

	private final String nombre;
	private final int edadMinima;
	private final int edadMaxima;

	TipoCategoria(String nombre, int edadMinima, int edadMaxima) {
		this.nombre = nombre;
		this.edadMinima = edadMinima;
		this.edadMaxima = edadMaxima;
	}

	public String getNombre() {
		return nombre;
	}

	public int getEdadMinima() {
		return edadMinima;
	}

	public int getEdadMaxima() {
		return edadMaxima;
	}
}