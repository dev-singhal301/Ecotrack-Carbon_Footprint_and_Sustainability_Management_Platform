import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { CarbonEntryService, CarbonEntryRequest, CarbonEntryResponse } from './carbon-entry.service';

@Component({
  selector: 'app-tracking',
  standalone: true,
  // FormsModule stays — the filter controls (category/from/to) still use [(ngModel)].
  // ReactiveFormsModule is new — only the log-entry form migrates to it.
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './tracking.html',
  styleUrls: ['./tracking.css']
})
export class TrackingComponent implements OnInit {

  // Reactive form replacing the old template-driven `entry` object.
  // 'unit' stays disabled — it's a derived, read-only field, never user-edited.
  trackingForm = new FormGroup({
    category: new FormControl('', Validators.required),
    amount: new FormControl<number | null>(null, [Validators.required, Validators.min(0.01)]),
    unit: new FormControl({ value: '', disabled: true }),
    activityDate: new FormControl('', Validators.required)
  });

  // Search & Filter state variables (unchanged — still template-driven)
  filters = {
    category: '',
    from: '',
    to: ''
  };

  // Dynamic preview mapping matching backend emission factors seeding
  private unitMapping: { [key: string]: { unit: string } } = {
    'TRANSPORTATION': { unit: 'km' },
    'ELECTRICITY': { unit: 'kWh' },
    'FOOD': { unit: 'kg' },
    'WASTE': { unit: 'kg' },
    'WATER': { unit: 'litres' },
    'FUEL': { unit: 'litres' }
  };

  // Bound array for the HTML structural template table grid
  historyLogs: CarbonEntryResponse[] = [];

  // Drives the skeleton-loader rows while a history fetch is in flight
  isLoading: boolean = false;

  // Entries currently mid fade-out — kept separate from historyLogs so the
  // CSS transition has time to play before the row is actually removed.
  removingIds = new Set<number>();

  constructor(
    private carbonService: CarbonEntryService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.trackingForm.patchValue({ activityDate: this.getTodayDate() });
    this.loadHistory();
  }

  private getTodayDate(): string {
    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  onCategoryChange(): void {
    const category = this.trackingForm.get('category')?.value ?? '';
    const mapping = this.unitMapping[category];
    // patchValue still updates a disabled control's value/display, it's
    // only excluded from .value (not getRawValue()) when reading back out.
    this.trackingForm.patchValue({ unit: mapping ? mapping.unit : '' });
  }

  // Load live, filtered entries from PostgreSQL database via Spring Boot API
  loadHistory(): void {
    this.isLoading = true;

    this.carbonService.getHistory(this.filters.category, this.filters.from, this.filters.to)
      .subscribe({
        next: (data) => {
          // Sort strictly by entryId descending (newest log is always on top)
          this.historyLogs = data.sort((a, b) => b.entryId - a.entryId);
          this.isLoading = false;

          // FORCE IMMEDIATE UI UPDATE
          this.cdr.detectChanges();
        },
        error: (err) => {
          console.error('Failed to load carbon entry records from backend:', err);
          this.isLoading = false;
          this.cdr.detectChanges();
        }
      });
  }

  // Persists standard submissions directly into the remote database
  onLogSubmit(): void {
    if (this.trackingForm.invalid) {
      // Marks every control touched so validation messages appear,
      // replacing the old blanket alert() with inline field feedback.
      this.trackingForm.markAllAsTouched();
      return;
    }

    // getRawValue() (not .value) so the disabled 'unit' control is included.
    const raw = this.trackingForm.getRawValue();
    const payload: CarbonEntryRequest = {
      category: raw.category ?? '',
      amount: Number(raw.amount),
      unit: raw.unit ?? '',
      activityDate: raw.activityDate ?? ''
    };

    this.carbonService.createEntry(payload).subscribe({
      next: (savedEntry: CarbonEntryResponse) => {
        // Prepend the newly created entry directly to the top of the local view array
        this.historyLogs = [savedEntry, ...this.historyLogs];

        // Reset cleanly back to defaults, with today's date prepopulated again.
        this.trackingForm.reset({
          category: '',
          amount: null,
          unit: '',
          activityDate: this.getTodayDate()
        });

        // FORCE IMMEDIATE UI UPDATE
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Submission error context:', err);
        alert(err.error?.message || 'Error occurred while saving your log entry to the server.');
      }
    });
  }

  // Remove records from persistent storage table
  onDeleteEntry(id: number): void {
    if (!confirm('Are you sure you want to delete this recorded log permanently?')) {
      return;
    }

    this.carbonService.deleteEntry(id).subscribe({
      next: () => {
        // Mark the row as removing so the CSS fade-out transition can play,
        // then actually drop it from the array once the animation finishes.
        this.removingIds.add(id);
        this.cdr.detectChanges();

        setTimeout(() => {
          this.historyLogs = this.historyLogs.filter(item => item.entryId !== id);
          this.removingIds.delete(id);
          this.cdr.detectChanges();
        }, 280); // matches --row-fade-duration in tracking.css
      },
      error: (err) => {
        alert(err.error?.message || 'Failed to remove log record from database.');
      }
    });
  }

  // Resets the filter models and forces change detection
  clearFilters(): void {
    this.filters = { category: '', from: '', to: '' }; // Clean values
    this.loadHistory(); // Reload fully-unfiltered history log

    // Explicitly enforce UI synchronization to clear visual date inputs
    this.cdr.detectChanges();
  }
}