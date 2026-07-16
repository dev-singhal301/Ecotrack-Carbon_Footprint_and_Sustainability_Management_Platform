import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TrackingComponent } from './tracking';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

describe('TrackingComponent', () => {
  let component: TrackingComponent;
  let fixture: ComponentFixture<TrackingComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TrackingComponent, FormsModule, CommonModule]
    }).compileComponents();

    fixture = TestBed.createComponent(TrackingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create tracking component', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize default parameters correctly', () => {
    expect(component.entry.category).toBe('');
    expect(component.entry.amount).toBeNull();
    expect(component.entry.activityDate).toBeTruthy();
  });

  it('should update measurement unit dynamically on category change', () => {
    component.entry.category = 'ELECTRICITY';
    component.onCategoryChange();
    expect(component.entry.unit).toBe('kWh');

    component.entry.category = 'TRANSPORTATION';
    component.onCategoryChange();
    expect(component.entry.unit).toBe('km');
  });
});