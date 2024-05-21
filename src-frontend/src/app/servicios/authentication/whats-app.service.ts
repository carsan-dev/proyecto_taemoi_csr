import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class WhatsAppService {

  constructor() { }

  enviarMensaje(mensaje: string, numeroDestino: string) {
    const textoMensaje = encodeURIComponent(mensaje);
    const url = `whatsapp://send?text=${textoMensaje}&phone=${numeroDestino}`;
    window.open(url, '_blank');
  }
}
