package com.taemoi.project.plataforma.importacion;

import java.util.List;

/**
 * Columnas de la hoja «Alumnos» de la hoja maestra (fase4-importacion-plan.md §1). Se localizan por el nombre de la
 * cabecera (fila 1), no por su posición; las desconocidas se ignoran.
 */
final class ColumnasHoja {

	static final String HOJA = "Alumnos";

	static final String NUMERO_FEDERACION = "numero_federacion";
	static final String NOMBRE_FEDERACION = "nombre_federacion";
	static final String FECHA_NACIMIENTO = "fecha_nacimiento";
	static final String MENOR = "menor";
	static final String GRADO_FEDERACION = "grado_federacion";
	static final String DNI_FEDERACION = "dni_federacion";
	static final String FECHA_LICENCIA = "fecha_licencia";
	static final String NOMBRE = "nombre";
	static final String APELLIDOS = "apellidos";
	static final String DNI_NIE = "dni_nie";
	static final String GRADO = "grado";
	static final String SEDE = "sede";
	static final String GRUPO = "grupo";
	static final String EMAIL_FAMILIA = "email_familia";
	static final String TELEFONO = "telefono";
	static final String TELEFONO2 = "telefono2";
	static final String DIRECCION = "direccion";
	static final String LOCALIDAD = "localidad";
	static final String CODIGO_POSTAL = "codigo_postal";
	static final String TUTOR_NOMBRE = "tutor_nombre";
	static final String TUTOR_PARENTESCO = "tutor_parentesco";
	static final String TUTOR_DNI = "tutor_dni";
	static final String TARIFA = "tarifa";
	static final String AUTORIZACION_IMAGEN = "autorizacion_imagen";
	static final String OBSERVACIONES = "observaciones";
	static final String REVISAR = "revisar";

	/** Todas las columnas conocidas, en el orden de la hoja maestra. */
	static final List<String> CONOCIDAS = List.of(NUMERO_FEDERACION, NOMBRE_FEDERACION, FECHA_NACIMIENTO, MENOR,
			GRADO_FEDERACION, DNI_FEDERACION, FECHA_LICENCIA, NOMBRE, APELLIDOS, DNI_NIE, GRADO, SEDE, GRUPO,
			EMAIL_FAMILIA, TELEFONO, TELEFONO2, DIRECCION, LOCALIDAD, CODIGO_POSTAL, TUTOR_NOMBRE, TUTOR_PARENTESCO,
			TUTOR_DNI, TARIFA, AUTORIZACION_IMAGEN, OBSERVACIONES, REVISAR);

	/** Las que tiene que tener la hoja para aceptarla (aunque alguna celda vaya vacía). */
	static final List<String> OBLIGATORIAS = List.of(NUMERO_FEDERACION, NOMBRE, APELLIDOS, FECHA_NACIMIENTO, GRADO,
			SEDE, GRUPO, TELEFONO, REVISAR);

	private ColumnasHoja() {
	}
}
