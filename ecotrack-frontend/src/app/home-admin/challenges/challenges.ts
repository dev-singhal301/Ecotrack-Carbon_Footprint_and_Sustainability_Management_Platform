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
  targetValue: null, unit: '', rewardXp: 100, cardImageUrl: '', badgeImageUrl: '',
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

  pageNumbers = computed(() =>
    Array.from({ length: this.totalPages() }, (_, i) => i + 1)
  );

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

  readonly categories = CHALLENGE_CATEGORIES;
  readonly cardImages = CARD_HEADER_IMAGES;
  readonly badgeImages = CHALLENGE_BADGES;
  readonly trackingTypes: { value: TrackingType; label: string }[] = [
    { value: 'COUNT', label: 'Count (e.g. trees planted)' },
    { value: 'DISTANCE', label: 'Distance (e.g. km cycled)' },
    { value: 'DURATION', label: 'Duration (e.g. minutes)' },
    { value: 'BOOLEAN', label: 'Yes/No check-in' },
  ];

  // Bound to the Start Date input's [min] so past dates aren't selectable
  // at all, rather than only being rejected after submit. Computed once —
  // this app is zoneless, but a static value read at construction time
  // doesn't need a signal since nothing ever writes to it again.
  readonly minDate: string = new Date().toISOString().split('T')[0];

  // End Date's minimum can't just be `minDate` (today) — if the selected
  // Start Date is later than today (the normal case), the End Date picker
  // would still let you pick something between today and that start date,
  // i.e. before the challenge even starts. This tracks whichever is later.
  get endMinDate(): string {
    return this.form.startDate && this.form.startDate > this.minDate
      ? this.form.startDate
      : this.minDate;
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

  openCreateForm(): void {
    this.editingChallengeId.set(null);
    this.form = { ...EMPTY_FORM };
    this.formError.set(null);
    this.showForm.set(true);
  }

  openEditForm(challenge: Challenge): void {
    this.editingChallengeId.set(challenge.challengeId);
    this.form = {
      title: challenge.title,
      description: challenge.description,
      category: challenge.category,
      challengeType: challenge.challengeType,
      trackingType: challenge.trackingType ?? '',
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
    this.showForm.set(true);
  }

  closeForm(): void {
    this.showForm.set(false);
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