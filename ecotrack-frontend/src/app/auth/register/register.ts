import { Component, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { notOnlyWhitespace } from '../validators';
import { Auth } from '../auth';
import { Router } from '@angular/router';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  registerForm: FormGroup;
  isSubmitting = signal(false);
  errorMessage = '';

  constructor(private fb: FormBuilder, private auth: Auth, private router: Router) {
    this.registerForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6), notOnlyWhitespace]],
    });
  }

  onSubmit() {
    this.errorMessage = '';
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }
    this.isSubmitting.set(true);


    this.auth.register(this.registerForm.value).subscribe({
      next: () => {
        this.isSubmitting.set(false);
        this.router.navigate(['/login']); // register succeeded — go log in
      },
      error: (err) => {
        this.isSubmitting.set(false);
        this.errorMessage = err.message || 'Registration failed.';
      },
    });
  }
}