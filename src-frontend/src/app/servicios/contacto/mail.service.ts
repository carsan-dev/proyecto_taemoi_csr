import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class MailService {
  private apiUrl = 'http://localhost:8080/api/mail/enviar';

  constructor(private http: HttpClient) { }

  enviarMail(emailData: any) {
    return this.http.post(this.apiUrl, emailData);
  }
}
