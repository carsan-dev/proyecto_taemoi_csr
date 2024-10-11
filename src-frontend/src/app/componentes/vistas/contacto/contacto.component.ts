import { Component } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
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

    const numeroWhatsApp = '34695568455'; // Número en formato internacional sin '+'

    // Codificar el mensaje
    const encodedMessage = encodeURIComponent(mensajeCompleto);

    // Generar el enlace de WhatsApp
    const whatsappUrl = `https://wa.me/${numeroWhatsApp}?text=${encodedMessage}`;

    // Abrir el enlace en una nueva ventana o pestaña
    window.open(whatsappUrl, '_blank');
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
