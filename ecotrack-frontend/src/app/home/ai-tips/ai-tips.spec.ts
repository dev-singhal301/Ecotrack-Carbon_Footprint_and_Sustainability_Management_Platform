import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AiTips } from './ai-tips';

describe('AiTips', () => {
  let component: AiTips;
  let fixture: ComponentFixture<AiTips>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AiTips],
    }).compileComponents();

    fixture = TestBed.createComponent(AiTips);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
