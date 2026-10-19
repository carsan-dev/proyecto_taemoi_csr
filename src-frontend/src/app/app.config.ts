import { ApplicationConfig, ErrorHandler, importProvidersFrom } from '@angular/core';
import { provideRouter, withInMemoryScrolling, withNavigationErrorHandler } from '@angular/router';

import { routes } from './app.routes';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { NgxSpinnerModule } from 'ngx-spinner';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { credentialsInterceptor } from './interceptors/credentials.interceptor';
import { loadingInterceptor } from './interceptors/loading.interceptor';
import { RecargaPorChunkErrorHandler, esErrorDeCargaDeChunk, recargarPaginaPorChunk } from './core/recarga-por-chunk';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(
      routes,
      // RouteScrollCoordinator owns all page-level restoration. Keep Angular's
      // Scroll events enabled while disabling its competing DOM writes.
      withInMemoryScrolling({
        scrollPositionRestoration: 'disabled',
        anchorScrolling: 'disabled',
      }),
      // Decisión 173: un chunk perezoso que ya no existe tras un despliegue recarga la página una vez.
      withNavigationErrorHandler((error) => {
        if (esErrorDeCargaDeChunk(error.error)) {
          recargarPaginaPorChunk();
        }
      })
    ),
    { provide: ErrorHandler, useFactory: () => new RecargaPorChunkErrorHandler() },
    provideHttpClient(withFetch(), withInterceptors([credentialsInterceptor, loadingInterceptor, errorInterceptor])),
    provideAnimationsAsync(),
    importProvidersFrom(NgxSpinnerModule.forRoot({ type: 'ball-clip-rotate' })),
  ],
};
