import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class WhatsAppService {
  private apiUrl = 'https://graph.facebook.com/v17.0/'; // Versión de la API
  private phoneNumberId = 'TU_PHONE_NUMBER_ID'; // Reemplaza con tu Phone Number ID
  private accessToken = 'TU_ACCESS_TOKEN'; // Reemplaza con tu Access Token

  constructor(private http: HttpClient) { }

  enviarMensaje(mensaje: string, numeroDestino: string) {
    const url = `${this.apiUrl}${this.phoneNumberId}/messages`;

    const headers = new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${this.accessToken}`
    });

    const body = {
      messaging_product: 'whatsapp',
      to: numeroDestino,
      type: 'text',
      text: {
        body: mensaje
      }
    };

    return this.http.post(url, body, { headers });
  }
}
