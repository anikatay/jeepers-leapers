import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '../core/pipes/translate.pipe';
import { LanguageSelectorComponent } from '../shared/components/language-selector.component';
import { SparklineComponent } from '../shared/components/sparkline.component';
import { getStockIcon, type StockIconStyle } from '../shared/utils/stock-icon';

interface PreviewHolding {
  symbol: string;
  name: string;
  shares: string;
  price: string;
  value: string;
  gain: number;
  gainPct: number;
  spark: number[];
}

@Component({
  selector: 'app-landing',
  imports: [RouterLink, TranslatePipe, LanguageSelectorComponent, SparklineComponent],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class Landing {
  readonly periods = ['1D', '1W', '1M', '3M', '1Y'] as const;
  readonly previewHoldings: PreviewHolding[] = [
    { symbol: 'AAPL', name: 'Apple Inc.', shares: '203', price: '$189.42', value: '$38,452.26', gain: 3210.4, gainPct: 9.1, spark: [168, 172, 170, 178, 184, 189] },
    { symbol: 'MSFT', name: 'Microsoft Corp.', shares: '75', price: '$412.18', value: '$30,913.50', gain: 2840, gainPct: 10.11, spark: [380, 388, 395, 392, 405, 412] },
    { symbol: 'NVDA', name: 'NVIDIA Corp.', shares: '204', price: '$118.60', value: '$24,194.40', gain: 4560.2, gainPct: 23.22, spark: [92, 98, 104, 110, 114, 119] },
    { symbol: 'AMZN', name: 'Amazon.com Inc.', shares: '93', price: '$178.22', value: '$16,574.46', gain: -120.4, gainPct: -0.72, spark: [186, 184, 181, 179, 176, 178] },
  ];

  icon(symbol: string): StockIconStyle {
    return getStockIcon(symbol);
  }
}
