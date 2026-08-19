import { Component, OnInit, signal, computed, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../admin.service';
import { Challenge, ChallengeCategory, ChallengeType, TrackingType, DerivedChallengeStatus } from '../../../types/challenge';
import { ChallengeAdminRequest } from './challenges.model';
import { CHALLENGE_CATEGORIES, CARD_HEADER_IMAGES, CHALLENGE_BADGES } from '../../../constants/challenge-assets';

export type CategoryFilter = ChallengeCategory | 'ALL';
export type TypeFilter = ChallengeType | 'ALL';
export type StatusFilter = DerivedChallengeStatus | 'ALL';

interface ChallengeFormModel {
  title: string;
  description: string;
  category: ChallengeCategory | '';
  challengeType: ChallengeType | '';
  trackingType: TrackingType | '';
  targetValue: number | null;
  unit: string;
  rewardXp: number;
  cardImageUrl: string;
  badgeImageUrl: string;
  startDate: string;
  endDate: string;
}

interface CalendarCell {
  date: string;
  day: number;
  inMonth: boolean;
}

const EMPTY_FORM: ChallengeFormModel = {
  title: '', description: '', category: '', challengeType: '', trackingType: '',
  targetValue: null, unit: '', rewardXp: 100,
  cardImageUrl: CARD_HEADER_IMAGES[0].url, badgeImageUrl: CHALLENGE_BADGES[0].url,
  startDate: '', endDate: '',
};

/**
 * Admin challenge management: list, create, edit, cancel.
 *
 * Uses signals for state written inside HttpClient subscribe callbacks —
 * same reasoning as the admin Dashboard/Users components: this app is
 * zoneless, so a plain field assignment inside .subscribe() would not
 * trigger a re-render. The form model itself (`form`) is a plain object
 * bound via ngModel — that's fine even zoneless, since ngModel writes go
 * through Angular's own template event bindings, not a raw subscribe callback.
 *
 * Edit/Cancel eligibility (canEdit/canCancel) mirrors the backend exactly:
 * edit only while status is UPCOMING, cancel while UPCOMING or ACTIVE.
 * This is a UI convenience only — the backend re-validates independently
 * and is the actual source of truth (see ChallengeService).
 *
 * Category / Challenge Type / Tracking Type / Start Date / End Date are all
 * custom-built dropdowns rather than native <select>/<input type="date">.
 * Native form controls' open state (the option list, the OS calendar) is
 * rendered by the browser/OS as a top-layer popup that sits entirely
 * outside this page's DOM and CSS — there's no way to size, position, or
 * style it, which is what was causing it to overflow the modal on mobile.
 * Card Header/Badge Image already used this custom dropdown-select pattern
 * (for image thumbnails, which a native <select> can't render at all); the
 * rest of the form's dropdowns now follow the same approach so every popup
 * in this form is regular in-DOM content we actually control.
 */
@Component({
  selector: 'app-challenges',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './challenges.html',
  styleUrl: './challenges.css',
})
export class Challenges implements OnInit {
  challenges = signal<Challenge[]>([]);
  isLoading = signal(false);
  dataError = signal<string | null>(null);
  actionError = signal<string | null>(null);

  // Tracks which row currently has a cancel request in flight.
  pendingChallengeId = signal<number | null>(null);

  // ── Filters ── all client-side over the already-fetched list, same
  // reasoning as Users' filteredUsers: GET /api/admin/challenges returns
  // everything in one call, no pagination, so there's nothing to gain by
  // round-tripping to the backend just to narrow down data already here.
  searchQuery = signal('');
  categoryFilter = signal<CategoryFilter>('ALL');
  typeFilter = signal<TypeFilter>('ALL');
  statusFilter = signal<StatusFilter>('ALL');

  filteredChallenges = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const category = this.categoryFilter();
    const type = this.typeFilter();
    const status = this.statusFilter();

    return this.challenges().filter(challenge => {
      if (query && !challenge.title.toLowerCase().includes(query)) {
        return false;
      }
      if (category !== 'ALL' && challenge.category !== category) {
        return false;
      }
      if (type !== 'ALL' && challenge.challengeType !== type) {
        return false;
      }
      if (status !== 'ALL' && challenge.status !== status) {
        return false;
      }
      return true;
    });
  });

  resetFilters(): void {
    this.searchQuery.set('');
    this.categoryFilter.set('ALL');
    this.typeFilter.set('ALL');
    this.statusFilter.set('ALL');
    this.currentPage.set(1);
    this.closeAllFilterPickers();
  }

  // ── List filter bar: Category / Type / Status custom dropdowns ──
  // Native <select>'s option list is a browser/OS top-layer popup outside
  // this page's CSS, which is what was causing it to overflow on mobile —
  // same fix as the Users page filter bar. Kept as a separate signal
  // group from the create/edit form's pickers below (cardPickerOpen etc.)
  // rather than folding into that group's closeAllPickers(): the form
  // only exists behind its own full-screen overlay, so there's no
  // scenario where a filter-bar picker and a form picker are ever
  // visible at the same time, and keeping them separate means opening a
  // filter dropdown can't have any effect on unrelated form state.
  categoryFilterPickerOpen = signal(false);
  typeFilterPickerOpen = signal(false);
  statusFilterPickerOpen = signal(false);

  anyFilterPickerOpen = computed(() =>
    this.categoryFilterPickerOpen() || this.typeFilterPickerOpen() || this.statusFilterPickerOpen()
  );

  private closeAllFilterPickers(): void {
    this.categoryFilterPickerOpen.set(false);
    this.typeFilterPickerOpen.set(false);
    this.statusFilterPickerOpen.set(false);
  }

  private toggleFilterPicker(openSignal: WritableSignal<boolean>): void {
    const opening = !openSignal();
    this.closeAllFilterPickers();
    if (opening) {
      openSignal.set(true);
    }
  }

  closeFilterPickers(): void {
    this.closeAllFilterPickers();
  }

  readonly typeFilterOptions: { value: TypeFilter; label: string }[] = [
    { value: 'ALL', label: 'All types' },
    { value: 'CARBON_REDUCTION', label: 'Carbon Reduction' },
    { value: 'ACTIVITY_BASED', label: 'Activity Based' },
  ];

  readonly statusFilterOptions: { value: StatusFilter; label: string }[] = [
    { value: 'ALL', label: 'All statuses' },
    { value: 'UPCOMING', label: 'Upcoming' },
    { value: 'ACTIVE', label: 'Active' },
    { value: 'ENDED', label: 'Ended' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ];

  toggleCategoryFilterPicker(): void {
    this.toggleFilterPicker(this.categoryFilterPickerOpen);
  }

  toggleTypeFilterPicker(): void {
    this.toggleFilterPicker(this.typeFilterPickerOpen);
  }

  toggleStatusFilterPicker(): void {
    this.toggleFilterPicker(this.statusFilterPickerOpen);
  }

  selectCategoryFilter(category: CategoryFilter): void {
    this.categoryFilter.set(category);
    this.categoryFilterPickerOpen.set(false);
  }

  selectTypeFilter(type: TypeFilter): void {
    this.typeFilter.set(type);
    this.typeFilterPickerOpen.set(false);
  }

  selectStatusFilter(status: StatusFilter): void {
    this.statusFilter.set(status);
    this.statusFilterPickerOpen.set(false);
  }

  getCategoryFilterTriggerLabel(): string {
    const c = this.categoryFilter();
    return c === 'ALL' ? 'All categories' : this.categoryLabel(c);
  }

  getTypeFilterTriggerLabel(): string {
    return this.typeFilterOptions.find(o => o.value === this.typeFilter())?.label ?? 'All types';
  }

  getStatusFilterTriggerLabel(): string {
    return this.statusFilterOptions.find(o => o.value === this.statusFilter())?.label ?? 'All statuses';
  }

  // ── Pagination ── same 10-per-page pattern as the Users table: paging
  // happens client-side over filteredChallenges, since GET /api/admin/
  // challenges already returns everything in one call.
  private readonly pageSize = 10;
  currentPage = signal(1);

  totalPages = computed(() =>
    Math.max(1, Math.ceil(this.filteredChallenges().length / this.pageSize))
  );

  safeCurrentPage = computed(() =>
    Math.min(this.currentPage(), this.totalPages())
  );

  // Windowed page numbers — same centered-and-clamped pattern as the
  // Users table (see users.ts for the full rationale).
  pageNumbers = computed(() => {
    const total = this.totalPages();
    const current = this.safeCurrentPage();
    const windowSize = Math.min(3, total);

    let start = current - 1;
    let end = current + 1;

    if (start < 1) {
      start = 1;
      end = start + windowSize - 1;
    }
    if (end > total) {
      end = total;
      start = end - windowSize + 1;
    }

    return Array.from({ length: end - start + 1 }, (_, i) => start + i);
  });

  pagedChallenges = computed(() => {
    const start = (this.safeCurrentPage() - 1) * this.pageSize;
    return this.filteredChallenges().slice(start, start + this.pageSize);
  });

  goToPage(page: number): void {
    this.currentPage.set(Math.min(Math.max(1, page), this.totalPages()));
  }

  prevPage(): void {
    this.goToPage(this.safeCurrentPage() - 1);
  }

  nextPage(): void {
    this.goToPage(this.safeCurrentPage() + 1);
  }

  showForm = signal(false);
  editingChallengeId = signal<number | null>(null);
  isSubmitting = signal(false);
  formError = signal<string | null>(null);

  // ── Custom dropdown open state ──
  // Every dropdown/calendar in this form shares one rule: opening one
  // closes all the others, matching how a row of native <select>s
  // behaves (you never see two option lists open at once). togglePicker()
  // below is the single chokepoint for that, so no toggle method has to
  // remember to close its siblings individually.
  cardPickerOpen = signal(false);
  badgePickerOpen = signal(false);
  categoryPickerOpen = signal(false);
  typePickerOpen = signal(false);
  trackingPickerOpen = signal(false);
  startPickerOpen = signal(false);
  endPickerOpen = signal(false);

  // Bound to the full-screen transparent backdrop that only exists in the
  // DOM while something is open, so a click anywhere outside the open
  // panel closes it — no document-level listener needed.
  anyPickerOpen = computed(() =>
    this.cardPickerOpen() || this.badgePickerOpen() || this.categoryPickerOpen()
    || this.typePickerOpen() || this.trackingPickerOpen()
    || this.startPickerOpen() || this.endPickerOpen()
  );

  private closeAllPickers(): void {
    this.cardPickerOpen.set(false);
    this.badgePickerOpen.set(false);
    this.categoryPickerOpen.set(false);
    this.typePickerOpen.set(false);
    this.trackingPickerOpen.set(false);
    this.startPickerOpen.set(false);
    this.endPickerOpen.set(false);
  }

  private togglePicker(openSignal: WritableSignal<boolean>, onOpen?: () => void): void {
    const opening = !openSignal();
    this.closeAllPickers();
    if (opening) {
      onOpen?.();
      openSignal.set(true);
    }
  }

  closePickers(): void {
    this.closeAllPickers();
  }

  readonly categories = CHALLENGE_CATEGORIES;
  readonly cardImages = CARD_HEADER_IMAGES;
  readonly badgeImages = CHALLENGE_BADGES;
  readonly trackingTypes: { value: TrackingType; label: string }[] = [
    { value: 'BOOLEAN', label: 'Daily Check-in (e.g. did you cycle to work today?)' },
    { value: 'DISTANCE', label: 'Cumulative Amount (e.g. km cycled, minutes saved, trees planted)' },
  ];

  readonly challengeTypeOptions: { value: ChallengeType; label: string }[] = [
    { value: 'CARBON_REDUCTION', label: 'Carbon Reduction' },
    { value: 'ACTIVITY_BASED', label: 'Activity Based' },
  ];

  readonly weekDayLabels = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

  // Bound to the Start Date calendar's day-disabling so past dates aren't
  // selectable at all, rather than only being rejected after submit.
  // Computed once — this app is zoneless, but a static value read at
  // construction time doesn't need a signal since nothing ever writes to
  // it again.
  //
  // Uses toLocalDateString, NOT .toISOString() — toISOString() converts to
  // UTC first, which silently shifts the date backward by one day for any
  // timezone ahead of UTC (e.g. IST, UTC+5:30: local midnight minus 5:30h
  // rolls back to the previous UTC calendar day, every time, not just near
  // midnight). That was producing an off-by-one floor that left "yesterday"
  // still selectable.
  readonly minDate: string = Challenges.toLocalDateString(new Date());

  // End Date's minimum can't just be `minDate` (today) — it has to respect
  // two separate floors and take whichever is later:
  //   1. Can't be before today.
  //   2. Can't be less than MIN_DURATION_DAYS after the selected Start
  //      Date — mirrors ChallengeService's server-side check
  //      (`ChronoUnit.DAYS.between(startDate, endDate) < MIN_DURATION_DAYS`)
  //      so the picker itself can't select an invalid range, instead of
  //      only rejecting it after submit.
  private static readonly MIN_DURATION_DAYS = 7;

  // Which month each calendar is currently showing. Kept separate from
  // form.startDate/endDate (the actual selected value) since the admin can
  // browse months without having picked a date yet.
  startViewMonth = signal<Date>(new Date());
  endViewMonth = signal<Date>(new Date());

  startCalendarDays = computed(() => this.buildCalendarDays(this.startViewMonth()));
  endCalendarDays = computed(() => this.buildCalendarDays(this.endViewMonth()));

  private buildCalendarDays(viewMonth: Date): CalendarCell[] {
    const year = viewMonth.getFullYear();
    const month = viewMonth.getMonth();
    const firstOfMonth = new Date(year, month, 1);
    const gridStart = new Date(year, month, 1 - firstOfMonth.getDay());

    // Always 42 cells (6 full weeks) so the grid's height doesn't jump
    // around as the admin flips between shorter and longer months.
    return Array.from({ length: 42 }, (_, i) => {
      const d = new Date(gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate() + i);
      return {
        date: Challenges.toLocalDateString(d),
        day: d.getDate(),
        inMonth: d.getMonth() === month,
      };
    });
  }

  // Formats a Date using its LOCAL calendar fields (not UTC) — see the
  // minDate comment above for why .toISOString() is the wrong tool here.
  private static toLocalDateString(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  // yyyy-mm-dd -> local Date, deliberately not `new Date(dateStr)` — that
  // parses as UTC midnight and can render as the previous day locally in
  // any timezone ahead of UTC, same class of bug as the .toISOString() note
  // above.
  private parseLocalDate(dateStr: string): Date | null {
    if (!dateStr) return null;
    const [y, m, d] = dateStr.split('-').map(Number);
    if (!y || !m || !d) return null;
    return new Date(y, m - 1, d);
  }

  get endMinDate(): string {
    if (!this.form.startDate) {
      return this.minDate;
    }
    const start = new Date(this.form.startDate + 'T00:00:00');
    start.setDate(start.getDate() + Challenges.MIN_DURATION_DAYS);
    const floorFromStart = Challenges.toLocalDateString(start);
    return floorFromStart > this.minDate ? floorFromStart : this.minDate;
  }

  form: ChallengeFormModel = { ...EMPTY_FORM };

  constructor(private adminService: AdminService) {}

  // 'OVERALL' reads oddly next to the specific categories in a dropdown —
  // give it a friendlier label without changing the underlying value that
  // actually gets submitted.
  categoryLabel(category: string): string {
    return category === 'OVERALL' ? 'Overall Carbon Emission' : category;
  }

  ngOnInit(): void {
    this.loadChallenges();
  }

  // Also callable by HomeAdmin's router-outlet (activate) handler, same
  // pattern as the admin Dashboard/Users components.
  loadChallenges(): void {
    this.isLoading.set(true);
    this.dataError.set(null);

    this.adminService.getChallenges().subscribe({
      next: (challenges) => {
        this.challenges.set(challenges);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Failed to load challenges:', err);
        this.dataError.set(err.error?.message || 'Could not load challenges. Please try again.');
        this.isLoading.set(false);
      }
    });
  }

  // ── Card Header / Badge image dropdown-selects ──
  toggleCardPicker(): void {
    this.togglePicker(this.cardPickerOpen);
  }

  toggleBadgePicker(): void {
    this.togglePicker(this.badgePickerOpen);
  }

  selectCardImage(url: string): void {
    this.form.cardImageUrl = url;
    this.cardPickerOpen.set(false);
  }

  selectBadgeImage(url: string): void {
    this.form.badgeImageUrl = url;
    this.badgePickerOpen.set(false);
  }

  getCardImageLabel(): string {
    return this.cardImages.find(i => i.url === this.form.cardImageUrl)?.label ?? 'Select image';
  }

  getBadgeImageLabel(): string {
    return this.badgeImages.find(i => i.url === this.form.badgeImageUrl)?.label ?? 'Select image';
  }

  // ── Category dropdown ──
  toggleCategoryPicker(): void {
    this.togglePicker(this.categoryPickerOpen);
  }

  selectCategory(category: ChallengeCategory): void {
    this.form.category = category;
    this.categoryPickerOpen.set(false);
  }

  getCategoryTriggerLabel(): string {
    return this.form.category ? this.categoryLabel(this.form.category) : 'Select category';
  }

  // ── Challenge Type dropdown ──
  toggleTypePicker(): void {
    this.togglePicker(this.typePickerOpen);
  }

  selectChallengeType(type: ChallengeType): void {
    this.form.challengeType = type;
    this.typePickerOpen.set(false);
    this.onChallengeTypeChange();
  }

  getChallengeTypeTriggerLabel(): string {
    return this.challengeTypeOptions.find(o => o.value === this.form.challengeType)?.label ?? 'Select type';
  }

  // ── Tracking Type dropdown ──
  toggleTrackingPicker(): void {
    this.togglePicker(this.trackingPickerOpen);
  }

  selectTrackingType(trackingType: TrackingType): void {
    this.form.trackingType = trackingType;
    this.trackingPickerOpen.set(false);
  }

  getTrackingTypeTriggerLabel(): string {
    return this.trackingTypes.find(t => t.value === this.form.trackingType)?.label ?? 'Select tracking type';
  }

  // ── Start / End Date calendars ──
  toggleStartPicker(): void {
    this.togglePicker(this.startPickerOpen, () => {
      const base = this.parseLocalDate(this.form.startDate) ?? new Date();
      this.startViewMonth.set(new Date(base.getFullYear(), base.getMonth(), 1));
    });
  }

  toggleEndPicker(): void {
    this.togglePicker(this.endPickerOpen, () => {
      const base = this.parseLocalDate(this.form.endDate)
        ?? this.parseLocalDate(this.endMinDate)
        ?? new Date();
      this.endViewMonth.set(new Date(base.getFullYear(), base.getMonth(), 1));
    });
  }

  shiftStartMonth(delta: number): void {
    const m = this.startViewMonth();
    this.startViewMonth.set(new Date(m.getFullYear(), m.getMonth() + delta, 1));
  }

  shiftEndMonth(delta: number): void {
    const m = this.endViewMonth();
    this.endViewMonth.set(new Date(m.getFullYear(), m.getMonth() + delta, 1));
  }

  isDayDisabled(dateStr: string, which: 'start' | 'end'): boolean {
    const floor = which === 'start' ? this.minDate : this.endMinDate;
    return dateStr < floor;
  }

  isDaySelected(dateStr: string, which: 'start' | 'end'): boolean {
    return which === 'start' ? this.form.startDate === dateStr : this.form.endDate === dateStr;
  }

  isTodayDate(dateStr: string): boolean {
    return dateStr === Challenges.toLocalDateString(new Date());
  }

  selectStartDate(dateStr: string): void {
    if (this.isDayDisabled(dateStr, 'start')) return;
    this.form.startDate = dateStr;
    this.onStartDateChange();
    this.startPickerOpen.set(false);
  }

  selectEndDate(dateStr: string): void {
    if (this.isDayDisabled(dateStr, 'end')) return;
    this.form.endDate = dateStr;
    this.endPickerOpen.set(false);
  }

  clearStartDate(): void {
    this.form.startDate = '';
    this.onStartDateChange();
    this.startPickerOpen.set(false);
  }

  clearEndDate(): void {
    this.form.endDate = '';
    this.endPickerOpen.set(false);
  }

  goToTodayStart(): void {
    const today = Challenges.toLocalDateString(new Date());
    this.startViewMonth.set(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
    if (!this.isDayDisabled(today, 'start')) {
      this.selectStartDate(today);
    }
  }

  goToTodayEnd(): void {
    const today = Challenges.toLocalDateString(new Date());
    this.endViewMonth.set(new Date(new Date().getFullYear(), new Date().getMonth(), 1));
    if (!this.isDayDisabled(today, 'end')) {
      this.selectEndDate(today);
    }
  }

  formatDisplayDate(dateStr: string): string {
    const d = this.parseLocalDate(dateStr);
    if (!d) return '';
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

  formatMonthLabel(viewMonth: Date): string {
    return viewMonth.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
  }

  openCreateForm(): void {
    this.editingChallengeId.set(null);
    this.form = { ...EMPTY_FORM };
    this.formError.set(null);
    this.closePickers();
    this.showForm.set(true);
  }

  openEditForm(challenge: Challenge): void {
    this.editingChallengeId.set(challenge.challengeId);
    this.form = {
      title: challenge.title,
      description: challenge.description,
      category: challenge.category,
      challengeType: challenge.challengeType,
      // COUNT and DURATION were folded into BOOLEAN and DISTANCE
      // respectively when the dropdown went from 4 options to 2 — they
      // behave identically on the backend either way, so remap here
      // purely so the picker doesn't render blank when editing a
      // challenge created before this change.
      trackingType: this.normalizeTrackingType(challenge.trackingType),
      targetValue: challenge.targetValue,
      // Normalize to '%' on load too, in case this is an older
      // carbon-reduction challenge created before unit got locked down —
      // keeps the now-disabled field showing the value it'll actually save as.
      unit: challenge.challengeType === 'CARBON_REDUCTION' ? '%' : challenge.unit,
      rewardXp: challenge.rewardXp,
      cardImageUrl: challenge.cardImageUrl,
      badgeImageUrl: challenge.badgeImageUrl,
      startDate: challenge.startDate,
      endDate: challenge.endDate,
    };
    this.formError.set(null);
    this.closePickers();
    this.showForm.set(true);
  }

  closeForm(): void {
    this.closePickers();
    this.showForm.set(false);
  }

  private normalizeTrackingType(trackingType: TrackingType | null | undefined): TrackingType | '' {
    if (trackingType === 'COUNT') return 'BOOLEAN';
    if (trackingType === 'DURATION') return 'DISTANCE';
    return trackingType ?? '';
  }

  // Carbon-reduction challenges are always a percentage — unit is locked
  // to '%' and not admin-editable (the "Target Value" field already reads
  // as a percentage everywhere else in this module, so a mismatched unit
  // like 'kg' here would be actively misleading). Activity-based challenges
  // have no fixed unit (trees, km, minutes, ...) — admin sets it freely,
  // and it's allowed to stay blank (e.g. a plain count of check-ins).
  onChallengeTypeChange(): void {
    if (this.form.challengeType === 'CARBON_REDUCTION') {
      this.form.unit = '%';
      this.form.trackingType = '';
    } else {
      this.form.unit = '';
    }
  }

  // If Start Date moves forward and that pushes the 7-day floor past an
  // already-picked End Date, the calendar's day-disabling alone won't
  // un-pick it — it only blocks new invalid selections, not ones already
  // sitting in the model. Clear it so the admin can't submit a now-too-
  // short range without noticing.
  onStartDateChange(): void {
    if (this.form.endDate && this.form.endDate < this.endMinDate) {
      this.form.endDate = '';
    }
  }

  submitForm(): void {
    this.formError.set(null);

    if (!this.form.title || !this.form.category || !this.form.challengeType
        || this.form.targetValue == null || !this.form.startDate || !this.form.endDate) {
      this.formError.set('Please fill in all required fields.');
      return;
    }
    if (this.form.challengeType === 'ACTIVITY_BASED' && !this.form.trackingType) {
      this.formError.set('Activity-based challenges require a tracking type.');
      return;
    }
    if (this.form.challengeType === 'CARBON_REDUCTION' && this.form.targetValue > 100) {
      this.formError.set('Carbon-reduction target must be between 0 and 100%.');
      return;
    }

    const payload: ChallengeAdminRequest = {
      title: this.form.title,
      description: this.form.description,
      category: this.form.category as ChallengeCategory,
      challengeType: this.form.challengeType as ChallengeType,
      trackingType: this.form.challengeType === 'ACTIVITY_BASED'
        ? (this.form.trackingType as TrackingType)
        : null,
      targetValue: this.form.targetValue,
      unit: this.form.unit,
      rewardXp: this.form.rewardXp,
      cardImageUrl: this.form.cardImageUrl,
      badgeImageUrl: this.form.badgeImageUrl,
      startDate: this.form.startDate,
      endDate: this.form.endDate,
    };

    this.isSubmitting.set(true);
    const editingId = this.editingChallengeId();
    const request$ = editingId
      ? this.adminService.updateChallenge(editingId, payload)
      : this.adminService.createChallenge(payload);

    request$.subscribe({
      next: (saved) => {
        this.isSubmitting.set(false);
        this.showForm.set(false);
        if (editingId) {
          this.challenges.update(list => list.map(c => c.challengeId === editingId ? saved : c));
        } else {
          this.challenges.update(list => [saved, ...list]);
        }
      },
      error: (err) => {
        console.error('Failed to save challenge:', err);
        this.isSubmitting.set(false);
        this.formError.set(err.error?.message || 'Could not save this challenge. Please try again.');
      }
    });
  }

  confirmCancel(challenge: Challenge): void {
    if (!confirm(`Cancel "${challenge.title}"? This cannot be undone.`)) {
      return;
    }
    this.pendingChallengeId.set(challenge.challengeId);
    this.actionError.set(null);

    this.adminService.cancelChallenge(challenge.challengeId).subscribe({
      next: () => {
        this.challenges.update(list => list.map(c =>
          c.challengeId === challenge.challengeId ? { ...c, cancelled: true, status: 'CANCELLED' as const } : c
        ));
        this.pendingChallengeId.set(null);
      },
      error: (err) => {
        console.error('Failed to cancel challenge:', err);
        this.actionError.set(err.error?.message || 'Could not cancel this challenge. Please try again.');
        this.pendingChallengeId.set(null);
      }
    });
  }

  canEdit(challenge: Challenge): boolean {
    return challenge.status === 'UPCOMING';
  }

  canCancel(challenge: Challenge): boolean {
    return challenge.status === 'UPCOMING' || challenge.status === 'ACTIVE';
  }
}