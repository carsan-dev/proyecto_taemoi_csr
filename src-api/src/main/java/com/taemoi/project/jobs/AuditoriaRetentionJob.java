package com.taemoi.project.jobs;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.taemoi.project.services.AuditoriaService;

@Component
public class AuditoriaRetentionJob {

	private static final Logger logger = LoggerFactory.getLogger(AuditoriaRetentionJob.class);

	private final AuditoriaService auditoriaService;

	@Value("${audit.retention.months:12}")
	private int retentionMonths;

	public AuditoriaRetentionJob(AuditoriaService auditoriaService) {
		this.auditoriaService = auditoriaService;
	}

	@Scheduled(cron = "${audit.retention.cleanup-cron:0 15 3 * * *}")
	public void ejecutarLimpiezaRetencion() {
		LocalDate fechaLimite = LocalDate.now().minusMonths(Math.max(1, retentionMonths));
		long eliminados = auditoriaService.eliminarEventosAnterioresA(fechaLimite);
		logger.info("Auditoria retention cleanup finalizado. Registros eliminados: {}", eliminados);
	}
}
