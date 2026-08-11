import { Component, OnInit, signal, computed } from '@angular/core';
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

  // Open/closed state for the Card Header / Badge image dropdown-selects.
  // These render like the native Category/Challenge Type <select>s above
  // them, but show image thumbnails in the option list, which a native
  // <select> can't do — see the dropdown-select markup/styles below.
  cardPickerOpen = signal(false);
  badgePickerOpen = signal(false);

  readonly categories = CHALLENGE_CATEGORIES;
  readonly cardImages = CARD_HEADER_IMAGES;
  readonly badgeImages = CHALLENGE_BADGES;
  readonly trackingTypes: { value: TrackingType; label: string }[] = [
    { value: 'BOOLEAN', label: 'Daily Check-in (e.g. did you cycle to work today?)' },
    { value: 'DISTANCE', label: 'Cumulative Amount (e.g. km cycled, minutes saved, trees planted)' },
  ];

  // Bound to the Start Date input's [min] so past dates aren't selectable
  // at all, rather than only being rejected after submit. Computed once —
  // this app is zoneless, but a static value read at construction time
  // doesn't need a signal since nothing ever writes to it again.
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

  // Formats a Date using its LOCAL calendar fields (not UTC) — see the
  // minDate comment above for why .toISOString() is the wrong tool here.
  private static toLocalDateString(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
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
  // Only one of the two can be open at a time (toggling one closes the
  // other), matching how a row of native <select>s behaves — opening one
  // doesn't leave a stray option list from a different field hanging open.
  toggleCardPicker(): void {
    this.cardPickerOpen.update(v => !v);
    this.badgePickerOpen.set(false);
  }

  toggleBadgePicker(): void {
    this.badgePickerOpen.update(v => !v);
    this.cardPickerOpen.set(false);
  }

  // Bound to a full-screen transparent backdrop that only exists in the DOM
  // while a picker is open, so a click anywhere outside the panel closes it
  // — no document-level listener needed.
  closePickers(): void {
    this.cardPickerOpen.set(false);
    this.badgePickerOpen.set(false);
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
      // purely so the <select> doesn't render blank when editing a
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
  // already-picked End Date, [min] alone won't un-pick it — it only blocks
  // new invalid selections, not ones already sitting in the model. Clear
  // it so the admin can't submit a now-too-short range without noticing.
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