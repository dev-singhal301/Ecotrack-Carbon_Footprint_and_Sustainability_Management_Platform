import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { Leaderboard } from './leaderboard';
import { LeaderboardUser, UserHistoryItem } from './leaderboard.service';

describe('Leaderboard Component', () => {
  let component: Leaderboard;
  let fixture: ComponentFixture<Leaderboard>;
  let httpMock: HttpTestingController;

  const mockMonthlyData: LeaderboardUser[] = [
    { userId: 1, name: 'Alice', userRank: 'Eco Warrior', userLevel: 5, monthlyXp: 1200, rank: 1 },
    { userId: 2, name: 'Bob', userRank: 'Green Advocate', userLevel: 3, monthlyXp: 850, rank: 2 },
    { userId: 3, name: 'Charlie', userRank: 'Eco Seed', userLevel: 2, monthlyXp: 400, rank: 3 },
    { userId: 4, name: 'David', userRank: '', userLevel: 1, monthlyXp: 0, rank: 4 } // 0 XP -> Unranked test
  ];

  const mockCurrentUser: LeaderboardUser = {
    userId: 4,
    name: 'David',
    userRank: 'Eco Novice',
    userLevel: 1,
    monthlyXp: 0,
    rank: 4
  };

  const mockPreviousMonthData: LeaderboardUser[] = [
    { userId: 1, name: 'Alice', userRank: 'Eco Master', userLevel: 6, monthlyXp: 2100, rank: 1 }
  ];

  const mockUserHistoryData: UserHistoryItem[] = [
    { monthKey: '2026-07', totalXp: 450 }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Leaderboard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Leaderboard);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should fetch all endpoints and separate top 3 podium from remaining list', () => {
    fixture.detectChanges(); // triggers ngOnInit

    // Expect HTTP requests
    const reqMonthly = httpMock.expectOne('http://localhost:8080/api/leaderboard/monthly?limit=50');
    expect(reqMonthly.request.method).toBe('GET');
    reqMonthly.flush(mockMonthlyData);

    const reqUserRank = httpMock.expectOne('http://localhost:8080/api/leaderboard/user-rank');
    expect(reqUserRank.request.method).toBe('GET');
    reqUserRank.flush(mockCurrentUser);

    const reqPrevMonth = httpMock.expectOne('http://localhost:8080/api/leaderboard/previous-month?limit=3');
    expect(reqPrevMonth.request.method).toBe('GET');
    reqPrevMonth.flush(mockPreviousMonthData);

    const reqHistory = httpMock.expectOne('http://localhost:8080/api/leaderboard/user-history?limit=6');
    expect(reqHistory.request.method).toBe('GET');
    reqHistory.flush(mockUserHistoryData);

    // Verify state updates
    expect(component.top3Podium.length).toBe(3);
    expect(component.rankingsList.length).toBe(1);
    expect(component.firstPlace?.name).toBe('Alice');
    expect(component.secondPlace?.name).toBe('Bob');
    expect(component.thirdPlace?.name).toBe('Charlie');
  });

  it('should assign "Unranked" title to a user with 0 XP', () => {
    const zeroXpUser: LeaderboardUser = {
      userId: 10,
      name: 'Newbie',
      userRank: 'Leaderboard Pioneer',
      userLevel: 1,
      monthlyXp: 0,
      rank: 10
    };

    expect(component.getRankTitle(zeroXpUser)).toBe('Unranked');
  });

  it('should correctly format user XP display', () => {
    const user: LeaderboardUser = {
      userId: 5,
      name: 'Eve',
      userRank: 'Eco Ranger',
      userLevel: 4,
      monthlyXp: 950,
      rank: 5
    };

    expect(component.getUserXp(user)).toBe(950);
  });

  it('should format month keys correctly', () => {
    expect(component.formatMonthKey('2026-07')).toBe('Jul 2026');
  });
});
