package com.taemoi.project.dtos.request;

/**
 * Pieza E8-4: el token de una invitación en el cuerpo ({@code POST /api/auth/invitacion/datos}), nunca en la URL: así
 * no queda en la barra de direcciones, los parámetros ni los logs de acceso.
 */
public record InvitacionTokenRequest(String token) {
}
