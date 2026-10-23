import {
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  computed,
  effect,
  input,
  output,
  signal,
  untracked
} from '@angular/core';
import { CommonModule } from '@angular/common';

let nextSwipeId = 0;

/**
 * Drag-to-confirm control. `confirmed` fires once per completed swipe; the
 * control then stays locked at the end until `resetKey` changes.
 */
@Component({
  selector: 'app-swipe-to-confirm',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="swipe"
         [class.buy]="variant() === 'buy'"
         [class.sell]="variant() === 'sell'"
         [class.is-disabled]="disabled() && !loading()"
         [class.is-loading]="loading()"
         [class.is-dragging]="dragging()"
         [class.is-ready]="progress() >= threshold && !completed()">
      <div class="track" #track>
        <div class="fill" [style.width]="fillWidth()"></div>
        <span class="label" [style.opacity]="labelOpacity()" aria-hidden="true">
          {{ loading() ? loadingLabel() : label() }}
          <span class="label-arrow" *ngIf="!loading()">→</span>
        </span>
        <div #handle
             class="handle"
             role="slider"
             [attr.id]="handleId"
             [attr.tabindex]="interactive() ? 0 : -1"
             [attr.aria-label]="label()"
             [attr.aria-describedby]="hintId"
             aria-valuemin="0"
             aria-valuemax="100"
             [attr.aria-valuenow]="valueNow()"
             [attr.aria-valuetext]="valueText()"
             [attr.aria-disabled]="!interactive()"
             [style.left]="handleLeft()"
             (pointerdown)="onPointerDown($event)"
             (pointermove)="onPointerMove($event)"
             (pointerup)="onPointerUp($event)"
             (pointercancel)="cancelDrag()"
             (lostpointercapture)="onLostCapture()"
             (keydown)="onKeyDown($event)"
             (blur)="onBlur()">
          <span *ngIf="loading()" class="spinner" aria-hidden="true"></span>
          <svg *ngIf="!loading()" width="22" height="22" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M5 12h14M13 6l6 6-6 6" />
          </svg>
        </div>
      </div>
      <p class="sr-only" [attr.id]="hintId">
        Drag the handle all the way to the right to confirm. Keyboard: press the Right Arrow key until the
        handle reaches the end, then press Enter. Press Escape or Left Arrow to move it back.
      </p>
      <p class="sr-only" role="status" aria-live="polite">{{ loading() ? loadingLabel() : '' }}</p>
    </div>
  `,
  styles: [`
    :host { display: block; }

    .swipe {
      --swipe-color: #16a34a;
      --swipe-label: #15803d;
      --swipe-tint: rgba(22, 163, 74, 0.12);
      --swipe-fill: rgba(22, 163, 74, 0.28);
      --swipe-border: rgba(22, 163, 74, 0.45);
      --handle-size: 52px;
      --inset: 4px;
    }
    .swipe.sell {
      --swipe-color: #dc2626;
      --swipe-label: #b91c1c;
      --swipe-tint: rgba(220, 38, 38, 0.1);
      --swipe-fill: rgba(220, 38, 38, 0.26);
      --swipe-border: rgba(220, 38, 38, 0.45);
    }
    :host-context(.dark) .swipe.buy { --swipe-color: #22c55e; --swipe-label: #4ade80; }
    :host-context(.dark) .swipe.sell { --swipe-color: #ef4444; --swipe-label: #f87171; }

    .track {
      position: relative;
      height: calc(var(--handle-size) + var(--inset) * 2);
      border-radius: 9999px;
      background: var(--swipe-tint);
      border: 1.5px solid var(--swipe-border);
      overflow: hidden;
      user-select: none;
      -webkit-user-select: none;
      touch-action: pan-y;
    }

    .fill {
      position: absolute;
      inset: 0 auto 0 0;
      background: var(--swipe-fill);
      border-radius: 9999px;
      transition: width 0.3s cubic-bezier(0.22, 1, 0.36, 1);
    }

    .label {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.4rem;
      padding-left: calc(var(--handle-size) + var(--inset));
      font-family: 'DM Sans', sans-serif;
      font-size: 1rem;
      font-weight: 700;
      letter-spacing: 0.01em;
      color: var(--swipe-label);
      pointer-events: none;
      transition: opacity 0.2s ease;
    }
    .label-arrow { animation: nudge 1.6s ease-in-out infinite; }

    .handle {
      position: absolute;
      top: var(--inset);
      width: var(--handle-size);
      height: var(--handle-size);
      border-radius: 9999px;
      background: var(--swipe-color);
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.22);
      cursor: grab;
      touch-action: none;
      transition: left 0.3s cubic-bezier(0.22, 1, 0.36, 1), transform 0.15s ease;
      outline: none;
    }
    .handle:focus-visible {
      box-shadow: 0 0 0 3px var(--card), 0 0 0 5px var(--swipe-color);
    }
    .is-dragging .handle,
    .is-dragging .fill { transition: none; }
    .is-dragging .handle { cursor: grabbing; transform: scale(1.04); }
    .is-ready .handle { transform: scale(1.06); }

    .is-disabled .track {
      background: var(--secondary);
      border-color: var(--border);
      border-style: dashed;
    }
    .is-disabled .label { color: var(--muted-foreground); }
    .is-disabled .label-arrow { animation: none; }
    .is-disabled .handle {
      background: var(--muted-foreground);
      color: var(--card);
      box-shadow: none;
      cursor: not-allowed;
      opacity: 0.6;
    }
    .is-loading .handle { cursor: progress; }

    .spinner {
      width: 20px;
      height: 20px;
      border: 2.5px solid rgba(255, 255, 255, 0.35);
      border-top-color: #ffffff;
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }

    .sr-only {
      position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
      overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0;
    }

    @keyframes spin { to { transform: rotate(360deg); } }
    @keyframes nudge { 0%, 100% { transform: translateX(0); } 50% { transform: translateX(4px); } }

    @media (prefers-reduced-motion: reduce) {
      .handle, .fill, .label { transition: none; }
      .label-arrow { animation: none; }
    }
  `]
})
export class SwipeToConfirmComponent implements OnDestroy {
  readonly threshold = 0.9;
  private static readonly KEY_STEPS = 10;

  readonly label = input('Slide to confirm');
  readonly loadingLabel = input('Processing…');
  readonly variant = input<'buy' | 'sell'>('buy');
  readonly disabled = input(false);
  readonly loading = input(false);
  readonly resetKey = input<unknown>(null);

  readonly confirmed = output<void>();

  @ViewChild('track', { static: true }) private trackRef!: ElementRef<HTMLElement>;
  @ViewChild('handle', { static: true }) private handleRef!: ElementRef<HTMLElement>;

  readonly handleId = `swipe-handle-${nextSwipeId}`;
  readonly hintId = `swipe-hint-${nextSwipeId++}`;

  readonly progress = signal(0);
  readonly dragging = signal(false);
  readonly completed = signal(false);

  readonly interactive = computed(() => !this.disabled() && !this.loading() && !this.completed());
  readonly handleLeft = computed(
    () => `calc(var(--inset) + (100% - var(--handle-size) - var(--inset) * 2) * ${this.progress()})`
  );
  readonly fillWidth = computed(
    () => `calc(var(--handle-size) + var(--inset) * 2 + (100% - var(--handle-size) - var(--inset) * 2) * ${this.progress()})`
  );
  readonly labelOpacity = computed(() => (this.loading() ? 1 : Math.max(0, 1 - this.progress() * 1.6)));
  readonly valueNow = computed(() => Math.round(this.progress() * 100));
  readonly valueText = computed(() => {
    if (this.loading()) return this.loadingLabel();
    if (this.disabled()) return `${this.label()} unavailable. Complete the order details first.`;
    if (this.progress() >= this.threshold) return 'Ready. Press Enter to confirm.';
    return `${this.valueNow()} percent. Press Right Arrow to slide.`;
  });

  private pointerId: number | null = null;
  private startX = 0;
  private startProgress = 0;
  private travel = 1;

  constructor() {
    effect(() => {
      this.resetKey();
      untracked(() => this.reset());
    });
    effect(() => {
      if (this.disabled() && !this.loading()) {
        untracked(() => {
          if (!this.completed()) this.reset();
        });
      }
    });
  }

  ngOnDestroy(): void {
    this.releasePointer();
  }

  reset(): void {
    this.dragging.set(false);
    this.releasePointer();
    this.completed.set(false);
    this.progress.set(0);
  }

  onPointerDown(event: PointerEvent): void {
    if (!this.interactive() || this.pointerId !== null) return;
    if (event.pointerType === 'mouse' && event.button !== 0) return;

    event.preventDefault();
    this.travel = this.measureTravel();
    this.pointerId = event.pointerId;
    this.startX = event.clientX;
    this.startProgress = this.progress();
    try {
      this.handleRef.nativeElement.setPointerCapture(event.pointerId);
    } catch {
      // Pointer already released; the drag still works while over the handle.
    }
    this.dragging.set(true);
  }

  onPointerMove(event: PointerEvent): void {
    if (!this.dragging() || event.pointerId !== this.pointerId) return;
    const delta = (event.clientX - this.startX) / this.travel;
    this.progress.set(Math.min(1, Math.max(0, this.startProgress + delta)));
  }

  onPointerUp(event: PointerEvent): void {
    if (!this.dragging() || event.pointerId !== this.pointerId) return;
    const reachedThreshold = this.progress() >= this.threshold;
    this.dragging.set(false);
    this.releasePointer();

    if (reachedThreshold && this.interactive()) {
      this.complete();
    } else {
      this.progress.set(0);
    }
  }

  cancelDrag(): void {
    if (!this.dragging()) return;
    this.dragging.set(false);
    this.releasePointer();
    this.progress.set(0);
  }

  onLostCapture(): void {
    // Capture can be lost without a pointerup (e.g. the browser takes over the gesture).
    if (this.dragging()) {
      this.pointerId = null;
      this.dragging.set(false);
      this.progress.set(0);
    }
  }

  onKeyDown(event: KeyboardEvent): void {
    if (!this.interactive()) return;
    const steps = SwipeToConfirmComponent.KEY_STEPS;
    const current = Math.round(this.progress() * steps);

    switch (event.key) {
      case 'ArrowRight':
      case 'ArrowUp':
        this.progress.set(Math.min(steps, current + 1) / steps);
        break;
      case 'ArrowLeft':
      case 'ArrowDown':
        this.progress.set(Math.max(0, current - 1) / steps);
        break;
      case 'Home':
      case 'Escape':
        if (event.key === 'Escape' && this.progress() === 0) return;
        this.progress.set(0);
        event.stopPropagation();
        break;
      case 'Enter':
      case ' ':
        if (this.progress() >= this.threshold) this.complete();
        break;
      default:
        return;
    }
    event.preventDefault();
  }

  onBlur(): void {
    if (!this.completed() && !this.dragging()) {
      this.progress.set(0);
    }
  }

  private complete(): void {
    if (this.completed()) return;
    this.completed.set(true);
    this.progress.set(1);
    this.confirmed.emit();
  }

  private measureTravel(): number {
    const trackWidth = this.trackRef.nativeElement.getBoundingClientRect().width;
    const handleWidth = this.handleRef.nativeElement.getBoundingClientRect().width;
    return Math.max(1, trackWidth - handleWidth - 8);
  }

  private releasePointer(): void {
    if (this.pointerId === null) return;
    const handle = this.handleRef?.nativeElement;
    try {
      if (handle?.hasPointerCapture(this.pointerId)) {
        handle.releasePointerCapture(this.pointerId);
      }
    } catch {
      // Capture already gone.
    }
    this.pointerId = null;
  }
}
