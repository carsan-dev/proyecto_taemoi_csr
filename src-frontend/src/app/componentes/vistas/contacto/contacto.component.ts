import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WhatsAppService } from '../../../servicios/authentication/whats-app.service';

@Component({
  selector: 'app-contacto',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './contacto.component.html',
  styleUrl: './contacto.component.scss'
})
export class ContactoComponent {
  nombre: string = '';
  apellidos: string = '';
  asunto: string = '';
  mensaje: string = '';

  constructor(private whatsappService: WhatsAppService) { }

  enviarWhatsApp() {
    const numeroWhatsApp = '34625752354';
    const nombreCompleto = `${this.nombre} ${this.apellidos}`;
    const mensajeCompleto = `Nombre completo: ${nombreCompleto}\nAsunto: ${this.asunto}\nCuerpo del mensaje: ${this.mensaje}`;
    this.whatsappService.enviarMensaje(mensajeCompleto, numeroWhatsApp);
  }
}
