package com.taemoi.project.services;
import java.time.*; import org.springframework.stereotype.Service;
@Service public class TemporadaService {
 public String actual(){return paraFecha(LocalDate.now(ZoneId.of("Europe/Madrid")));}
 public String paraFecha(LocalDate fecha){int y=fecha.getYear(); if(fecha.isBefore(LocalDate.of(y,6,30))) y--; return y+"-"+(y+1);}
}
