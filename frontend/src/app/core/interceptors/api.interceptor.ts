import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable()
export class ApiInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // Add Content-Type header to API requests
    if (req.url.startsWith('/api')) {
      const clonedReq = req.clone({
        setHeaders: {
          'Content-Type': 'application/json'
        }
      });
      return next.handle(clonedReq);
    }
    return next.handle(req);
  }
}