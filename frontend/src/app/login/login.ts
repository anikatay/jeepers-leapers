import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/services/auth.service';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  email = '';
  password = '';
  message = signal('');
  isError = signal(false);

  constructor(private authService: AuthService, private router: Router) {}

  confirmLogIn() {
    this.message.set('');

    this.authService.login({ email: this.email, password: this.password }).subscribe({
      next: (response) => {
        const target = response.role === 'ROLE_ADMIN'
          ? '/admin'
          : response.role === 'ROLE_ANALYST'
            ? '/analyst'
            : '/client';
        this.router.navigateByUrl(target).then(ok => console.log('navigated:', ok));
        console.log(response); // token, userId, role
      },
      error: (err: HttpErrorResponse) => {
        this.isError.set(true);
        if (err.status === 401 || err.status === 404) {
          this.message.set('Invalid email or password');
        } else if (err.status === 0) {
          this.message.set('Cannot reach the server');
        } else {
          this.message.set('Something went wrong, please try again');
        }
      },
    });
  }
}