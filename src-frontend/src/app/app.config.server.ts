import { ApplicationConfig, importProvidersFrom } from '@angular/core';
import { provideServerRendering } from '@angular/platform-server';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { routes } from './app.routes';
import { NgxSpinnerModule } from 'ngx-spinner';

export const config: ApplicationConfig = {
  providers: [
    provideServerRendering(),
    provideRouter(routes),
    provideHttpClient(withFetch()),
    provideNoopAnimations(),
    importProvidersFrom(NgxSpinnerModule.forRoot({ type: 'ball-clip-rotate' })),
  ],
};
