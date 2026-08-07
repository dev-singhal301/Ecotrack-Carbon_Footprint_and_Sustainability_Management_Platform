import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EcoIdentityComponent } from './eco-identity';

describe('EcoIdentity', () => {
  let component: EcoIdentityComponent;
  let fixture: ComponentFixture<EcoIdentityComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EcoIdentityComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EcoIdentityComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});