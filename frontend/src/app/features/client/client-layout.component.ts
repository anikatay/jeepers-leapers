import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { NavbarComponent, NavItem } from '../../shared/components/navbar.component';
import { TradeModalService } from '../../core/services/trade-modal.service';
import { InstrumentService } from '../../core/services/instrument.service';
import { InstrumentResponse } from '../../core/models/instrument.model';

@Component({
  selector: 'app-client-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent],
  template: `
    <app-navbar 
      [navItems]="navItems" 
      [userName]="userName"
      (profileOptionSelected)="onProfileOptionSelected($event)">
    </app-navbar>
    <router-outlet></router-outlet>
  `,
  styles: []
})
export class ClientLayoutComponent implements OnInit {
  navItems: NavItem[] = [
    { label: 'Home', path: '' },
    { label: 'Portfolio', path: 'portfolio' },
    { label: 'Trades', path: 'trades' }
  ];

  userName = 'Joanna';
  
  private allInstruments = signal<InstrumentResponse[]>([]);

  constructor(
    private tradeModalService: TradeModalService,
    private instrumentService: InstrumentService,
    private router: Router
  ) {}

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
    // Handle trade: option
    if (option.startsWith('trade:')) {
      const symbol = option.substring(6);
      const instrument = this.allInstruments().find(i => i.ticker === symbol);
      
      if (instrument) {
        // Set the instrument to trade in the service
        this.tradeModalService.openTradeModal(instrument, 'BUY');
        // Navigate to portfolio
        this.router.navigate(['/client/portfolio']);
      }
    }
  }
}
