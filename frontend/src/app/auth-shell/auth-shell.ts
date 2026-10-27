import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Login } from '../login/login';
import { Register } from '../register/register';
import { TranslatePipe } from '../core/pipes/translate.pipe';
import { LanguageSelectorComponent } from '../shared/components/language-selector.component';

@Component({
  selector: 'app-auth-slot',
  template: '',
})
export class AuthSlot {}

@Component({
  selector: 'app-auth-shell',
  imports: [Login, Register, RouterLink, RouterOutlet, TranslatePipe, LanguageSelectorComponent],
  templateUrl: './auth-shell.html',
  styleUrl: './auth-shell.css',
})
export class AuthShell {
  private readonly router = inject(Router);
  readonly isRegister = signal(this.isRegisterUrl(this.router.url));

  constructor() {
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      takeUntilDestroyed(),
    ).subscribe((event) => {
      this.isRegister.set(this.isRegisterUrl(event.urlAfterRedirects));
    });
  }

  private isRegisterUrl(url: string): boolean {
    return url.split('?')[0].includes('/auth/register');
  }
}
