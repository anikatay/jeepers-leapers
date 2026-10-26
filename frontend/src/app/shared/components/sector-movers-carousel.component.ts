import {
  Component,
  Input,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SectorMoversService } from '../../core/services/sector-movers.service';
import { TranslationService } from '../../core/services/translation.service';
import { TranslatePipe } from '../../core/pipes/translate.pipe';
import { SectorMover } from '../utils/sector-movers';

/** Seconds of travel per chip, which sets the scroll speed independently of how many there are. */
const SECONDS_PER_CHIP = 2.5;

/** Floor so a short strip does not whip around. */
const MIN_DURATION_S = 20;

@Component({
  selector: 'app-sector-movers-carousel',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslatePipe],
  template: `
    <section
      *ngIf="movers().length > 0"
      class="sm-strip"
      [class.paused]="paused()"
      [attr.aria-label]="'movers.region_aria' | translate"
      (pointerenter)="pause()"
      (pointerleave)="resume()"
      (focusin)="pause()"
      (focusout)="resume()"
    >
      <!-- The lane is rendered twice and translated by exactly half its width, so the second
           copy is already in place when the first scrolls out and the loop has no seam. Only
           the first copy is exposed; the clone is decorative and must not be announced or
           reachable by tab, or every ticker would be read out twice. -->
      <div class="sm-lane" [style.animation-duration.s]="durationSeconds()">
        <ul class="sm-run" [attr.aria-label]="'movers.list_aria' | translate">
          <li *ngFor="let mover of movers(); trackBy: trackByKey" class="sm-item">
            <a
              class="sm-chip"
              [class.laggard]="mover.laggard"
              [routerLink]="[routePrefix, mover.ticker]"
              [attr.aria-label]="describe(mover)"
            >
              <span class="sm-sector">{{ mover.sectorName }}</span>
              <span class="sm-ticker font-mono-data">{{ mover.ticker }}</span>
              <span class="sm-change font-mono-data" [class.up]="mover.change >= 0" [class.down]="mover.change < 0">
                {{ formatChange(mover.change) }}
              </span>
            </a>
          </li>
        </ul>

        <ul class="sm-run" aria-hidden="true">
          <li *ngFor="let mover of movers(); trackBy: trackByKey" class="sm-item">
            <span class="sm-chip" [class.laggard]="mover.laggard" tabindex="-1">
              <span class="sm-sector">{{ mover.sectorName }}</span>
              <span class="sm-ticker font-mono-data">{{ mover.ticker }}</span>
              <span class="sm-change font-mono-data" [class.up]="mover.change >= 0" [class.down]="mover.change < 0">
                {{ formatChange(mover.change) }}
              </span>
            </span>
          </li>
        </ul>
      </div>
    </section>
  `,
  styles: [`
    .sm-strip {
      max-width: 1400px;
      margin: 0 auto;
      padding: 0 0.75rem 0.5rem 0.75rem;
      overflow: hidden;
      /* Fade the ends so chips enter and leave instead of being chopped off. */
      mask-image: linear-gradient(to right, transparent, #000 2rem, #000 calc(100% - 2rem), transparent);
      -webkit-mask-image: linear-gradient(to right, transparent, #000 2rem, #000 calc(100% - 2rem), transparent);
    }

    .sm-lane {
      display: flex;
      width: max-content;
      animation-name: sm-marquee;
      animation-timing-function: linear;
      animation-iteration-count: infinite;
    }

    /* Hovering or tabbing in holds the strip still, so a chip can actually be clicked. */
    .sm-strip.paused .sm-lane { animation-play-state: paused; }

    @keyframes sm-marquee {
      from { transform: translateX(0); }
      to   { transform: translateX(-50%); }
    }

    /* The lane runs the other way under RTL, so the loop still moves with the reading order. */
    :host-context([dir='rtl']) .sm-lane { animation-direction: reverse; }

    .sm-run {
      display: flex;
      gap: 0.375rem;
      margin: 0;
      padding: 0 0.1875rem;
      list-style: none;
    }

    .sm-item { flex: 0 0 auto; }

    .sm-chip {
      display: inline-flex;
      align-items: baseline;
      gap: 0.375rem;
      padding: 0.25rem 0.5rem;
      border: 1px solid var(--border);
      border-radius: 999px;
      background: var(--card);
      color: var(--foreground);
      text-decoration: none;
      font-size: 0.6875rem;
      white-space: nowrap;
    }

    .sm-chip:hover { background: var(--secondary); }

    .sm-chip:focus-visible {
      outline: 2px solid var(--ring, var(--primary));
      outline-offset: 2px;
    }

    /* A laggard is the one chip in its sector showing the downside, so it reads differently
       from the leaders even before the colour is parsed. */
    .sm-chip.laggard {
      border-style: dashed;
    }

    .sm-sector {
      color: var(--muted-foreground);
      font-size: 0.625rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .sm-ticker { font-weight: 700; }

    .sm-change { font-weight: 600; }
    .sm-change.up { color: var(--gain); }
    .sm-change.down { color: var(--loss); }

    /* Motion is the whole point here, so when it is unwelcome the strip stops entirely and
       becomes an ordinary scrollable row rather than animating more slowly. */
    @media (prefers-reduced-motion: reduce) {
      .sm-strip {
        overflow-x: auto;
        scrollbar-width: none;
      }

      .sm-strip::-webkit-scrollbar { display: none; }

      .sm-lane {
        animation: none;
        transform: none;
      }

      .sm-lane .sm-run[aria-hidden='true'] { display: none; }
    }

    @media (max-width: 640px) {
      .sm-strip { padding-inline: 0.5rem; }
    }
  `]
})
export class SectorMoversCarouselComponent implements OnInit, OnDestroy {
  /** Route the ticker chips link into; the admin shell has no instrument page. */
  @Input() routePrefix = '/client/stock';

  private readonly service = inject(SectorMoversService);
  private readonly translation = inject(TranslationService);

  readonly movers = this.service.movers;

  readonly paused = signal(false);

  /**
   * One full loop is the width of a single copy of the lane, so the duration is derived from
   * the chip count to keep a constant reading speed: more movers means a longer cycle, not a
   * faster scroll.
   */
  readonly durationSeconds = computed(() =>
    Math.max(MIN_DURATION_S, this.movers().length * SECONDS_PER_CHIP)
  );

  ngOnInit(): void {
    this.service.retain();
  }

  ngOnDestroy(): void {
    this.service.release();
  }

  pause(): void {
    this.paused.set(true);
  }

  resume(): void {
    this.paused.set(false);
  }

  trackByKey(_index: number, mover: SectorMover): string {
    return `${mover.sectorId}:${mover.ticker}:${mover.laggard}`;
  }

  /** Signed percent, in the active locale. */
  formatChange(change: number): string {
    const formatted = new Intl.NumberFormat(this.translation.currentLanguage(), {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
      signDisplay: 'exceptZero'
    }).format(change);
    return `${formatted}%`;
  }

  /** Spoken label: a bare ticker and number tells a screen reader nothing about why it is here. */
  describe(mover: SectorMover): string {
    return this.translation.t(mover.laggard ? 'movers.laggard_aria' : 'movers.leader_aria', {
      ticker: mover.ticker,
      sector: mover.sectorName,
      change: this.formatChange(mover.change)
    });
  }


}
