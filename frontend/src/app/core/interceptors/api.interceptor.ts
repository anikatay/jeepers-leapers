import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable()
export class ApiInterceptor implements HttpInterceptor {

  private apiUrl = 'http://localhost:8090';

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (req.url.startsWith('/api')) {
      const token = localStorage.getItem('token');
      const headers: Record<string, string> = {
        'Content-Type': 'application/json'
      };

      if (token && !req.url.includes('/auth/login')) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const clonedReq = req.clone({
        setHeaders: headers
      });
      return next.handle(clonedReq);
    }
    return next.handle(req);
  }
}