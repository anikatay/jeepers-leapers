import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, throwError } from 'rxjs';

@Injectable()
export class ApiInterceptor implements HttpInterceptor {

  constructor(private router: Router) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (req.url.startsWith('/api')) {
      const token = sessionStorage.getItem('token');
      const headers: Record<string, string> = {
        'Content-Type': 'application/json'
      };

      if (token && !req.url.includes('/auth/login')) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const clonedReq = req.clone({
        setHeaders: headers
      });
      return next.handle(clonedReq).pipe(
        catchError(error => {
          if (error.status === 401) {
            sessionStorage.removeItem('token');
            sessionStorage.removeItem('session');
            this.router.navigate(['/auth/login']);
          }
          return throwError(() => error);
        })
      );
    }
    return next.handle(req);
  }
}