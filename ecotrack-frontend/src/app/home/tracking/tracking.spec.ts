import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TrackingComponent } from './tracking';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { CarbonEntryService } from './carbon-entry.service';
import { Chart, registerables } from 'chart.js';
import { of } from 'rxjs';
import { vi, describe, it, expect, beforeEach } from 'vitest';

describe('TrackingComponent', () => {
  let component: TrackingComponent;
  let fixture: ComponentFixture<TrackingComponent>;
  let carbonServiceSpy: {
    getHistory: ReturnType<typeof vi.fn>;
    createEntry: ReturnType<typeof vi.fn>;
    deleteEntry: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    // 1. Mock 2D Canvas Context for Chart.js rendering in JSDOM
    HTMLCanvasElement.prototype.getContext = vi.fn().mockReturnValue({
      fillRect: () => {},
      clearRect: () => {},
      getImageData: () => ({ data: [] }),
      putImageData: () => {},
      createImageData: () => ([]),
      setTransform: () => {},
      drawImage: () => {},
      save: () => {},
      fillText: () => {},
      restore: () => {},
      beginPath: () => {},
      moveTo: () => {},
      lineTo: () => {},
      closePath: () => {},
      stroke: () => {},
      translate: () => {},
      scale: () => {},
      rotate: () => {},
      arc: () => {},
      fill: () => {},
      measureText: () => ({ width: 0 }),
      transform: () => {},
      rect: () => {},
      clip: () => {},
      createLinearGradient: () => ({
        addColorStop: () => {}
      })
    } as any);

    Chart.register(...registerables);

    // 2. Mock CarbonEntryService to prevent HTTP calls
    carbonServiceSpy = {
      getHistory: vi.fn().mockReturnValue(of([])),
      createEntry: vi.fn().mockReturnValue(of({
        entryId: 1,
        category: 'ELECTRICITY',
        amount: 100,
        unit: 'kWh',
        carbonEmission: 50,
        activityDate: '2026-03-30'
      })),
      deleteEntry: vi.fn().mockReturnValue(of(void 0))
    };

    await TestBed.configureTestingModule({
      imports: [TrackingComponent, FormsModule, ReactiveFormsModule, CommonModule],
      providers: [
        { provide: CarbonEntryService, useValue: carbonServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TrackingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create tracking component', () => {
    expect(component).toBeTruthy();
    expect(carbonServiceSpy.getHistory).toHaveBeenCalled();
  });

  it('should initialize default parameters correctly', () => {
    expect(component.trackingForm.get('category')?.value).toBe('');
    expect(component.trackingForm.get('amount')?.value).toBeNull();
    expect(component.trackingForm.get('activityDate')?.value).toBeTruthy();
  });

  it('should update measurement unit dynamically on category change', () => {
    component.trackingForm.patchValue({ category: 'ELECTRICITY' });
    component.onCategoryChange();
    expect(component.trackingForm.get('unit')?.value).toBe('kWh');

    component.trackingForm.patchValue({ category: 'TRANSPORTATION' });
    component.onCategoryChange();
    expect(component.trackingForm.get('unit')?.value).toBe('km');
  });
});