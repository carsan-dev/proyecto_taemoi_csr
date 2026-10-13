package com.taemoi.project.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.taemoi.project.organizacion.EjecutorPorOrganizacion;
import com.taemoi.project.services.RecordatorioRachaService;

@Component
public class RachaReminderJob {

	private final RecordatorioRachaService recordatorioRachaService;
	private final EjecutorPorOrganizacion ejecutorPorOrganizacion;

	public RachaReminderJob(RecordatorioRachaService recordatorioRachaService,
			EjecutorPorOrganizacion ejecutorPorOrganizacion) {
		this.recordatorioRachaService = recordatorioRachaService;
		this.ejecutorPorOrganizacion = ejecutorPorOrganizacion;
	}

	/** Una pasada por cada organización activa, con su organización fijada. */
	@Scheduled(cron = "${streak.reminder.cron:0 */10 * * * *}")
	public void ejecutarRecordatorioRacha() {
		ejecutorPorOrganizacion.paraCadaOrganizacionActiva("recordatorio-racha",
				organizacionId -> recordatorioRachaService.enviarRecordatoriosSiCorresponde());
	}
}
