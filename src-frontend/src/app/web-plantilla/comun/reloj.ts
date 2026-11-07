import { InjectionToken } from '@angular/core';

/** La hora de ahora para la web por plantilla (próxima clase, año del pie). Los specs la fijan. */
export const RELOJ_WEB = new InjectionToken<() => Date>('RELOJ_WEB', {
  providedIn: 'root',
  factory: () => () => new Date(),
});
