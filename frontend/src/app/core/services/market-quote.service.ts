import { Injectable, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

export interface MarketQuote {
  type: 'QUOTE';
  ticker: string;
  price: number;
  quoteTime: string;
  receivedTime: string;
  source: string;
}

@Injectable({
  providedIn: 'root'
})
export class MarketQuoteService {
  private socket: WebSocket | null = null;
  private readonly quotesSubject = new Subject<MarketQuote>();
  private readonly subscribedTickers = new Set<string>();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  readonly quotes$: Observable<MarketQuote> = this.quotesSubject.asObservable();
  readonly connected = signal(false);

  connect(): void {
    if (this.socket && this.socket.readyState !== WebSocket.CLOSED) {
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.socket = new WebSocket(`${protocol}//${window.location.host}/ws/market`);

    this.socket.onopen = () => {
      this.connected.set(true);
      this.subscribedTickers.forEach((ticker) => this.send('SUBSCRIBE', ticker));
    };

    this.socket.onmessage = (event) => {
      const message = JSON.parse(event.data) as MarketQuote;
      if (message.type === 'QUOTE') {
        this.quotesSubject.next(message);
      }
    };

    this.socket.onclose = () => {
      this.connected.set(false);
      this.socket = null;
      if (this.subscribedTickers.size > 0 && !this.reconnectTimer) {
        this.reconnectTimer = setTimeout(() => {
          this.reconnectTimer = null;
          this.connect();
        }, 3000);
      }
    };

    this.socket.onerror = () => {
      this.connected.set(false);
    };
  }

  subscribe(ticker: string): void {
    const normalizedTicker = ticker.trim().toUpperCase();
    this.subscribedTickers.add(normalizedTicker);
    this.connect();
    this.send('SUBSCRIBE', normalizedTicker);
  }

  unsubscribe(ticker: string): void {
    const normalizedTicker = ticker.trim().toUpperCase();
    this.subscribedTickers.delete(normalizedTicker);
    this.send('UNSUBSCRIBE', normalizedTicker);
  }

  disconnect(): void {
    this.subscribedTickers.clear();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.socket?.close();
    this.socket = null;
    this.connected.set(false);
  }

  private send(action: 'SUBSCRIBE' | 'UNSUBSCRIBE', ticker: string): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ action, ticker }));
    }
  }
}
