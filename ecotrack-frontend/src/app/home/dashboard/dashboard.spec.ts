import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Dashboard } from './dashboard';
import { DashboardService } from './dashboard.service';
import { Router } from '@angular/router';
import { DashboardSummary } from './dashboard.model';

describe('Dashboard', () => {
  let component: Dashboard;
  let fixture: ComponentFixture<Dashboard>;
  let dashboardServiceSpy: jasmine.SpyObj<DashboardService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const mockSummary: DashboardSummary = {
    username: 'Jane',
    userLevel: 2,
    userRank: 'Green Guardian',
    currentXp: 700,
    nextLevelXpTarget: 1000,
    totalCarbonFootprint: 250.5,
    carbonSaved: 45.2,
    savingsPercentage: 15.3,
    totalRewardPoints: 420,
    categoryImpacts: [
      { categoryName: 'ELECTRICITY', totalEmissions: 120.5, percentageShare: 48.1 },
      { categoryName: 'TRANSPORTATION', totalEmissions: 80.0, percentageShare: 31.9 }
    ],
    activeGoals: [
      { goalId: 1, goalName: 'Cut Electricity', goalType: 'REDUCE_ELECTRICITY_CONSUMPTION', targetValue: 100, currentValue: 60, completionPercentage: 60, rewardPoints: 84 }
    ]
  };

  beforeEach(async () => {
    dashboardServiceSpy = jasmine.createSpyObj('DashboardService', ['getSummary']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        { provide: DashboardService, useValue: dashboardServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();

    dashboardServiceSpy.getSummary.and.returnValue(of(mockSummary));

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
    dashboardServiceSpy.getSummary.and.returnValue(throwError(() => ({ error: { message: 'Server error' } })));
    fixture.detectChanges();
    expect(component.dataError).toBe('Server error');
  });

  it('should compute XP progress WITHIN the current level, not from zero', () => {
    // Level 2 spans 500-1000 XP. At 700 XP that's (700-500)/(1000-500) = 40%,
    // not the naive currentXp/nextLevelXpTarget = 700/1000 = 70%.
    fixture.detectChanges();
    expect(component.xpProgressPercent).toBe(40);
  });

  it('should read active goals directly from the summary response', () => {
    fixture.detectChanges();
    expect(component.summary?.activeGoals.length).toBe(1);
    expect(component.getGoalStatusLabel(component.summary!.activeGoals[0])).toBe('On Track');
  });

  it('should flag a goal as Over Budget when currentValue exceeds targetValue', () => {
    const breachedGoal = { goalId: 2, goalName: 'Cut Water', goalType: 'REDUCE_WATER_USAGE', targetValue: 50, currentValue: 60, completionPercentage: 120, rewardPoints: 35 };
    expect(component.getGoalStatusLabel(breachedGoal)).toBe('Over Budget');
  });

  it('should navigate to the tracking route when Log New Entry is clicked', () => {
    fixture.detectChanges();
    component.goToTracking();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/home/tracking']);
  });
});