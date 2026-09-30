import { Component, inject, signal } from '@angular/core';
import { ReceiptApi } from './receipt-api';

@Component({
  selector: 'app-root',
  standalone: true,
  template: `
    <h1>Receipt lookup</h1>
    <label>id <input [value]="id()" (input)="id.set($any($event.target).value)" /></label>
    <button type="button" (click)="load()">Load</button>
    <pre>{{ result() }}</pre>
  `,
})
export class AppComponent {
  id = signal('');
  result = signal('');

  // inject() instead of a constructor parameter: constructor DI needs decorator metadata that only the Angular
  // compiler emits; inject() works in JIT (tests) and AOT alike.
  private readonly api = inject(ReceiptApi);

  async load(): Promise<void> {
    this.result.set(await this.api.get(this.id()));
  }
}
