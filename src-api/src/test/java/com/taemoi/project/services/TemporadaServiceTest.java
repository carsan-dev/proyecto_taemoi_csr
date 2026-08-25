package com.taemoi.project.services;
import static org.junit.jupiter.api.Assertions.*; import java.time.LocalDate; import org.junit.jupiter.api.Test;
class TemporadaServiceTest {private final TemporadaService service=new TemporadaService();
 @Test void antesDelCorteUsaTemporadaAnterior(){assertEquals("2025-2026",service.paraFecha(LocalDate.of(2026,6,29)));}
 @Test void elTreintaDeJunioAbreTemporadaNueva(){assertEquals("2026-2027",service.paraFecha(LocalDate.of(2026,6,30)));}
 @Test void despuesDelCorteMantieneTemporadaNueva(){assertEquals("2026-2027",service.paraFecha(LocalDate.of(2027,1,15)));}
 @Test void cohorteIgnoraSiElCumpleanosYaHaOcurrido(){assertEquals(8,service.edadCohorte(LocalDate.of(2018,1,2),"2026-2027"));}
 @Test void cohorteIgnoraSiElCumpleanosAunNoHaOcurrido(){assertEquals(8,service.edadCohorte(LocalDate.of(2018,12,30),"2026-2027"));}
 @Test void rechazaTemporadaInvalida(){assertThrows(IllegalArgumentException.class,()->service.edadCohorte(LocalDate.of(2018,1,1),"2026/2027"));}
}
