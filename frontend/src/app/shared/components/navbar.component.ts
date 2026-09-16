import { Component, Input, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';

export interface NavItem {
  label: string;
  path: string; // relative path, e.g. '' for home, 'portfolio', 'trades'
}

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css'
})
export class NavbarComponent {
  @Input() navItems: NavItem[] = [];
  @Input() userName = '';
  @Input() brandName = 'Jeepers Leapers';

  @Output() profileOptionSelected = new EventEmitter<string>();

  profileOpen = false;

  toggleProfile(event: Event) {
    event.stopPropagation();
    this.profileOpen = !this.profileOpen;
  }

  selectProfileOption(option: string) {
    this.profileOptionSelected.emit(option); // let the PARENT decide what to do (logout, go to settings, etc.)
    this.profileOpen = false;
  }
}