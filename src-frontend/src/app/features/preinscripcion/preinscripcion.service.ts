import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Injectable({providedIn:'root'})
export class PreinscripcionService {
 private readonly base=`${environment.apiUrl}/preinscripciones`;
 constructor(private readonly http:HttpClient){}
 temporada(){return this.http.get<{temporada:string}>(`${this.base}/public/temporada`);}
 configuracion(deporte:string){return this.http.get<any>(`${this.base}/public/configuracion/${deporte}`);}
 grupos(deporte:string){return this.http.get<any[]>(`${this.base}/public/grupos/${deporte}`);}
 turnos(deporte:string){return this.http.get<any[]>(`${this.base}/public/turnos/${deporte}`);}
 crear(data:any){return this.http.post<any>(this.base,data);}
 listar(filtros:Record<string,string>={}){let params=new HttpParams();Object.entries(filtros).filter(([,v])=>v).forEach(([k,v])=>params=params.set(k,v));return this.http.get<any[]>(this.base,{params});}
 cancelar(ref:string){return this.http.post<void>(`${this.base}/${ref}/cancelar`,{});}
 reenviar(ref:string){return this.http.post<void>(`${this.base}/${ref}/reenviar`,{});}
 finalizar(ref:string,alumnoId:number){return this.http.post<void>(`${this.base}/${ref}/finalizar`,{alumnoId,actualizarDatos:true,reactivar:true});}
}
