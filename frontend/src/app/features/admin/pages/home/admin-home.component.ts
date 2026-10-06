import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterOutlet } from '@angular/router';
import { NavbarComponent, NavItem } from '../../../../shared/components/navbar.component';



@Component({
  selector: 'app-admin-home',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent],
  templateUrl: './admin-home.component.html',
  styleUrl: './admin-home.component.css'
})
export class AdminHomeComponent implements OnInit{
  constructor(private router: Router ) {}

  ngOnInit() {
    // Note: All trades endpoint (GET /api/trades) not available from backend
    // Feature disabled until endpoint is implemented
  }

  navItems: NavItem[] = [
    { label: 'Home', path: '' },
    { label: 'Trades', path: 'trades' },
    { label: 'Users', path: 'users' },
  ];

  userName = 'ADMIN'; // or pulled from an auth service later
}