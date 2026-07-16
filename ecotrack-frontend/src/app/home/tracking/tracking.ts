import { Component, OnInit, ChangeDetectorRef } from '@angular/core'; 
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CarbonEntryService, CarbonEntryRequest, CarbonEntryResponse } from './carbon-entry.service'; 

@Component({
  selector: 'app-tracking',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tracking.html',
  styleUrls: ['./tracking.css']
})
export class TrackingComponent implements OnInit {

  // Current Logging Entry matching CarbonEntryRequest DTO
  entry: CarbonEntryRequest = {
    category: '',
    amount: null,
    unit: '',
    activityDate: ''
  };

  // Search & Filter state variables
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

  constructor(
    private carbonService: CarbonEntryService, 
    private cdr: ChangeDetectorRef 
  ) {} 

  ngOnInit(): void {
    this.setDefaultDate();
    this.loadHistory(); 
  }

  setDefaultDate(): void {
    const today = new Date();
    const yyyy = today.getFullYear(); 
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    this.entry.activityDate = `${yyyy}-${mm}-${dd}`; 
  }

  onCategoryChange(): void {
    const mapping = this.unitMapping[this.entry.category];
    this.entry.unit = mapping ? mapping.unit : ''; 
  }

  // Load live, filtered entries from PostgreSQL database via Spring Boot API
  loadHistory(): void {
    this.carbonService.getHistory(this.filters.category, this.filters.from, this.filters.to)
      .subscribe({
        next: (data) => {
          // Sort strictly by entryId descending (newest log is always on top)
          this.historyLogs = data.sort((a, b) => b.entryId - a.entryId);
          
          // FORCE IMMEDIATE UI UPDATE
          this.cdr.detectChanges();
        },
        error: (err) => {
          console.error('Failed to load carbon entry records from backend:', err);
        }
      }); 
  }

  // Persists standard submissions directly into the remote database
  onLogSubmit(): void {
    if (!this.entry.category || !this.entry.amount || this.entry.amount <= 0) {
      alert('Please fill out all fields with valid amounts!'); 
      return; 
    }

    const payload: CarbonEntryRequest = {
      category: this.entry.category,
      amount: Number(this.entry.amount),
      unit: this.entry.unit,
      activityDate: this.entry.activityDate
    }; 

    this.carbonService.createEntry(payload).subscribe({
      next: (savedEntry: CarbonEntryResponse) => {
        // Prepend the newly created entry directly to the top of the local view array
        this.historyLogs = [savedEntry, ...this.historyLogs]; 
        
        // Reset form controls safely
        this.entry.amount = null;
        this.entry.category = ''; 
        this.entry.unit = '';
        this.setDefaultDate();

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
    if (confirm('Are you sure you want to delete this recorded log permanently?')) { 
      this.carbonService.deleteEntry(id).subscribe({
        next: () => {
          // Filter the deleted item out of the current table array instantly
          this.historyLogs = this.historyLogs.filter(item => item.entryId !== id); 

          // FORCE IMMEDIATE UI UPDATE
          this.cdr.detectChanges();
        },
        error: (err) => {
          alert('Failed to remove log record from database.'); 
        }
      }); 
    }
  }

  // Resets the filter models and forces change detection
  clearFilters(): void {
    this.filters = { category: '', from: '', to: '' }; // Clean values
    this.loadHistory(); // Reload fully-unfiltered history log

    // Explicitly enforce UI synchronization to clear visual date inputs
    this.cdr.detectChanges();
  }
}