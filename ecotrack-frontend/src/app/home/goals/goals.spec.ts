import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Goals } from './goals';

describe('Goals Component Unit Tests', () => {
  let component: Goals;
  let fixture: ComponentFixture<Goals>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReactiveFormsModule, HttpClientTestingModule, Goals]
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
      targetValue: null
    });
    expect(component.goalForm.invalid).toBeTrue();
  });

  it('should validate form structures when correct values are provided', () => {
    component.goalForm.patchValue({
      goalType: 'REDUCE_WATER_USAGE',
      targetValue: 45,
      durationDays: 7,
      baselinePeriodDays: 30
    });
    expect(component.goalForm.valid).toBeTrue();
  });
});