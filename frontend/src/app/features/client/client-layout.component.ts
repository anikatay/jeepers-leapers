import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { NavbarComponent, NavItem } from '../../shared/components/navbar.component';
import { InstrumentService } from '../../core/services/instrument.service';
import { InstrumentResponse } from '../../core/models/instrument.model';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-client-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent],
  template: `
    <div class="client-app" dir="ltr">
      <app-navbar 
        [navItems]="navItems" 
        [userName]="userName"
        (profileOptionSelected)="onProfileOptionSelected($event)">
      </app-navbar>
      <router-outlet></router-outlet>
    </div>
  `,
  styles: [`
    .client-app { min-height: 100%; }
  `]
})
export class ClientLayoutComponent implements OnInit {
  navItems: NavItem[] = [
    { label: 'nav.home', path: '/client' },
    { label: 'nav.portfolio', path: '/client/portfolio' },
    { label: 'nav.trades', path: '/client/trades' }
  ];

  userName: string;

  private allInstruments = signal<InstrumentResponse[]>([]);

  constructor(
    private instrumentService: InstrumentService,
    private router: Router,
    private authService: AuthService
  ) {
    this.userName = this.authService.userName;
  }

  ngOnInit() {
    this.loadInstruments();
  }

  private loadInstruments() {
    this.instrumentService.getAllInstruments().subscribe({
      next: (instruments) => {
        this.allInstruments.set(instruments);
      },
      error: (err) => {
        console.error('Failed to load instruments:', err);
      }
    });
  }

  onProfileOptionSelected(option: string) {
    if (option === 'logout') {
      this.authService.logout();
      this.router.navigate(['/auth/login']);
      return;
    }

    if (option === 'profile') {
      this.router.navigate(['/client/portfolio']);
      return;
    }

    // Handle trade: option
    if (option.startsWith('trade:')) {
      const symbol = option.substring(6);
      const instrument = this.allInstruments().find(i => i.ticker === symbol);
      
      if (instrument) {
        this.openTrade(instrument);
        return;
      }

      this.instrumentService.getInstrumentByTicker(symbol).subscribe({
        next: resolvedInstrument => {
          this.allInstruments.update(current => [
            ...current.filter(item => item.ticker !== resolvedInstrument.ticker),
            resolvedInstrument
          ]);
          this.openTrade(resolvedInstrument);
        },
        error: err => {
          console.error(`Failed to load instrument ${symbol}:`, err);
        }
      });
    }
  }

  private openTrade(instrument: InstrumentResponse) {
    this.router.navigate(['/client/stock', instrument.ticker]);
  }
}
