package com.taemoi.project.services.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.taemoi.project.entities.ConfiguracionSistema;
import com.taemoi.project.repositories.ConfiguracionSistemaRepository;
import com.taemoi.project.services.ConfiguracionSistemaService;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;

/**
 * Implementación del servicio para operaciones relacionadas con la configuración del sistema.
 */
@Service
public class ConfiguracionSistemaServiceImpl implements ConfiguracionSistemaService {

    private static final String CLAVE_LIMITE_TURNO = "limite_turno";
    private static final Integer LIMITE_TURNO_DEFAULT = 36;
    private static final String CLAVE_SPOTIFY_URL = "spotify_url";
    private static final Integer VALOR_TEXTO_DEFAULT = 1;

    @Autowired
    private ConfiguracionSistemaRepository configuracionSistemaRepository;

    /**
     * Inicializa la configuración del sistema con valores por defecto si no existen.
     */
    @PostConstruct
    @Transactional
    public void inicializarConfiguracion() {
        if (configuracionSistemaRepository.findByClave(CLAVE_LIMITE_TURNO).isEmpty()) {
            ConfiguracionSistema config = new ConfiguracionSistema(CLAVE_LIMITE_TURNO, LIMITE_TURNO_DEFAULT);
            configuracionSistemaRepository.save(config);
        }
    }

    /**
     * Obtiene el límite de alumnos por turno.
     *
     * @return El límite de alumnos por turno configurado.
     */
    @Override
    public Integer obtenerLimiteTurno() {
        return configuracionSistemaRepository.findByClave(CLAVE_LIMITE_TURNO)
                .map(ConfiguracionSistema::getValor)
                .orElse(LIMITE_TURNO_DEFAULT);
    }

    /**
     * Actualiza el límite de alumnos por turno.
     *
     * @param nuevoLimite El nuevo límite a establecer.
     */
    @Override
    @Transactional
    public void actualizarLimiteTurno(@NonNull Integer nuevoLimite) {
        ConfiguracionSistema config = configuracionSistemaRepository.findByClave(CLAVE_LIMITE_TURNO)
                .orElse(new ConfiguracionSistema(CLAVE_LIMITE_TURNO, nuevoLimite));
        config.setValor(nuevoLimite);
        configuracionSistemaRepository.save(config);
    }

    @Override
    @Nullable
    public String obtenerSpotifyUrl() {
        return configuracionSistemaRepository.findByClave(CLAVE_SPOTIFY_URL)
                .map(ConfiguracionSistema::getValorTexto)
                .orElse(null);
    }

    @Override
    @Transactional
    public void actualizarSpotifyUrl(@Nullable String spotifyUrl) {
        String normalizado = spotifyUrl == null ? null : spotifyUrl.trim();
        if (normalizado != null && normalizado.isEmpty()) {
            normalizado = null;
        }

        ConfiguracionSistema config = configuracionSistemaRepository.findByClave(CLAVE_SPOTIFY_URL).orElse(null);
        if (normalizado == null && config == null) {
            return;
        }

        if (config == null) {
            config = new ConfiguracionSistema(CLAVE_SPOTIFY_URL, VALOR_TEXTO_DEFAULT);
        }
        config.setValor(VALOR_TEXTO_DEFAULT);
        config.setValorTexto(normalizado);
        configuracionSistemaRepository.save(config);
    }
}
