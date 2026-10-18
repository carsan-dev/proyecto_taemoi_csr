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
import java.util.Collection;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taemoi.project.acceso.GestionPorSede;
import com.taemoi.project.acceso.PredicadosAlcance;
import com.taemoi.project.dtos.request.ActualizarTurnosPreinscripcionRequest;
import com.taemoi.project.dtos.request.CancelarPreinscripcionRequest;
import com.taemoi.project.dtos.request.FinalizarPreinscripcionRequest;
import com.taemoi.project.dtos.request.PreinscripcionRequest;
import com.taemoi.project.dtos.request.PlantillaPreinscripcionRequest;
import com.taemoi.project.entities.Alumno;
import com.taemoi.project.entities.AlumnoDeporte;
import com.taemoi.project.entities.EstadoPreinscripcion;
import com.taemoi.project.entities.EstadoEmailCambioTurnos;
import com.taemoi.project.entities.EstadoEmailFinalizacion;
import com.taemoi.project.entities.EstadoEmailCancelacion;
import com.taemoi.project.entities.Grupo;
import com.taemoi.project.entities.PlantillaPreinscripcion;
import com.taemoi.project.entities.Preinscripcion;
import com.taemoi.project.entities.RolFamiliar;
import com.taemoi.project.entities.Turno;
import com.taemoi.project.repositories.AlumnoDeporteRepository;
import com.taemoi.project.repositories.AlumnoRepository;
import com.taemoi.project.repositories.GrupoRepository;
import com.taemoi.project.repositories.PlantillaPreinscripcionRepository;
import com.taemoi.project.repositories.PreinscripcionDocumentosRepository;
import com.taemoi.project.repositories.PreinscripcionRepository;
import com.taemoi.project.repositories.TurnoRepository;
import com.taemoi.project.events.PreinscripcionCanceladaEvent;
import com.taemoi.project.events.PreinscripcionFinalizadaEvent;
import com.taemoi.project.events.PreinscripcionPromocionadaEvent;
import com.taemoi.project.events.PreinscripcionRecibidaEvent;
import com.taemoi.project.events.PreinscripcionTurnosModificadosEvent;
import com.taemoi.project.exceptions.preinscripcion.PreinscripcionDuplicadaException;
import com.taemoi.project.organizacion.AlcanceSesion;

/**
 * Preinscripciones. Desde V26 escribe {@code disciplina_id} junto a {@code deporte} y resuelve el grado del
 * alta en el sistema de grados de la disciplina ({@link ProgresionGradosService}).
 */
@Service
public class PreinscripcionService {
 private static final Set<EstadoPreinscripcion> ESTADOS_NO_REPETIBLES=Set.of(EstadoPreinscripcion.PENDIENTE,EstadoPreinscripcion.EN_LISTA_ESPERA,EstadoPreinscripcion.FINALIZADA);
 /** Motivo que recibe la familia cuando el personal cancela sin escribir uno. */
 public static final String MOTIVO_CANCELACION_POR_DEFECTO="No ha sido posible confirmar tu plaza en esta ocasión.";
 private static final String MENSAJE_DUPLICADA="Ya existe una preinscripción para esta persona y deporte en la temporada actual.";
 private final PreinscripcionRepository repo; private final PlantillaPreinscripcionRepository plantillas; private final GrupoRepository grupos; private final TurnoRepository turnos;
 private final AlumnoRepository alumnos; private final AlumnoDeporteRepository alumnoDeportes; private final ProgresionGradosService progresion; private final CatalogoDisciplinasService catalogo; private final TemporadaService temporadas; private final PDFService pdf;
 private final EmailService email; private final ConfiguracionSistemaService configuracion; private final ObjectMapper json; private final AforoPreinscripcionService aforo; private final ApplicationEventPublisher eventos; private final ContadorService contadores; private final TutorService tutores; private final ConsentimientoService consentimientos; private final TarifasService tarifas;
 /** Entrega 6-2: filtros y comprobaciones del responsable de sede (sin efecto para la gestión ni para el alta pública). */
 private final GestionPorSede gestionPorSede;
 /** R2-P7c: firma, PDF firmado y plantilla congelada, leídos aparte y solo donde hacen falta ({@link Preinscripcion} no los carga). */
 private final PreinscripcionDocumentosRepository documentos;
 @Value("${app.preinscripciones.admin-email:}") private String adminEmail;
 @Value("${app.frontend.base-url:http://localhost:4200}") private String frontendBaseUrl;
 public PreinscripcionService(PreinscripcionRepository r,PlantillaPreinscripcionRepository p,GrupoRepository g,TurnoRepository t,AlumnoRepository a,AlumnoDeporteRepository ad,ProgresionGradosService gr,TemporadaService ts,PDFService ps,EmailService es,ConfiguracionSistemaService cs,ObjectMapper om,AforoPreinscripcionService af,ApplicationEventPublisher ev,ContadorService cn,TutorService tu,ConsentimientoService co,CatalogoDisciplinasService cat,TarifasService tar,GestionPorSede gps,PreinscripcionDocumentosRepository doc){gestionPorSede=gps;documentos=doc;tarifas=tar;repo=r;plantillas=p;grupos=g;turnos=t;alumnos=a;alumnoDeportes=ad;progresion=gr;catalogo=cat;temporadas=ts;pdf=ps;email=es;configuracion=cs;json=om;aforo=af;eventos=ev;contadores=cn;tutores=tu;consentimientos=co;}

 @Transactional public Map<String,Object> crear(PreinscripcionRequest d){return crear(d,null);}
 @Transactional public Map<String,Object> crear(PreinscripcionRequest d,String idempotencyKey){
  catalogo.validarCodigo(d.deporte()); /* antes lo garantizaba el enum Deporte al leer la petición */
  String idempotencyKeyHash=hashIdempotencia(idempotencyKey);
  if(idempotencyKeyHash!=null&&repo.existsByIdempotencyKeyHash(idempotencyKeyHash))throw duplicada();
  String dni=blank(d.dni())?null:d.dni().trim().toUpperCase(Locale.ROOT), temporada=temporadas.actual();String identidadHash=hash(identidadCanonica(dni,d.tutorDni(),d.nombre(),d.apellidos(),d.fechaNacimiento()));
  if(repo.existsByIdentidadHashAndDisciplinaCodigoAndTemporadaAndEstadoIn(identidadHash,d.deporte(),temporada,ESTADOS_NO_REPETIBLES))throw duplicada();
  List<Turno> horarios=aforo.bloquearTurnos(d.turnoIds());validarSeleccion(d.deporte(),d.fechaNacimiento(),temporada,horarios);
  Grupo grupo=horarios.get(0).getGrupo();
  if(Period.between(d.fechaNacimiento(),LocalDate.now()).getYears()<18&&(blank(d.tutorNombre())||blank(d.tutorDni()))) throw new IllegalArgumentException("Los menores deben indicar responsable legal y su DNI.");
  byte[] firma=decodificarFirma(d.firmaBase64()); PlantillaPreinscripcion plantilla=plantillas.findFirstByDisciplinaCodigoAndActivaTrueOrderByVersionDesc(d.deporte()).orElseThrow(()->new IllegalStateException("No hay plantilla activa."));
  Preinscripcion p=new Preinscripcion(); p.setReferencia(referencia()); p.setTemporada(temporada); p.setDisciplina(catalogo.obtenerPorCodigo(d.deporte())); p.setEstado(aforo.algunaPlaza(horarios,temporada)?EstadoPreinscripcion.PENDIENTE:EstadoPreinscripcion.EN_LISTA_ESPERA); p.setNombre(clean(d.nombre())); p.setApellidos(clean(d.apellidos())); p.setDni(dni);p.setIdentidadHash(identidadHash); p.setFechaNacimiento(d.fechaNacimiento()); p.setDireccion(clean(d.direccion())); p.setTelefono(clean(d.telefono()));p.setTelefono2(blank(d.telefono2())?null:clean(d.telefono2())); p.setEmail(d.email().trim().toLowerCase(Locale.ROOT));p.setObservaciones(limpiarObservacion(d.observaciones()));p.setTieneDiscapacidad(d.tieneDiscapacidad()); p.setTutorNombre(clean(d.tutorNombre())); p.setTutorDni(blank(d.tutorDni())?null:d.tutorDni().trim().toUpperCase(Locale.ROOT)); p.setConsentimientoFotografico(d.consentimientoFotografico()); p.setAceptacionNormas(true); p.setFirmanteNombre(clean(d.firmanteNombre())); p.setFirma(firma); p.setGrupo(grupo);p.setTurnos(new LinkedHashSet<>(horarios)); p.setGrupoSnapshot(snapshotTurnos(d.deporte(),horarios)); p.setPlantilla(plantilla);
  try{p.setPlantillaSnapshot(json.writeValueAsString(Map.of("version",plantilla.getVersion(),"contenido",plantilla.getContenido(),"instrucciones",plantilla.getInstrucciones())));}catch(Exception e){throw new IllegalStateException(e);}
  String token=UUID.randomUUID().toString().replace("-",""); p.setTokenDocumentoHash(hash(token));p.setIdempotencyKeyHash(idempotencyKeyHash);p.setPdfFirmado(pdf.generarPreinscripcionFirmada(p));try{repo.saveAndFlush(p);}catch(DataIntegrityViolationException e){if(esConflictoDuplicado(e))throw duplicada(e);throw e;}
  /* R2-P7d: correo a la familia (con el PDF) y aviso interno, preparados aquí y enviados tras el commit (PreinscripcionAltaEmailListener): la respuesta lleva emailEnviado=false (pendiente). */
  eventos.publishEvent(eventoRecibida(p));
  long posicion=p.getEstado()==EstadoPreinscripcion.EN_LISTA_ESPERA?aforo.posicion(p):0;Map<String,Object> respuesta=new LinkedHashMap<>();respuesta.put("referencia",p.getReferencia());respuesta.put("temporada",p.getTemporada());respuesta.put("tokenDocumento",token);respuesta.put("emailEnviado",p.getEmailEnviado());respuesta.put("estado",p.getEstado());respuesta.put("plazaAsignada",p.getEstado()==EstadoPreinscripcion.PENDIENTE);respuesta.put("posicionListaEspera",posicion==0?null:posicion);respuesta.put("mensaje",p.getEstado()==EstadoPreinscripcion.PENDIENTE?"Preinscripción recibida con plaza asignada.":"Preinscripción añadida a la lista de espera.");return respuesta;
 }
 public Map<String,Object> configuracion(String deporte){parametroRequerido(deporte);PlantillaPreinscripcion p=plantillas.findFirstByDisciplinaCodigoAndActivaTrueOrderByVersionDesc(deporte).orElseThrow(); return Map.of("deporte",deporte,"version",p.getVersion(),"contenido",parse(p.getContenido()),"instrucciones",p.getInstrucciones());}
 /** R2-P3: los turnos de cada grupo se leen una vez (también para ordenar) y el aforo de todos, por lotes. */
 public List<Map<String,Object>> grupos(String deporte){parametroRequerido(deporte);String temporada=temporadas.actual();List<Grupo> visibles=grupos.findAll().stream().filter(g->java.util.Objects.equals(g.getDeporte(), deporte)).filter(this::visibleEnPreinscripcion).toList();Map<Grupo,List<Turno>> horarios=new java.util.IdentityHashMap<>();/* R2-P6: los turnos de todos los grupos en una consulta, por id como findByGrupo. */Map<Long,List<Turno>> porGrupo=visibles.isEmpty()?Map.of():turnos.findByGrupoIn(visibles).stream().collect(java.util.stream.Collectors.groupingBy(t->t.getGrupo().getId()));visibles.forEach(g->horarios.put(g,porGrupo.getOrDefault(g.getId(),List.of())));AforoPreinscripcionService.Ocupacion ocupacion=aforo.ocupacion(horarios.values().stream().flatMap(List::stream).toList(),temporada);return visibles.stream().sorted(comparadorGrupos(horarios::get)).map(g->Map.<String,Object>of("id",g.getId(),"deporte",deporte,"nombre",nombrePublico(g),"rangoEdadMin",Optional.ofNullable(g.getRangoEdadMin()).orElse(0),"rangoEdadMax",Optional.ofNullable(g.getRangoEdadMax()).orElse(99),"turnos",horarios.get(g).stream().sorted(comparadorTurnos()).map(t->turnoPublico(t,ocupacion)).toList(),"completo",ocupacion.completos(horarios.get(g)))).toList();}
 public List<Map<String,Object>> turnos(String deporte){parametroRequerido(deporte);String temporada=temporadas.actual();/* R2-P6: solo los turnos del deporte, sin alumnos (el aforo se cuenta por lotes). */List<Turno> lista=turnos.findConGrupoPorDisciplina(deporte).stream().filter(t->java.util.Objects.equals(t.getGrupo().getDeporte(), deporte)/* exacto, la columna no distingue mayúsculas */&&visibleEnPreinscripcion(t.getGrupo())).toList();AforoPreinscripcionService.Ocupacion ocupacion=aforo.ocupacion(lista,temporada);return lista.stream().map(t->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",t.getId());m.put("deporte",deporte);m.put("grupo",t.getGrupo().getNombre());m.put("grupoId",t.getGrupo().getId());m.put("rangoEdadMin",Optional.ofNullable(t.getGrupo().getRangoEdadMin()).orElse(0));m.put("rangoEdadMax",Optional.ofNullable(t.getGrupo().getRangoEdadMax()).orElse(99));m.put("diaSemana",t.getDiaSemana());m.put("horaInicio",t.getHoraInicio());m.put("horaFin",t.getHoraFin());m.put("completo",ocupacion.completo(t));return m;}).toList();}
 public byte[] documento(String referencia,String token){Preinscripcion p=buscar(referencia); if(!MessageDigest.isEqual(hash(token).getBytes(),p.getTokenDocumentoHash().getBytes()))throw new SecurityException("Token de documento inválido."); return pdfFirmado(p);}
 /** R2-P7c: solo la firma (sin cargar la solicitud ni el PDF). Misma respuesta que antes si no existe. */
 public byte[] firma(String referencia){return existente(documentos.findFirmaByReferencia(referencia));}
 /** R2-P7c: solo el PDF firmado (sin cargar la solicitud ni la firma). */
 public byte[] documentoFirmado(String referencia){return existente(documentos.findPdfFirmadoByReferencia(referencia));}
 /** R2-P7c: la plantilla congelada en el alta, para el detalle. */
 public String plantillaSnapshot(Preinscripcion p){return existente(documentos.findPlantillaSnapshotById(p.getId()));}
 private byte[] pdfFirmado(Preinscripcion p){return existente(documentos.findPdfFirmadoById(p.getId()));}
 /** Las columnas son NOT NULL: null solo si la solicitud no existe (o es de otra escuela). */
 private static <T> T existente(T valor){if(valor==null)throw new NoSuchElementException("Preinscripción no encontrada.");return valor;}
public Page<Preinscripcion> listar(String temporada,String deporteParametro,EstadoPreinscripcion estado,String query,Pageable pageable){String deporte=catalogo.validarParametro(deporteParametro);Specification<Preinscripcion> spec=Specification.unrestricted();if(temporada!=null&&!temporada.isBlank())spec=spec.and((root,q,cb)->cb.equal(root.get("temporada"),temporada));if(deporte!=null)spec=spec.and((root,q,cb)->cb.equal(root.get("disciplina").get("codigo"),deporte));if(estado!=null)spec=spec.and((root,q,cb)->cb.equal(root.get("estado"),estado));if(query!=null&&!query.isBlank()){String patron="%"+query.trim().toUpperCase(Locale.ROOT)+"%";spec=spec.and((root,q,cb)->cb.or(cb.like(cb.upper(root.get("dni")),patron),cb.like(cb.upper(root.get("referencia")),patron),cb.like(cb.upper(root.get("nombre")),patron),cb.like(cb.upper(root.get("apellidos")),patron)));}
  /* Entrega 6-2: el responsable de sede, solo las de sus sedes (por el grupo). La gestión, la consulta de siempre. */
  Optional<AlcanceSesion> restriccion=gestionPorSede.restriccion();if(restriccion.isPresent())spec=spec.and(PredicadosAlcance.preinscripcionesDeSusSedes(restriccion.get()));
  return repo.findAll(spec,pageable);} public Preinscripcion buscar(String ref){return repo.findByReferencia(ref).orElseThrow(()->new NoSuchElementException("Preinscripción no encontrada."));}
 /** Fichas que coinciden con la solicitud. Entrega 6-2: al responsable de sede, solo las que puede vincular (sede principal suya, D6-3). */
 public List<Map<String,Object>> alumnosCoincidentes(Preinscripcion p){List<Alumno> candidatos=buscarCandidatos(p);if(gestionPorSede.restriccion().isPresent())candidatos=candidatos.stream().filter(a->gestionPorSede.alumnoVinculable(a.getId())).toList();return candidatos.stream().map(a->candidatoDto(a,p.getDeporte())).toList();}
 private Map<String,Object> candidatoDto(Alumno a,String deporte){Map<String,Object> m=new LinkedHashMap<>();m.put("id",a.getId());m.put("nombre",a.getNombre());m.put("apellidos",a.getApellidos());m.put("nif",a.getNif());m.put("fechaNacimiento",a.getFechaNacimiento());m.put("direccion",a.getDireccion());m.put("telefono",a.getTelefono());m.put("telefono2",a.getTelefono2());m.put("email",a.getEmail());m.put("tieneDiscapacidad",a.getTieneDiscapacidad());com.taemoi.project.utils.ResponsableLegal rl=com.taemoi.project.utils.ResponsableLegal.de(a);m.put("responsableLegalNombre",rl.nombre());m.put("responsableLegalNif",rl.nif());m.put("activo",Boolean.TRUE.equals(a.getActivo()));AlumnoDeporte ad=alumnoDeportes.findByAlumnoIdAndDeporte(a.getId(),deporte).orElse(null);m.put("deporteRegistrado",ad!=null);m.put("deporteActivo",ad!=null&&Boolean.TRUE.equals(ad.getActivo()));m.put("requiereDatosDeporte",ad==null);m.put("tipoTarifa",ad==null?null:ad.getTipoTarifa());m.put("cuantiaTarifa",ad==null?null:ad.getCuantiaTarifa());m.put("rolFamiliar",ad==null?null:ad.getRolFamiliar());m.put("grupoFamiliar",ad==null?null:ad.getGrupoFamiliar());return m;}
 private List<Alumno> buscarCandidatos(Preinscripcion p){if(!blank(p.getDni())){Optional<Alumno> exacto=alumnos.findByNif(p.getDni());if(exacto.isPresent())return List.of(exacto.get());}Date fecha=java.sql.Date.valueOf(p.getFechaNacimiento());return alumnos.findByFechaNacimiento(fecha).stream().filter(a->coincideIdentidadPersonal(a,p)).filter(a->blank(p.getDni())||blank(a.getNif())||p.getDni().equalsIgnoreCase(a.getNif())).toList();}
 public Long posicionListaEspera(Preinscripcion p){return p.getEstado()==EstadoPreinscripcion.EN_LISTA_ESPERA?aforo.posicion(p):null;}
 public List<Map<String,Object>> turnosSolicitados(Preinscripcion p){return turnosDe(p).stream().map(t->{Grupo g=t.getGrupo();Map<String,Object> m=new LinkedHashMap<>();m.put("id",t.getId());m.put("grupoId",g==null?null:g.getId());m.put("grupo",g==null?"Grupo no disponible":g.getNombre());m.put("diaSemana",t.getDiaSemana());m.put("horaInicio",t.getHoraInicio());m.put("horaFin",t.getHoraFin());return m;}).toList();}
 public String grupoOriginal(Preinscripcion p){return p.getGrupoSnapshot()!=null?p.getGrupoSnapshot():p.getTurnoSnapshot();}
 public String grupoVigente(Preinscripcion p){return snapshotTurnos(p.getDeporte(),turnosDe(p));}
 /** R2-P7a: {@link #grupoVigente} para las filas de un listado, con el nombre de cada disciplina leído una vez por petición (antes, un SELECT de disciplina por fila). */
 public java.util.function.Function<Preinscripcion,String> grupoVigentePorPeticion(){Map<String,String> nombres=new java.util.HashMap<>();return p->snapshotTurnos(turnosDe(p),nombres.computeIfAbsent(p.getDeporte(),this::nombreDeporte));}
 /** Cancela, guarda el motivo (o el de por defecto si llega vacío) y publica el aviso a la familia, que sale tras el commit
  * ({@link PreinscripcionCancelacionEmailListener}): si el correo falla la cancelación se mantiene y queda el error registrado.
  * R2-P3: si libera plaza, bloquea a la vez sus turnos y los de la lista de espera ({@link #bloquearConLaCola}); con
  * los turnos ya bloqueados, bloquea la solicitud y la relee (otra operación pudo cambiarla mientras esperaba: una segunda
  * cancelación de la misma responde 409 y no repite el aviso); lee con READ COMMITTED ({@link #promocionarDisponibles}). */
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void cancelar(String ref,String motivo){String motivoFinal=motivoCancelacion(motivo);Preinscripcion p=buscar(ref);exigirCancelable(p);boolean libera=p.getEstado()==EstadoPreinscripcion.PENDIENTE;String temporada=p.getTemporada();List<Turno> seleccion=turnosDe(p);Map<Long,Turno> bloqueados=new LinkedHashMap<>(bloquearConLaCola(seleccion.stream().map(Turno::getId).toList(),libera,temporada));
  p=aforo.releerBloqueada(p);exigirCancelable(p);libera=p.getEstado()==EstadoPreinscripcion.PENDIENTE;seleccion=turnosDe(p);
  List<Long> propios=seleccion.stream().map(Turno::getId).toList();Set<Long> faltan=new java.util.TreeSet<>(propios);if(libera)faltan.addAll(aforo.turnosEnEspera(temporada));faltan.removeAll(bloqueados.keySet());if(!faltan.isEmpty())bloqueados.putAll(aforo.bloquearTurnos(propios,faltan));
  p.setEstado(EstadoPreinscripcion.CANCELADA);p.setCanceladaEn(Instant.now());p.setMotivoCancelacion(motivoFinal);p.setEstadoEmailCancelacion(EstadoEmailCancelacion.PENDIENTE);p.setEmailCancelacionEnviadoEn(null);p.setEmailCancelacionUltimoError(null);repo.saveAndFlush(p);eventos.publishEvent(new PreinscripcionCanceladaEvent(p.getReferencia()));if(libera)promocionarDisponibles(temporada,bloqueados);}
 private static void exigirCancelable(Preinscripcion p){if(p.getEstado()!=EstadoPreinscripcion.PENDIENTE&&p.getEstado()!=EstadoPreinscripcion.EN_LISTA_ESPERA)throw new IllegalStateException("Solo se puede cancelar una solicitud pendiente o en lista de espera.");}
 private String motivoCancelacion(String motivo){if(blank(motivo))return MOTIVO_CANCELACION_POR_DEFECTO;String limpio=motivo.trim();if(limpio.length()>CancelarPreinscripcionRequest.MOTIVO_MAX)throw new IllegalArgumentException("El motivo no puede superar "+CancelarPreinscripcionRequest.MOTIVO_MAX+" caracteres.");return limpio;}
 @Transactional public void eliminar(String ref){Preinscripcion p=buscar(ref);if(p.getEstado()!=EstadoPreinscripcion.CANCELADA)throw new IllegalStateException("Solo se pueden eliminar definitivamente las solicitudes canceladas.");repo.delete(p);}
 @Transactional public void reenviar(String ref){reenviarSolicitudOriginal(buscar(ref));}
 @Transactional public void reenviarCambioTurnos(String ref){Preinscripcion p=repo.findByReferenciaForUpdate(ref).orElseThrow(()->new NoSuchElementException("Preinscripción no encontrada."));if(p.getEstado()!=EstadoPreinscripcion.PENDIENTE)throw new IllegalStateException("El aviso de cambio solo puede reenviarse mientras la solicitud siga pendiente.");if(blank(p.getEmailCambioTurnosAnteriorSnapshot())||blank(p.getEmailCambioTurnosNuevoSnapshot()))throw new IllegalStateException("La solicitud no tiene ningún cambio de turnos para reenviar.");p.setEstadoEmailCambioTurnos(EstadoEmailCambioTurnos.PENDIENTE);p.setEmailCambioTurnosUltimoError(null);eventos.publishEvent(new PreinscripcionTurnosModificadosEvent(p.getReferencia()));}
 @Transactional public void reenviarFinalizacion(String ref){Preinscripcion p=repo.findByReferenciaForUpdate(ref).orElseThrow(()->new NoSuchElementException("Preinscripción no encontrada."));if(p.getEstado()!=EstadoPreinscripcion.FINALIZADA)throw new IllegalStateException("La confirmación final solo puede reenviarse para preinscripciones finalizadas.");p.setEstadoEmailFinalizacion(EstadoEmailFinalizacion.PENDIENTE);p.setEmailFinalizacionUltimoError(null);eventos.publishEvent(new PreinscripcionFinalizadaEvent(p.getReferencia()));}
 /** R2-P3: como {@link #cancelar} y {@link #actualizarTurnos}, primero sus turnos y después la solicitud, bloqueada y releída (antes, al revés: con un cambio de turnos a la vez, interbloqueo); si cambió mientras esperaba, se vuelve a exigir PENDIENTE (409) y se bloquea lo que falte. READ COMMITTED: tras el bloqueo, sus turnos son los confirmados (con REPEATABLE READ se leían los de la foto de antes de esperar). */
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void finalizar(String ref,FinalizarPreinscripcionRequest d){
  Preinscripcion p=buscar(ref);exigirFinalizable(p);
  Map<Long,Turno> bloqueados=new LinkedHashMap<>();aforo.bloquearTurnos(turnosDe(p).stream().map(Turno::getId).toList()).forEach(t->bloqueados.put(t.getId(),t));
  p=aforo.releerBloqueada(p);exigirFinalizable(p);
  List<Long> propios=turnosDe(p).stream().map(Turno::getId).distinct().sorted().toList();if(!bloqueados.keySet().containsAll(propios))aforo.bloquearTurnos(propios).forEach(t->bloqueados.put(t.getId(),t));
  List<Turno> horarios=propios.stream().map(bloqueados::get).toList();validarSeleccionAdministrativa(p.getDeporte(),horarios);
  /* Entrega 6-2. Responsable de sede: todos los turnos en sus sedes (D6-6). Todos los roles: el alumno nuevo va a la sede
     del grupo de la solicitud (D6-2; con una sola sede, la única) y nunca se finaliza hacia una sede inactiva (D6-18, 400). */
  Optional<AlcanceSesion> restriccion=gestionPorSede.restriccion();if(restriccion.isPresent())exigirTurnosDeSusSedes(horarios,restriccion.get().sedes());
  Long sedePrincipal=gestionPorSede.sedeDeAlta(sedeDeLaSolicitud(p,horarios),true);
  Boolean discapacidad=p.getTieneDiscapacidad()!=null?p.getTieneDiscapacidad():d.discapacidadHistorica();if(discapacidad==null)throw new IllegalArgumentException("Debe confirmarse si el alumno tiene discapacidad.");Alumno a;TutorService.CambioResponsableLegal responsable;
  if(d.accionAlumno()==FinalizarPreinscripcionRequest.AccionAlumno.VINCULAR_EXISTENTE){if(d.alumnoId()==null)throw new IllegalArgumentException("Debe seleccionarse una ficha existente.");gestionPorSede.exigirAlumnoConSedePrincipalPropia(d.alumnoId());/* D6-3 */a=alumnos.findById(d.alumnoId()).orElseThrow(()->new IllegalArgumentException("Alumno no encontrado."));if(!esCandidatoValido(a,p))throw new IllegalArgumentException("La ficha seleccionada no coincide con la identidad de la preinscripción.");if(!blank(p.getDni())){Optional<Alumno> porDni=alumnos.findByNif(p.getDni());if(porDni.isPresent()&&!porDni.get().getId().equals(a.getId()))throw new IllegalArgumentException("El DNI/NIE ya pertenece a otro alumno.");}validarYAplicarDecisionEmail(a,p,d.decisionEmail());responsable=aplicarCamposConfirmados(a,p,discapacidad,d.camposActualizarSeguros());}
  else if(d.accionAlumno()==FinalizarPreinscripcionRequest.AccionAlumno.CREAR_NUEVO){if(!blank(p.getDni())&&alumnos.findByNif(p.getDni()).isPresent())throw new IllegalArgumentException("Ya existe una ficha con el DNI/NIE de la preinscripción.");a=crearAlumnoDesdePreinscripcion(p,discapacidad,sedePrincipal);responsable=TutorService.CambioResponsableLegal.fijar(p.getTutorNombre(),p.getTutorDni());}
  else throw new IllegalArgumentException("Debe indicarse si se crea o se vincula la ficha del alumno.");
  a.setAutorizacionWeb(Boolean.TRUE.equals(p.getConsentimientoFotografico()));reactivarAlumno(a);alumnos.saveAndFlush(a);tutores.aplicarResponsableLegal(a,responsable);consentimientos.registrarDesdePreinscripcion(a,p);asegurarDeporte(a,p.getDeporte(),d.datosDeporte());
  sincronizarAsignaciones(a,p.getDeporte(),horarios,restriccion.map(AlcanceSesion::sedes).orElse(null));alumnos.save(a);
  p.setAlumno(a);p.setEstado(EstadoPreinscripcion.FINALIZADA);p.setFinalizadaEn(Instant.now());p.setEstadoEmailFinalizacion(EstadoEmailFinalizacion.PENDIENTE);p.setEmailFinalizacionUltimoError(null);eventos.publishEvent(new PreinscripcionFinalizadaEvent(p.getReferencia()));
 }
 /**
  * R2-P3: como {@link #cancelar}, primero los turnos en una sola sentencia ordenada (los actuales, los nuevos y, si libera
  * plaza, los de la lista de espera) y después la solicitud, bloqueada y releída (antes se bloqueaba primero la solicitud:
  * con una promoción que la subía a la vez, interbloqueo). Si cambió mientras esperaba, se vuelve a exigir su estado (409)
  * y se bloquea lo que falte. Lee con READ COMMITTED.
  */
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void actualizarTurnos(String ref,ActualizarTurnosPreinscripcionRequest d){
  Preinscripcion p=buscar(ref);exigirModificable(p);
  List<Turno> anteriores=turnosDe(p);Map<Long,Turno> bloqueados=new LinkedHashMap<>(bloquearConLaCola(turnosDelCambio(anteriores,d),liberaConElCambio(p,anteriores,d),p.getTemporada()));
  p=aforo.releerBloqueada(p);exigirModificable(p);
  anteriores=turnosDe(p);Set<Long> idsBloqueo=turnosDelCambio(anteriores,d);Set<Long> faltan=new java.util.TreeSet<>(idsBloqueo);if(liberaConElCambio(p,anteriores,d))faltan.addAll(aforo.turnosEnEspera(p.getTemporada()));faltan.removeAll(bloqueados.keySet());if(!faltan.isEmpty())bloqueados.putAll(aforo.bloquearTurnos(idsBloqueo,faltan));
  List<Turno> nuevos=d.turnoIds().stream().map(bloqueados::get).sorted(comparadorTurnos()).toList();validarSeleccionAdministrativa(p.getDeporte(),nuevos);validarMismaSede(nuevos);/* D6-7 también en el cambio de turnos, todos los roles */
  /* Entrega 6-2: el responsable de sede solo pasa la solicitud a turnos de sus sedes. */
  Optional<AlcanceSesion> restriccion=gestionPorSede.restriccion();if(restriccion.isPresent())exigirTurnosDeSusSedes(nuevos,restriccion.get().sedes());
  Set<Long> idsAnteriores=anteriores.stream().map(Turno::getId).collect(java.util.stream.Collectors.toSet());Set<Long> idsNuevos=nuevos.stream().map(Turno::getId).collect(java.util.stream.Collectors.toSet());if(idsAnteriores.equals(idsNuevos))return;
  boolean liberaPlaza=p.getEstado()==EstadoPreinscripcion.PENDIENTE;String snapshotAnterior=snapshotTurnos(p.getDeporte(),anteriores),snapshotNuevo=snapshotTurnos(p.getDeporte(),nuevos);
  p.setTurno(null);p.setGrupo(nuevos.get(0).getGrupo());p.setTurnos(new LinkedHashSet<>(nuevos));p.setEstado(EstadoPreinscripcion.PENDIENTE);p.setEstadoEmailCambioTurnos(EstadoEmailCambioTurnos.PENDIENTE);p.setEmailCambioTurnosEnviadoEn(null);p.setEmailCambioTurnosUltimoError(null);p.setTurnosModificadosEn(Instant.now());p.setEmailCambioTurnosAnteriorSnapshot(snapshotAnterior);p.setEmailCambioTurnosNuevoSnapshot(snapshotNuevo);repo.saveAndFlush(p);eventos.publishEvent(new PreinscripcionTurnosModificadosEvent(p.getReferencia()));if(liberaPlaza)promocionarDisponibles(p.getTemporada(),bloqueados);
 }
 private static void exigirFinalizable(Preinscripcion p){if(p.getEstado()!=EstadoPreinscripcion.PENDIENTE)throw new IllegalStateException("Solo puede finalizarse una preinscripción con plaza asignada.");}
 private static void exigirModificable(Preinscripcion p){if(p.getEstado()!=EstadoPreinscripcion.PENDIENTE&&p.getEstado()!=EstadoPreinscripcion.EN_LISTA_ESPERA)throw new IllegalStateException("Solo pueden modificarse los turnos de una solicitud pendiente o en lista de espera.");}
 /** Turnos que bloquea un cambio: los actuales de la solicitud y los nuevos. */
 private static Set<Long> turnosDelCambio(List<Turno> anteriores,ActualizarTurnosPreinscripcionRequest d){Set<Long> ids=new java.util.TreeSet<>();for(Turno t:anteriores)ids.add(t.getId());ids.addAll(d.turnoIds());return ids;}
 /** El cambio libera plaza (y promociona) si la solicitud está pendiente y sus turnos cambian. */
 private static boolean liberaConElCambio(Preinscripcion p,List<Turno> anteriores,ActualizarTurnosPreinscripcionRequest d){return p.getEstado()==EstadoPreinscripcion.PENDIENTE&&!anteriores.stream().map(Turno::getId).collect(java.util.stream.Collectors.toSet()).equals(new java.util.HashSet<>(d.turnoIds()));}
 private Alumno crearAlumnoDesdePreinscripcion(Preinscripcion p,Boolean discapacidad,Long sedePrincipal){Date hoy=new Date();Alumno a=new Alumno();a.setSedePrincipalId(sedePrincipal);a.setNombre(mayusculas(p.getNombre()));a.setApellidos(mayusculas(p.getApellidos()));a.setFechaNacimiento(java.sql.Date.valueOf(p.getFechaNacimiento()));a.setNif(p.getDni());a.setDireccion(p.getDireccion());a.setTelefono(normalizarTelefono(p.getTelefono()));a.setTelefono2(normalizarTelefonoOpcional(p.getTelefono2()));a.setEmail(p.getEmail());a.setNumeroExpediente(contadores.siguienteNumeroExpediente());a.setFechaAlta(hoy);a.setFechaAltaInicial(hoy);a.setActivo(true);a.setFechaBaja(null);a.setTieneDiscapacidad(discapacidad);return alumnos.saveAndFlush(a);}
 /** Aplica a la ficha los campos confirmados; devuelve el cambio del responsable legal (va al tutor, ver TutorService). */
 private TutorService.CambioResponsableLegal aplicarCamposConfirmados(Alumno a,Preinscripcion p,Boolean discapacidad,Set<FinalizarPreinscripcionRequest.CampoActualizable> campos){if(campos.contains(FinalizarPreinscripcionRequest.CampoActualizable.NOMBRE))a.setNombre(p.getNombre());if(campos.contains(FinalizarPreinscripcionRequest.CampoActualizable.APELLIDOS))a.setApellidos(p.getApellidos());if(campos.contains(FinalizarPreinscripcionRequest.CampoActualizable.FECHA_NACIMIENTO))a.setFechaNacimiento(java.sql.Date.valueOf(p.getFechaNacimiento()));if(campos.contains(FinalizarPreinscripcionRequest.CampoActualizable.DIRECCION))a.setDireccion(p.getDireccion());if(campos.contains(FinalizarPreinscripcionRequest.CampoActualizable.TELEFONO))a.setTelefono(normalizarTelefono(p.getTelefono()));if(campos.contains(FinalizarPreinscripcionRequest.CampoActualizable.TELEFONO2)&&!blank(p.getTelefono2()))a.setTelefono2(normalizarTelefonoOpcional(p.getTelefono2()));if(campos.contains(FinalizarPreinscripcionRequest.CampoActualizable.NIF)&&!blank(p.getDni())&&!esNifDeUnTutor(a,p.getDni()))a.setNif(p.getDni());if(campos.contains(FinalizarPreinscripcionRequest.CampoActualizable.TIENE_DISCAPACIDAD))a.setTieneDiscapacidad(discapacidad);String nombreResponsable=campos.contains(FinalizarPreinscripcionRequest.CampoActualizable.RESPONSABLE_LEGAL_NOMBRE)&&esMenor(p)?vacioSiNulo(p.getTutorNombre()):null;String nifResponsable=campos.contains(FinalizarPreinscripcionRequest.CampoActualizable.RESPONSABLE_LEGAL_NIF)&&esMenor(p)?vacioSiNulo(p.getTutorDni()):null;return new TutorService.CambioResponsableLegal(nombreResponsable,nifResponsable);}
 private static String vacioSiNulo(String valor){return valor==null?"":valor;}
 /** Entrega 6-2: el responsable de sede nunca cambia el email de una ficha con cuenta (mismo criterio que actualizarAlumno, 403). */
 private void validarYAplicarDecisionEmail(Alumno a,Preinscripcion p,FinalizarPreinscripcionRequest.DecisionEmail decision){String antes=normalizarEmail(a.getEmail());aplicarDecisionEmail(a,p,decision);if(a.getUsuario()!=null&&!antes.equals(normalizarEmail(a.getEmail()))&&gestionPorSede.restriccion().isPresent())throw new AccessDeniedException("Acceso denegado.");}
 private void aplicarDecisionEmail(Alumno a,Preinscripcion p,FinalizarPreinscripcionRequest.DecisionEmail decision){String ficha=normalizarEmail(a.getEmail()),solicitud=normalizarEmail(p.getEmail());if(ficha.equals(solicitud)){if(blank(a.getEmail()))a.setEmail(p.getEmail());return;}if(decision==null)throw new IllegalArgumentException("Los correos de la ficha y la preinscripción son diferentes. Debe elegirse expresamente cuál conservar.");if(decision==FinalizarPreinscripcionRequest.DecisionEmail.USAR_PREINSCRIPCION)a.setEmail(p.getEmail());if(blank(a.getEmail()))throw new IllegalArgumentException("La ficha debe conservar un correo electrónico para acceder al portal.");}
 private String normalizarEmail(String value){return blank(value)?"":value.trim().toLowerCase(Locale.ROOT);}
 private void reactivarAlumno(Alumno a){if(Boolean.TRUE.equals(a.getActivo()))return;Date hoy=new Date();a.setActivo(true);a.setFechaBaja(null);a.setFechaAlta(hoy);if(a.getFechaAltaInicial()==null)a.setFechaAltaInicial(hoy);}
 /** @param sedesPermitidas con responsable de sede, sus sedes: no quita grupos ni turnos de otras sedes (D6-16); {@code null}, todo. */
 private void sincronizarAsignaciones(Alumno a,String deporte,List<Turno> horarios,Set<Long> sedesPermitidas){
  Map<Long,Turno> turnosSeleccionados=new LinkedHashMap<>();Map<Long,Grupo> gruposSeleccionados=new LinkedHashMap<>();for(Turno horario:horarios){turnosSeleccionados.putIfAbsent(horario.getId(),horario);Grupo grupo=horario.getGrupo();gruposSeleccionados.putIfAbsent(grupo.getId(),grupo);}
  new ArrayList<>(a.getTurnos()).stream().filter(t->t.getGrupo()!=null&&java.util.Objects.equals(t.getGrupo().getDeporte(), deporte)).filter(t->!turnosSeleccionados.containsKey(t.getId())).filter(t->enSedes(t.getGrupo(),sedesPermitidas)).forEach(a::removeTurno);
  turnosSeleccionados.values().stream().filter(t->a.getTurnos().stream().noneMatch(actual->java.util.Objects.equals(actual.getId(),t.getId()))).forEach(a::addTurno);
  new ArrayList<>(a.getGrupos()).stream().filter(g->java.util.Objects.equals(g.getDeporte(), deporte)).filter(g->!gruposSeleccionados.containsKey(g.getId())).filter(g->enSedes(g,sedesPermitidas)).forEach(g->g.removeAlumno(a));
  gruposSeleccionados.values().stream().filter(g->a.getGrupos().stream().noneMatch(actual->java.util.Objects.equals(actual.getId(),g.getId()))).forEach(g->g.addAlumno(a));
 }
 private void asegurarDeporte(Alumno a,String deporte,FinalizarPreinscripcionRequest.DatosAltaDeporte datos){Optional<AlumnoDeporte> existente=alumnoDeportes.findByAlumnoIdAndDeporte(a.getId(),deporte);boolean nuevo=existente.isEmpty();validarDatosDeporte(deporte,datos,nuevo);Date hoy=new Date();AlumnoDeporte ad=existente.orElseGet(AlumnoDeporte::new);if(nuevo){ad.setAlumno(a);ad.setDisciplina(catalogo.obtenerPorCodigo(deporte));ad.setActivo(true);ad.setPrincipal(alumnoDeportes.countByAlumnoIdAndActivoTrue(a.getId())==0);ad.setFechaAlta(hoy);ad.setFechaAltaInicial(hoy);ad.setAptoParaExamen(false);ad.setCompetidor(false);ad.setTieneLicencia(false);}else if(!Boolean.TRUE.equals(ad.getActivo())){long activos=alumnoDeportes.countByAlumnoIdAndActivoTrue(a.getId());ad.setActivo(true);ad.setFechaBaja(null);ad.setFechaAlta(hoy);if(ad.getFechaAltaInicial()==null)ad.setFechaAltaInicial(hoy);if(activos==0)ad.setPrincipal(true);}tarifas.asignarTarifa(ad,datos.tipoTarifa());ad.setCuantiaTarifa(datos.cuantiaTarifa());ad.setRolFamiliar(datos.rolFamiliar());ad.setGrupoFamiliar(clean(datos.grupoFamiliar()));if(nuevo&&progresion.tieneProgresion(deporte)){var grado=(datos.grado()==null?Optional.<com.taemoi.project.entities.Grado>empty():progresion.buscarGrado(deporte,datos.grado())).orElseThrow(()->new IllegalArgumentException("Grado no encontrado."));ad.setGrado(grado);ad.setFechaGrado(java.sql.Date.valueOf(datos.fechaGrado()));}alumnoDeportes.save(ad);}
 private void validarDatosDeporte(String deporte,FinalizarPreinscripcionRequest.DatosAltaDeporte datos,boolean altaNueva){if(datos==null||datos.tipoTarifa()==null||datos.cuantiaTarifa()==null)throw new IllegalArgumentException("Tipo y cuantía de tarifa son obligatorios para el alta deportiva.");com.taemoi.project.entities.Tarifa tarifa=tarifas.buscarPorCodigo(datos.tipoTarifa()).orElse(null);/* Tarifa de la organización y admitida en la disciplina (tarifa.disciplina_id) */boolean tarifaValida=tarifa!=null&&tarifas.admiteDisciplina(tarifa.getCodigo(),deporte);if(!tarifaValida)throw new IllegalArgumentException("La tarifa no corresponde al deporte seleccionado.");if(tarifas.requiereRolFamiliar(tarifa)&&(datos.rolFamiliar()==null||datos.rolFamiliar()==RolFamiliar.NINGUNO))throw new IllegalArgumentException("La tarifa padres/hijos requiere rol familiar.");if(tarifas.requiereGrupoFamiliar(tarifa)&&blank(datos.grupoFamiliar()))throw new IllegalArgumentException("La tarifa hermanos requiere grupo familiar.");boolean requiereGrado=altaNueva&&progresion.tieneProgresion(deporte);if(requiereGrado&&(datos.grado()==null||datos.fechaGrado()==null))throw new IllegalArgumentException("Grado y fecha de grado son obligatorios para este deporte.");if(requiereGrado&&progresion.buscarGrado(deporte,datos.grado()).map(g->g.getAplicaA()==com.taemoi.project.entities.AplicaGrado.NINGUNO).orElse(true))throw new IllegalArgumentException("El grado no es válido para "+catalogo.obtenerPorCodigo(deporte).getNombre()+".");}
 private String normalizarTelefono(String telefono){String digitos=telefono==null?"":telefono.replaceAll("\\D","");if(digitos.length()==13&&digitos.startsWith("0034"))digitos=digitos.substring(4);else if(digitos.length()==11&&digitos.startsWith("34"))digitos=digitos.substring(2);if(!digitos.matches("[6789]\\d{8}"))throw new IllegalArgumentException("El teléfono debe ser un número español de nueve dígitos, con prefijo +34 opcional.");return digitos;}
 private String normalizarTelefonoOpcional(String telefono){return blank(telefono)?null:normalizarTelefono(telefono);}
 private String mayusculas(String valor){String limpio=clean(valor);return limpio==null?null:limpio.toUpperCase(Locale.ROOT);}
 public Map<String,Object> plantillas(String deporte){parametroRequerido(deporte);List<PlantillaPreinscripcion> lista=plantillas.findByDisciplinaCodigoOrderByVersionDesc(deporte);PlantillaPreinscripcion activa=lista.stream().filter(x->Boolean.TRUE.equals(x.getActiva())).findFirst().orElseThrow(()->new NoSuchElementException("No hay plantilla activa."));return Map.of("activa",plantillaDto(activa),"historial",lista.stream().map(this::plantillaDto).toList());}
 @Transactional public Map<String,Object> nuevaPlantilla(String deporte,PlantillaPreinscripcionRequest body){parametroRequerido(deporte);return plantillaDto(guardarPlantilla(deporte,body));}
 public byte[] preview(String deporte,PlantillaPreinscripcionRequest body){parametroRequerido(deporte);String contenido=contenido(body);return pdf.generarPreviewPreinscripcion(deporte,contenido,limpiar(body.instrucciones(),3000));}
 @Transactional public Map<String,Object> restaurar(String deporte,Integer version){parametroRequerido(deporte);PlantillaPreinscripcion origen=plantillas.findByDisciplinaCodigoAndVersion(deporte,version).orElseThrow(()->new NoSuchElementException("Versión no encontrada."));PlantillaPreinscripcionRequest body=leerPlantilla(origen);return plantillaDto(guardarPlantilla(deporte,body));}
 private PlantillaPreinscripcion guardarPlantilla(String deporte,PlantillaPreinscripcionRequest body){String contenido=contenido(body);List<PlantillaPreinscripcion> anteriores=plantillas.findByDisciplinaCodigoOrderByVersionDesc(deporte);anteriores.forEach(x->x.setActiva(false));PlantillaPreinscripcion p=new PlantillaPreinscripcion();p.setDisciplina(catalogo.obtenerPorCodigo(deporte));p.setVersion(anteriores.isEmpty()?1:anteriores.get(0).getVersion()+1);p.setActiva(true);p.setContenido(contenido);p.setInstrucciones(limpiar(body.instrucciones(),3000));return plantillas.save(p);}
 private String contenido(PlantillaPreinscripcionRequest b){try{return json.writeValueAsString(Map.of("cabecera",limpiar(b.cabecera(),120),"contacto",limpiar(b.contacto(),180),"titulo",limpiar(b.titulo(),180),"consentimiento",limpiar(b.consentimiento(),1500),"normas",b.normas().stream().map(x->limpiar(x,2000)).toList(),"importes",limpiar(b.importes(),3000)));}catch(Exception e){throw new IllegalArgumentException("Contenido de plantilla inválido.");}}
 private PlantillaPreinscripcionRequest leerPlantilla(PlantillaPreinscripcion p){try{var n=json.readTree(p.getContenido());List<String> normas=new java.util.ArrayList<>();n.path("normas").forEach(x->normas.add(x.asText()));return new PlantillaPreinscripcionRequest(n.path("cabecera").asText(),n.path("contacto").asText(),n.path("titulo").asText(),n.path("consentimiento").asText(),normas,n.path("importes").asText(),p.getInstrucciones());}catch(Exception e){throw new IllegalStateException("Plantilla histórica inválida.");}}
 private Map<String,Object> plantillaDto(PlantillaPreinscripcion p){Map<String,Object> m=new LinkedHashMap<>();m.put("id",p.getId());m.put("deporte",p.getDeporte());m.put("version",p.getVersion());m.put("activa",p.getActiva());m.put("creadaEn",p.getCreadaEn());m.put("contenido",parse(p.getContenido()));m.put("instrucciones",p.getInstrucciones());return m;}
 private String limpiar(String value,int max){String v=clean(value);if(v==null||v.isBlank())throw new IllegalArgumentException("Todos los campos y al menos una norma son obligatorios.");if(v.length()>max)throw new IllegalArgumentException("Contenido demasiado largo.");return v;}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void promocionarGrupo(Long grupoId,String temporada){promocionarDisponibles(temporada,aforo.bloquearTurnos(Set.of(),aforo.turnosEnEspera(temporada)));}
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void promocionarTodas(){String temporada=temporadas.actual();/* R2-P3: los turnos de la cola, bloqueados ANTES de leerla */promocionarDisponibles(temporada,aforo.bloquearTurnos(Set.of(),aforo.turnosEnEspera(temporada)));}
 /**
  * Promociona la lista de espera de la temporada. Regla (la de siempre): en orden {@code creadaEn, id}, una solicitud
  * pasa a PENDIENTE si ALGUNO de sus turnos tiene plaza; desde ese momento cuenta como pendiente en todos sus turnos de
  * {@code preinscripcion_turno} (las antiguas solo con {@code turno_id}/{@code grupo_id} no cuentan) y las siguientes lo ven.
  *
  * <p>R2-P3, la cola de una vez: los turnos de todas las solicitudes se bloquean en una sola sentencia por orden de id, la
  * ocupación se calcula una vez tras el bloqueo y se actualiza en memoria al promocionar (igual que la contaría una nueva
  * consulta tras guardar). Una solicitud sin turnos, o con un turno inexistente o sin grupo, hace fallar la promoción
  * cuando le llega su turno, después de las anteriores (como antes). Quien llama lee con READ COMMITTED y ya tiene
  * bloqueados los turnos de la cola ({@code yaBloqueados}): la cola se lee DESPUÉS del bloqueo, así que sus solicitudes y
  * la ocupación son las confirmadas por la operación que tenía los turnos (con REPEATABLE READ, o leyendo la cola antes de
  * bloquear, se trabajaba con solicitudes obsoletas: promociones dobles o perdidas). Si al leerla aparece una solicitud con
  * turnos aún sin bloquear (entró mientras tanto), se bloquean y se comprueba su estado confirmado antes de tocarla.
  */
 private void promocionarDisponibles(String temporada,Map<Long,Turno> yaBloqueados){
  List<Preinscripcion> cola=aforo.cola(temporada);if(cola.isEmpty())return;
  List<List<Turno>> selecciones=new ArrayList<>();List<RuntimeException> errores=new ArrayList<>();Set<Long> ids=new java.util.TreeSet<>();
  for(Preinscripcion p:cola){try{List<Turno> seleccion=turnosDe(p);seleccion.forEach(t->ids.add(t.getId()));selecciones.add(seleccion);errores.add(null);}catch(RuntimeException e){selecciones.add(null);errores.add(e);}}
  Map<Long,Turno> bloqueados=new LinkedHashMap<>(yaBloqueados);Set<Long> sinBloquear=new java.util.TreeSet<>(ids);sinBloquear.removeAll(bloqueados.keySet());
  if(!sinBloquear.isEmpty())bloqueados.putAll(aforo.bloquearTurnos(Set.of(),ids));
  AforoPreinscripcionService.Ocupacion ocupacion=aforo.ocupacion(bloqueados.values(),temporada);
  for(int i=0;i<cola.size();i++){
   if(errores.get(i)!=null)throw errores.get(i);
   List<Long> idsSeleccion=selecciones.get(i).stream().map(Turno::getId).distinct().toList();
   if(!bloqueados.keySet().containsAll(idsSeleccion))throw new IllegalArgumentException("Alguno de los turnos seleccionados no existe.");
   if(idsSeleccion.stream().anyMatch(sinBloquear::contains)&&aforo.estadoConfirmado(cola.get(i).getId())!=EstadoPreinscripcion.EN_LISTA_ESPERA)continue;
   if(!ocupacion.algunaPlaza(idsSeleccion.stream().map(bloqueados::get).toList()))continue;
   Preinscripcion p=cola.get(i);p.setEstado(EstadoPreinscripcion.PENDIENTE);p.setPromocionadaEn(Instant.now());repo.saveAndFlush(p);ocupacion.sumarPendiente(p.getTurnos());eventos.publishEvent(eventoPromocion(p));
  }
 }
 /**
  * R2-P3: bloquea los turnos de la operación y, si va a promocionar, también los de la lista de espera, en una sola
  * sentencia por orden de id (como un alta pública), para no cruzar bloqueos con otra operación sobre turnos solapados.
  * Falla como siempre si falta alguno de los de la operación.
  */
 private Map<Long,Turno> bloquearConLaCola(Collection<Long> turnoIds,boolean promocionara,String temporada){
  Set<Long> enEspera=promocionara?aforo.turnosEnEspera(temporada):Set.of();
  if(enEspera.isEmpty())return aforo.bloquearTurnos(turnoIds).stream().collect(java.util.stream.Collectors.toMap(Turno::getId,t->t,(a,b)->a,LinkedHashMap::new));
  return aforo.bloquearTurnos(turnoIds,enEspera);
 }
 /** R2-P7d: asunto y cuerpos del alta (los de siempre), calculados en su transacción; el aviso interno, solo si hay buzón. */
 private PreinscripcionRecibidaEvent eventoRecibida(Preinscripcion p){String admin=buzonInterno();return new PreinscripcionRecibidaEvent(p.getReferencia(),asuntoCliente(p),htmlConfirmacion(p),admin,admin==null?null:"Nueva preinscripción recibida: "+p.getReferencia(),admin==null?null:htmlAdmin(p));}
 private String buzonInterno(){return adminEmail==null||adminEmail.isBlank()?null:adminEmail.trim();}
 /** R2-P7c: el PDF se lee antes de tocar la solicitud (leerlo después forzaría un UPDATE adicional por auto-flush). */
 private void reenviarSolicitudOriginal(Preinscripcion p){byte[] pdfOriginal=pdfFirmado(p);p.setEmailIntentos(p.getEmailIntentos()+1);try{email.sendEmailConAdjunto(p.getEmail(),"Copia de la solicitud original · "+p.getReferencia(),htmlSolicitudOriginal(p),"solicitud-original-"+p.getReferencia()+".pdf",pdfOriginal);p.setEmailEnviado(true);p.setEmailUltimoError(null);}catch(Exception e){p.setEmailEnviado(false);p.setEmailUltimoError(clean(e.getMessage()));}}
 private String asuntoCliente(Preinscripcion p){return p.getEstado()==EstadoPreinscripcion.EN_LISTA_ESPERA?"Lista de espera · "+p.getReferencia():"Plaza asignada · "+p.getReferencia();}
 private String htmlConfirmacion(Preinscripcion p){boolean espera=p.getEstado()==EstadoPreinscripcion.EN_LISTA_ESPERA;String estado=espera?"<p style='padding:12px;border-left:4px solid #b7791f;background:#fff8e6'><strong>Lista de espera.</strong> Tu posición actual es <strong>"+aforo.posicion(p)+"</strong>. Te avisaremos automáticamente cuando uno de los horarios tenga plaza.</p>":"<p style='padding:12px;border-left:4px solid #237a57;background:#edf8f3'><strong>Plaza asignada.</strong> Esta preinscripción ya cuenta en el aforo de todos los turnos elegidos.</p>";return "<div style='font-family:Arial,sans-serif;color:#172126;line-height:1.55;max-width:640px'><h2>Hemos recibido tu preinscripción</h2><p>Hola, "+esc(p.getNombre())+".</p><p>Solicitud para <strong>"+esc(nombreDeporte(p.getDeporte()))+"</strong>, temporada <strong>"+esc(p.getTemporada())+"</strong>, referencia <strong>"+esc(p.getReferencia())+"</strong>.</p>"+estado+"<p><strong>Horarios:</strong> "+esc(p.getGrupoSnapshot())+"</p>"+bloqueObservaciones(p)+"<h3>Próximos pasos</h3><p>"+esc(p.getPlantilla().getInstrucciones())+"</p><p>Conserva este correo y la referencia.</p><p>Un saludo,<br/><strong>Moi's Kim Do</strong></p></div>";}
 private String htmlSolicitudOriginal(Preinscripcion p){return "<div style='font-family:Arial,sans-serif;color:#172126;line-height:1.55;max-width:640px'><h2>Copia de tu solicitud original</h2><p>Hola, "+esc(p.getNombre())+".</p><p>Te reenviamos la solicitud original firmada para <strong>"+esc(nombreDeporte(p.getDeporte()))+"</strong>, temporada <strong>"+esc(p.getTemporada())+"</strong>, referencia <strong>"+esc(p.getReferencia())+"</strong>.</p><p><strong>Horario solicitado originalmente:</strong> "+esc(grupoOriginal(p))+"</p>"+bloqueObservaciones(p)+"<p>Este correo y el PDF adjunto reflejan los datos presentados originalmente y no informan sobre el estado o la asignación vigente.</p><p>Un saludo,<br/><strong>Moi's Kim Do</strong></p></div>";}
 private String htmlAdmin(Preinscripcion p){String url=frontendBaseUrl.replaceAll("/+$","")+"/preinscripciones";String estado=p.getEstado()==EstadoPreinscripcion.EN_LISTA_ESPERA?"Lista de espera · posición "+aforo.posicion(p):"Plaza asignada";String telefonos=p.getTelefono()+(blank(p.getTelefono2())?"":" · "+p.getTelefono2());return "<h2>Nueva preinscripción recibida</h2><p><strong>Referencia:</strong> "+esc(p.getReferencia())+"</p><table cellpadding='6' cellspacing='0' border='1' style='border-collapse:collapse'><tr><td><strong>Alumno/a</strong></td><td>"+esc(p.getNombre()+" "+p.getApellidos())+"</td></tr><tr><td><strong>DNI/NIE</strong></td><td>"+esc(blank(p.getDni())?"No facilitado":p.getDni())+"</td></tr><tr><td><strong>Deporte / grupo</strong></td><td>"+esc(p.getGrupoSnapshot())+"</td></tr><tr><td><strong>Estado</strong></td><td>"+esc(estado)+"</td></tr><tr><td><strong>Teléfonos</strong></td><td>"+esc(telefonos)+"</td></tr><tr><td><strong>Discapacidad</strong></td><td>"+(Boolean.TRUE.equals(p.getTieneDiscapacidad())?"Sí":"No")+"</td></tr><tr><td><strong>Email</strong></td><td>"+esc(p.getEmail())+"</td></tr><tr><td><strong>Temporada</strong></td><td>"+esc(p.getTemporada())+"</td></tr><tr><td><strong>Observaciones</strong></td><td>"+esc(blank(p.getObservaciones())?"Sin observaciones":p.getObservaciones())+"</td></tr></table><p>Revisar en el panel: <a href='"+esc(url)+"'>"+esc(url)+"</a></p>";}
 private String bloqueObservaciones(Preinscripcion p){return blank(p.getObservaciones())?"":"<h3>Tu observación</h3><p style='padding:12px;background:#f4f6f6;border-left:4px solid #547078;white-space:pre-wrap'>"+esc(p.getObservaciones())+"</p><p>Nuestro equipo la revisará y se pondrá en contacto contigo si es necesario.</p>";}
 /** R2-P7d: avisos de plaza (los de siempre), preparados en la transacción de la promoción y enviados tras el commit (PreinscripcionPromocionEmailListener). */
 private PreinscripcionPromocionadaEvent eventoPromocion(Preinscripcion p){String html="<h2>Tu preinscripción ya tiene plaza</h2><p>Se ha liberado una plaza para <strong>"+esc(nombreDeporte(p.getDeporte()))+"</strong>. Tu preinscripción "+esc(p.getReferencia())+" ya cuenta en el aforo.</p><p>"+esc(p.getPlantilla().getInstrucciones())+"</p>";String admin=buzonInterno();return new PreinscripcionPromocionadaEvent(p.getReferencia(),p.getEmail(),"Ya tienes plaza · "+p.getReferencia(),html,admin,admin==null?null:"Lista de espera promocionada: "+p.getReferencia(),admin==null?null:"<h2>Plaza asignada automáticamente</h2><p>"+esc(p.getNombre()+" "+p.getApellidos())+" · "+esc(nombreDeporte(p.getDeporte()))+" · "+esc(p.getReferencia())+".</p>");}
 private byte[] decodificarFirma(String value){try{String b=value.substring(value.indexOf(',')+1);byte[] data=java.util.Base64.getDecoder().decode(b);if(data.length<100||data.length>500000)throw new IllegalArgumentException();BufferedImage original=ImageIO.read(new ByteArrayInputStream(data));if(original==null||original.getWidth()>2400||original.getHeight()>1200)throw new IllegalArgumentException();BufferedImage limpia=new BufferedImage(original.getWidth(),original.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=limpia.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,limpia.getWidth(),limpia.getHeight());g.drawImage(original,0,0,null);g.dispose();ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(limpia,"png",out);return out.toByteArray();}catch(Exception e){throw new IllegalArgumentException("La firma no es una imagen válida o excede 500 KB.");}}
 private Map<String,Object> turnoPublico(Turno t,AforoPreinscripcionService.Ocupacion ocupacion){return Map.of("id",t.getId(),"diaSemana",t.getDiaSemana(),"horaInicio",t.getHoraInicio(),"horaFin",t.getHoraFin(),"completo",ocupacion.completo(t));}
 /* Código de disciplina de la ruta (antes enum Deporte convertido por Spring): 400 si la escuela no lo tiene. */
 private String parametroRequerido(String deporte){String codigo=catalogo.validarParametro(deporte);if(codigo==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);return codigo;}
 private boolean visibleEnPreinscripcion(Grupo g){return !CatalogoLegadoTaemoi.TAEKWONDO.equals(g.getDeporte())||!normalizar(g.getNombre()+" "+g.getTipo()).contains("competicion");}
 private String nombrePublico(Grupo g){return nombreDeporte(g.getDeporte());}
 /* Nombre público de la disciplina (en Taemoi: «Taekwondo», «Defensa Personal Femenina» desde V30…). */
 private String nombreDeporte(String deporte){return catalogo.obtenerPorCodigo(deporte).getNombre();}
 private String normalizar(String s){return s==null?"":java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
 private String normalizarIdentidad(String s){return normalizar(clean(s)).replaceAll("[^a-z0-9]","");}
 private String identidadCanonica(String dni,String tutorDni,String nombre,String apellidos,LocalDate nacimiento){if(!blank(dni))return "DNI|"+dni.trim().toUpperCase(Locale.ROOT);return "MENOR|"+(blank(tutorDni)?"":tutorDni.trim().toUpperCase(Locale.ROOT))+"|"+normalizarIdentidad(nombre)+"|"+normalizarIdentidad(apellidos)+"|"+nacimiento;}
 private boolean coincideIdentidadPersonal(Alumno a,Preinscripcion p){if(a.getFechaNacimiento()==null)return false;LocalDate nacimiento=new java.sql.Date(a.getFechaNacimiento().getTime()).toLocalDate();return nacimiento.equals(p.getFechaNacimiento())&&normalizarIdentidad(a.getNombre()).equals(normalizarIdentidad(p.getNombre()))&&normalizarIdentidad(a.getApellidos()).equals(normalizarIdentidad(p.getApellidos()));}
 /** El DNI de un tutor del alumno (p. ej. el de un padre movido por la corrección de DNIs compartidos) no vuelve a su ficha. */
 private static boolean esNifDeUnTutor(Alumno a,String dni){String nif=com.taemoi.project.utils.NifUtils.normalizeForComparison(dni);if(nif==null||a.getTutores()==null)return false;return a.getTutores().stream().filter(com.taemoi.project.entities.Tutor::tieneNombre).anyMatch(t->nif.equals(com.taemoi.project.utils.NifUtils.normalizeForComparison(t.getNif())));}
 private boolean esCandidatoValido(Alumno a,Preinscripcion p){if(!blank(p.getDni())&&!blank(a.getNif())&&p.getDni().equalsIgnoreCase(a.getNif()))return true;if(!blank(p.getDni())&&!blank(a.getNif()))return false;return coincideIdentidadPersonal(a,p);}
 private boolean esMenor(Preinscripcion p){return Period.between(p.getFechaNacimiento(),LocalDate.now()).getYears()<18;}
 private List<Turno> turnosDe(Preinscripcion p){if(p.getTurnos()!=null&&!p.getTurnos().isEmpty())return p.getTurnos().stream().sorted(comparadorTurnos()).toList();if(p.getTurno()!=null)return List.of(p.getTurno());if(p.getGrupo()!=null){List<Turno> legacy=turnos.findByGrupo(p.getGrupo()).stream().sorted(comparadorTurnos()).toList();if(!legacy.isEmpty())return legacy;}throw new IllegalStateException("La solicitud no tiene turnos asociados.");}
 void validarSeleccion(String deporte,LocalDate nacimiento,String temporada,List<Turno> seleccion){validarSeleccionAdministrativa(deporte,seleccion);validarMismaSede(seleccion);int edad=temporadas.edadCohorte(nacimiento,temporada);for(Turno t:seleccion){Grupo g=t.getGrupo();int minima=Optional.ofNullable(g.getRangoEdadMin()).orElse(0),maxima=Optional.ofNullable(g.getRangoEdadMax()).orElse(99);if(edad<minima||edad>maxima)throw new IllegalArgumentException("Uno de los turnos no es compatible con la edad de cohorte para la temporada indicada.");}}
 private void validarSeleccionAdministrativa(String deporte,List<Turno> seleccion){if(seleccion==null||seleccion.isEmpty())throw new IllegalArgumentException("Es necesario seleccionar al menos un turno.");Set<String> dias=new java.util.HashSet<>();for(Turno t:seleccion){Grupo g=t.getGrupo();if(g==null||!java.util.Objects.equals(g.getDeporte(), deporte))throw new IllegalArgumentException("Todos los turnos deben pertenecer al deporte seleccionado.");if(!visibleEnPreinscripcion(g))throw new IllegalArgumentException("Uno de los turnos no está disponible para preinscripción.");if(!dias.add(normalizar(t.getDiaSemana())))throw new IllegalArgumentException("Solo se puede seleccionar un turno por día.");}}
 /** D6-7 (alta pública y cambio de turnos): todos los turnos de la misma sede. Solo cuentan los grupos con sede: con una sola sede (o grupos sin sede) no cambia nada. */
 private void validarMismaSede(List<Turno> seleccion){if(seleccion.stream().map(t->t.getGrupo().getSedeId()).filter(java.util.Objects::nonNull).distinct().count()>1)throw new IllegalArgumentException("Todos los turnos deben ser de la misma sede.");}
 /** Entrega 6-2: para el responsable de sede, el grupo de cada turno es de una de sus sedes (si no, 403). */
 private static void exigirTurnosDeSusSedes(List<Turno> seleccion,Set<Long> sedes){for(Turno t:seleccion)if(!enSedes(t.getGrupo(),sedes))throw new AccessDeniedException("Acceso denegado.");}
 private static boolean enSedes(Grupo g,Set<Long> sedes){return sedes==null||(g!=null&&g.getSedeId()!=null&&sedes.contains(g.getSedeId()));}
 /** Sede del grupo de la solicitud (el que decide quién la ve); sin grupo, la del primer turno. */
 private static Long sedeDeLaSolicitud(Preinscripcion p,List<Turno> horarios){Grupo g=p.getGrupo()!=null?p.getGrupo():horarios.get(0).getGrupo();return g==null?null:g.getSedeId();}
 private String snapshotTurnos(String deporte,List<Turno> horarios){return snapshotTurnos(horarios,nombreDeporte(deporte));}
 private String snapshotTurnos(List<Turno> horarios,String nombreDeporte){String hs=horarios.stream().sorted(comparadorTurnos()).map(t->t.getDiaSemana()+" "+t.getHoraInicio()+"-"+t.getHoraFin()+" ("+t.getGrupo().getNombre()+")").reduce((a,b)->a+"; "+b).orElse("");return nombreDeporte+" · "+hs;}
 /** @param horarios turnos de cada grupo (R2-P3: los ya leídos, en vez de una consulta por comparación). */
 private Comparator<Grupo> comparadorGrupos(java.util.function.Function<Grupo,List<Turno>> horarios){return Comparator.comparingInt((Grupo g)->Optional.ofNullable(g.getRangoEdadMin()).orElse(0)).thenComparingInt(g->Optional.ofNullable(g.getRangoEdadMax()).orElse(99)).thenComparingInt((Grupo g)->primerDiaGrupo(horarios.apply(g))).thenComparing((Grupo g)->primeraHoraGrupo(horarios.apply(g))).thenComparing(Grupo::getId);}
 private int primerDiaGrupo(List<Turno> horarios){return horarios.stream().mapToInt(t->ordenDia(t.getDiaSemana())).min().orElse(99);}
 private String primeraHoraGrupo(List<Turno> horarios){return horarios.stream().map(Turno::getHoraInicio).filter(h->h!=null&&!h.isBlank()).min(String::compareTo).orElse("99:99");}
 private Comparator<Turno> comparadorTurnos(){return Comparator.comparingInt((Turno t)->ordenDia(t.getDiaSemana())).thenComparing(Turno::getHoraInicio);}
 private int ordenDia(String dia){return switch(normalizar(dia)){case "lunes"->1;case "martes"->2;case "miercoles"->3;case "jueves"->4;case "viernes"->5;case "sabado"->6;case "domingo"->7;default->99;};}
 private String referencia(){return "PRE-"+LocalDate.now().getYear()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();} private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private String hashIdempotencia(String value){if(blank(value))return null;String normalizada=value.trim();if(normalizada.length()>128||!normalizada.matches("[A-Za-z0-9._:-]+"))throw new IllegalArgumentException("La clave de idempotencia no tiene un formato válido.");return hash(normalizada);}
 private PreinscripcionDuplicadaException duplicada(){return new PreinscripcionDuplicadaException(MENSAJE_DUPLICADA);} private PreinscripcionDuplicadaException duplicada(Throwable cause){PreinscripcionDuplicadaException error=duplicada();error.initCause(cause);return error;}
 private boolean esConflictoDuplicado(DataIntegrityViolationException error){Throwable causa=error;while(causa!=null){String mensaje=causa.getMessage();if(mensaje!=null){String normalizado=mensaje.toLowerCase(Locale.ROOT);if(normalizado.contains("uk_preinscripcion_activa")||normalizado.contains("uk_preinscripcion_idempotencia"))return true;}causa=causa.getCause();}return false;}
 private String clean(String s){return s==null?null:s.replaceAll("[<>]","").trim();} private String limpiarObservacion(String s){return blank(s)?null:s.trim();} private boolean blank(String s){return s==null||s.isBlank();} private Object parse(String s){try{return json.readTree(s);}catch(Exception e){return s;}} private void jsonValido(String s){try{json.readTree(s);}catch(Exception e){throw new IllegalArgumentException("El contenido debe ser JSON válido.");}}
 private String esc(String value){return value==null?"":value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
