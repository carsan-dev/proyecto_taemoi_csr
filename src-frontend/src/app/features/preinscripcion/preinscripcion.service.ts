import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
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

@Injectable({providedIn:'root'})
export class PreinscripcionService {
 private readonly base=`${environment.apiUrl}/preinscripciones`;
 constructor(private readonly http:HttpClient){}
 temporada(){return this.http.get<{temporada:string}>(`${this.base}/public/temporada`);}
 configuracion(deporte:string){return this.http.get<ConfiguracionPreinscripcion>(`${this.base}/public/configuracion/${deporte}`);}
 grupos(deporte:string){return this.http.get<any[]>(`${this.base}/public/grupos/${deporte}`);}
 turnos(deporte:string){return this.http.get<any[]>(`${this.base}/public/turnos/${deporte}`);}
 crear(data:any){return this.http.post<any>(this.base,data);}
 listar(filtros:Record<string,string>={},page=0,size=25){let params=new HttpParams().set('page',page).set('size',size);Object.entries(filtros).filter(([,v])=>v).forEach(([k,v])=>params=params.set(k,v));return this.http.get<{content:any[];page:number;size:number;totalElements:number;totalPages:number;first:boolean;last:boolean}>(this.base,{params});}
 cancelar(ref:string){return this.http.post<void>(`${this.base}/${ref}/cancelar`,{});}
 eliminar(ref:string){return this.http.delete<void>(`${this.base}/${ref}`);}
 reenviar(ref:string){return this.http.post<void>(`${this.base}/${ref}/reenviar`,{});}
 reenviarFinalizacion(ref:string){return this.http.post<void>(`${this.base}/${ref}/reenviar-finalizacion`,{});}
 finalizar(ref:string,data:{accionAlumno:string;alumnoId?:number;camposActualizar:string[];discapacidadHistorica:boolean|null;decisionEmail?:string;datosDeporte:any|null}){return this.http.post<void>(`${this.base}/${ref}/finalizar`,data);}
 firma(ref:string){return this.http.get(`${this.base}/${ref}/firma`,{responseType:'blob'});}
 documentoFirmado(ref:string){return this.http.get(`${this.base}/${ref}/documento-firmado`,{responseType:'blob'});}
 plantillas(deporte:string){return this.http.get<any>(`${this.base}/plantillas/${deporte}`);}
 publicarPlantilla(deporte:string,data:any){return this.http.post<any>(`${this.base}/plantillas/${deporte}`,data);}
 previewPlantilla(deporte:string,data:any){return this.http.post(`${this.base}/plantillas/${deporte}/preview`,data,{responseType:'blob'});}
 restaurarPlantilla(deporte:string,version:number){return this.http.post<any>(`${this.base}/plantillas/${deporte}/${version}/restaurar`,{});}
}
