import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class WhatsAppService {

  constructor() { }

  enviarMensaje(mensaje: string, numeroDestino: string) {
    const texto = encodeURIComponent(mensaje);
    const url = `https://api.whatsapp.com/send?phone=${numeroDestino}&text=${texto}`;
    window.open(url, '_blank');
  }
}
