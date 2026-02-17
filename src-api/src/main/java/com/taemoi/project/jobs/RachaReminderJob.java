package com.taemoi.project.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.taemoi.project.services.RecordatorioRachaService;

@Component
public class RachaReminderJob {

	private final RecordatorioRachaService recordatorioRachaService;

	public RachaReminderJob(RecordatorioRachaService recordatorioRachaService) {
		this.recordatorioRachaService = recordatorioRachaService;
	}

	@Scheduled(cron = "${streak.reminder.cron:0 */10 * * * *}")
	public void ejecutarRecordatorioRacha() {
		recordatorioRachaService.enviarRecordatoriosSiCorresponde();
	}
}
