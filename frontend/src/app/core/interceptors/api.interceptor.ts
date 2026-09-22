import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable()
export class ApiInterceptor implements HttpInterceptor {
  private apiUrl = 'http://localhost:8090';

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (req.url.startsWith('/api')) {
      const clonedReq = req.clone({
        url: `${this.apiUrl}${req.url}`,
        setHeaders: {
          'Content-Type': 'application/json'
        }
      });
      return next.handle(clonedReq);
    }
    return next.handle(req);
  }
}