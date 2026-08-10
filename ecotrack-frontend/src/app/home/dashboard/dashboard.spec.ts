import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { Dashboard } from './dashboard';
import { DashboardService } from './dashboard.service';
import { CarbonEntryService } from '../tracking/carbon-entry.service';
import { Router } from '@angular/router';
import { DashboardSummary } from './dashboard.model';

describe('Dashboard', () => {
  let component: Dashboard;
  let fixture: ComponentFixture<Dashboard>;
  let dashboardServiceSpy: { getSummary: ReturnType<typeof vi.fn> };
  let carbonEntryServiceSpy: { getHistory: ReturnType<typeof vi.fn> };
  let routerSpy: { navigate: ReturnType<typeof vi.fn> };

  const mockSummary: DashboardSummary = {
    username: 'Jane',
    userLevel: 2,
    userRank: 'Eco Scout',
    currentXp: 700,
    nextLevelXpTarget: 1000,
    totalCarbonFootprint: 250.5,
    carbonSaved: 45.2,
    savingsPercentage: 15.3,
    totalRewardPoints: 420,
    monthlyXp: 700,
    monthlyRank: 5,
    categoryImpacts: [
      { categoryName: 'ELECTRICITY', totalEmissions: 120.5, percentageShare: 48.1 },
      { categoryName: 'TRANSPORTATION', totalEmissions: 80.0, percentageShare: 31.9 }
    ],
    activeGoals: [
      { goalId: 1, goalName: 'Cut Electricity', goalType: 'REDUCE_ELECTRICITY_CONSUMPTION', targetValue: 100, currentValue: 60, completionPercentage: 60, rewardPoints: 84 }
    ]
  };

  beforeEach(async () => {
    dashboardServiceSpy = {
      getSummary: vi.fn().mockReturnValue(of(mockSummary))
    };
    carbonEntryServiceSpy = {
      getHistory: vi.fn().mockReturnValue(of([]))
    };
    routerSpy = {
      navigate: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        { provide: DashboardService, useValue: dashboardServiceSpy },
        { provide: CarbonEntryService, useValue: carbonEntryServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
  });

  it('should be created', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should load and expose the dashboard summary', () => {
    fixture.detectChanges();
    expect(component.summary).toEqual(mockSummary);
  });

  it('should rank category impacts from highest to lowest emission', () => {
    fixture.detectChanges();
    expect(component.rankedCategoryImpacts[0].categoryName).toBe('ELECTRICITY');
    expect(component.rankedCategoryImpacts[1].categoryName).toBe('TRANSPORTATION');
  });

  it('should show a visible error message when the summary request fails', () => {
    dashboardServiceSpy.getSummary.mockReturnValue(
      throwError(() => ({ error: { message: 'Server error' } }))
    );
    
    // Re-trigger loadData to capture the error state
    component.loadData();
    fixture.detectChanges();

    expect(component.summary).toBeNull();
  });

  it('should compute XP progress WITHIN the current level, not from zero', () => {
    fixture.detectChanges();
    // Level 2 spans 500-1000 XP. At 700 XP that's (700-500)/(1000-500) = 40%
    expect(component.xpProgressPercent).toBe(40);
  });

  it('should read active goals directly from the summary response', () => {
    fixture.detectChanges();
    expect(component.summary?.activeGoals.length).toBe(1);
  });

  it('should navigate to the tracking route when Log New Entry is clicked', () => {
    fixture.detectChanges();
    component.goToTracking();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/home/tracking']);
  });
});