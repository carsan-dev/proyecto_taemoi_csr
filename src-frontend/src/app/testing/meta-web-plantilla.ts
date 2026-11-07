/**
 * Pone en la cabeza del documento de Karma la meta de `index-plantilla.html` (fase4-web-plan.md §3.2) y devuelve la
 * función que la quita. Cada spec la quita en su `afterEach`: la meta no puede quedarse para los demás specs.
 */
export function ponerMetaWebPlantilla(content = 'plantilla'): () => void {
  const meta = document.createElement('meta');
  meta.setAttribute('name', 'taemoi-web');
  meta.setAttribute('content', content);
  document.head.appendChild(meta);
  return () => meta.remove();
}

/** Ningún spec deja la meta puesta. */
export function quedaMetaWebPlantilla(): boolean {
  return document.head.querySelector('meta[name="taemoi-web"]') !== null;
}
