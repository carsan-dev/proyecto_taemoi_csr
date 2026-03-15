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
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-contacto',
  standalone: true,
  imports: [FormsModule, NgxSpinnerModule, CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './contacto.component.html',
  styleUrl: './contacto.component.scss',
})
export class ContactoComponent {
  contactForm: FormGroup;
  readonly deportesDisponibles = [
    'Taekwondo',
    'Kickboxing Light',
    'Pilates Balance',
    'Defensa Personal Femenina',
  ];

  constructor(
    private readonly fb: FormBuilder,
    private readonly mailService: MailService,
    private readonly spinner: NgxSpinnerService
  ) {
    this.contactForm = this.fb.group({
      nombre: ['', Validators.required],
      apellidos: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      asunto: ['', Validators.required],
      deportes: [[], Validators.required],
      mensaje: ['', Validators.required],
      consentimiento: [false, Validators.requiredTrue],
    });
  }

  private obtenerDeportesSeleccionados(): string[] {
    return (this.contactForm.get('deportes')?.value ?? []) as string[];
  }

  toggleDeporte(deporte: string) {
    const deportes = this.obtenerDeportesSeleccionados();
    const existe = deportes.includes(deporte);
    const nuevosDeportes = existe
      ? deportes.filter((item) => item !== deporte)
      : [...deportes, deporte];

    this.contactForm.get('deportes')?.setValue(nuevosDeportes);
    this.contactForm.get('deportes')?.markAsTouched();
  }

  deporteSeleccionado(deporte: string): boolean {
    return this.obtenerDeportesSeleccionados().includes(deporte);
  }

  private formatearDeportes(deportes: string[]): string {
    if (!deportes || deportes.length === 0) {
      return 'No especificado';
    }

    return deportes.join(', ');
  }

  private construirMensajeConDeportes(mensaje: string, deportes: string[]): string {
    const deportesTexto = this.formatearDeportes(deportes);
    return `${mensaje}\nDeportes de interes: ${deportesTexto}`;
  }

  enviarWhatsApp() {
    if (this.contactForm.invalid) {
      this.contactForm.markAllAsTouched();
      return;
    }

    const { nombre, apellidos, email, asunto, mensaje } = this.contactForm.value;
    const nombreCompleto = `${nombre} ${apellidos}`;
    const deportesSeleccionados = this.obtenerDeportesSeleccionados();
    const deportesTexto = this.formatearDeportes(deportesSeleccionados);
    const mensajeCompleto = `Nombre completo: ${nombreCompleto}\nCorreo electrónico: ${email}\nAsunto: ${asunto}\nDeportes de interes: ${deportesTexto}\nCuerpo del mensaje: ${mensaje}`;

    const numeroWhatsApp = '34625752354'; // Número en formato internacional sin '+'

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

    const { nombre, apellidos, email, asunto, mensaje } = this.contactForm.value;
    const deportesSeleccionados = this.obtenerDeportesSeleccionados();
    const mensajeConDeportes = this.construirMensajeConDeportes(mensaje, deportesSeleccionados);
    const emailData = {
      nombre,
      apellidos,
      email,
      asunto,
      mensaje: mensajeConDeportes,
    };

    this.spinner.show();

    this.mailService.enviarMail(emailData).subscribe({
      next: () => {
        this.spinner.hide();
        Swal.fire({
          title: 'Correo enviado',
          text: '¡El correo ha sido enviado correctamente!.',
          icon: 'success',
          timer: 2000,
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
    this.contactForm.reset({
      nombre: '',
      apellidos: '',
      email: '',
      asunto: '',
      deportes: [],
      mensaje: '',
      consentimiento: false,
    });
  }

  onSubmit() {
    this.enviarCorreo();
  }
}
