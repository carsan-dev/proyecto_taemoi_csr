import { CommonModule } from '@angular/common';
import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import Swal from 'sweetalert2';
import { PreinscripcionService } from '../../../features/preinscripcion/preinscripcion.service';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';
import { PaginacionComponent } from '../../generales/paginacion/paginacion.component';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { ActivatedRoute, Router } from '@angular/router';

@Component({selector:'app-preinscripciones-admin',standalone:true,imports:[CommonModule,FormsModule,PaginacionComponent],templateUrl:'./preinscripciones-admin.component.html',styleUrl:'./preinscripciones-admin.component.scss'})
export class PreinscripcionesAdminComponent implements OnInit, OnDestroy {
  items:any[]=[]; cargando=false; filtros={temporada:'',deporte:'',estado:'',q:''}; seleccion:any; firmaUrl?:string;
  mostrandoFinalizacion=false; procesandoFinalizacion=false; camposActualizar=new Set<string>();
  accionAlumno:''|'CREAR_NUEVO'|'VINCULAR_EXISTENTE'=''; alumnoSeleccionado:any; discapacidadHistorica:boolean|null=null;
  decisionEmail:''|'CONSERVAR_FICHA'|'USAR_PREINSCRIPCION'='';
  datosAlta:any={tipoTarifa:'',cuantiaTarifa:null,rolFamiliar:'NINGUNO',grupoFamiliar:'',grado:'BLANCO',fechaGrado:''};
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
  tab:'solicitudes'|'plantillas'='solicitudes'; deportePlantilla='TAEKWONDO'; plantilla:any=this.vacia(); historial:any[]=[]; cargandoPlantilla=false; procesandoPlantilla=false; dirty=false;
  readonly deportes=['TAEKWONDO','KICKBOXING','PILATES','DEFENSA_PERSONAL_FEMENINA'];
  constructor(private readonly api:PreinscripcionService,public readonly auth:AuthenticationService,private readonly route:ActivatedRoute,private readonly router:Router){}
  ngOnInit(){this.tab=this.route.snapshot.queryParamMap.get('tab')==='plantillas'?'plantillas':'solicitudes';this.buscar();if(this.tab==='plantillas')this.cargarPlantilla();}
  ngOnDestroy(){this.liberarFirma();}

  buscar(reiniciarPagina=false){if(reiniciarPagina)this.pagina=0;this.cargando=true;this.api.listar(this.filtros,this.pagina,this.tamanoPagina).subscribe({next:r=>{if(!r.content.length&&this.pagina>0&&r.totalElements>0){this.pagina--;this.buscar();return;}this.items=r.content;this.pagina=r.page;this.totalElementos=r.totalElements;this.totalPaginas=r.totalPages;this.cargando=false;},error:()=>{this.cargando=false;showErrorToast('No se pudieron cargar las preinscripciones.');}});}
  cambiarPagina(numeroPagina:number){const destino=numeroPagina-1;if(this.cargando||destino<0||destino>=this.totalPaginas)return;this.pagina=destino;this.cerrarDetalle();this.buscar();}
  get primerResultado(){return this.totalElementos?this.pagina*this.tamanoPagina+1:0;}
  get ultimoResultado(){return Math.min((this.pagina+1)*this.tamanoPagina,this.totalElementos);}
  get puedeEliminar(){return this.seleccion?.estado==='CANCELADA'&&this.auth.tieneRolAdmin();}

  seleccionar(item:any){this.liberarFirma();this.seleccion=item;this.api.firma(item.referencia).subscribe({next:firma=>this.firmaUrl=URL.createObjectURL(firma),error:()=>showErrorToast('No se pudo cargar la firma.')});}
  cerrarDetalle(){this.cerrarFinalizacion();this.liberarFirma();this.seleccion=undefined;}
  descargarDocumento(){if(!this.seleccion)return;this.api.documentoFirmado(this.seleccion.referencia).subscribe({next:documento=>{const url=URL.createObjectURL(documento);const enlace=document.createElement('a');enlace.href=url;enlace.download=`preinscripcion-${this.seleccion.referencia}.pdf`;enlace.click();URL.revokeObjectURL(url);},error:()=>showErrorToast('No se pudo descargar el PDF firmado.')});}
  private liberarFirma(){if(this.firmaUrl)URL.revokeObjectURL(this.firmaUrl);this.firmaUrl=undefined;}

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

  async cancelar(){if(!this.seleccion)return;const r=await Swal.fire({title:'¿Cancelar preinscripción?',text:`Se cancelará la solicitud ${this.seleccion.referencia}.`,icon:'warning',showCancelButton:true,confirmButtonText:'Sí, cancelar',cancelButtonText:'Volver',confirmButtonColor:'#a52229'});if(!r.isConfirmed)return;this.api.cancelar(this.seleccion.referencia).subscribe({next:()=>{showSuccessToast('Preinscripción cancelada.');this.cerrarDetalle();this.buscar();},error:e=>showErrorToast(e?.error?.mensaje||'No se pudo cancelar la preinscripción.')});}
  async eliminar(){if(!this.puedeEliminar)return;const referencia=this.seleccion.referencia;const r=await Swal.fire({title:'Eliminar definitivamente',html:`<p>Se eliminarán la solicitud <strong>${referencia}</strong>, sus datos personales, la firma y el PDF.</p><p>Escribe la referencia para confirmar:</p>`,input:'text',inputPlaceholder:referencia,icon:'error',showCancelButton:true,confirmButtonText:'Eliminar definitivamente',cancelButtonText:'Volver',confirmButtonColor:'#a52229',preConfirm:valor=>{if(valor?.trim().toUpperCase()!==referencia)return Swal.showValidationMessage('La referencia no coincide.');return valor;}});if(!r.isConfirmed)return;this.api.eliminar(referencia).subscribe({next:()=>{showSuccessToast('Preinscripción eliminada definitivamente.');this.cerrarDetalle();this.buscar();},error:e=>showErrorToast(e?.error?.mensaje||'No se pudo eliminar la preinscripción.')});}
  reenviar(){if(!this.seleccion)return;this.api.reenviar(this.seleccion.referencia).subscribe({next:()=>showSuccessToast('Correo reenviado correctamente.'),error:e=>showErrorToast(e?.error?.mensaje||'No se pudo reenviar el correo.')});}
  reenviarFinalizacion(){if(!this.seleccion||this.seleccion.estado!=='FINALIZADA')return;this.api.reenviarFinalizacion(this.seleccion.referencia).subscribe({next:()=>{showSuccessToast('Reenvío de confirmación solicitado.');this.cerrarDetalle();this.buscar();},error:(e:any)=>showErrorToast(e?.error?.mensaje||'No se pudo reenviar la confirmación.')});}
  abrirFinalizacion(){if(!this.seleccion)return;this.discapacidadHistorica=this.seleccion.tieneDiscapacidad??null;this.prepararTarifa();const candidatos=this.seleccion.alumnosCoincidentes||[];if(this.seleccion.dni&&candidatos.length===1&&candidatos[0].nif?.toUpperCase()===this.seleccion.dni.toUpperCase())this.elegirAlumno(candidatos[0]);else if(candidatos.length===0)this.crearNuevaFicha();else{this.accionAlumno='';this.alumnoSeleccionado=undefined;this.camposActualizar.clear();}this.mostrandoFinalizacion=true;}
  cerrarFinalizacion(){if(this.procesandoFinalizacion)return;this.mostrandoFinalizacion=false;this.camposActualizar.clear();this.accionAlumno='';this.alumnoSeleccionado=undefined;this.discapacidadHistorica=null;this.decisionEmail='';}
  elegirAlumno(alumno:any){this.accionAlumno='VINCULAR_EXISTENTE';this.alumnoSeleccionado=alumno;this.decisionEmail='';this.prepararTarifa(alumno);this.camposActualizar.clear();this.camposAplicables.filter(c=>this.hayDiferencia(c)).forEach(c=>this.camposActualizar.add(c.clave));}
  crearNuevaFicha(){if(this.tieneCoincidenciaDniExacta)return;this.accionAlumno='CREAR_NUEVO';this.alumnoSeleccionado=undefined;this.decisionEmail='';this.prepararTarifa();this.camposActualizar.clear();}
  confirmarDiscapacidadHistorica(valor:boolean){this.discapacidadHistorica=valor;if(this.accionAlumno!=='VINCULAR_EXISTENTE'||!this.alumnoSeleccionado)return;const campo=this.camposAplicables.find(c=>c.clave==='TIENE_DISCAPACIDAD');if(campo&&this.hayDiferencia(campo))this.camposActualizar.add(campo.clave);else this.camposActualizar.delete('TIENE_DISCAPACIDAD');}
  alternarCampo(clave:string,marcado:boolean){marcado?this.camposActualizar.add(clave):this.camposActualizar.delete(clave);}
  get camposAplicables(){return this.camposComparables.filter(c=>c.clave!=='EMAIL'&&(!c.soloMenor||this.esMenorSeleccionado)&&(c.clave!=='NIF'||this.tieneValor(this.seleccion?.dni))&&(c.clave!=='TELEFONO2'||this.tieneValor(this.seleccion?.telefono2)));}
  hayDiferencia(c:any){const actual=this.valorComparable(this.alumnoSeleccionado?.[c.alumno],c.clave);const recibido=this.valorComparable(this.valorPreinscripcion(c),c.clave);return actual!==recibido;}
  valorPreinscripcion(c:any){return c.clave==='TIENE_DISCAPACIDAD'?(this.seleccion?.tieneDiscapacidad??this.discapacidadHistorica):this.seleccion?.[c.pre];}
  mostrarValor(v:any){if(v===true)return 'Sí';if(v===false)return 'No';return v??'Sin dato';}
  private valorComparable(v:any,campo:string){if(v==null)return '';if(v instanceof Date)return v.toISOString().slice(0,10);if(typeof v==='boolean')return v?'true':'false';const texto=String(v).trim().toLowerCase();if(['TELEFONO','TELEFONO2'].includes(campo)){const digitos=texto.replace(/\D/g,'');if(digitos.length===13&&digitos.startsWith('0034'))return digitos.slice(4);return digitos.length===11&&digitos.startsWith('34')?digitos.slice(2):digitos;}return /^\d{4}-\d{2}-\d{2}/.test(texto)?texto.slice(0,10):texto.replace(/\s+/g,' ');}
  get requiereDatosDeporte(){return this.accionAlumno==='CREAR_NUEVO'||(this.accionAlumno==='VINCULAR_EXISTENTE'&&this.alumnoSeleccionado?.requiereDatosDeporte);}
  get esMenorSeleccionado(){const nacimiento=new Date(this.seleccion?.fechaNacimiento);if(Number.isNaN(nacimiento.getTime()))return false;const hoy=new Date();let edad=hoy.getFullYear()-nacimiento.getFullYear();if(hoy.getMonth()<nacimiento.getMonth()||(hoy.getMonth()===nacimiento.getMonth()&&hoy.getDate()<nacimiento.getDate()))edad--;return edad<18;}
  get requiereGrado(){return ['TAEKWONDO','KICKBOXING'].includes(this.seleccion?.deporte);}
  get tarifasDisponibles():string[]{switch(this.seleccion?.deporte){case 'KICKBOXING':return ['KICKBOXING'];case 'PILATES':return ['PILATES'];case 'DEFENSA_PERSONAL_FEMENINA':return ['DEFENSA_PERSONAL_FEMENINA'];default:return ['INFANTIL','ADULTO','INFANTIL_GRUPO','ADULTO_GRUPO','FAMILIAR','HERMANOS','PADRES_HIJOS'];}}
  private tarifaInicial(){if(this.seleccion?.deporte!=='TAEKWONDO')return this.tarifasDisponibles[0]||'';const nacimiento=new Date(this.seleccion.fechaNacimiento);if(Number.isNaN(nacimiento.getTime()))return 'INFANTIL';const hoy=new Date();let edad=hoy.getFullYear()-nacimiento.getFullYear();if(hoy.getMonth()<nacimiento.getMonth()||(hoy.getMonth()===nacimiento.getMonth()&&hoy.getDate()<nacimiento.getDate()))edad--;return edad<18?'INFANTIL':'ADULTO';}
  private prepararTarifa(alumno?:any){const hoy=new Date().toISOString().slice(0,10),tipo=alumno?.tipoTarifa||this.tarifaInicial();this.datosAlta={tipoTarifa:tipo,cuantiaTarifa:alumno?.cuantiaTarifa??this.cuantiaEstandar(tipo),rolFamiliar:alumno?.rolFamiliar||'NINGUNO',grupoFamiliar:alumno?.grupoFamiliar||'',grado:'BLANCO',fechaGrado:hoy};}
  get gradosDisponibles():string[]{const todos=['BLANCO','BLANCO_AMARILLO','AMARILLO','AMARILLO_NARANJA','NARANJA','NARANJA_VERDE','VERDE','VERDE_AZUL','AZUL','AZUL_ROJO','ROJO','ROJO_NEGRO_1_PUM','ROJO_NEGRO_2_PUM','ROJO_NEGRO_3_PUM','NEGRO_1_DAN','NEGRO_2_DAN','NEGRO_3_DAN','NEGRO_4_DAN','NEGRO_5_DAN'];if(this.seleccion?.deporte!=='KICKBOXING')return todos;const kick=new Set(['BLANCO','AMARILLO','NARANJA','VERDE','AZUL','ROJO','NEGRO_1_DAN','NEGRO_2_DAN','NEGRO_3_DAN','NEGRO_4_DAN','NEGRO_5_DAN']);return todos.filter(g=>kick.has(g));}
  cambiarTarifa(){this.datosAlta.cuantiaTarifa=this.cuantiaEstandar(this.datosAlta.tipoTarifa);if(this.datosAlta.tipoTarifa!=='PADRES_HIJOS')this.datosAlta.rolFamiliar='NINGUNO';if(this.datosAlta.tipoTarifa!=='HERMANOS')this.datosAlta.grupoFamiliar='';}
  private cuantiaEstandar(t:string){const importes:Record<string,number>={FAMILIAR:0,PADRES_HIJOS:0,ADULTO_GRUPO:20,INFANTIL_GRUPO:20,HERMANOS:26,INFANTIL:28,ADULTO:30,KICKBOXING:30,PILATES:30,DEFENSA_PERSONAL_FEMENINA:25};return importes[t]??0;}
  get correosDiferentes(){return this.accionAlumno==='VINCULAR_EXISTENTE'&&this.alumnoSeleccionado&&this.valorComparable(this.alumnoSeleccionado.email,'EMAIL')!==this.valorComparable(this.seleccion?.email,'EMAIL');}
  get finalizacionValida(){if(!this.accionAlumno||this.discapacidadEfectiva==null||this.accionAlumno==='VINCULAR_EXISTENTE'&&!this.alumnoSeleccionado)return false;if(this.correosDiferentes&&!this.decisionEmail)return false;if(!this.datosAlta.tipoTarifa||this.datosAlta.cuantiaTarifa==null||this.datosAlta.cuantiaTarifa<0)return false;if(this.requiereDatosDeporte&&this.requiereGrado&&(!this.datosAlta.grado||!this.datosAlta.fechaGrado))return false;if(this.datosAlta.tipoTarifa==='PADRES_HIJOS'&&this.datosAlta.rolFamiliar==='NINGUNO')return false;if(this.datosAlta.tipoTarifa==='HERMANOS'&&!this.datosAlta.grupoFamiliar.trim())return false;return true;}
  get discapacidadEfectiva(){return this.seleccion?.tieneDiscapacidad??this.discapacidadHistorica;}
  get tieneCoincidenciaDniExacta(){const dni=this.seleccion?.dni?.trim().toUpperCase();return Boolean(dni&&(this.seleccion?.alumnosCoincidentes||[]).some((a:any)=>a.nif?.trim().toUpperCase()===dni));}
  finalizar(){if(!this.seleccion||!this.finalizacionValida||this.procesandoFinalizacion)return;this.procesandoFinalizacion=true;const datosDeporte={...this.datosAlta,grado:this.requiereDatosDeporte&&this.requiereGrado?this.datosAlta.grado:null,fechaGrado:this.requiereDatosDeporte&&this.requiereGrado?this.datosAlta.fechaGrado:null};this.api.finalizar(this.seleccion.referencia,{accionAlumno:this.accionAlumno,alumnoId:this.accionAlumno==='VINCULAR_EXISTENTE'?this.alumnoSeleccionado.id:undefined,camposActualizar:[...this.camposActualizar],discapacidadHistorica:this.seleccion.tieneDiscapacidad==null?this.discapacidadHistorica:null,decisionEmail:this.decisionEmail||undefined,datosDeporte}).subscribe({next:()=>{this.procesandoFinalizacion=false;showSuccessToast('Preinscripción finalizada: tarifa, grupos y turnos actualizados.');this.cerrarFinalizacion();this.cerrarDetalle();this.buscar();},error:e=>{this.procesandoFinalizacion=false;showErrorToast(e?.error?.mensaje||'No se pudo finalizar la preinscripción.');}});}
  private tieneValor(valor:any){return valor!==null&&valor!==undefined&&String(valor).trim()!=='';}
}
