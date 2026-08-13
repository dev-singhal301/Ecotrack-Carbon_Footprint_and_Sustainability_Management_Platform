import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { EcoIdentityService, UserProfileData } from './eco-identity.service';

@Component({
  selector: 'app-eco-identity',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './eco-identity.html',
  styleUrl: './eco-identity.css',
})
export class EcoIdentityComponent implements OnInit, OnDestroy {
  private readonly ecoIdentityService = inject(EcoIdentityService);
  private profileSub!: Subscription;

  profile: UserProfileData | null = null;

  // Name Editing State
  isEditingName = false;
  tempName = '';
  isSavingName = false;

  // Avatar Upload State
  isUploadingAvatar = false;

  ngOnInit(): void {
    // Keep profile in sync with shared state across components
    this.profileSub = this.ecoIdentityService.profile$.subscribe((data) => {
      this.profile = data;
    });

    // Fetch initial profile data from backend
    this.ecoIdentityService.fetchProfile().subscribe({
      error: (err) => console.error('Error fetching user profile:', err),
    });
  }

  ngOnDestroy(): void {
    if (this.profileSub) {
      this.profileSub.unsubscribe();
    }
  }

  get xpProgressPercent(): number {
    if (!this.profile || !this.profile.nextLevelXpTarget) return 0;
    const percent = (this.profile.currentXp / this.profile.nextLevelXpTarget) * 100;
    return Math.min(100, Math.max(0, percent));
  }

  // Name Edit Actions
  startNameEdit(): void {
    if (!this.profile) return;
    this.tempName = this.profile.name;
    this.isEditingName = true;
  }

  cancelNameEdit(): void {
    this.isEditingName = false;
    this.tempName = '';
  }

  saveName(): void {
    if (!this.tempName.trim() || this.tempName === this.profile?.name) {
      this.cancelNameEdit();
      return;
    }

    this.isSavingName = true;
    this.ecoIdentityService.updateName(this.tempName.trim()).subscribe({
      next: () => {
        this.isSavingName = false;
        this.isEditingName = false;
      },
      error: (err) => {
        console.error('Failed to update user name:', err);
        this.isSavingName = false;
      },
    });
  }

  // Avatar Image Upload Action
  onAvatarFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;

    const file = input.files[0];
    this.isUploadingAvatar = true;

    this.ecoIdentityService.uploadAvatar(file).subscribe({
      next: () => {
        this.isUploadingAvatar = false;
      },
      error: (err) => {
        console.error('Failed to upload avatar image:', err);
        this.isUploadingAvatar = false;
      },
    });
  }
}