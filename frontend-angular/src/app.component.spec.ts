// @vitest-environment jsdom
import '@angular/compiler'; // JIT: compile the inline template at test time (no Angular CLI in this lab)
import { provideExperimentalZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BrowserDynamicTestingModule, platformBrowserDynamicTesting } from '@angular/platform-browser-dynamic/testing';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { AppComponent } from './app.component';
import { ReceiptApi } from './receipt-api';

describe('AppComponent', () => {
  beforeAll(() => TestBed.initTestEnvironment(BrowserDynamicTestingModule, platformBrowserDynamicTesting()));

  it('looks up a receipt by the typed id and renders it', async () => {
    const get = vi.fn().mockResolvedValue('{ "payer": "deepak" }');
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideExperimentalZonelessChangeDetection(), { provide: ReceiptApi, useValue: { get } }],
    });
    const fixture = TestBed.createComponent(AppComponent);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    const input = el.querySelector('input')!;
    input.value = 'r-42';
    input.dispatchEvent(new Event('input'));
    el.querySelector('button')!.click();
    await vi.waitFor(() => expect(get).toHaveBeenCalledWith('r-42'));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.querySelector('pre')!.textContent).toContain('deepak');
  });
});
