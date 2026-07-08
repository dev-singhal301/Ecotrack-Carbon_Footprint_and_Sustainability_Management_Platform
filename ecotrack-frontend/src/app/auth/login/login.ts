import { Component, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { notOnlyWhitespace } from '../validators';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  loginForm: FormGroup;
  isSubmitting = signal(false);

  constructor(private fb: FormBuilder) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6), notOnlyWhitespace]],
    });
  }

  onSubmit() {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched(); // shows errors even if user never touched a field
      return;
    }
    this.isSubmitting.set(true);
    console.log('Login form value:', this.loginForm.value);
    // No backend call yet — Person A's endpoints aren't ready.

    // Placeholder — remove this setTimeout once auth.ts is wired to the real API.
    // Right now it just simulates a request finishing after 1 second,
    // so you can see the disabled state working.
    setTimeout(() => {
      this.isSubmitting.set(false);
    }, 1000);
  }
}