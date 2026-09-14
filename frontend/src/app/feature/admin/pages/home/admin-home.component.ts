import { Component, OnInit, ChangeDetectorRef, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TradesService } from '../../../../core/services/trades.service';
import { Router, RouterOutlet } from '@angular/router';
import { NavbarComponent, NavItem } from '../../../../layout/navbar/navbar.component';



@Component({
  selector: 'app-admin-home',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent],
  templateUrl: './admin-home.component.html',
  styleUrl: './admin-home.component.css'
})
export class AdminHomeComponent implements OnInit{
  trades;

  constructor(private tradesService: TradesService, private router: Router ) {
    this.trades = this.tradesService.trades;
    effect(() => {
      const data = this.trades();
      if (data) {
        console.log('Portfolio updated:', data);
      }
    });
  }

  ngOnInit() {
    this.tradesService.getTrades(); 
  }

  navItems: NavItem[] = [
    { label: 'Home', path: '' },
    { label: 'Trades', path: 'trades' },
    { label: 'Users', path: 'users' },
  ];

  userName = 'ADMIN'; // or pulled from an auth service later
}