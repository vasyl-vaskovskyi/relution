import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { UserMessage, problemMessage } from '../../core/errors/problem-message';

const DEFAULT_TARGET = '/search';

/** Only in-app paths; anything else (or the login page itself) goes to the search. */
export function safeReturnUrl(value: string | null): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/login')) {
    return DEFAULT_TARGET;
  }
  return value;
}

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatError,
    MatFormField,
    MatInput,
    MatLabel,
    MatProgressBar,
  ],
  templateUrl: './login.component.html',
  styles: `
    :host {
      display: flex;
      justify-content: center;
      padding-top: 48px;
    }
    mat-card {
      width: 100%;
      max-width: 400px;
    }
    form {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding-top: 16px;
    }
    .problem {
      color: var(--mat-sys-error);
      margin: 0 0 8px;
    }
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = inject(NonNullableFormBuilder).group({
    clientId: ['', Validators.required],
    clientSecret: ['', Validators.required],
  });
  protected readonly submitting = signal(false);
  protected readonly problem = signal<UserMessage | null>(null);

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const { clientId, clientSecret } = this.form.getRawValue();
    this.submitting.set(true);
    this.problem.set(null);
    this.auth.login(clientId, clientSecret).subscribe({
      next: () => {
        // Don't keep the secret in the form after a successful login
        this.form.reset();
        this.submitting.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        void this.router.navigateByUrl(safeReturnUrl(returnUrl));
      },
      error: (error: unknown) => {
        this.form.controls.clientSecret.reset();
        this.submitting.set(false);
        this.problem.set(problemMessage(error, 'token'));
      },
    });
  }
}
