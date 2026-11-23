import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { textoApto, textoListo } from './rallas-textos';

/**
 * Distintivo «✓ Listo/Lista para examen» (decisiones 510 y 513; boceto `boceto-rallas-cinturon.html`): con las 5
 * rallas, en la línea del grado. Pastilla acento-suave con filete acento-borde y texto en acento.
 */
@Component({
  selector: 'pn-distintivo-examen',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
    stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 5 5 9-10" /></svg>{{ texto() }}`,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      min-height: 22px;
      padding: 0 8px;
      border-radius: var(--pn-radio-pastilla);
      background: var(--pn-acento-suave);
      box-shadow: inset 0 0 0 1px var(--pn-acento-borde);
      color: var(--pn-acento);
      font: var(--pn-etiqueta);
      white-space: nowrap;
    }

    svg {
      flex: none;
      width: 13px;
      height: 13px;
    }
  `,
})
export class DistintivoExamenComponent {
  /** `FEMENINO` → «Lista»; cualquier otro valor o sin dato → «Listo». */
  readonly genero = input<string | null | undefined>(null);
  /** Ya es apto en la disciplina: se muestra solo «Apto/Apta»; «Listo/Lista» es únicamente para quien tiene las 10 medias y no es apto. */
  readonly apto = input(false);
  protected readonly texto = computed(() => {
    if (!this.apto()) return textoListo(this.genero());
    const t = textoApto(this.genero());
    return t.charAt(0).toUpperCase() + t.slice(1);
  });
}
