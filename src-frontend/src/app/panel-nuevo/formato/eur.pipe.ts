import { Pipe, PipeTransform } from '@angular/core';
import { eur } from './formato-es';

@Pipe({ name: 'eur', standalone: true })
export class EurPipe implements PipeTransform {
  transform(valor: number | null | undefined): string {
    return eur(valor);
  }
}
