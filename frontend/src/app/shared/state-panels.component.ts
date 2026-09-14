import { Component, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { UserMessage } from '../core/errors/problem-message';

/** Loading state shared by search and details: a progress bar, plus a hint after 3 s. */
@Component({
  selector: 'app-loading-panel',
  imports: [MatProgressBar],
  template: `
    <mat-progress-bar mode="indeterminate" aria-label="Loading" />
    @if (slow()) {
      <p class="hint">Still loading, the App Store is slow</p>
    }
  `,
  styles: `
    :host {
      display: block;
      margin: 16px 0;
    }
    .hint {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class LoadingPanelComponent {
  readonly slow = input(false);
}

/** Error state shared by search and details: the mapped message and a Retry button. */
@Component({
  selector: 'app-problem-panel',
  imports: [MatButton, MatIcon],
  template: `
    <div class="problem" role="alert">
      <mat-icon svgIcon="error" />
      <div class="text">
        <p class="message">{{ message().message }}</p>
        @if (message().details.length > 0) {
          <ul>
            @for (detail of message().details; track $index) {
              <li>{{ detail }}</li>
            }
          </ul>
        }
        @if (message().correlationId; as correlationId) {
          <p class="reference">Reference: {{ correlationId }}</p>
        }
      </div>
    </div>
    <button mat-stroked-button type="button" (click)="retry.emit()">
      <mat-icon svgIcon="refresh" />
      Retry
    </button>
  `,
  styles: `
    :host {
      display: block;
      margin: 16px 0;
    }
    .problem {
      display: flex;
      gap: 8px;
      color: var(--mat-sys-error);
    }
    .message {
      margin: 0 0 4px;
    }
    .reference {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class ProblemPanelComponent {
  readonly message = input.required<UserMessage>();
  readonly retry = output<void>();
}
