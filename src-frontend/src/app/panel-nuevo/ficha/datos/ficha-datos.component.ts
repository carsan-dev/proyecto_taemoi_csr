import { NgTemplateOutlet } from '@angular/common';
import { Component, Input } from '@angular/core';

import { fecha } from '../../formato/formato-es';
import { fechaParaCampo } from '../ficha-vista';
import type { FichaAlumnoComponent } from '../ficha-alumno.component';

/** Un campo de la edición en línea de hoy: id (el de hoy), etiqueta, tipo y si es obligatorio. */
interface Campo {
  id: string;
  etiqueta: string;
  tipo: 'text' | 'date' | 'email' | 'tel';
  obligatorio: boolean;
  marcador?: string;
  maximo?: number;
}

const PERSONALES: Campo[] = [
  { id: 'nombre', etiqueta: 'Nombre', tipo: 'text', obligatorio: true, marcador: 'Nombre del alumno' },
  { id: 'apellidos', etiqueta: 'Apellidos', tipo: 'text', obligatorio: true, marcador: 'Apellidos del alumno' },
  { id: 'direccion', etiqueta: 'Dirección', tipo: 'text', obligatorio: true, marcador: 'Dirección completa' },
  { id: 'fechaNacimiento', etiqueta: 'Fecha de nacimiento', tipo: 'date', obligatorio: true },
  { id: 'nif', etiqueta: 'DNI', tipo: 'text', obligatorio: false, marcador: '12345678A (opcional)' },
];

const CONTACTO: Campo[] = [
  { id: 'email', etiqueta: 'Email', tipo: 'email', obligatorio: true, marcador: 'correo@ejemplo.com' },
  { id: 'telefono', etiqueta: 'Teléfono principal', tipo: 'tel', obligatorio: true, marcador: '123456789' },
  { id: 'telefono2', etiqueta: 'Teléfono secundario', tipo: 'tel', obligatorio: false, marcador: '123456789' },
  { id: 'responsableLegalNombre', etiqueta: 'Responsable legal', tipo: 'text', obligatorio: false, marcador: 'Nombre y apellidos', maximo: 180 },
  { id: 'responsableLegalNif', etiqueta: 'DNI/NIE del responsable', tipo: 'text', obligatorio: false, marcador: '12345678Z', maximo: 9 },
];

/**
 * Pestaña «Datos y contacto» de la ficha nueva (8b-3b): información personal (con «Editar» y la edición en línea de hoy)
 * y contacto con el responsable legal. Los cambios, las validaciones y el guardado son los de la ficha de hoy
 * (`onBasicInfoChange`, `applyBasicInfoChanges`, `cancelBasicInfoChanges`); la barra de guardar está en la ficha.
 */
@Component({
  selector: 'app-ficha-datos',
  standalone: true,
  imports: [NgTemplateOutlet],
  templateUrl: './ficha-datos.component.html',
  styleUrls: ['./ficha-datos.component.scss'],
})
export class FichaDatosComponent {
  @Input({ required: true }) ficha!: FichaAlumnoComponent;

  readonly personales = PERSONALES;
  readonly contacto = CONTACTO;

  /** Valor del campo en edición: lo pendiente o lo guardado (la fecha, como hoy, del alumno en «yyyy-MM-dd»). */
  valor(campo: Campo): string {
    if (campo.id === 'fechaNacimiento') {
      const pendiente = this.ficha.pendingBasicInfoChanges.fechaNacimiento;
      return pendiente ?? fechaParaCampo(this.ficha.alumno?.fechaNacimiento);
    }
    return this.ficha.getDisplayedBasicInfo(campo.id) ?? '';
  }

  /** Lectura: fechas es-ES; vacío, «No establecido». */
  lectura(campo: Campo): string {
    const valor = this.ficha.alumno?.[campo.id];
    if (valor === null || valor === undefined || valor === '') {
      return '';
    }
    return campo.tipo === 'date' ? fecha(valor) : String(valor);
  }

  enlace(campo: Campo): string | null {
    const valor = this.lectura(campo);
    if (!valor) {
      return null;
    }
    if (campo.tipo === 'email') {
      return `mailto:${valor}`;
    }
    return campo.tipo === 'tel' ? `tel:${valor}` : null;
  }
}
