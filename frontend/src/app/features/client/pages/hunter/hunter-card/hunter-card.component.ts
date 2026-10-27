import { Component, computed, inject, input, model } from '@angular/core';
import { TranslatePipe } from '../../../../../core/pipes/translate.pipe';
import { TranslationService } from '../../../../../core/services/translation.service';
import {
  Criterion,
  HunterCatalog,
  HunterFieldError,
  HunterSpec,
  MetricId,
  NumericOp,
  QuantRule,
  SectorOp,
  UnsupportedClause,
} from '../../../../../core/models/hunter.model';
import { formatMonth } from '../hunter-format';
import '../hunter-i18n';

/** The editable card of rules (section 8.2 of the spec). Every edit sets a new spec object. */
@Component({
  selector: 'app-hunter-card',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './hunter-card.component.html',
  styleUrl: './hunter-card.component.css'
})
export class HunterCardComponent {
  private translation = inject(TranslationService);

  spec = model.required<HunterSpec>();
  catalog = input.required<HunterCatalog>();
  fieldErrors = input<HunterFieldError[]>([]);
  disabled = input(false);

  readonly ops: NumericOp[] = ['gt', 'gte', 'lt', 'lte', 'between'];

  cutoffMonth = computed(() => formatMonth(this.catalog().knowledgeCutoff, this.translation.currentLanguage()));
  canAddFilter = computed(() => this.spec().quantRules.length < this.catalog().limits.maxQuantRules
    || this.spec().sectorRule === null);
  canAddCheck = computed(() => this.spec().criteria.length < this.catalog().limits.maxCriteria);
  sectorsToAdd = computed(() => {
    const chosen = new Set(this.spec().sectorRule?.values ?? []);
    return this.catalog().sectors.filter(sector => !chosen.has(sector));
  });
  generalErrors = computed(() => this.fieldErrors().filter(error =>
    !/^(quantRules|criteria)\[\d+\]|^sectorRule/.test(error.field)));

  metricLabel(id: MetricId): string {
    return this.translation.translate(`hunter.metric_${id}`);
  }

  unit(id: MetricId): string {
    return this.catalog().metrics.find(metric => metric.id === id)?.unit ?? '';
  }

  definition(id: MetricId): string {
    return this.catalog().metrics.find(metric => metric.id === id)?.definition ?? '';
  }

  named(key: string, name: string): string {
    return this.translation.format(key, { name });
  }

  errorsFor(prefix: string): HunterFieldError[] {
    return this.fieldErrors().filter(error => error.field === prefix || error.field.startsWith(`${prefix}.`));
  }

  unsupportedMessage(clause: UnsupportedClause): string {
    if (clause.reason === 'NO_RECENT_DATA') {
      return this.translation.format('hunter.reason_NO_RECENT_DATA', { month: this.cutoffMonth() });
    }
    if (clause.reason === 'ADVICE_REQUEST') {
      return this.translation.translate('hunter.reason_ADVICE_REQUEST');
    }
    return clause.message;
  }

  setName(name: string) {
    this.update(spec => ({ ...spec, name }));
  }

  toggleQuant(index: number) {
    this.updateQuant(index, rule => ({ ...rule, enabled: !rule.enabled }));
  }

  setMetric(index: number, metric: string) {
    this.updateQuant(index, rule => ({ ...rule, metric: metric as MetricId }));
  }

  setOp(index: number, op: string) {
    this.updateQuant(index, rule => op === 'between'
      ? { ...rule, op: 'between', value2: rule.value2 ?? rule.value }
      : { ...rule, op: op as NumericOp, value2: null });
  }

  setValue(index: number, text: string, upper = false) {
    const value = Number.parseFloat(text);
    if (!Number.isFinite(value)) {
      return;
    }
    this.updateQuant(index, rule => upper ? { ...rule, value2: value } : { ...rule, value });
  }

  removeQuant(index: number) {
    this.update(spec => ({ ...spec, quantRules: spec.quantRules.filter((_, i) => i !== index) }));
  }

  pickFilter(event: Event) {
    this.addFilter(takeChoice(event));
  }

  pickSector(event: Event) {
    this.addSector(takeChoice(event));
  }

  /** choice is a metric ID, or "sector" for the sector rule. */
  addFilter(choice: string) {
    if (!choice) {
      return;
    }
    if (choice === 'sector') {
      this.update(spec => ({
        ...spec,
        sectorRule: { id: 'sector', enabled: true, sourceText: null, op: 'in', values: [] }
      }));
      return;
    }
    this.update(spec => ({
      ...spec,
      quantRules: [...spec.quantRules, {
        id: nextId('q', spec), enabled: true, sourceText: null, metric: choice as MetricId,
        op: 'gte', value: 0, value2: null
      }]
    }));
  }

  toggleSector() {
    this.updateSector(rule => ({ ...rule, enabled: !rule.enabled }));
  }

  setSectorOp(op: string) {
    this.updateSector(rule => ({ ...rule, op: op as SectorOp }));
  }

  addSector(sector: string) {
    if (sector) {
      this.updateSector(rule => ({ ...rule, values: [...rule.values, sector] }));
    }
  }

  removeSector(sector: string) {
    this.updateSector(rule => ({ ...rule, values: rule.values.filter(value => value !== sector) }));
  }

  removeSectorRule() {
    this.update(spec => ({ ...spec, sectorRule: null }));
  }

  toggleCriterion(index: number) {
    this.updateCriterion(index, criterion => ({ ...criterion, enabled: !criterion.enabled }));
  }

  setStatement(index: number, statement: string) {
    this.updateCriterion(index, criterion => ({ ...criterion, statement }));
  }

  removeCriterion(index: number) {
    this.update(spec => ({ ...spec, criteria: spec.criteria.filter((_, i) => i !== index) }));
  }

  addCriterion() {
    this.update(spec => ({
      ...spec,
      criteria: [...spec.criteria, { id: nextId('s', spec), enabled: true, sourceText: null, statement: '' }]
    }));
  }

  private update(change: (spec: HunterSpec) => HunterSpec) {
    this.spec.set(change(this.spec()));
  }

  private updateQuant(index: number, change: (rule: QuantRule) => QuantRule) {
    this.update(spec => ({ ...spec, quantRules: spec.quantRules.map((rule, i) => i === index ? change(rule) : rule) }));
  }

  private updateSector(change: (rule: NonNullable<HunterSpec['sectorRule']>) => NonNullable<HunterSpec['sectorRule']>) {
    this.update(spec => spec.sectorRule ? { ...spec, sectorRule: change(spec.sectorRule) } : spec);
  }

  private updateCriterion(index: number, change: (criterion: Criterion) => Criterion) {
    this.update(spec => ({ ...spec, criteria: spec.criteria.map((c, i) => i === index ? change(c) : c) }));
  }
}

/** For the "add" menus: the chosen option, after showing the prompt option again. */
function takeChoice(event: Event): string {
  const select = event.target as HTMLSelectElement;
  const value = select.value;
  select.value = '';
  return value;
}

/** The lowest free ID with the prefix, so IDs stay unique within the spec. */
function nextId(prefix: 'q' | 's', spec: HunterSpec): string {
  const used = new Set([...spec.quantRules.map(rule => rule.id), ...spec.criteria.map(criterion => criterion.id)]);
  let n = 1;
  while (used.has(`${prefix}${n}`)) {
    n++;
  }
  return `${prefix}${n}`;
}
