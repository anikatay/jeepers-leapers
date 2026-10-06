import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs/operators';
import { Instrument } from '../models/instrument.model';

@Injectable({
  providedIn: 'root'
})
export class InstrumentService {

  instruments = signal<Instrument[] | null>(null);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  constructor(private http: HttpClient) {}

  getInstruments() {
    this.loading.set(true);
    this.error.set(null);
    return this.http.get<Instrument[]>(`/api/instruments`)
      .pipe(
        // Update signals on success
        tap({
          next: (data) => {
            this.instruments.set(data);
            this.loading.set(false);
          },
          error: (err) => {
            this.error.set(err.message);
            this.loading.set(false);
          }
        })
      );
  }
}