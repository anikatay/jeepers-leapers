import { Component, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { RegisterRequest } from '../core/models/auth.model';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  formData: RegisterRequest = {
    firstName: '',
    lastName: '',
    dateOfBirth: '',
    ssn: '',
    address: '',
    phoneNumber: '',
    email: '',
    password: '',
  };
  message = signal('');
  isError = signal(false);

  constructor(private authService: AuthService, private router: Router) {}

  register(form: NgForm) {
    this.message.set('');
    this.isError.set(false);

    if (form.invalid) {
      this.isError.set(true);
      this.message.set('Please complete all fields with valid information.');
      return;
    }

    const request: RegisterRequest = {
      ...this.formData,
      dateOfBirth: `${this.formData.dateOfBirth}T00:00:00Z`,
    };

    this.authService.register(request).subscribe({
      next: (response) => {
        const target = response.role === 'ROLE_ADMIN'
          ? '/admin'
          : response.role === 'ROLE_ANALYST'
            ? '/analyst'
            : '/client';
        this.router.navigateByUrl(target);
      },
      error: (err: HttpErrorResponse) => {
        this.isError.set(true);
        if (err.status === 409) {
          this.message.set('An account with this email already exists.');
        } else if (err.status === 400) {
          this.message.set('Please check your information and try again.');
        } else if (err.status === 0) {
          this.message.set('Cannot reach the server.');
        } else {
          this.message.set('Something went wrong, please try again.');
        }
      },
    });
  }
}