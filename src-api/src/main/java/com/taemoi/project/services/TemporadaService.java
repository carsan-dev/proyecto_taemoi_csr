package com.taemoi.project.services;
import java.time.*; import org.springframework.stereotype.Service;
@Service public class TemporadaService {
 public String actual(){return paraFecha(LocalDate.now(ZoneId.of("Europe/Madrid")));}
 public String paraFecha(LocalDate fecha){int y=fecha.getYear(); if(fecha.isBefore(LocalDate.of(y,6,30))) y--; return y+"-"+(y+1);}
 public int edadCohorte(LocalDate fechaNacimiento,String temporada){
  if(fechaNacimiento==null||temporada==null||!temporada.matches("\\d{4}-\\d{4}"))throw new IllegalArgumentException("Temporada o fecha de nacimiento inválida.");
  int inicio=Integer.parseInt(temporada.substring(0,4)),fin=Integer.parseInt(temporada.substring(5));
  if(fin!=inicio+1)throw new IllegalArgumentException("La temporada debe tener formato AAAA-AAAA con años consecutivos.");
  return inicio-fechaNacimiento.getYear();
 }
}
