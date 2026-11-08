import { provideHttpClient, withFetch } from '@angular/common/http';
import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';

bootstrapApplication(App, { providers: [provideHttpClient(withFetch())] }).catch((error) => console.error(error));
