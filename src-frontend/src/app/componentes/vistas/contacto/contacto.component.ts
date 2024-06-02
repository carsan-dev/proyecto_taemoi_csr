import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WhatsAppService } from '../../../servicios/contacto/whats-app.service';
import { MailService } from '../../../servicios/contacto/mail.service';
import Swal from 'sweetalert2';
import { NgxSpinnerModule, NgxSpinnerService } from 'ngx-spinner';

@Component({
  selector: 'app-contacto',
  standalone: true,
  imports: [FormsModule, NgxSpinnerModule],
  templateUrl: './contacto.component.html',
  styleUrl: './contacto.component.scss'
})
export class ContactoComponent {
  nombre: string = '';
  apellidos: string = '';
  email: string = '';
  asunto: string = '';
  mensaje: string = '';

  constructor(private whatsappService: WhatsAppService, private mailService: MailService, private spinner: NgxSpinnerService) { }

  enviarWhatsApp() {
    const numeroWhatsApp = '34625752354';
    const nombreCompleto = `${this.nombre} ${this.apellidos}`;
    const mensajeCompleto = `Nombre completo: ${nombreCompleto}\nCorreo electrónico: ${this.email}\nAsunto: ${this.asunto}\nCuerpo del mensaje: ${this.mensaje}`;
    this.whatsappService.enviarMensaje(mensajeCompleto, numeroWhatsApp);
    this.limpiarCampos();
  }

  enviarCorreo() {
    const emailData = {
      nombre: this.nombre,
      apellidos: this.apellidos,
      email: this.email,
      asunto: this.asunto,
      mensaje: this.mensaje
    };

    this.spinner.show();

    this.mailService.enviarMail(emailData).subscribe({
      next: () => {
        this.spinner.hide();
        Swal.fire({
          title: 'Correo enviado',
          text: '¡El correo ha sido enviado correctamente!.',
          icon: 'success',
        });
        this.limpiarCampos();
      },
      error: () => {
        this.spinner.hide();
        Swal.fire({
          title: 'Error al enviar correo',
          text: 'Ha ocurrido un error al intentar enviar el correo.',
          icon: 'error',
        });
      },
    });
  }

  limpiarCampos() {
    this.nombre = '';
    this.apellidos = '';
    this.email = '';
    this.asunto = '';
    this.mensaje = '';
  }
}
