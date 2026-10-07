import { Component, Input, Output, EventEmitter, signal, HostListener, effect, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { InstrumentService } from '../../core/services/instrument.service';
import { TranslationService, LANGUAGES, type Language, type LanguageOption } from '../../core/services/translation.service';
import { InstrumentResponse } from '../../core/models/instrument.model';

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
export class NavbarComponent implements OnInit {
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
  languageOpen = signal(false);
  
  // Search state
  searchQuery = signal('');
  searchResults = signal<StockSymbol[]>([]);
  
  // Language options
  languages = signal<LanguageOption[]>(LANGUAGES);
  selectedLanguage = signal<Language>('en');
  
  // Real instruments from backend
  private instruments = signal<InstrumentResponse[]>([]);

  // Get first letter of username for avatar
  get avatarLetter(): string {
    return this.userName.charAt(0).toUpperCase();
  }

  constructor(private instrumentService: InstrumentService, private translationService: TranslationService) {
    // Sync selected language with translation service
    this.selectedLanguage.set(this.translationService.currentLanguage());
    
    // Filter search results when search query changes
    effect(() => {
      const query = this.searchQuery().toLowerCase();
      if (query.length > 0) {
        const results = this.instruments()
          .filter(inst =>
            inst.ticker.toLowerCase().includes(query) ||
            inst.name.toLowerCase().includes(query)
          )
          .map(inst => ({
            symbol: inst.ticker,
            name: inst.name,
            price: inst.currentPrice
          }));
        this.searchResults.set(results);
      } else {
        this.searchResults.set([]);
      }
    });
  }

  ngOnInit() {
    this.loadInstruments();
  }

  private loadInstruments() {
    this.instrumentService.getAllInstruments().subscribe({
      next: (data) => {
        this.instruments.set(data);
      },
      error: (err) => {
        console.error('Failed to load instruments:', err);
        this.instruments.set([]);
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
    if (!target.closest('.language-container') && !target.closest('.language-menu')) {
      this.languageOpen.set(false);
    }
    if (!target.closest('.appearance-container') && !target.closest('.appearance-menu')) {
      this.appearanceOpen.set(false);
    }
  }

  toggleLanguageDropdown() {
    this.languageOpen.set(!this.languageOpen());
  }

  selectLanguage(lang: Language) {
    this.selectedLanguage.set(lang);
    this.translationService.setLanguage(lang);
    this.languageOpen.set(false);
  }

  getLanguageName(): string {
    const lang = LANGUAGES.find(l => l.code === this.selectedLanguage());
    return lang ? `${lang.englishName} / ${lang.nativeName}` : 'English';
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