import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class MailService {
  private readonly apiUrl = environment.apiUrl + '/mail/enviar';

  constructor(private readonly http: HttpClient) { }

  enviarMail(emailData: any) {
    return this.http.post(this.apiUrl, emailData);
  }
}
