import { CommonModule } from '@angular/common';
import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import Swal, { SweetAlertOptions } from 'sweetalert2';
import { MOTIVO_CANCELACION_MAX, MOTIVO_CANCELACION_POR_DEFECTO, PreinscripcionService } from '../../../features/preinscripcion/preinscripcion.service';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';
import { FechaApiPipe, diaApi } from '../../../utils/fecha-api';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { ActivatedRoute, Router } from '@angular/router';
import { CatalogoService } from '../../../features/catalogo/catalogo.service';
import { CatalogoTarifa } from '../../../features/catalogo/catalogo.models';
import { cuantiaEstandar } from '../../../features/catalogo/catalogo-reglas';
import { tarifaInicialPreinscripcion } from '../../../features/catalogo/legado-taemoi';

@Component({selector:'app-preinscripciones-admin',standalone:true,imports:[CommonModule,FormsModule,PaginacionComponent,SkeletonCardComponent,FechaApiPipe],templateUrl:'./preinscripciones-admin.component.html',styleUrl:'./preinscripciones-admin.component.scss'})
export class PreinscripcionesAdminComponent implements OnInit, OnDestroy {
  readonly catalogo=inject(CatalogoService);
  items:any[]=[]; cargando=true; filtros={temporada:'',deporte:'',estado:'',q:''}; seleccion:any; firmaUrl?:string;
  /** R2-P7a: las fichas compatibles (`alumnosCoincidentes`) llegan con el detalle, que se pide al abrir una fila PENDIENTE (la única que muestra la sección de finalizar). */
  cargandoCoincidencias=false; errorCoincidencias=false; private detalleSub?:Subscription;
  mostrandoFinalizacion=false; procesandoFinalizacion=false; camposActualizar=new Set<string>();
  editandoTurnos=false; cargandoTurnos=false; procesandoTurnos=false; turnosDisponibles:any[]=[]; turnoIdsEdicion:number[]=[];
  accionAlumno:''|'CREAR_NUEVO'|'VINCULAR_EXISTENTE'=''; alumnoSeleccionado:any; discapacidadHistorica:boolean|null=null;
  decisionEmail:''|'CONSERVAR_FICHA'|'USAR_PREINSCRIPCION'='';
  datosAlta:any={tipoTarifa:'',cuantiaTarifa:null,rolFamiliar:'NINGUNO',grupoFamiliar:'',grado:'',fechaGrado:''};
  readonly camposComparables=[
    {clave:'NOMBRE',etiqueta:'Nombre',pre:'nombre',alumno:'nombre'},
    {clave:'APELLIDOS',etiqueta:'Apellidos',pre:'apellidos',alumno:'apellidos'},
    {clave:'FECHA_NACIMIENTO',etiqueta:'Fecha de nacimiento',pre:'fechaNacimiento',alumno:'fechaNacimiento'},
    {clave:'DIRECCION',etiqueta:'Dirección',pre:'direccion',alumno:'direccion'},
    {clave:'TELEFONO',etiqueta:'Teléfono',pre:'telefono',alumno:'telefono'},
    {clave:'TELEFONO2',etiqueta:'Teléfono secundario',pre:'telefono2',alumno:'telefono2'},
    {clave:'NIF',etiqueta:'DNI/NIE',pre:'dni',alumno:'nif'},
    {clave:'TIENE_DISCAPACIDAD',etiqueta:'Discapacidad',pre:'tieneDiscapacidad',alumno:'tieneDiscapacidad'},
    {clave:'RESPONSABLE_LEGAL_NOMBRE',etiqueta:'Responsable legal',pre:'tutorNombre',alumno:'responsableLegalNombre',soloMenor:true},
    {clave:'RESPONSABLE_LEGAL_NIF',etiqueta:'DNI/NIE del responsable',pre:'tutorDni',alumno:'responsableLegalNif',soloMenor:true},
    {clave:'EMAIL',etiqueta:'Correo electrónico',pre:'email',alumno:'email'},
  ];
  pagina=0; readonly tamanoPagina=25; totalElementos=0; totalPaginas=0;
  tab:'solicitudes'|'plantillas'='solicitudes'; deportePlantilla=this.catalogo.primeraDisciplinaActiva(); plantilla:any=this.vacia(); historial:any[]=[]; cargandoPlantilla=false; procesandoPlantilla=false; dirty=false;
  get deportes():string[]{return this.catalogo.disciplinasActivas().map(d=>d.codigo);}
  constructor(private readonly api:PreinscripcionService,public readonly auth:AuthenticationService,private readonly route:ActivatedRoute,private readonly router:Router){}
  ngOnInit(){this.tab=this.route.snapshot.queryParamMap.get('tab')==='plantillas'?'plantillas':'solicitudes';this.buscar();if(this.tab==='plantillas')this.cargarPlantilla();}
  ngOnDestroy(){this.liberarFirma();this.detalleSub?.unsubscribe();}

  buscar(reiniciarPagina=false){if(reiniciarPagina)this.pagina=0;this.cargando=true;this.api.listar(this.filtros,this.pagina,this.tamanoPagina).subscribe({next:r=>{if(!r.content.length&&this.pagina>0&&r.totalElements>0){this.pagina--;this.buscar();return;}this.items=r.content;this.pagina=r.page;this.totalElementos=r.totalElements;this.totalPaginas=r.totalPages;this.cargando=false;},error:()=>{this.cargando=false;showErrorToast('No se pudieron cargar las preinscripciones.');}});}
  cambiarPagina(numeroPagina:number){const destino=numeroPagina-1;if(this.cargando||destino<0||destino>=this.totalPaginas)return;this.pagina=destino;this.cerrarDetalle();this.buscar();}
  get primerResultado(){return this.totalElementos?this.pagina*this.tamanoPagina+1:0;}
  get ultimoResultado(){return Math.min((this.pagina+1)*this.tamanoPagina,this.totalElementos);}
  get puedeEliminar(){return this.seleccion?.estado==='CANCELADA'&&this.auth.tieneRolAdmin();}
  get puedeEditarTurnos(){return ['PENDIENTE','EN_LISTA_ESPERA'].includes(this.seleccion?.estado);}
  get puedeReenviarCambioTurnos(){return this.seleccion?.estado==='PENDIENTE'&&Boolean(this.seleccion?.turnosModificadosEn);}
  get turnosEdicionCambiados(){const actuales=(this.seleccion?.turnosSolicitados||[]).map((t:any)=>t.id).sort((a:number,b:number)=>a-b),nuevos=[...this.turnoIdsEdicion].sort((a,b)=>a-b);return actuales.length!==nuevos.length||actuales.some((id:number,i:number)=>id!==nuevos[i]);}

  seleccionar(item:any){if(this.procesandoTurnos||this.procesandoFinalizacion)return;this.cerrarFinalizacion();this.cerrarEdicionTurnos();this.liberarFirma();this.seleccion=item;if(item.estado==='PENDIENTE')this.cargarCoincidencias(item.referencia);else this.descartarCoincidencias();this.api.firma(item.referencia).subscribe({next:firma=>this.firmaUrl=URL.createObjectURL(firma),error:()=>showErrorToast('No se pudo cargar la firma.')});}
  cerrarDetalle(){this.cerrarFinalizacion();this.cerrarEdicionTurnos();this.liberarFirma();this.descartarCoincidencias();this.seleccion=undefined;}
  /** Sin petición de detalle en curso ni estado de carga/error (filas que no muestran la sección de finalizar). */
  private descartarCoincidencias(){this.detalleSub?.unsubscribe();this.cargandoCoincidencias=false;this.errorCoincidencias=false;}
  /** Las fichas compatibles del detalle sustituyen a las de la fila (que ya no las trae); el resto de la fila no cambia. */
  private cargarCoincidencias(referencia:string){this.detalleSub?.unsubscribe();this.cargandoCoincidencias=true;this.errorCoincidencias=false;this.detalleSub=this.api.detalle(referencia).subscribe({next:detalle=>{if(this.seleccion?.referencia===referencia)this.seleccion={...this.seleccion,alumnosCoincidentes:detalle?.alumnosCoincidentes||[]};this.cargandoCoincidencias=false;},error:()=>{this.cargandoCoincidencias=false;this.errorCoincidencias=true;showErrorToast('No se pudieron cargar las fichas compatibles.');}});}
  descargarDocumento(){if(!this.seleccion)return;this.api.documentoFirmado(this.seleccion.referencia).subscribe({next:documento=>{const url=URL.createObjectURL(documento);const enlace=document.createElement('a');enlace.href=url;enlace.download=`solicitud-original-${this.seleccion.referencia}.pdf`;enlace.click();URL.revokeObjectURL(url);},error:()=>showErrorToast('No se pudo descargar la solicitud original firmada.')});}
  private liberarFirma(){if(this.firmaUrl)URL.revokeObjectURL(this.firmaUrl);this.firmaUrl=undefined;}

  abrirEdicionTurnos(){if(!this.puedeEditarTurnos||this.cargandoTurnos)return;this.editandoTurnos=true;this.cargandoTurnos=true;this.turnoIdsEdicion=(this.seleccion.turnosSolicitados||[]).map((t:any)=>t.id);this.api.turnos(this.seleccion.deporte).subscribe({next:(turnos:any[])=>{this.turnosDisponibles=[...turnos].sort((a,b)=>this.ordenDia(a.diaSemana)-this.ordenDia(b.diaSemana)||(a.horaInicio||'').localeCompare(b.horaInicio||''));this.cargandoTurnos=false;},error:()=>{this.cargandoTurnos=false;this.editandoTurnos=false;showErrorToast('No se pudieron cargar los turnos disponibles.');}});}
  cerrarEdicionTurnos(){if(this.procesandoTurnos)return;this.editandoTurnos=false;this.cargandoTurnos=false;this.turnosDisponibles=[];this.turnoIdsEdicion=[];}
  turnoAdminSeleccionado(id:number){return this.turnoIdsEdicion.includes(id);}
  alternarTurnoAdmin(turno:any,marcado:boolean){let ids=this.turnoIdsEdicion.filter(id=>id!==turno.id);if(marcado){const dia=this.normalizarDia(turno.diaSemana);const mismoDia=new Set(this.turnosDisponibles.filter(t=>this.normalizarDia(t.diaSemana)===dia).map(t=>t.id));ids=ids.filter(id=>!mismoDia.has(id));ids.push(turno.id);}this.turnoIdsEdicion=ids;}
  turnoFueraEdad(turno:any){const edad=this.edadCohorteSeleccionada();return edad!==null&&(edad<(turno.rangoEdadMin??0)||edad>(turno.rangoEdadMax??99));}
  turnoEsAsignacionActual(turno:any){return (this.seleccion?.turnosSolicitados||[]).some((actual:any)=>actual.id===turno.id);}
  turnoEsPlazaActual(turno:any){return turno.completo&&this.seleccion?.estado==='PENDIENTE'&&this.turnoEsAsignacionActual(turno);}
  async guardarTurnos(){if(!this.seleccion||!this.turnoIdsEdicion.length||!this.turnosEdicionCambiados||this.procesandoTurnos)return;const elegidos=this.turnosDisponibles.filter(t=>this.turnoIdsEdicion.includes(t.id));const nuevos=elegidos.filter(t=>!this.turnoEsAsignacionActual(t));if(nuevos.some(t=>t.completo||this.turnoFueraEdad(t))){const r=await Swal.fire({title:'Forzar asignación de turnos',text:'La selección contiene turnos nuevos fuera de la edad recomendada o sin plaza disponible. La solicitud quedará con plaza asignada aunque supere el aforo.',icon:'warning',showCancelButton:true,confirmButtonText:'Sí, forzar asignación',cancelButtonText:'Volver',confirmButtonColor:'#a52229'});if(!r.isConfirmed)return;}this.procesandoTurnos=true;this.api.actualizarTurnos(this.seleccion.referencia,this.turnoIdsEdicion).subscribe({next:()=>{this.procesandoTurnos=false;showSuccessToast('Turnos actualizados. El aviso al cliente se ha puesto en cola.');this.cerrarDetalle();this.buscar();},error:(e:any)=>{this.procesandoTurnos=false;showErrorToast(e?.error?.mensaje||'No se pudieron actualizar los turnos.');}});}
  private edadCohorteSeleccionada(){const inicio=Number.parseInt(String(this.seleccion?.temporada||'').slice(0,4),10),nacimiento=Number.parseInt(String(this.seleccion?.fechaNacimiento||'').slice(0,4),10);return Number.isInteger(inicio)&&Number.isInteger(nacimiento)?inicio-nacimiento:null;}
  private normalizarDia(dia:string){return (dia||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();}
  private ordenDia(dia:string){const orden=['lunes','martes','miercoles','jueves','viernes','sabado','domingo'].indexOf(this.normalizarDia(dia));return orden<0?99:orden;}

  async cambiarTab(tab:'solicitudes'|'plantillas'){if(tab===this.tab)return;if(!await this.confirmarSalida())return;this.tab=tab;this.router.navigate([],{relativeTo:this.route,queryParams:{tab},queryParamsHandling:'merge'});if(tab==='plantillas')this.cargarPlantilla();}
  async cambiarDeporte(deporte:string){if(deporte===this.deportePlantilla)return;if(!await this.confirmarSalida())return;this.deportePlantilla=deporte;this.cargarPlantilla();}
  cargarPlantilla(){this.cargandoPlantilla=true;this.api.plantillas(this.deportePlantilla).subscribe({next:r=>{this.plantilla={...r.activa.contenido,instrucciones:r.activa.instrucciones,version:r.activa.version};this.historial=r.historial;this.dirty=false;this.cargandoPlantilla=false;},error:()=>{this.cargandoPlantilla=false;showErrorToast('No se pudo cargar la plantilla.');}});}
  marcarCambios(){if(this.auth.tieneRolAdmin())this.dirty=true;}
  agregarNorma(){this.plantilla.normas.push('');this.marcarCambios();}
  eliminarNorma(i:number){this.plantilla.normas.splice(i,1);this.marcarCambios();}
  moverNorma(i:number,d:number){const j=i+d;if(j<0||j>=this.plantilla.normas.length)return;[this.plantilla.normas[i],this.plantilla.normas[j]]=[this.plantilla.normas[j],this.plantilla.normas[i]];this.marcarCambios();}
  valida(){return ['cabecera','contacto','titulo','consentimiento','importes','instrucciones'].every(k=>this.plantilla[k]?.trim())&&this.plantilla.normas?.length>0&&this.plantilla.normas.every((x:string)=>x.trim());}
  preview(){if(!this.valida()){showErrorToast('Completa todos los campos y añade al menos una norma.');return;}this.procesandoPlantilla=true;this.api.previewPlantilla(this.deportePlantilla,this.payload()).subscribe({next:b=>{this.procesandoPlantilla=false;window.open(URL.createObjectURL(b),'_blank','noopener');},error:e=>{this.procesandoPlantilla=false;showErrorToast(e?.error?.mensaje||'No se pudo generar la previsualización.');}});}
  publicar(){if(!this.auth.tieneRolAdmin()||!this.valida())return;this.procesandoPlantilla=true;this.api.publicarPlantilla(this.deportePlantilla,this.payload()).subscribe({next:()=>{this.procesandoPlantilla=false;showSuccessToast('Nueva versión publicada.');this.cargarPlantilla();},error:e=>{this.procesandoPlantilla=false;showErrorToast(e?.error?.mensaje||'No se pudo publicar.');}});}
  async restaurar(version:number){if(!this.auth.tieneRolAdmin())return;const r=await Swal.fire({title:`Restaurar versión ${version}`,text:'Se creará una nueva versión activa. El histórico no cambiará.',icon:'warning',showCancelButton:true,confirmButtonText:'Restaurar',cancelButtonText:'Cancelar'});if(!r.isConfirmed)return;this.procesandoPlantilla=true;this.api.restaurarPlantilla(this.deportePlantilla,version).subscribe({next:()=>{this.procesandoPlantilla=false;showSuccessToast('Versión restaurada como nueva versión.');this.cargarPlantilla();},error:()=>{this.procesandoPlantilla=false;showErrorToast('No se pudo restaurar.');}});}
  private payload(){const {version,...p}=this.plantilla;return p;}
  private vacia(){return {cabecera:'',contacto:'',titulo:'',consentimiento:'',normas:[''],importes:'',instrucciones:''};}
  private async confirmarSalida(){if(!this.dirty)return true;const r=await Swal.fire({title:'Cambios sin guardar',text:'Perderás los cambios realizados.',icon:'warning',showCancelButton:true,confirmButtonText:'Descartar cambios',cancelButtonText:'Seguir editando'});return r.isConfirmed;}
  @HostListener('window:beforeunload',['$event']) antesDeSalir(e:BeforeUnloadEvent){if(this.dirty)e.preventDefault();}

  async cancelar(){if(!this.seleccion)return;const r=await Swal.fire(this.dialogoCancelacion(this.seleccion.referencia,this.seleccion.email));if(!r.isConfirmed)return;this.api.cancelar(this.seleccion.referencia,String(r.value??'')).subscribe({next:()=>{showSuccessToast('Preinscripción cancelada.');this.cerrarDetalle();this.buscar();},error:e=>showErrorToast(e?.error?.mensaje||'No se pudo cancelar la preinscripción.')});}
  /** Motivo opcional: se envía lo escrito o vacío (el backend aplica el de por defecto). La nota y el contador se montan con textContent. */
  private dialogoCancelacion(referencia:string,email?:string):SweetAlertOptions{return {title:'¿Cancelar preinscripción?',text:`Se cancelará la solicitud ${referencia} y se avisará a la familia por correo.`,icon:'warning',input:'textarea',inputLabel:'Motivo (opcional)',inputPlaceholder:MOTIVO_CANCELACION_POR_DEFECTO,inputAttributes:{maxlength:String(MOTIVO_CANCELACION_MAX)},showCancelButton:true,confirmButtonText:'Cancelar y avisar',cancelButtonText:'Volver',confirmButtonColor:'#a52229',didOpen:(popup:HTMLElement)=>{const campo=popup.querySelector<HTMLTextAreaElement>('textarea.swal2-textarea');if(!campo)return;const ayuda=document.createElement('div');ayuda.className='swal2-motivo-ayuda';const nota=document.createElement('p');nota.id='swal2-motivo-nota';nota.textContent='Si lo dejas vacío se enviará el motivo por defecto.'+(email?` El aviso se enviará a ${email}.`:'');const contador=document.createElement('span');contador.setAttribute('aria-live','polite');const contar=()=>contador.textContent=`${campo.value.length}/${MOTIVO_CANCELACION_MAX}`;contar();ayuda.append(nota,contador);campo.insertAdjacentElement('afterend',ayuda);campo.setAttribute('aria-describedby',nota.id);campo.addEventListener('input',contar);}};}
  async eliminar(){if(!this.puedeEliminar)return;const referencia=this.seleccion.referencia;const r=await Swal.fire({title:'Eliminar definitivamente',html:`<p>Se eliminarán la solicitud <strong>${referencia}</strong>, sus datos personales, la firma y el PDF.</p><p>Escribe la referencia para confirmar:</p>`,input:'text',inputPlaceholder:referencia,icon:'error',showCancelButton:true,confirmButtonText:'Eliminar definitivamente',cancelButtonText:'Volver',confirmButtonColor:'#a52229',preConfirm:valor=>{if(valor?.trim().toUpperCase()!==referencia)return Swal.showValidationMessage('La referencia no coincide.');return valor;}});if(!r.isConfirmed)return;this.api.eliminar(referencia).subscribe({next:()=>{showSuccessToast('Preinscripción eliminada definitivamente.');this.cerrarDetalle();this.buscar();},error:e=>showErrorToast(e?.error?.mensaje||'No se pudo eliminar la preinscripción.')});}
  reenviar(){if(!this.seleccion)return;this.api.reenviar(this.seleccion.referencia).subscribe({next:()=>showSuccessToast('Solicitud original reenviada correctamente.'),error:e=>showErrorToast(e?.error?.mensaje||'No se pudo reenviar la solicitud original.')});}
  reenviarCambioTurnos(){if(!this.puedeReenviarCambioTurnos)return;this.api.reenviarCambioTurnos(this.seleccion.referencia).subscribe({next:()=>{showSuccessToast('Reenvío del aviso de cambio solicitado.');this.cerrarDetalle();this.buscar();},error:(e:any)=>showErrorToast(e?.error?.mensaje||'No se pudo reenviar el aviso de cambio.')});}
  reenviarFinalizacion(){if(!this.seleccion||this.seleccion.estado!=='FINALIZADA')return;this.api.reenviarFinalizacion(this.seleccion.referencia).subscribe({next:()=>{showSuccessToast('Reenvío de confirmación solicitado.');this.cerrarDetalle();this.buscar();},error:(e:any)=>showErrorToast(e?.error?.mensaje||'No se pudo reenviar la confirmación.')});}
  abrirFinalizacion(){if(!this.seleccion||this.cargandoCoincidencias||this.errorCoincidencias)return;this.discapacidadHistorica=this.seleccion.tieneDiscapacidad??null;this.prepararTarifa();const candidatos=this.seleccion.alumnosCoincidentes||[];if(this.seleccion.dni&&candidatos.length===1&&candidatos[0].nif?.toUpperCase()===this.seleccion.dni.toUpperCase())this.elegirAlumno(candidatos[0]);else if(candidatos.length===0)this.crearNuevaFicha();else{this.accionAlumno='';this.alumnoSeleccionado=undefined;this.camposActualizar.clear();}this.mostrandoFinalizacion=true;}
  cerrarFinalizacion(){if(this.procesandoFinalizacion)return;this.mostrandoFinalizacion=false;this.camposActualizar.clear();this.accionAlumno='';this.alumnoSeleccionado=undefined;this.discapacidadHistorica=null;this.decisionEmail='';}
  elegirAlumno(alumno:any){this.accionAlumno='VINCULAR_EXISTENTE';this.alumnoSeleccionado=alumno;this.decisionEmail='';this.prepararTarifa(alumno);this.camposActualizar.clear();this.camposAplicables.filter(c=>this.hayDiferencia(c)).forEach(c=>this.camposActualizar.add(c.clave));}
  crearNuevaFicha(){if(this.tieneCoincidenciaDniExacta)return;this.accionAlumno='CREAR_NUEVO';this.alumnoSeleccionado=undefined;this.decisionEmail='';this.prepararTarifa();this.camposActualizar.clear();}
  confirmarDiscapacidadHistorica(valor:boolean){this.discapacidadHistorica=valor;if(this.accionAlumno!=='VINCULAR_EXISTENTE'||!this.alumnoSeleccionado)return;const campo=this.camposAplicables.find(c=>c.clave==='TIENE_DISCAPACIDAD');if(campo&&this.hayDiferencia(campo))this.camposActualizar.add(campo.clave);else this.camposActualizar.delete('TIENE_DISCAPACIDAD');}
  alternarCampo(clave:string,marcado:boolean){marcado?this.camposActualizar.add(clave):this.camposActualizar.delete(clave);}
  get camposAplicables(){return this.camposComparables.filter(c=>c.clave!=='EMAIL'&&(!c.soloMenor||this.esMenorSeleccionado)&&(c.clave!=='NIF'||this.tieneValor(this.seleccion?.dni))&&(c.clave!=='TELEFONO2'||this.tieneValor(this.seleccion?.telefono2)));}
  hayDiferencia(c:any){const actual=this.valorComparable(this.alumnoSeleccionado?.[c.alumno],c.clave);const recibido=this.valorComparable(this.valorPreinscripcion(c),c.clave);return actual!==recibido;}
  valorPreinscripcion(c:any){return c.clave==='TIENE_DISCAPACIDAD'?(this.seleccion?.tieneDiscapacidad??this.discapacidadHistorica):this.seleccion?.[c.pre];}
  mostrarValor(v:any,campo?:string){if(campo==='FECHA_NACIMIENTO'&&v!=null){const dia=diaApi(v);if(dia)return dia.split('-').reverse().join('/');}if(v===true)return 'Sí';if(v===false)return 'No';return v??'Sin dato';}
  /** FECHA_NACIMIENTO llega como [a,m,d] (preinscripción) o en ms (ficha): se compara el día, sin hora ni zona. */
  private valorComparable(v:any,campo:string){if(v==null)return '';if(campo==='FECHA_NACIMIENTO')return diaApi(v)??String(v);if(v instanceof Date)return v.toISOString().slice(0,10);if(typeof v==='boolean')return v?'true':'false';const texto=String(v).trim().toLowerCase();if(['TELEFONO','TELEFONO2'].includes(campo)){const digitos=texto.replace(/\D/g,'');if(digitos.length===13&&digitos.startsWith('0034'))return digitos.slice(4);return digitos.length===11&&digitos.startsWith('34')?digitos.slice(2):digitos;}return /^\d{4}-\d{2}-\d{2}/.test(texto)?texto.slice(0,10):texto.replace(/\s+/g,' ');}
  get requiereDatosDeporte(){return this.accionAlumno==='CREAR_NUEVO'||(this.accionAlumno==='VINCULAR_EXISTENTE'&&this.alumnoSeleccionado?.requiereDatosDeporte);}
  /** Edad por año/mes/día de diaApi: la API envía [a,m,d] y new Date(array) no es portable (Safari/Firefox). */
  get esMenorSeleccionado(){const dia=diaApi(this.seleccion?.fechaNacimiento);if(!dia)return false;const [a,m,d]=dia.split('-').map(Number);const hoy=new Date();let edad=hoy.getFullYear()-a;if(hoy.getMonth()+1<m||(hoy.getMonth()+1===m&&hoy.getDate()<d))edad--;return edad<18;}
  get requiereGrado(){return this.catalogo.tieneGrados(this.seleccion?.deporte);}
  /** D7/D8: tarifas que admite la disciplina de la solicitud, en orden del catálogo (se muestra `nombre`, se envía `codigo`). */
  get tarifasDisponibles():CatalogoTarifa[]{return this.catalogo.tarifasDe(this.seleccion?.deporte);}
  /** D9: preselección de Taemoi (legado-taemoi); otras escuelas, ninguna. */
  private tarifaInicial(){const deporte=this.seleccion?.deporte;return tarifaInicialPreinscripcion(this.catalogo.esLegadoTaemoi(),deporte,diaApi(this.seleccion?.fechaNacimiento),this.catalogo.tarifasDe(deporte))??'';}
  /** La tarifa elegida pide rol familiar / grupo familiar (banderas del catálogo). */
  get requiereRolFamiliar(){return this.catalogo.tarifa(this.datosAlta.tipoTarifa)?.requiereRolFamiliar===true;}
  get requiereGrupoFamiliar(){return this.catalogo.tarifa(this.datosAlta.tipoTarifa)?.requiereGrupoFamiliar===true;}
  private prepararTarifa(alumno?:any){const hoy=new Date().toISOString().slice(0,10),tipo=alumno?.tipoTarifa||this.tarifaInicial();this.datosAlta={tipoTarifa:tipo,cuantiaTarifa:alumno?.cuantiaTarifa??this.cuantiaEstandar(tipo),rolFamiliar:alumno?.rolFamiliar||'NINGUNO',grupoFamiliar:alumno?.grupoFamiliar||'',grado:this.gradosDisponibles[0]?.codigo??'',fechaGrado:hoy};}
  /** Grados que ofrece el sistema de la disciplina de la solicitud, por `orden` (D3: se muestra `nombre`). */
  get gradosDisponibles():{codigo:string;nombre:string}[]{return this.catalogo.gradosSeleccionables(this.seleccion?.deporte);}
  cambiarTarifa(){this.datosAlta.cuantiaTarifa=this.cuantiaEstandar(this.datosAlta.tipoTarifa);if(!this.requiereRolFamiliar)this.datosAlta.rolFamiliar='NINGUNO';if(!this.requiereGrupoFamiliar)this.datosAlta.grupoFamiliar='';}
  /** Importe estándar sin rol, como hasta ahora en esta pantalla (la tarifa que pide rol da su `importe`). Desconocida: 0. */
  private cuantiaEstandar(t:string){return cuantiaEstandar(this.catalogo.tarifa(t));}
  get correosDiferentes(){return this.accionAlumno==='VINCULAR_EXISTENTE'&&this.alumnoSeleccionado&&this.valorComparable(this.alumnoSeleccionado.email,'EMAIL')!==this.valorComparable(this.seleccion?.email,'EMAIL');}
  get finalizacionValida(){if(!this.accionAlumno||this.discapacidadEfectiva==null||this.accionAlumno==='VINCULAR_EXISTENTE'&&!this.alumnoSeleccionado)return false;if(this.correosDiferentes&&!this.decisionEmail)return false;if(!this.datosAlta.tipoTarifa||this.datosAlta.cuantiaTarifa==null||this.datosAlta.cuantiaTarifa<0)return false;if(this.requiereDatosDeporte&&this.requiereGrado&&(!this.datosAlta.grado||!this.datosAlta.fechaGrado))return false;if(this.requiereRolFamiliar&&this.datosAlta.rolFamiliar==='NINGUNO')return false;if(this.requiereGrupoFamiliar&&!this.datosAlta.grupoFamiliar.trim())return false;return true;}
  get discapacidadEfectiva(){return this.seleccion?.tieneDiscapacidad??this.discapacidadHistorica;}
  get tieneCoincidenciaDniExacta(){const dni=this.seleccion?.dni?.trim().toUpperCase();return Boolean(dni&&(this.seleccion?.alumnosCoincidentes||[]).some((a:any)=>a.nif?.trim().toUpperCase()===dni));}
  finalizar(){if(!this.seleccion||!this.finalizacionValida||this.procesandoFinalizacion)return;this.procesandoFinalizacion=true;const datosDeporte={...this.datosAlta,grado:this.requiereDatosDeporte&&this.requiereGrado?this.datosAlta.grado:null,fechaGrado:this.requiereDatosDeporte&&this.requiereGrado?this.datosAlta.fechaGrado:null};this.api.finalizar(this.seleccion.referencia,{accionAlumno:this.accionAlumno,alumnoId:this.accionAlumno==='VINCULAR_EXISTENTE'?this.alumnoSeleccionado.id:undefined,camposActualizar:[...this.camposActualizar],discapacidadHistorica:this.seleccion.tieneDiscapacidad==null?this.discapacidadHistorica:null,decisionEmail:this.decisionEmail||undefined,datosDeporte}).subscribe({next:()=>{this.procesandoFinalizacion=false;showSuccessToast('Preinscripción finalizada: tarifa, grupos y turnos actualizados.');this.cerrarFinalizacion();this.cerrarDetalle();this.buscar();},error:e=>{this.procesandoFinalizacion=false;showErrorToast(e?.error?.mensaje||'No se pudo finalizar la preinscripción.');}});}
  private tieneValor(valor:any){return valor!==null&&valor!==undefined&&String(valor).trim()!=='';}
}
