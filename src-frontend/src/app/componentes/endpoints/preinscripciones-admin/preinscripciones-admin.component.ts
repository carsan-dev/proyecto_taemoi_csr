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
  items:any[]=[]; cargando=false; filtros={temporada:'',deporte:'',estado:'',q:''}; seleccion:any; alumnoId?:number; firmaUrl?:string;
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

  seleccionar(item:any){this.liberarFirma();this.seleccion=item;this.alumnoId=item.alumnoCoincidente?.id;this.api.firma(item.referencia).subscribe({next:firma=>this.firmaUrl=URL.createObjectURL(firma),error:()=>showErrorToast('No se pudo cargar la firma.')});}
  cerrarDetalle(){this.liberarFirma();this.seleccion=undefined;this.alumnoId=undefined;}
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
  async finalizar(){if(!this.seleccion||!this.alumnoId)return;const r=await Swal.fire({title:'¿Finalizar preinscripción?',text:'Se reactivará el alumno si fuera necesario y se le asignará el grupo solicitado.',icon:'question',showCancelButton:true,confirmButtonText:'Sí, finalizar',cancelButtonText:'Volver'});if(!r.isConfirmed)return;this.api.finalizar(this.seleccion.referencia,this.alumnoId).subscribe({next:()=>{showSuccessToast('Preinscripción finalizada y grupo asignado.');this.cerrarDetalle();this.buscar();},error:e=>showErrorToast(e?.error?.mensaje||'No se pudo finalizar la preinscripción.')});}
}
