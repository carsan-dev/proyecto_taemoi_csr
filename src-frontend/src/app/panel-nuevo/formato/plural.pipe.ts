import { Pipe, PipeTransform } from '@angular/core';
import { plural } from './formato-es';

@Pipe({ name: 'plural', standalone: true })
export class PluralPipe implements PipeTransform {
  transform(cantidad: number, singular: string, pluralTexto: string): string {
    return plural(cantidad, singular, pluralTexto);
  }
}
