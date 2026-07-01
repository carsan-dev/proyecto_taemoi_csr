package com.taemoi.project.dtos.request;
import jakarta.validation.constraints.NotNull;
public record FinalizarPreinscripcionRequest(@NotNull Long alumnoId, boolean actualizarDatos, boolean reactivar) {}
