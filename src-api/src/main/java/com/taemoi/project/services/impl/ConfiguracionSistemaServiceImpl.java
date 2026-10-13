package com.taemoi.project.services.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.taemoi.project.entities.ConfiguracionOrganizacion;
import com.taemoi.project.organizacion.ResolutorOrganizacion;
import com.taemoi.project.organizacion.TenantContext;
import com.taemoi.project.repositories.ConfiguracionOrganizacionRepository;
import com.taemoi.project.services.ConfiguracionSistemaService;

import java.util.Set;

import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolationException;

/**
 * Configuración de la organización activa (tabla configuracion_organizacion, filtrada por
 * {@code @TenantId}). Sustituye a configuracion_sistema, que queda sin uso hasta la contracción.
 */
@Service
public class ConfiguracionSistemaServiceImpl implements ConfiguracionSistemaService {

    private static final Logger logger = LoggerFactory.getLogger(ConfiguracionSistemaServiceImpl.class);
    private static final String CLAVE_LIMITE_TURNO = "limite_turno";
    private static final Integer LIMITE_TURNO_DEFAULT = 36;

    @Autowired
    private ConfiguracionOrganizacionRepository configuracionOrganizacionRepository;

    @Autowired
    private ResolutorOrganizacion resolutorOrganizacion;

    /**
     * Al arrancar, crea el límite por defecto de la organización por defecto si no existe
     * (mismo comportamiento que antes con configuracion_sistema).
     */
    @PostConstruct
    public void inicializarConfiguracion() {
        TenantContext.ejecutarCon(resolutorOrganizacion.getOrganizacionPorDefecto(), () -> {
            if (configuracionOrganizacionRepository.findByClave(CLAVE_LIMITE_TURNO).isEmpty()) {
                configuracionOrganizacionRepository.save(
                        new ConfiguracionOrganizacion(CLAVE_LIMITE_TURNO, String.valueOf(LIMITE_TURNO_DEFAULT)));
            }
        });
    }

    /**
     * Obtiene el límite de alumnos por turno.
     *
     * @return El límite de alumnos por turno configurado.
     */
    @Override
    public Integer obtenerLimiteTurno() {
        return configuracionOrganizacionRepository.findByClave(CLAVE_LIMITE_TURNO)
                .map(config -> aEntero(config.getValor()))
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
        if (nuevoLimite < 1) {
            // Misma validación que tenía ConfiguracionSistema.valor (@Min(1)).
            throw new ConstraintViolationException("El límite debe ser al menos 1", Set.of());
        }
        ConfiguracionOrganizacion config = configuracionOrganizacionRepository.findByClave(CLAVE_LIMITE_TURNO)
                .orElseGet(() -> new ConfiguracionOrganizacion(CLAVE_LIMITE_TURNO, String.valueOf(nuevoLimite)));
        config.setValor(String.valueOf(nuevoLimite));
        configuracionOrganizacionRepository.save(config);
    }

    private Integer aEntero(String valor) {
        try {
            return Integer.valueOf(valor.trim());
        } catch (RuntimeException e) {
            logger.warn("Valor no numérico para {}: '{}'. Se usa {}", CLAVE_LIMITE_TURNO, valor, LIMITE_TURNO_DEFAULT);
            return LIMITE_TURNO_DEFAULT;
        }
    }
}
