import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { AiTips } from './ai-tips';
import { RecommendationResponse } from './recommendation.model';

describe('AiTips', () => {
  let component: AiTips;
  let fixture: ComponentFixture<AiTips>;
  let router: Router;
  let httpMock: HttpTestingController;

  const mockResponse: RecommendationResponse = {
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
    component.selectPanel('goals');
    expect(component.activePanel).toBe('goals');
    component.selectPanel('goals');
    expect(component.activePanel).toBeNull();
  });

  it('should call GET /api/recommendations exactly once when the recommendations panel is first opened', () => {
    component.selectPanel('recommendations');

    const req = httpMock.expectOne('http://localhost:8080/api/recommendations');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(component.recommendations).toEqual(mockResponse);
    expect(component.recLoading).toBeFalsy();
  });

  it('should not re-fetch if the recommendations panel is closed and reopened', () => {
    component.selectPanel('recommendations');
    const req = httpMock.expectOne('http://localhost:8080/api/recommendations');
    req.flush(mockResponse);

    component.selectPanel('recommendations'); // close
    component.selectPanel('recommendations'); // reopen

    httpMock.expectNone('http://localhost:8080/api/recommendations');
  });

  it('should show only the top category by default and reveal more via Explore', () => {
    component.selectPanel('recommendations');
    httpMock.expectOne('http://localhost:8080/api/recommendations').flush(mockResponse);

    expect(component.revealedCategories.length).toBe(1);
    expect(component.revealedCategories[0].category).toBe('TRANSPORTATION');

    component.exploreNext();
    expect(component.revealedCategories.length).toBe(2);
    expect(component.revealedCategories[1].category).toBe('ELECTRICITY');
  });

  it('should stop revealing once all 6 categories are shown', () => {
    component.selectPanel('recommendations');
    httpMock.expectOne('http://localhost:8080/api/recommendations').flush(mockResponse);

    for (let i = 0; i < 10; i++) {
      component.exploreNext();
    }

    expect(component.revealedCategories.length).toBe(6);
    expect(component.hasMoreToExplore).toBeFalsy();
  });

  it('should return null from getTipFor for categories outside the top 3', () => {
    component.selectPanel('recommendations');
    httpMock.expectOne('http://localhost:8080/api/recommendations').flush(mockResponse);

    expect(component.getTipFor('WASTE')).toBeNull();
    expect(component.getTipFor('TRANSPORTATION')?.suggestionText).toBe('Use public transport twice a week');
  });

  it('should set recError and stop loading if the request fails', () => {
    component.selectPanel('recommendations');
    const req = httpMock.expectOne('http://localhost:8080/api/recommendations');
    req.flush({ message: 'Server error' }, { status: 500, statusText: 'Server Error' });

    expect(component.recLoading).toBeFalsy();
    expect(component.recError).toBe('Server error');
  });

  it('should navigate to /home/dashboard when goToDashboard is called', () => {
    const navigateSpy = vi.spyOn(router, 'navigate');
    component.goToDashboard();
    expect(navigateSpy).toHaveBeenCalledWith(['/home/dashboard']);
  });
});
