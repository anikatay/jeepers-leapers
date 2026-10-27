import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/services/auth.service';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '../core/pipes/translate.pipe';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink, TranslatePipe],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  email = '';
  password = '';
  messageKey = signal('');
  isError = signal(false);
  showPassword = signal(false);

  togglePassword(): void {
    this.showPassword.update((visible) => !visible);
  }

  constructor(private authService: AuthService, private router: Router) {}

  confirmLogIn() {
    this.messageKey.set('');

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
          this.messageKey.set('auth.invalid_credentials');
        } else if (err.status === 0) {
          this.messageKey.set('auth.server_unreachable');
        } else {
          this.messageKey.set('auth.generic_error');
        }
      },
    });
  }
}
