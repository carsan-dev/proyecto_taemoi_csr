package com.taemoi.project.services;

import org.springframework.lang.NonNull;

public interface ConfiguracionSistemaService {

    Integer obtenerLimiteTurno();

    void actualizarLimiteTurno(@NonNull Integer nuevoLimite);
}
