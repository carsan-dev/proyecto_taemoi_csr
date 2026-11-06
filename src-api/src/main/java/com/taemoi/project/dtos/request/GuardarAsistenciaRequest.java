package com.taemoi.project.dtos.request;

import java.util.List;
import java.util.Map;

/**
 * {@code PUT .../clases/{turnoId}/{fecha}} (decisión 324): {@code {"presentes": [ids]}}, los alumnos presentes. Los demás
 * alumnos de la lista quedan como falta. {@code motivos} (opcional, decisiones 455-463): id del alumno → motivo de su
 * falta ({@code INJUSTIFICADA}, {@code MEDICA}, {@code FAMILIAR} u {@code OTRA_JUSTIFICADA}); sin él, o con un alumno que
 * no aparece, la falta queda sin motivo. El PUT reemplaza la lista entera, también los motivos.
 */
public record GuardarAsistenciaRequest(List<Long> presentes, Map<Long, String> motivos) {
}
