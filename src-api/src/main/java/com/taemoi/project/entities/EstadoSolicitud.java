package com.taemoi.project.entities;

/**
 * Estado de una {@link Solicitud} (fase3-6c-solicitudes.md §6.1). Las transiciones permitidas están en un solo sitio:
 * {@code SolicitudTransiciones}.
 * <ul>
 * <li>Tipos 1 a 3: {@link #PENDIENTE} y, al resolverse, {@link #ACEPTADA}, {@link #DENEGADA}, {@link #RETIRADA} o
 * {@link #NO_APLICABLE}.</li>
 * <li>Encargo: {@link #PEDIDO}, {@link #ENCARGADO}, {@link #RECIBIDO}, {@link #ENTREGADO}, {@link #RECHAZADO} o
 * {@link #RETIRADA}.</li>
 * </ul>
 */
public enum EstadoSolicitud {
	PENDIENTE,
	ACEPTADA,
	DENEGADA,
	RETIRADA,
	NO_APLICABLE,
	PEDIDO,
	ENCARGADO,
	RECIBIDO,
	ENTREGADO,
	RECHAZADO
}
