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
    const numeroWhatsApp = '34695568455';
    const { nombre, apellidos, email, asunto, mensaje } =
      this.contactForm.value;
    const nombreCompleto = `${nombre} ${apellidos}`;
    const mensajeCompleto = `Nombre completo: ${nombreCompleto}\nCorreo electrónico: ${email}\nAsunto: ${asunto}\nCuerpo del mensaje: ${mensaje}`;
    this.whatsappService.enviarMensaje(mensajeCompleto, numeroWhatsApp);
    this.limpiarCampos();
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
