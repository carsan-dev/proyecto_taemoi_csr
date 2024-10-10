import { Component } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { WhatsAppService } from '../../../servicios/contacto/whats-app.service';
import { MailService } from '../../../servicios/contacto/mail.service';
import Swal from 'sweetalert2';
import { NgxSpinnerModule, NgxSpinnerService } from 'ngx-spinner';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-contacto',
  standalone: true,
  imports: [FormsModule, NgxSpinnerModule, CommonModule, ReactiveFormsModule],
  templateUrl: './contacto.component.html',
  styleUrl: './contacto.component.scss',
})
export class ContactoComponent {
  contactForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    private whatsappService: WhatsAppService,
    private mailService: MailService,
    private spinner: NgxSpinnerService
  ) {
    this.contactForm = this.fb.group({
      nombre: ['', Validators.required],
      apellidos: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      asunto: ['', Validators.required],
      mensaje: ['', Validators.required],
    });
  }

  enviarWhatsApp() {
    if (this.contactForm.invalid) {
      this.contactForm.markAllAsTouched();
      return;
    }
  
    const { nombre, apellidos, email, asunto, mensaje } = this.contactForm.value;
    const nombreCompleto = `${nombre} ${apellidos}`;
    const mensajeCompleto = `Nombre completo: ${nombreCompleto}\nCorreo electrónico: ${email}\nAsunto: ${asunto}\nCuerpo del mensaje: ${mensaje}`;
  
    const numeroWhatsApp = '34695568455'; // Asegúrate de que el número esté en formato internacional sin el '+'
  
    this.spinner.show();
  
    this.whatsappService.enviarMensaje(mensajeCompleto, numeroWhatsApp).subscribe({
      next: () => {
        this.spinner.hide();
        Swal.fire({
          title: 'Mensaje enviado',
          text: '¡El mensaje de WhatsApp ha sido enviado correctamente!',
          icon: 'success',
        });
        this.limpiarCampos();
      },
      error: (error) => {
        this.spinner.hide();
        Swal.fire({
          title: 'Error al enviar mensaje',
          text: 'Ha ocurrido un error al intentar enviar el mensaje de WhatsApp.',
          icon: 'error',
        });
        console.error('Error al enviar WhatsApp:', error);
      }
    });
  }

  enviarCorreo() {
    if (this.contactForm.invalid) {
      this.contactForm.markAllAsTouched();
      return;
    }

    const emailData = this.contactForm.value;

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
    this.contactForm.reset();
  }

  onSubmit() {
    this.enviarCorreo();
  }
}
