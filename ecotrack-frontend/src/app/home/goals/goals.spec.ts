import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of } from 'rxjs';
import { Goals } from './goals';
import { GoalService } from './goals.service';

describe('Goals Component Unit Tests', () => {
  let component: Goals;
  let fixture: ComponentFixture<Goals>;
  let mockGoalService: any;

  beforeEach(async () => {
    // Create a mock service object utilizing Vitest's mocking utility
    mockGoalService = {
      getGoals: vi.fn().mockReturnValue(of([])),
      getGoalProgress: vi.fn().mockReturnValue(of({})),
      createGoal: vi.fn(),
      deleteGoal: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, HttpClientTestingModule, Goals],
      providers: [
        { provide: GoalService, useValue: mockGoalService },
        // No route params in these tests — the component just falls
        // back to its default ACTIVE tab.
        { provide: ActivatedRoute, useValue: { queryParams: of({}) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Goals);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize the goals tracker component context', () => {
    expect(component).toBeTruthy();
  });

  it('should invalidate form structures when required values are missing', () => {
    component.goalForm.patchValue({
      goalType: '',
      targetValue: null,
      endDate: ''
    });
    expect(component.goalForm.invalid).toBe(true);
  });

  it('should validate form structures when correct values are provided', () => {
    // Setup dynamic tomorrow date matching the validation constraints
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const tomorrowStr = tomorrow.toISOString().split('T')[0];

    component.goalForm.patchValue({
      goalType: 'REDUCE_WATER_USAGE',
      targetValue: 45,
      endDate: tomorrowStr
    });
    expect(component.goalForm.valid).toBe(true);
  });
});
