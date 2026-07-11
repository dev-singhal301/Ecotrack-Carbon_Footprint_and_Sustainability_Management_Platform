import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Auth } from '../auth'; // Adjust the import path if your auth service file is named differently

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reset-password.html',
  styleUrl: './reset-password.css'
})
export class ResetPasswordComponent implements OnInit {
  token: string = '';
  newPassword: string = '';
  confirmPassword: string = '';
  
  errorMessage: string = '';
  successMessage: string = '';
  isLoading: boolean = false;

  constructor(
    private route: ActivatedRoute,
    private auth: Auth,
    private router: Router
  ) {}

  ngOnInit(): void {
    // Grabs the UUID token directly from the URL: http://localhost:4200/reset-password?token=XYZ
    this.token = this.route.snapshot.queryParams['token'] || '';
    
    if (!this.token) {
      this.errorMessage = 'Invalid or missing password reset token.';
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

    this.auth.resetPassword(this.token, this.newPassword).subscribe({
      next: (res: any) => {
        this.isLoading = false;
        this.successMessage = 'Password reset successfully! Redirecting to login...';
        
        // Redirects the user back to the login page after 2 seconds
        setTimeout(() => {
          this.router.navigate(['/login']); 
        }, 2000);
      },
      error: (err: any) => {
        this.isLoading = false;
        this.errorMessage = err.message || 'Something went wrong. Please try again.';
      }
    });
  }
}