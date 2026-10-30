package com.taemoi.project.dtos.request;

import java.util.List;

/**
 * {@code PUT .../clases/{turnoId}/{fecha}} (decisión 324): {@code {"presentes": [ids]}}, los alumnos presentes. Los demás
 * alumnos de la lista quedan como falta.
 */
public record GuardarAsistenciaRequest(List<Long> presentes) {
}
