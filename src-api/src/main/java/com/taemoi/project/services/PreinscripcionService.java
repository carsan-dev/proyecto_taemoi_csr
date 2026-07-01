package com.taemoi.project.services;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.dtos.request.FinalizarPreinscripcionRequest;
import com.taemoi.project.dtos.request.PreinscripcionRequest;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.Deporte;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.PlantillaPreinscripcion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.PlantillaPreinscripcionRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.TurnoRepository;

@Service
public class PreinscripcionService {
 private static final Logger log=LoggerFactory.getLogger(PreinscripcionService.class);
 private final PreinscripcionRepository repo; private final PlantillaPreinscripcionRepository plantillas; private final GrupoRepository grupos; private final TurnoRepository turnos;
 private final AlumnoRepository alumnos; private final AlumnoDeporteRepository alumnoDeportes; private final TemporadaService temporadas; private final PDFService pdf;
 private final EmailService email; private final ConfiguracionSistemaService configuracion; private final ObjectMapper json;
 @Value("${app.preinscripciones.admin-email:}") private String adminEmail;
 @Value("${app.frontend.base-url:http://localhost:4200}") private String frontendBaseUrl;
 public PreinscripcionService(PreinscripcionRepository r,PlantillaPreinscripcionRepository p,GrupoRepository g,TurnoRepository t,AlumnoRepository a,AlumnoDeporteRepository ad,TemporadaService ts,PDFService ps,EmailService es,ConfiguracionSistemaService cs,ObjectMapper om){repo=r;plantillas=p;grupos=g;turnos=t;alumnos=a;alumnoDeportes=ad;temporadas=ts;pdf=ps;email=es;configuracion=cs;json=om;}

 @Transactional public Map<String,Object> crear(PreinscripcionRequest d){
  String dni=d.dni().trim().toUpperCase(Locale.ROOT), temporada=temporadas.actual();
  if(repo.existsByDniAndDeporteAndTemporadaAndEstadoNot(dni,d.deporte(),temporada,EstadoPreinscripcion.CANCELADA)) throw new IllegalArgumentException("Ya existe una preinscripción activa para ese DNI, deporte y temporada.");
  Grupo grupo=resolverGrupo(d);
  if(grupo.getDeporte()!=d.deporte()) throw new IllegalArgumentException("El grupo no pertenece al deporte seleccionado.");
  List<Turno> horarios=turnos.findByGrupo(grupo).stream().sorted(comparadorTurnos()).toList();
  if(horarios.isEmpty()) throw new IllegalArgumentException("El grupo seleccionado no tiene horarios publicados.");
  if(Period.between(d.fechaNacimiento(),LocalDate.now()).getYears()<18&&(blank(d.tutorNombre())||blank(d.tutorDni()))) throw new IllegalArgumentException("Los menores deben indicar responsable legal y su DNI.");
  byte[] firma=decodificarFirma(d.firmaBase64()); PlantillaPreinscripcion plantilla=plantillas.findFirstByDeporteAndActivaTrueOrderByVersionDesc(d.deporte()).orElseThrow(()->new IllegalStateException("No hay plantilla activa."));
  Preinscripcion p=new Preinscripcion(); p.setReferencia(referencia()); p.setTemporada(temporada); p.setDeporte(d.deporte()); p.setNombre(clean(d.nombre())); p.setApellidos(clean(d.apellidos())); p.setDni(dni); p.setFechaNacimiento(d.fechaNacimiento()); p.setDireccion(clean(d.direccion())); p.setTelefono(clean(d.telefono())); p.setEmail(d.email().trim().toLowerCase(Locale.ROOT)); p.setTutorNombre(clean(d.tutorNombre())); p.setTutorDni(clean(d.tutorDni())); p.setConsentimientoFotografico(d.consentimientoFotografico()); p.setAceptacionNormas(true); p.setFirmanteNombre(clean(d.firmanteNombre())); p.setFirma(firma); p.setGrupo(grupo); p.setGrupoSnapshot(snapshotGrupo(grupo,horarios)); p.setPlantilla(plantilla);
  try{p.setPlantillaSnapshot(json.writeValueAsString(Map.of("version",plantilla.getVersion(),"contenido",plantilla.getContenido(),"instrucciones",plantilla.getInstrucciones())));}catch(Exception e){throw new IllegalStateException(e);}
  String token=UUID.randomUUID().toString().replace("-",""); p.setTokenDocumentoHash(hash(token)); p.setPdfFirmado(pdf.generarPreinscripcionFirmada(p)); repo.save(p); enviar(p); notificarAdmin(p);
  return Map.of("referencia",p.getReferencia(),"temporada",p.getTemporada(),"tokenDocumento",token,"emailEnviado",p.getEmailEnviado(),"mensaje","Preinscripción recibida; no equivale a inscripción ni reserva de plaza.");
 }
 public Map<String,Object> configuracion(Deporte deporte){PlantillaPreinscripcion p=plantillas.findFirstByDeporteAndActivaTrueOrderByVersionDesc(deporte).orElseThrow(); return Map.of("deporte",deporte,"version",p.getVersion(),"contenido",parse(p.getContenido()),"instrucciones",p.getInstrucciones());}
 public List<Map<String,Object>> grupos(Deporte deporte){return grupos.findAll().stream().filter(g->g.getDeporte()==deporte).filter(this::visibleEnPreinscripcion).sorted(comparadorGrupos()).map(g->Map.<String,Object>of("id",g.getId(),"deporte",deporte,"nombre",nombrePublico(g),"rangoEdadMin",Optional.ofNullable(g.getRangoEdadMin()).orElse(0),"rangoEdadMax",Optional.ofNullable(g.getRangoEdadMax()).orElse(99),"turnos",turnos.findByGrupo(g).stream().sorted(comparadorTurnos()).map(this::turnoPublico).toList())).toList();}
 public List<Map<String,Object>> turnos(Deporte deporte){return turnos.findAllWithAlumnos().stream().filter(t->t.getGrupo()!=null&&t.getGrupo().getDeporte()==deporte).map(t->Map.<String,Object>of("id",t.getId(),"deporte",deporte,"grupo",t.getGrupo().getNombre(),"grupoId",t.getGrupo().getId(),"rangoEdadMin",Optional.ofNullable(t.getGrupo().getRangoEdadMin()).orElse(0),"rangoEdadMax",Optional.ofNullable(t.getGrupo().getRangoEdadMax()).orElse(99),"diaSemana",t.getDiaSemana(),"horaInicio",t.getHoraInicio(),"horaFin",t.getHoraFin())).toList();}
 public byte[] documento(String referencia,String token){Preinscripcion p=buscar(referencia); if(!MessageDigest.isEqual(hash(token).getBytes(),p.getTokenDocumentoHash().getBytes()))throw new SecurityException("Token de documento inválido."); return p.getPdfFirmado();}
 public Page<Preinscripcion> listar(String temporada,Deporte deporte,EstadoPreinscripcion estado,String query,Pageable pageable){Specification<Preinscripcion> spec=Specification.where(null);if(temporada!=null&&!temporada.isBlank())spec=spec.and((root,q,cb)->cb.equal(root.get("temporada"),temporada));if(deporte!=null)spec=spec.and((root,q,cb)->cb.equal(root.get("deporte"),deporte));if(estado!=null)spec=spec.and((root,q,cb)->cb.equal(root.get("estado"),estado));if(query!=null&&!query.isBlank()){String patron="%"+query.trim().toUpperCase(Locale.ROOT)+"%";spec=spec.and((root,q,cb)->cb.or(cb.like(cb.upper(root.get("dni")),patron),cb.like(cb.upper(root.get("referencia")),patron)));}return repo.findAll(spec,pageable);} public Preinscripcion buscar(String ref){return repo.findByReferencia(ref).orElseThrow(()->new NoSuchElementException("Preinscripción no encontrada."));}
 public Map<String,Object> alumnoCoincidente(String dni){return alumnos.findByNif(dni).map(a->Map.<String,Object>of("id",a.getId(),"nombre",a.getNombre(),"apellidos",a.getApellidos(),"activo",Boolean.TRUE.equals(a.getActivo()))).orElse(null);}
 @Transactional public void cancelar(String ref){Preinscripcion p=buscar(ref); if(p.getEstado()!=EstadoPreinscripcion.PENDIENTE)throw new IllegalStateException("Solo se puede cancelar una solicitud pendiente."); p.setEstado(EstadoPreinscripcion.CANCELADA);p.setCanceladaEn(Instant.now());}
 @Transactional public void reenviar(String ref){enviar(buscar(ref));}
 @Transactional public void finalizar(String ref,FinalizarPreinscripcionRequest d){Preinscripcion p=buscar(ref); if(p.getEstado()!=EstadoPreinscripcion.PENDIENTE)throw new IllegalStateException("La solicitud no está pendiente."); Grupo g=p.getGrupo()!=null?p.getGrupo():(p.getTurno()!=null?p.getTurno().getGrupo():null); if(g==null)throw new IllegalStateException("La solicitud no tiene grupo asociado."); List<Turno> horarios=turnos.findByGrupo(g); if(horarios.isEmpty())throw new IllegalStateException("El grupo seleccionado no tiene horarios."); for(Turno t:horarios){if(t.getAlumnos().size()>=configuracion.obtenerLimiteTurno())throw new IllegalStateException("El grupo seleccionado ya no tiene plazas en todos sus horarios.");} Alumno a=alumnos.findById(d.alumnoId()).orElseThrow(); if(d.actualizarDatos()){a.setNombre(p.getNombre());a.setApellidos(p.getApellidos());a.setNif(p.getDni());a.setDireccion(p.getDireccion());a.setTelefono(Integer.valueOf(p.getTelefono().replaceAll("\\D","")));a.setEmail(p.getEmail());} if(d.reactivar())a.setActivo(true); AlumnoDeporte ad=alumnoDeportes.findByAlumnoIdAndDeporte(a.getId(),p.getDeporte()).orElseGet(()->{AlumnoDeporte n=new AlumnoDeporte();n.setAlumno(a);n.setDeporte(p.getDeporte());return n;}); ad.setActivo(true); alumnoDeportes.save(ad); horarios.forEach(a::addTurno); if(!g.getAlumnos().contains(a))g.addAlumno(a); alumnos.save(a); p.setAlumno(a);p.setEstado(EstadoPreinscripcion.FINALIZADA);p.setFinalizadaEn(Instant.now());}
 @Transactional public PlantillaPreinscripcion nuevaPlantilla(Deporte deporte,String contenido,String instrucciones){jsonValido(contenido); List<PlantillaPreinscripcion> anteriores=plantillas.findByDeporteOrderByVersionDesc(deporte); anteriores.forEach(x->x.setActiva(false)); PlantillaPreinscripcion p=new PlantillaPreinscripcion();p.setDeporte(deporte);p.setVersion(anteriores.isEmpty()?1:anteriores.get(0).getVersion()+1);p.setActiva(true);p.setContenido(contenido);p.setInstrucciones(clean(instrucciones));return plantillas.save(p);}
 private void enviar(Preinscripcion p){p.setEmailIntentos(p.getEmailIntentos()+1);try{email.sendEmailConAdjunto(p.getEmail(),"Preinscripción recibida · "+p.getReferencia(),htmlConfirmacion(p),"preinscripcion-"+p.getReferencia()+".pdf",p.getPdfFirmado());p.setEmailEnviado(true);p.setEmailUltimoError(null);}catch(Exception e){p.setEmailEnviado(false);p.setEmailUltimoError(clean(e.getMessage()));}}
 private String htmlConfirmacion(Preinscripcion p){return "<div style='font-family:Arial,sans-serif;color:#172126;line-height:1.55;max-width:640px'><h2 style='margin-bottom:8px'>Hemos recibido tu preinscripción</h2><p>Hola, "+esc(p.getNombre())+".</p><p>La solicitud para <strong>"+esc(nombreDeporte(p.getDeporte()))+"</strong>, temporada <strong>"+esc(p.getTemporada())+"</strong>, se ha registrado correctamente con la referencia <strong>"+esc(p.getReferencia())+"</strong>.</p><h3 style='margin:24px 0 10px'>Próximos pasos</h3><ol style='padding-left:22px'><li style='margin-bottom:12px'><strong>Descarga y revisa el documento adjunto.</strong><br/>Comprueba que los datos personales, el grupo solicitado, los consentimientos y la firma sean correctos. Si detectas algún error, contacta con el club antes de formalizar la inscripción.</li><li><strong>Formaliza la inscripción al inicio de la temporada.</strong><br/>Acude presencialmente al club con el documento disponible en tu móvil o impreso. Allí confirmaremos la disponibilidad y podrás abonar el importe correspondiente a la inscripción.</li></ol><p style='margin-top:24px;padding:12px;border-left:4px solid #b7272d;background:#f4f1e9'><strong>Importante:</strong> esta preinscripción no supone una inscripción definitiva ni reserva de plaza.</p><p>Conserva este correo y la referencia de la solicitud hasta completar el trámite presencial.</p><p>Un saludo,<br/><strong>Moi's Kim Do</strong></p></div>";}
 private void notificarAdmin(Preinscripcion p){if(adminEmail==null||adminEmail.isBlank())return;try{email.sendEmail(adminEmail.trim(),"Nueva preinscripción recibida: "+p.getReferencia(),htmlAdmin(p));}catch(Exception e){log.warn("No se pudo enviar aviso interno de preinscripción {}: {}",p.getReferencia(),e.getMessage());}}
 private String htmlAdmin(Preinscripcion p){String url=frontendBaseUrl.replaceAll("/+$","")+"/preinscripciones";return "<h2>Nueva preinscripción recibida</h2><p><strong>Referencia:</strong> "+esc(p.getReferencia())+"</p><table cellpadding='6' cellspacing='0' border='1' style='border-collapse:collapse'><tr><td><strong>Alumno/a</strong></td><td>"+esc(p.getNombre()+" "+p.getApellidos())+"</td></tr><tr><td><strong>DNI/NIE</strong></td><td>"+esc(p.getDni())+"</td></tr><tr><td><strong>Deporte</strong></td><td>"+esc(String.valueOf(p.getDeporte()))+"</td></tr><tr><td><strong>Grupo</strong></td><td>"+esc(p.getGrupoSnapshot()!=null?p.getGrupoSnapshot():p.getTurnoSnapshot())+"</td></tr><tr><td><strong>Teléfono</strong></td><td>"+esc(p.getTelefono())+"</td></tr><tr><td><strong>Email</strong></td><td>"+esc(p.getEmail())+"</td></tr><tr><td><strong>Temporada</strong></td><td>"+esc(p.getTemporada())+"</td></tr></table><p>Revisar en el panel: <a href='"+esc(url)+"'>"+esc(url)+"</a></p><p>La solicitud queda pendiente hasta que se revise y se formalice presencialmente.</p>";}
 private byte[] decodificarFirma(String value){try{String b=value.substring(value.indexOf(',')+1);byte[] data=java.util.Base64.getDecoder().decode(b);if(data.length<100||data.length>500000)throw new IllegalArgumentException();BufferedImage original=ImageIO.read(new ByteArrayInputStream(data));if(original==null||original.getWidth()>2400||original.getHeight()>1200)throw new IllegalArgumentException();BufferedImage limpia=new BufferedImage(original.getWidth(),original.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=limpia.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,limpia.getWidth(),limpia.getHeight());g.drawImage(original,0,0,null);g.dispose();ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(limpia,"png",out);return out.toByteArray();}catch(Exception e){throw new IllegalArgumentException("La firma no es una imagen válida o excede 500 KB.");}}
 private Map<String,Object> turnoPublico(Turno t){return Map.of("id",t.getId(),"diaSemana",t.getDiaSemana(),"horaInicio",t.getHoraInicio(),"horaFin",t.getHoraFin());}
 private boolean visibleEnPreinscripcion(Grupo g){return g.getDeporte()!=Deporte.TAEKWONDO||!normalizar(g.getNombre()+" "+g.getTipo()).contains("competicion");}
 private String nombrePublico(Grupo g){return nombreDeporte(g.getDeporte());}
 private String nombreDeporte(Deporte deporte){return switch(deporte){case TAEKWONDO -> "Taekwondo";case KICKBOXING -> "Kickboxing";case PILATES -> "Pilates";case DEFENSA_PERSONAL_FEMENINA -> "Defensa personal femenina";};}
 private String normalizar(String s){return s==null?"":java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
 private Grupo resolverGrupo(PreinscripcionRequest d){if(d.grupoId()!=null)return grupos.findById(d.grupoId()).orElseThrow(()->new IllegalArgumentException("Grupo no encontrado."));Turno turno=turnos.findById(d.turnoId()).orElseThrow(()->new IllegalArgumentException("Turno no encontrado."));if(turno.getGrupo()==null)throw new IllegalArgumentException("El turno no tiene grupo asociado.");return turno.getGrupo();}
 private String snapshotGrupo(Grupo g,List<Turno> horarios){String rango=Optional.ofNullable(g.getRangoEdadMin()).orElse(0)+"-"+Optional.ofNullable(g.getRangoEdadMax()).orElse(99)+" años";String hs=horarios.stream().map(t->t.getDiaSemana()+" "+t.getHoraInicio()+"-"+t.getHoraFin()).reduce((a,b)->a+"; "+b).orElse("");return g.getNombre()+" · "+rango+" · "+hs;}
 private Comparator<Grupo> comparadorGrupos(){return Comparator.comparingInt((Grupo g)->Optional.ofNullable(g.getRangoEdadMin()).orElse(0)).thenComparingInt(g->Optional.ofNullable(g.getRangoEdadMax()).orElse(99)).thenComparingInt(this::primerDiaGrupo).thenComparing(this::primeraHoraGrupo).thenComparing(Grupo::getId);}
 private int primerDiaGrupo(Grupo g){return turnos.findByGrupo(g).stream().mapToInt(t->ordenDia(t.getDiaSemana())).min().orElse(99);}
 private String primeraHoraGrupo(Grupo g){return turnos.findByGrupo(g).stream().map(Turno::getHoraInicio).filter(h->h!=null&&!h.isBlank()).min(String::compareTo).orElse("99:99");}
 private Comparator<Turno> comparadorTurnos(){return Comparator.comparingInt((Turno t)->ordenDia(t.getDiaSemana())).thenComparing(Turno::getHoraInicio);}
 private int ordenDia(String dia){return switch(normalizar(dia)){case "lunes"->1;case "martes"->2;case "miercoles"->3;case "jueves"->4;case "viernes"->5;case "sabado"->6;case "domingo"->7;default->99;};}
 private String referencia(){return "PRE-"+LocalDate.now().getYear()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();} private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private String clean(String s){return s==null?null:s.replaceAll("[<>]","").trim();} private boolean blank(String s){return s==null||s.isBlank();} private Object parse(String s){try{return json.readTree(s);}catch(Exception e){return s;}} private void jsonValido(String s){try{json.readTree(s);}catch(Exception e){throw new IllegalArgumentException("El contenido debe ser JSON válido.");}}
 private String esc(String value){return value==null?"":value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
