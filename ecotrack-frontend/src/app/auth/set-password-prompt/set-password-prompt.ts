import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Auth } from '../auth';

@Component({
  selector: 'app-set-password-prompt',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './set-password-prompt.html',
  styleUrl: './set-password-prompt.css'
})
export class SetPasswordPrompt implements OnInit {
  newPassword: string = '';
  confirmPassword: string = '';

  errorMessage: string = '';
  successMessage: string = '';
  isLoading: boolean = false;

  constructor(private auth: Auth, private router: Router) {}

  ngOnInit(): void {
    if (this.auth.hasPassword()) {
      this.router.navigate(['/home']);
    }
  }

  onSubmit(): void {
    this.errorMessage = '';
    this.successMessage = '';

    if (!this.newPassword || !this.confirmPassword) {
      this.errorMessage = 'Please fill in both fields.';
      return;
    }

    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage = 'Passwords do not match!';
      return;
    }

    this.isLoading = true;

    this.auth.setPassword(this.newPassword).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = 'Password set! Taking you to your dashboard...';

        setTimeout(() => {
          this.router.navigate(['/home']);
        }, 1500);
      },
      error: (err: any) => {
        this.isLoading = false;
        this.errorMessage = err.message || 'Something went wrong. Please try again.';
      }
    });
  }
}