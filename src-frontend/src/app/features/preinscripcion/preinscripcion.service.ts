import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface ContenidoPreinscripcion {
 cabecera?:string; contacto?:string; titulo?:string; consentimiento?:string;
 normas:string[]; importes?:string;
}

export interface ConfiguracionPreinscripcion {
 deporte:string; version:number;
 contenido:ContenidoPreinscripcion|string;
 instrucciones?:string;
}

/** Motivo que recibe la familia si se cancela sin escribir uno (lo aplica el backend; aquí solo se muestra). */
export const MOTIVO_CANCELACION_POR_DEFECTO='No ha sido posible confirmar tu plaza en esta ocasión.';
/** Longitud máxima del motivo: la misma que valida el backend. */
export const MOTIVO_CANCELACION_MAX=500;

@Injectable({providedIn:'root'})
export class PreinscripcionService {
 private readonly base=`${environment.apiUrl}/preinscripciones`;
 constructor(private readonly http:HttpClient){}
 temporada(){return this.http.get<{temporada:string}>(`${this.base}/public/temporada`);}
 configuracion(deporte:string){return this.http.get<ConfiguracionPreinscripcion>(`${this.base}/public/configuracion/${deporte}`);}
 grupos(deporte:string){return this.http.get<any[]>(`${this.base}/public/grupos/${deporte}`);}
 turnos(deporte:string){return this.http.get<any[]>(`${this.base}/public/turnos/${deporte}`);}
 crear(data:any,idempotencyKey?:string){const headers=idempotencyKey?new HttpHeaders({'Idempotency-Key':idempotencyKey}):undefined;return this.http.post<any>(this.base,data,{headers});}
 listar(filtros:Record<string,string>={},page=0,size=25){let params=new HttpParams().set('page',page).set('size',size);Object.entries(filtros).filter(([,v])=>v).forEach(([k,v])=>params=params.set(k,v));return this.http.get<{content:any[];page:number;size:number;totalElements:number;totalPages:number;first:boolean;last:boolean}>(this.base,{params});}
 /** `motivo` vacío o en blanco: el backend aplica {@link MOTIVO_CANCELACION_POR_DEFECTO}. */
 cancelar(ref:string,motivo=''){return this.http.post<void>(`${this.base}/${ref}/cancelar`,{motivo});}
 eliminar(ref:string){return this.http.delete<void>(`${this.base}/${ref}`);}
 reenviar(ref:string){return this.http.post<void>(`${this.base}/${ref}/reenviar`,{});}
 reenviarCambioTurnos(ref:string){return this.http.post<void>(`${this.base}/${ref}/reenviar-cambio-turnos`,{});}
 reenviarFinalizacion(ref:string){return this.http.post<void>(`${this.base}/${ref}/reenviar-finalizacion`,{});}
 finalizar(ref:string,data:{accionAlumno:string;alumnoId?:number;camposActualizar:string[];discapacidadHistorica:boolean|null;decisionEmail?:string;datosDeporte:any|null}){return this.http.post<void>(`${this.base}/${ref}/finalizar`,data);}
 actualizarTurnos(ref:string,turnoIds:number[]){return this.http.put<void>(`${this.base}/${ref}/turnos`,{turnoIds});}
 firma(ref:string){return this.http.get(`${this.base}/${ref}/firma`,{responseType:'blob'});}
 documentoFirmado(ref:string){return this.http.get(`${this.base}/${ref}/documento-firmado`,{responseType:'blob'});}
 plantillas(deporte:string){return this.http.get<any>(`${this.base}/plantillas/${deporte}`);}
 publicarPlantilla(deporte:string,data:any){return this.http.post<any>(`${this.base}/plantillas/${deporte}`,data);}
 previewPlantilla(deporte:string,data:any){return this.http.post(`${this.base}/plantillas/${deporte}/preview`,data,{responseType:'blob'});}
 restaurarPlantilla(deporte:string,version:number){return this.http.post<any>(`${this.base}/plantillas/${deporte}/${version}/restaurar`,{});}
}
