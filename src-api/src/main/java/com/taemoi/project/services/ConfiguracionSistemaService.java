package com.taemoi.project.services;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

public interface ConfiguracionSistemaService {

    Integer obtenerLimiteTurno();

    void actualizarLimiteTurno(@NonNull Integer nuevoLimite);

    @Nullable
    String obtenerSpotifyUrl();

    void actualizarSpotifyUrl(@Nullable String spotifyUrl);
}
