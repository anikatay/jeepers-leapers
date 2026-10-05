import { Component, Input, Output, EventEmitter, signal, HostListener, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FormsModule } from '@angular/forms';

export interface NavItem {
  label: string;
  path: string;
}

export interface StockSymbol {
  symbol: string;
  name: string;
  price: number;
}

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, FormsModule],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css'
})
export class NavbarComponent {
  @Input() navItems: NavItem[] = [
    { label: 'Home', path: '' },
    { label: 'Portfolio', path: 'portfolio' },
    { label: 'Trades', path: 'trades' }
  ];
  @Input() userName = 'User';
  @Input() brandName = 'Bull Exchange';

  @Output() profileOptionSelected = new EventEmitter<string>();

  // Dropdown state signals
  searchOpen = signal(false);
  profileOpen = signal(false);
  appearanceOpen = signal(false);
  
  // Search state
  searchQuery = signal('');
  searchResults = signal<StockSymbol[]>([]);
  
  // Mock stock symbols from Figma
  private symbols: StockSymbol[] = [
    { symbol: 'AAPL', name: 'Apple Inc.', price: 175.20 },
    { symbol: 'TSLA', name: 'Tesla Inc.', price: 242.15 },
    { symbol: 'MSFT', name: 'Microsoft Corp.', price: 415.30 },
    { symbol: 'NVDA', name: 'NVIDIA Corp.', price: 894.60 },
    { symbol: 'GOOGL', name: 'Alphabet Inc.', price: 178.50 },
    { symbol: 'AMZN', name: 'Amazon.com Inc.', price: 201.30 },
    { symbol: 'META', name: 'Meta Platforms', price: 553.10 },
    { symbol: 'BRK.B', name: 'Berkshire Hathaway', price: 415.00 },
  ];

  // Get first letter of username for avatar
  get avatarLetter(): string {
    return this.userName.charAt(0).toUpperCase();
  }

  constructor() {
    // Filter search results when search query changes
    effect(() => {
      const query = this.searchQuery().toLowerCase();
      if (query.length > 0) {
        this.searchResults.set(
          this.symbols.filter(s =>
            s.symbol.toLowerCase().includes(query) ||
            s.name.toLowerCase().includes(query)
          )
        );
      } else {
        this.searchResults.set([]);
      }
    });
  }

  // Close dropdowns when clicking outside
  @HostListener('document:click', ['$event'])
  onClickOutside(event: MouseEvent) {
    const target = event.target as HTMLElement;
    if (!target.closest('.search-container') && !target.closest('.search-dropdown')) {
      this.searchOpen.set(false);
    }
    if (!target.closest('.profile-container') && !target.closest('.profile-menu')) {
      this.profileOpen.set(false);
    }
    if (!target.closest('.appearance-container') && !target.closest('.appearance-menu')) {
      this.appearanceOpen.set(false);
    }
  }

  // Search handlers
  onSearchInput(event: Event) {
    const input = (event.target as HTMLInputElement).value;
    this.searchQuery.set(input);
    if (input.length > 0) {
      this.searchOpen.set(true);
    }
  }

  onSearchFocus() {
    if (this.searchQuery().length > 0) {
      this.searchOpen.set(true);
    }
  }

  onSearchKeyDown(event: KeyboardEvent) {
    if (event.key === 'Enter' && this.searchResults().length > 0) {
      this.selectStock(this.searchResults()[0]);
    }
  }

  selectStock(stock: StockSymbol) {
    this.profileOptionSelected.emit(`trade:${stock.symbol}`);
    this.searchQuery.set('');
    this.searchOpen.set(false);
  }

  tradeClick() {
    if (this.searchResults().length > 0) {
      this.selectStock(this.searchResults()[0]);
    }
  }

  // Profile handlers
  toggleProfile() {
    this.profileOpen.set(!this.profileOpen());
  }

  selectProfileOption(option: string) {
    this.profileOptionSelected.emit(option);
    this.profileOpen.set(false);
  }

  // Appearance handlers
  toggleAppearance() {
    this.appearanceOpen.set(!this.appearanceOpen());
  }

  toggleDarkMode() {
    const html = document.documentElement;
    html.classList.toggle('dark');
  }

  changeTheme(theme: string) {
    const html = document.documentElement;
    // Remove all theme classes
    html.className = html.className.replace(/(bull|ocean|lavender|rose|sage|mono|midnight|a11y-bo|a11y-bg|a11y-hc)\s*/g, '');
    // Add new theme
    html.classList.add(theme);
    this.appearanceOpen.set(false);
  }
}