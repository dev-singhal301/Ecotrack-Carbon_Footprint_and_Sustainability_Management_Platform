import { Component, computed, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { notOnlyWhitespace } from '../validators';
import { Auth } from '../auth';

/**
 * Single unified authentication component. Replaces the separate Login and
 * Register components — toggling between the two is a client-side signal
 * flip (isLoginMode), not a route change, per the design spec.
 */
@Component({
  selector: 'app-auth-form',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './auth-form.html',
  styleUrl: './auth-form.css',
})
export class AuthForm {
  isLoginMode = signal(true);
  isSubmitting = signal(false);
  errorMessage = signal('');
  successMessage = signal('');

  authForm: FormGroup;

  headerText = computed(() => (this.isLoginMode() ? 'Welcome Back' : 'Sign Up for Free'));

  submitLabel = computed(() => {
    if (this.isSubmitting()) {
      return this.isLoginMode() ? 'Logging in...' : 'Registering...';
    }
    return this.isLoginMode() ? 'LOGIN' : 'GET STARTED';
  });

  constructor(private fb: FormBuilder, private auth: Auth, private router: Router) {
    // 'name' starts with no validators since the default mode is Log In.
    // updateValidators() below applies the right rules for the starting mode.
    this.authForm = this.fb.group({
      name: [''],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6), notOnlyWhitespace]],
    });
    this.updateValidators();
  }

  /**
   * Flips between Sign Up and Log In. Clears the form and re-applies
   * validators so the hidden field (name, in Log In mode) never blocks
   * submission — per spec section 4.
   */
  switchMode(toLogin: boolean): void {
    if (this.isLoginMode() === toLogin) {
      return;
    }
    this.isLoginMode.set(toLogin);
    this.errorMessage.set('');
    this.successMessage.set('');
    this.authForm.reset();
    this.updateValidators();
  }

  private updateValidators(): void {
    const nameControl = this.authForm.get('name');
    if (this.isLoginMode()) {
      nameControl?.clearValidators();
    } else {
      nameControl?.setValidators([Validators.required, Validators.minLength(2)]);
    }
    nameControl?.updateValueAndValidity();
  }

  onSubmit(): void {
    this.errorMessage.set('');
    this.successMessage.set('');

    if (this.authForm.invalid) {
      this.authForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);

    if (this.isLoginMode()) {
      const { email, password } = this.authForm.value;
      this.auth.login({ email, password }).subscribe({
        next: () => {
          this.isSubmitting.set(false);
          this.router.navigate(['/home']);
        },
        error: (err) => {
          this.isSubmitting.set(false);
          // FIXED: Use err.message fallback just like the old login.ts
          const msg = err.error?.message || err.message || 'Login failed. Check your credentials.';
          this.errorMessage.set(msg);
        },
      });
    } else {
      // For registration, pass the values clearly matching the old structure
      const { name, email, password } = this.authForm.value;
      this.auth.register({ name, email, password }).subscribe({
        next: () => {
          this.isSubmitting.set(false);
          this.switchMode(true);
          this.authForm.patchValue({ email });
          this.successMessage.set('Account created. Please log in.');
        },
        error: (err) => {
          this.isSubmitting.set(false);
          // FIXED: Use err.message fallback just like old register.ts
          const msg = err.error?.message || err.message || 'Registration failed.';
          this.errorMessage.set(msg);
        },
      });
    }
  }
}