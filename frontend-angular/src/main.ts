import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app.component';
import { ReceiptApi } from './receipt-api';

bootstrapApplication(AppComponent, {
  providers: [{ provide: ReceiptApi, useValue: new ReceiptApi('http://localhost:8090') }],
});
