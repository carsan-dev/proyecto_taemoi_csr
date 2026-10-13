package com.taemoi.project.entities;

/**
 * A qué grupo de edad se aplica un grado como destino de progresión (columna {@code grado.aplica_a}).
 *
 * <p>{@code NINGUNO}: el grado existe (un alumno puede tenerlo) pero nunca es el siguiente de otro.
 * La regla completa está en {@code ProgresionGradosService}.
 */
public enum AplicaGrado {
	TODOS, MENORES, ADULTOS, NINGUNO
}
