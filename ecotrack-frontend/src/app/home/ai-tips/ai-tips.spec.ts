import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { AiTips } from './ai-tips';
import { RecommendationResponse } from './recommendation.model';
import { GoalResponse, GoalProgressResponse } from './goal.model';

describe('AiTips', () => {
  let component: AiTips;
  let fixture: ComponentFixture<AiTips>;
  let router: Router;
  let httpMock: HttpTestingController;

  const mockRecResponse: RecommendationResponse = {
    topRecommendations: [
      { category: 'TRANSPORTATION', suggestionText: 'Use public transport twice a week', impactEstimate: 6.2 },
      { category: 'ELECTRICITY', suggestionText: 'Switch to LED lighting', impactEstimate: 3.1 },
      { category: 'FUEL', suggestionText: 'Combine short car trips', impactEstimate: 1.4 },
    ],
    allCategoriesSorted: [
      { category: 'TRANSPORTATION', percentage: 38 },
      { category: 'ELECTRICITY', percentage: 25 },
      { category: 'FUEL', percentage: 18 },
      { category: 'WASTE', percentage: 10 },
      { category: 'WATER', percentage: 6 },
      { category: 'FOOD', percentage: 3 },
    ],
  };

  const mockGoal: GoalResponse = {
    goalId: 1,
    goalName: 'Reduce Electricity Consumption',
    goalType: 'ELECTRICITY',
    targetValue: 20.0,
    rewardPoints: 100,
    status: 'ACTIVE',
    startDate: '2026-07-01',
    endDate: '2026-07-31',
    baselineValue: 100.0,
  };

  const mockGoal2: GoalResponse = {
    goalId: 2,
    goalName: 'Cut Transportation Emissions',
    goalType: 'TRANSPORTATION',
    targetValue: 15.0,
    rewardPoints: 80,
    status: 'ACTIVE',
    startDate: '2026-07-01',
    endDate: '2026-07-31',
    baselineValue: 50.0,
  };

  const mockProgress: GoalProgressResponse = {
    goalId: 1,
    currentValue: 85.0,
    targetValue: 80.0,
    completionPercentage: 75.0,
    completed: false,
    lastUpdated: '2026-07-30T15:30:00',
    baselineValue: 100.0,
  };

  const mockProgress2: GoalProgressResponse = {
    goalId: 2,
    currentValue: 30.0,
    targetValue: 35.0,
    completionPercentage: 40.0,
    completed: false,
    lastUpdated: '2026-07-30T15:30:00',
    baselineValue: 50.0,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AiTips],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(AiTips);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    httpMock = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should start with no active panel', () => {
    expect(component.activePanel).toBeNull();
  });

  it('should render all three panel buttons', () => {
    fixture.detectChanges();
    const buttons = fixture.nativeElement.querySelectorAll('.panel-btn');
    expect(buttons.length).toBe(3);
  });

  it('should toggle a panel closed if the same panel is selected twice (no HTTP call)', () => {
    component.selectPanel('facts');
    expect(component.activePanel).toBe('facts');
    component.selectPanel('facts');
    expect(component.activePanel).toBeNull();
  });

  // --- Recommendations panel ---

  describe('Personalized Recommendations panel', () => {
    it('should call GET /api/recommendations exactly once when first opened', () => {
      component.selectPanel('recommendations');

      const req = httpMock.expectOne('http://localhost:8080/api/recommendations');
      expect(req.request.method).toBe('GET');
      req.flush(mockRecResponse);

      expect(component.recommendations).toEqual(mockRecResponse);
      expect(component.recLoading).toBeFalsy();
    });

    it('should not re-fetch if closed and reopened', () => {
      component.selectPanel('recommendations');
      httpMock.expectOne('http://localhost:8080/api/recommendations').flush(mockRecResponse);

      component.selectPanel('recommendations'); // close
      component.selectPanel('recommendations'); // reopen

      httpMock.expectNone('http://localhost:8080/api/recommendations');
    });

    it('should reveal one category at a time via Explore, with no extra API calls', () => {
      component.selectPanel('recommendations');
      httpMock.expectOne('http://localhost:8080/api/recommendations').flush(mockRecResponse);

      expect(component.revealedCategories.length).toBe(1);
      component.exploreNext();
      expect(component.revealedCategories.length).toBe(2);

      httpMock.expectNone('http://localhost:8080/api/recommendations');
    });

    it('should set recError if the request fails', () => {
      component.selectPanel('recommendations');
      httpMock
        .expectOne('http://localhost:8080/api/recommendations')
        .flush({ message: 'Server error' }, { status: 500, statusText: 'Server Error' });

      expect(component.recLoading).toBeFalsy();
      expect(component.recError).toBe('Server error');
    });
  });

  // --- Goal Insights panel ---

  describe('Goal Insights panel', () => {
    it('should show empty state when GET /api/goals returns []', () => {
      component.selectPanel('goals');

      const req = httpMock.expectOne('http://localhost:8080/api/goals?status=ACTIVE');
      expect(req.request.method).toBe('GET');
      req.flush([]);

      expect(component.goalsWithProgress).toEqual([]);
      expect(component.goalsLoading).toBeFalsy();
      expect(component.goalsError).toBeNull();
    });

    it('should fetch progress for every active goal and pair it correctly', () => {
      component.selectPanel('goals');

      const goalsReq = httpMock.expectOne('http://localhost:8080/api/goals?status=ACTIVE');
      goalsReq.flush([mockGoal, mockGoal2]);

      const progressReq1 = httpMock.expectOne('http://localhost:8080/api/goals/1/progress');
      const progressReq2 = httpMock.expectOne('http://localhost:8080/api/goals/2/progress');
      progressReq1.flush(mockProgress);
      progressReq2.flush(mockProgress2);

      expect(component.goalsWithProgress.length).toBe(2);
      expect(component.goalsWithProgress[0].goal.goalId).toBe(1);
      expect(component.goalsWithProgress[0].progress?.currentValue).toBe(85.0);
      expect(component.goalsWithProgress[1].goal.goalId).toBe(2);
      expect(component.goalsWithProgress[1].progress?.currentValue).toBe(30.0);
      expect(component.goalsLoading).toBeFalsy();
    });

    it('should still show the goal with progress: null if one progress call fails', () => {
      component.selectPanel('goals');

      const goalsReq = httpMock.expectOne('http://localhost:8080/api/goals?status=ACTIVE');
      goalsReq.flush([mockGoal]);

      const progressReq = httpMock.expectOne('http://localhost:8080/api/goals/1/progress');
      progressReq.flush({ message: 'error' }, { status: 500, statusText: 'Server Error' });

      expect(component.goalsWithProgress.length).toBe(1);
      expect(component.goalsWithProgress[0].progress).toBeNull();
    });

    it('should not re-fetch if the goals panel is closed and reopened', () => {
      component.selectPanel('goals');
      httpMock.expectOne('http://localhost:8080/api/goals?status=ACTIVE').flush([mockGoal]);
      httpMock.expectOne('http://localhost:8080/api/goals/1/progress').flush(mockProgress);

      component.selectPanel('goals'); // close
      component.selectPanel('goals'); // reopen

      httpMock.expectNone('http://localhost:8080/api/goals?status=ACTIVE');
    });

    it('should set goalsError if the goals list request fails', () => {
      component.selectPanel('goals');
      httpMock
        .expectOne('http://localhost:8080/api/goals?status=ACTIVE')
        .flush({ message: 'Could not load goals' }, { status: 500, statusText: 'Server Error' });

      expect(component.goalsLoading).toBeFalsy();
      expect(component.goalsError).toBe('Could not load goals');
    });

    it('should toggle expand/collapse state per goal independently', () => {
      expect(component.isGoalExpanded(1)).toBeFalsy();

      component.toggleGoalExpand(1);
      expect(component.isGoalExpanded(1)).toBeTruthy();
      expect(component.isGoalExpanded(2)).toBeFalsy();

      component.toggleGoalExpand(1);
      expect(component.isGoalExpanded(1)).toBeFalsy();
    });

    it('should return the correct unit for a known category and empty string for unknown', () => {
      expect(component.getUnitFor('ELECTRICITY')).toBe('kWh');
      expect(component.getUnitFor('SOMETHING_UNKNOWN')).toBe('');
    });
  });

  it('should navigate to /home/dashboard when goToDashboard is called', () => {
    const navigateSpy = vi.spyOn(router, 'navigate');
    component.goToDashboard();
    expect(navigateSpy).toHaveBeenCalledWith(['/home/dashboard']);
  });
});
