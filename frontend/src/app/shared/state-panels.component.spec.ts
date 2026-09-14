import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAppIcons } from '../core/icons/app-icons';
import { LoadingPanelComponent, ProblemPanelComponent } from './state-panels.component';

describe('state panels', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideAppIcons()],
    });
  });

  describe('LoadingPanelComponent', () => {
    it('shows a progress bar without the slow hint at first', async () => {
      const fixture = TestBed.createComponent(LoadingPanelComponent);
      await fixture.whenStable();
      const element = fixture.nativeElement as HTMLElement;

      expect(element.querySelector('mat-progress-bar[aria-label="Loading"]')).not.toBeNull();
      expect(element.textContent).not.toContain('Still loading');
    });

    it('shows the slow hint when slow', async () => {
      const fixture = TestBed.createComponent(LoadingPanelComponent);
      fixture.componentRef.setInput('slow', true);
      await fixture.whenStable();

      expect((fixture.nativeElement as HTMLElement).textContent).toContain(
        'Still loading, the App Store is slow',
      );
    });
  });

  describe('ProblemPanelComponent', () => {
    it('shows the message, the field details and the reference as an alert', async () => {
      const fixture = TestBed.createComponent(ProblemPanelComponent);
      fixture.componentRef.setInput('message', {
        message: 'Please check your input.',
        details: ['cc: must be two letters', 'limit: must be at most 50'],
        correlationId: 'abc-123',
      });
      await fixture.whenStable();
      const alert = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');

      expect(alert?.querySelector('.message')?.textContent).toBe('Please check your input.');
      expect([...(alert?.querySelectorAll('li') ?? [])].map((li) => li.textContent)).toEqual([
        'cc: must be two letters',
        'limit: must be at most 50',
      ]);
      expect(alert?.textContent).toContain('Reference: abc-123');
    });

    it('shows neither a list nor a reference when there are none', async () => {
      const fixture = TestBed.createComponent(ProblemPanelComponent);
      fixture.componentRef.setInput('message', {
        message: 'Cannot reach the server.',
        details: [],
      });
      await fixture.whenStable();
      const element = fixture.nativeElement as HTMLElement;

      expect(element.querySelector('ul')).toBeNull();
      expect(element.textContent).not.toContain('Reference');
    });

    it('emits retry when the Retry button is clicked', async () => {
      const fixture = TestBed.createComponent(ProblemPanelComponent);
      fixture.componentRef.setInput('message', {
        message: 'Cannot reach the server.',
        details: [],
      });
      await fixture.whenStable();
      const retry = vi.fn();
      fixture.componentInstance.retry.subscribe(retry);

      const button = (fixture.nativeElement as HTMLElement).querySelector('button');
      expect(button?.textContent).toContain('Retry');
      button?.click();

      expect(retry).toHaveBeenCalledTimes(1);
    });
  });
});
