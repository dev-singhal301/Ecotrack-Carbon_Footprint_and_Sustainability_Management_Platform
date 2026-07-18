import { Component, computed, signal, AfterViewInit, ElementRef, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { notOnlyWhitespace } from '../validators';
import { Auth } from '../auth';

// Google Identity Services loads a global `google` object from the script
// tag in index.html — this tells TypeScript it exists, without needing an npm package.
declare const google: any;

// Replace with your actual Client ID from Google Cloud Console.
// This is safe to expose publicly — it's not a secret, unlike a client *secret*.
const GOOGLE_CLIENT_ID = '1060715159333-01t5hgjdc1ke4ltra4e4edn3562vnjot.apps.googleusercontent.com';

/**
 * Single unified authentication component. Replaces the separate Login and
 * Register components — toggling between modes is a client-side signal
 * flip, not a route change, per the design spec. Supports Login, Registration,
 * and a sliding Forgot Password dynamic view state.
 */
@Component({
  selector: 'app-auth-form',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './auth-form.html',
  styleUrl: './auth-form.css',
})
export class AuthForm implements AfterViewInit {
  @ViewChild('googleBtn') googleBtnRef!: ElementRef;
  isLoginMode = signal(true);
  isForgotPasswordMode = signal(false); // Third dynamic state state link
  isSubmitting = signal(false);
  errorMessage = signal('');
  successMessage = signal('');

  authForm: FormGroup;

  // Header rewrites dynamically across all 3 view orientations
  headerText = computed(() => {
    if (this.isForgotPasswordMode()) {
      return 'Reset Password';
    }
    return this.isLoginMode() ? 'Welcome Back' : 'Sign Up for Free';
  });

  // Action labels match processing operations exactly
  submitLabel = computed(() => {
    if (this.isSubmitting()) {
      if (this.isForgotPasswordMode()) return 'Sending request...';
      return this.isLoginMode() ? 'Logging in...' : 'Registering...';
    }
    if (this.isForgotPasswordMode()) return 'SEND RESET LINK';
    return this.isLoginMode() ? 'LOGIN' : 'GET STARTED';
  });

  constructor(private fb: FormBuilder, private auth: Auth, private router: Router) {
    this.authForm = this.fb.group({
      name: [''],
      // Enforce strict syntax formatting via Regex to completely filter out incomplete inputs
      email: ['', [
        Validators.required, 
        Validators.email,
        Validators.pattern(/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/)
      ]],
      password: ['', [Validators.required, Validators.minLength(6), notOnlyWhitespace]],
    });
    this.updateValidators();
  }

  ngAfterViewInit(): void {
    if (typeof google !== 'undefined' && google.accounts) {
      google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: (response: any) => this.handleGoogleCredentialResponse(response),
      });
      google.accounts.id.renderButton(this.googleBtnRef.nativeElement, {
        theme: 'outline',
        size: 'large',
        width: 300,
      });
    } else {
      console.error('Google Identity Services script did not load. Check index.html and your network connection.');
    }
  }

  handleGoogleCredentialResponse(response: any): void {
    this.errorMessage.set('');
    this.successMessage.set('');
    this.isSubmitting.set(true);

    this.auth.googleLogin(response.credential).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.router.navigate(['/home']);
      },
      error: (err) => {
        this.isSubmitting.set(false);
        const msg = err.error?.message || err.message || 'Google sign-in failed. Please try again.';
        this.errorMessage.set(msg);
      },
    });
  }

  /**
   * Flips between Sign Up and Log In styles. Clears the form values and resets 
   * custom flags so components scale cleanly.
   */
  switchMode(toLogin: boolean): void {
    if (this.isLoginMode() === toLogin && !this.isForgotPasswordMode()) {
      return;
    }
    this.isForgotPasswordMode.set(false); // Pull out of password recovery mode if moving models
    this.isLoginMode.set(toLogin);
    this.errorMessage.set('');
    this.successMessage.set('');
    this.authForm.reset();
    this.updateValidators();
  }

  /**
   * Controls the in-place slide state transition overlay to handle forgot password logic
   */
  toggleForgotPassword(show: boolean): void {
    this.isForgotPasswordMode.set(show);
    this.errorMessage.set('');
    this.successMessage.set('');
    
    // Clear the validators and reset fields safely
    this.authForm.get('name')?.setValue('');
    this.authForm.get('password')?.setValue('');
    this.updateValidators();
  }

  private updateValidators(): void {
    const nameControl = this.authForm.get('name');
    const passwordControl = this.authForm.get('password');

    // If password state is active, only email is validated
    if (this.isForgotPasswordMode()) {
      nameControl?.clearValidators();
      passwordControl?.clearValidators();
    } else {
      // Standard auth state configurations
      passwordControl?.setValidators([Validators.required, Validators.minLength(6), notOnlyWhitespace]);
      if (this.isLoginMode()) {
        nameControl?.clearValidators();
      } else {
        nameControl?.setValidators([Validators.required, Validators.minLength(2)]);
      }
    }

    nameControl?.updateValueAndValidity();
    passwordControl?.updateValueAndValidity();
  }

  /**
   * Separate handler method dedicated to sending the validated password recovery payload
   */
  handleForgotPasswordRequest(): void {
    const emailControl = this.authForm.get('email');
    
    if (emailControl?.invalid) {
      emailControl.markAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');

    const email = emailControl?.value;
    
    // Ensure you add forgotPassword(email) to your frontend Auth file
    this.auth.forgotPassword(email).subscribe({
      next: (res: any) => {
        this.isSubmitting.set(false);
        this.successMessage.set(res?.message || 'A secure password recovery link has been generated.');
      },
      error: (err) => {
        this.isSubmitting.set(false);
        const msg = err.error?.message || err.message || 'Recovery attempt failed. Please check the email entered.';
        this.errorMessage.set(msg);
      }
    });
  }

  onSubmit(): void {
    if (this.isForgotPasswordMode()) {
      this.handleForgotPasswordRequest();
      return;
    }

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
          const msg = err.error?.message || err.message || 'Login failed. Check your credentials.';
          this.errorMessage.set(msg);
        },
      });
    } else {
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
          const msg = err.error?.message || err.message || 'Registration failed.';
          this.errorMessage.set(msg);
        },
      });
    }
  }
}