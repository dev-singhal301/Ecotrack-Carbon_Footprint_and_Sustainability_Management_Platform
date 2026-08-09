import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { ChallengesComponent } from './challenges';
import { ChallengeService, UserChallengeResponseDto } from './challenges.service';

function makeItem(overrides: Partial<UserChallengeResponseDto> = {}): UserChallengeResponseDto {
  return {
    challenge: {
      challengeId: 1,
      title: 'Cycle to Work',
      description: 'Cycle instead of driving',
      category: 'TRANSPORTATION',
      challengeType: 'ACTIVITY_BASED',
      trackingType: 'DISTANCE',
      targetValue: 50,
      unit: 'km',
      rewardXp: 150,
      badgeImageUrl: '',
      cardImageUrl: '',
      startDate: '2026-08-01',
      endDate: '2026-08-20',
      createdBy: 1,
      cancelled: false,
      status: 'ACTIVE'
    },
    userStatus: 'IN_PROGRESS',
    currentValue: 0,
    completionPercentage: 0,
    ...overrides
  };
}

describe('ChallengesComponent', () => {
  let component: ChallengesComponent;
  let fixture: ComponentFixture<ChallengesComponent>;
  let mockChallengeService: any;

  beforeEach(async () => {
    mockChallengeService = {
      getUserChallenges: vi.fn().mockReturnValue(of([])),
      joinChallenge: vi.fn(),
      claimReward: vi.fn(),
      logActivity: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, ChallengesComponent],
      providers: [
        { provide: ChallengeService, useValue: mockChallengeService },
        { provide: ActivatedRoute, useValue: { queryParams: of({}) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ChallengesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // ── canLogActivity / isCarbonType / isQuickLogType ──

  it('canLogActivity: true for an in-progress activity-based challenge', () => {
    const item = makeItem();
    expect(component.canLogActivity(item)).toBe(true);
  });

  it('canLogActivity: false for a carbon-reduction challenge', () => {
    const item = makeItem({
      challenge: { ...makeItem().challenge, challengeType: 'CARBON_REDUCTION', trackingType: undefined as any }
    });
    expect(component.canLogActivity(item)).toBe(false);
  });

  it('canLogActivity: false once the challenge is completed', () => {
    const item = makeItem({ userStatus: 'COMPLETED', completionPercentage: 100 });
    expect(component.canLogActivity(item)).toBe(false);
  });

  it('isQuickLogType: true for COUNT and BOOLEAN, false for DISTANCE/DURATION', () => {
    expect(component.isQuickLogType(makeItem({
      challenge: { ...makeItem().challenge, trackingType: 'COUNT' }
    }))).toBe(true);
    expect(component.isQuickLogType(makeItem({
      challenge: { ...makeItem().challenge, trackingType: 'BOOLEAN' }
    }))).toBe(true);
    expect(component.isQuickLogType(makeItem({
      challenge: { ...makeItem().challenge, trackingType: 'DISTANCE' }
    }))).toBe(false);
  });

  // ── logQuickActivity ──

  it('logQuickActivity: calls the service without an amount and updates the card in place, taking loggedToday from the server', () => {
    const item = makeItem({ challenge: { ...makeItem().challenge, trackingType: 'COUNT' } });
    const updated = makeItem({ currentValue: 1, completionPercentage: 20, loggedToday: true });
    mockChallengeService.logActivity.mockReturnValue(of(updated));
    component.challengeItems = [item];

    component.logQuickActivity(item);

    expect(mockChallengeService.logActivity).toHaveBeenCalledWith(1, undefined);
    expect(component.challengeItems[0]).toEqual(updated);
    expect(component.challengeItems[0].loggedToday).toBe(true);
  });

  it('logQuickActivity: marks loggedToday on the item on a 409 so the button disables itself', () => {
    const item = makeItem({ challenge: { ...makeItem().challenge, trackingType: 'BOOLEAN' } });
    mockChallengeService.logActivity.mockReturnValue(
      throwError(() => ({ status: 409, error: { message: 'Only one log per day is allowed for this challenge' } }))
    );
    component.challengeItems = [item];
    vi.spyOn(window, 'alert').mockImplementation(() => {});

    component.logQuickActivity(item);

    expect(component.challengeItems[0].loggedToday).toBe(true);
    expect(component.loggingInFlight.has(1)).toBe(false);
  });

  // ── logAmountActivity ──

  it('logAmountActivity: rejects a missing/zero amount without calling the service', () => {
    const item = makeItem();
    vi.spyOn(window, 'alert').mockImplementation(() => {});
    component.logAmounts[1] = null;

    component.logAmountActivity(item);

    expect(mockChallengeService.logActivity).not.toHaveBeenCalled();
  });

  it('logAmountActivity: submits the entered amount and clears the input on success', () => {
    const item = makeItem();
    const updated = makeItem({ currentValue: 5, completionPercentage: 10 });
    mockChallengeService.logActivity.mockReturnValue(of(updated));
    component.challengeItems = [item];
    component.logAmounts[1] = 5;

    component.logAmountActivity(item);

    expect(mockChallengeService.logActivity).toHaveBeenCalledWith(1, 5);
    expect(component.logAmounts[1]).toBeNull();
  });

  // ── filteredChallenges: tab filtering ──

  it('filteredChallenges: EXPIRED tab shows only userStatus EXPIRED, not challenge.status ENDED', () => {
    const expiredItem = makeItem({
      challenge: { ...makeItem().challenge, challengeId: 1, status: 'ENDED' },
      userStatus: 'EXPIRED'
    });
    const endedNotJoinedItem = makeItem({
      challenge: { ...makeItem().challenge, challengeId: 2, status: 'ENDED' },
      userStatus: 'NOT_JOINED'
    });
    const completedItem = makeItem({
      challenge: { ...makeItem().challenge, challengeId: 3, status: 'ENDED' },
      userStatus: 'COMPLETED'
    });
    component.challengeItems = [expiredItem, endedNotJoinedItem, completedItem];
    component.activeTab = 'EXPIRED';

    const result = component.filteredChallenges;

    expect(result).toEqual([expiredItem]);
  });
});