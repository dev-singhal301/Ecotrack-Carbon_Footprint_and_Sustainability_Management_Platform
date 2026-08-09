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
