import { Component, OnInit, OnDestroy, inject, ViewChild, ElementRef, ChangeDetectorRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { EcoIdentityService, UserProfileData, BadgeItem } from './eco-identity.service';
import { AuthService } from '../../auth.service';

@Component({
  selector: 'app-eco-identity',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './eco-identity.html',
  styleUrl: './eco-identity.css',
})
export class EcoIdentityComponent implements OnInit, OnDestroy {
  private readonly ecoIdentityService = inject(EcoIdentityService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private profileSub!: Subscription;

  @ViewChild('cropCanvas') cropCanvasRef?: ElementRef<HTMLCanvasElement>;

  profile: UserProfileData | null = null;

  // Fallback avatar for missing/broken images
  defaultAvatar = 'https://api.dicebear.com/7.x/bottts/svg?seed=EcoUser';

  // --- Badge Gallery State ---
  private rawBadges: BadgeItem[] = [];
  badges: BadgeItem[] = [];
  isLoadingBadges = true;
  defaultBadgeIcon = '/badges/ch_1.avif'; // Fallback icon path if badge image fails to load

  // --- Badge Detail Modal State (click-to-expand pop-up) ---
  selectedBadge: BadgeItem | null = null;

  // Name Editing State
  isEditingName = false;
  tempName = '';
  isSavingName = false;

  // Avatar Upload State
  isUploadingAvatar = false;

  // --- Avatar Crop Modal State (Google/WhatsApp style) ---
  showCropModal = false;
  private sourceImage: HTMLImageElement | null = null;

  readonly canvasSize = 280; // on-screen preview size (px)
  private readonly exportSize = 480; // resolution of the uploaded image (px)

  zoom = 1;
  minZoom = 1;
  maxZoom = 3;

  private offsetX = 0;
  private offsetY = 0;
  private drawnWidth = 0;
  private drawnHeight = 0;
  private baseScale = 1;

  private isDragging = false;
  private dragStartX = 0;
  private dragStartY = 0;
  private dragStartOffsetX = 0;
  private dragStartOffsetY = 0;

  // --- Delete Account Confirmation Modal State ---
  showDeleteModal = false;
  deleteConfirmText = '';
  isDeletingAccount = false;
  deleteError: string | null = null;

  // --- Report Export State ---
  isDownloadingPdf = false;
  isDownloadingExcel = false;
  isDownloadingCsv = false;
  downloadError: string | null = null;

  ngOnInit(): void {
    // Keep profile in sync with shared state across components
    this.profileSub = this.ecoIdentityService.profile$.subscribe((data) => {
      this.profile = data;
      // Re-apply badge filters whenever user profile data (e.g. userLevel) arrives or updates
      this.applyBadgeFilters();
      this.cdr.markForCheck();
    });

    // Fetch initial profile data from backend
    this.ecoIdentityService.fetchProfile().subscribe({
      next: () => this.cdr.markForCheck(),
      error: (err: HttpErrorResponse) => console.error('Error fetching user profile:', err),
    });

    // Fetch user's earned badges
    this.loadBadges();
  }

  ngOnDestroy(): void {
    if (this.profileSub) {
      this.profileSub.unsubscribe();
    }
  }

  // --- Badges Methods ---
  loadBadges(): void {
    this.isLoadingBadges = true;
    this.ecoIdentityService.getUserBadges().subscribe({
      next: (badgeList) => {
        this.rawBadges = badgeList || [];
        this.applyBadgeFilters();
        this.isLoadingBadges = false;
        this.cdr.markForCheck();
      },
      error: (err: HttpErrorResponse) => {
        console.error('Failed to fetch user badges:', err);
        this.isLoadingBadges = false;
        this.cdr.markForCheck();
      },
    });
  }

  /**
   * Filters out badges that are locked/unearned or exceed the user's current level milestone.
   */
  private applyBadgeFilters(): void {
    if (!this.rawBadges.length) {
      this.badges = [];
      return;
    }

    let filtered = [...this.rawBadges];

    // 1. Keep only unlocked or explicitly awarded badges
    filtered = filtered.filter((badge) => {
      return badge.unlocked ?? badge.isUnlocked ?? !!badge.awardedAt;
    });

    // 2. Safeguard: Filter out level milestone badges that exceed the user's current level
    if (this.profile?.userLevel !== undefined) {
      const currentLevel = this.profile.userLevel;
      filtered = filtered.filter((badge) => {
        if (badge.category?.toUpperCase() === 'MILESTONE' && badge.requiredLevel !== undefined) {
          return currentLevel >= badge.requiredLevel;
        }
        return true;
      });
    }

    this.badges = filtered;
  }

  formatBadgeSource(category: string): string {
    switch (category?.toUpperCase()) {
      case 'CHALLENGE':
        return 'Challenge';
      case 'LEADERBOARD':
        return 'Leaderboard Rank';
      case 'MILESTONE':
        return 'Level Milestone';
      default:
        return category || 'Achievement';
    }
  }

  onBadgeImageError(event: Event): void {
    const target = event.target as HTMLImageElement;
    if (target) {
      target.src = this.defaultBadgeIcon;
    }
  }

  // --- Badge Detail Modal Actions (click-to-expand pop-up) ---
  openBadgeDetail(badge: BadgeItem): void {
    this.selectedBadge = badge;
    this.cdr.markForCheck();
  }

  closeBadgeDetail(): void {
    this.selectedBadge = null;
    this.cdr.markForCheck();
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.selectedBadge) {
      this.closeBadgeDetail();
    }
  }

  // --- Level & XP Calculations ---

  get currentLevelXpProgress(): number {
    return this.profile?.currentLevelXpProgress || 0;
  }

  get xpForNextLevel(): number {
    return this.profile?.xpForNextLevel || 0;
  }

  get xpProgressPercent(): number {
    const target = this.xpForNextLevel;
    if (target <= 0) return 100; // Fallback to prevent division by zero

    const percent = (this.currentLevelXpProgress / target) * 100;
    return Math.min(100, Math.max(0, percent));
  }

  // Fallback image handler
  onImageError(event: Event): void {
    (event.target as HTMLImageElement).src = this.defaultAvatar;
  }

  // Name Editing Actions
  startNameEdit(): void {
    if (!this.profile) return;
    this.tempName = this.profile.name;
    this.isEditingName = true;
    this.cdr.markForCheck();
  }

  cancelNameEdit(): void {
    this.isEditingName = false;
    this.tempName = '';
    this.cdr.markForCheck();
  }

  saveName(): void {
    if (!this.tempName.trim() || this.tempName === this.profile?.name) {
      this.cancelNameEdit();
      return;
    }

    this.isSavingName = true;
    this.cdr.markForCheck();

    this.ecoIdentityService.updateName(this.tempName.trim()).subscribe({
      next: () => {
        this.isSavingName = false;
        this.isEditingName = false;
        this.cdr.markForCheck();
      },
      error: (err: HttpErrorResponse) => {
        console.error('Failed to update user name:', err);
        this.isSavingName = false;
        this.cdr.markForCheck();
      },
    });
  }

  // --- Avatar selection: opens the crop modal instead of uploading immediately ---
  onAvatarFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;

    const file = input.files[0];

    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        this.sourceImage = img;
        this.zoom = 1;
        this.offsetX = 0;
        this.offsetY = 0;
        this.showCropModal = true;
        this.cdr.markForCheck();

        // Wait a tick so the canvas element exists in the DOM before drawing
        setTimeout(() => {
          this.setupCanvas();
          this.cdr.markForCheck();
        }, 0);
      };
      img.onerror = () => console.error('Failed to load selected image for cropping.');
      img.src = reader.result as string;
    };
    reader.onerror = () => console.error('Failed to read selected file.');
    reader.readAsDataURL(file);

    input.value = ''; // Reset input so the same file can be re-selected later
  }

  private setupCanvas(): void {
    if (!this.sourceImage) return;
    const { width, height } = this.sourceImage;
    // baseScale ensures the image always fully covers the circular canvas at zoom = 1
    this.baseScale = Math.max(this.canvasSize / width, this.canvasSize / height);
    this.recalculateDrawnSize();
    this.drawCrop();
  }

  private recalculateDrawnSize(): void {
    if (!this.sourceImage) return;
    this.drawnWidth = this.sourceImage.width * this.baseScale * this.zoom;
    this.drawnHeight = this.sourceImage.height * this.baseScale * this.zoom;
    this.clampOffsets();
  }

  private clampOffsets(): void {
    // Keeps the image edges from ever exposing empty space inside the circle
    const maxOffsetX = Math.max(0, (this.drawnWidth - this.canvasSize) / 2);
    const maxOffsetY = Math.max(0, (this.drawnHeight - this.canvasSize) / 2);
    this.offsetX = Math.min(maxOffsetX, Math.max(-maxOffsetX, this.offsetX));
    this.offsetY = Math.min(maxOffsetY, Math.max(-maxOffsetY, this.offsetY));
  }

  onZoomChange(): void {
    this.recalculateDrawnSize();
    this.drawCrop();
  }

  private drawCrop(): void {
    const canvas = this.cropCanvasRef?.nativeElement;
    if (!canvas || !this.sourceImage) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const size = this.canvasSize;
    ctx.clearRect(0, 0, size, size);

    // Dimmed backdrop copy of the full image, for spatial context outside the crop circle
    ctx.save();
    ctx.globalAlpha = 0.35;
    ctx.translate(size / 2 + this.offsetX, size / 2 + this.offsetY);
    ctx.drawImage(this.sourceImage, -this.drawnWidth / 2, -this.drawnHeight / 2, this.drawnWidth, this.drawnHeight);
    ctx.restore();

    // Full-brightness copy clipped to the circle - this is what gets uploaded
    ctx.save();
    ctx.beginPath();
    ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
    ctx.closePath();
    ctx.clip();
    ctx.translate(size / 2 + this.offsetX, size / 2 + this.offsetY);
    ctx.drawImage(this.sourceImage, -this.drawnWidth / 2, -this.drawnHeight / 2, this.drawnWidth, this.drawnHeight);
    ctx.restore();

    // Circle outline
    ctx.save();
    ctx.beginPath();
    ctx.arc(size / 2, size / 2, size / 2 - 1, 0, Math.PI * 2);
    ctx.strokeStyle = '#10b981';
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.restore();
  }

  // --- Drag-to-reposition (mouse & touch via Pointer Events) ---
  onCanvasPointerDown(event: PointerEvent): void {
    const canvas = this.cropCanvasRef?.nativeElement;
    if (!canvas) return;
    canvas.setPointerCapture(event.pointerId);
    this.isDragging = true;
    this.dragStartX = event.clientX;
    this.dragStartY = event.clientY;
    this.dragStartOffsetX = this.offsetX;
    this.dragStartOffsetY = this.offsetY;
  }

  onCanvasPointerMove(event: PointerEvent): void {
    if (!this.isDragging) return;
    const dx = event.clientX - this.dragStartX;
    const dy = event.clientY - this.dragStartY;
    this.offsetX = this.dragStartOffsetX + dx;
    this.offsetY = this.dragStartOffsetY + dy;
    this.clampOffsets();
    this.drawCrop();
  }

  onCanvasPointerUp(event: PointerEvent): void {
    this.isDragging = false;
    const canvas = this.cropCanvasRef?.nativeElement;
    if (canvas && canvas.hasPointerCapture(event.pointerId)) {
      canvas.releasePointerCapture(event.pointerId);
    }
  }

  cancelCrop(): void {
    this.showCropModal = false;
    this.sourceImage = null;
    this.cdr.markForCheck();
  }

  // --- Confirm: render the final crop at higher resolution and upload it ---
  confirmCrop(): void {
    if (!this.sourceImage) return;

    const exportCanvas = document.createElement('canvas');
    exportCanvas.width = this.exportSize;
    exportCanvas.height = this.exportSize;
    const ctx = exportCanvas.getContext('2d');
    if (!ctx) return;

    const scaleFactor = this.exportSize / this.canvasSize;
    const drawnWidth = this.drawnWidth * scaleFactor;
    const drawnHeight = this.drawnHeight * scaleFactor;
    const offsetX = this.offsetX * scaleFactor;
    const offsetY = this.offsetY * scaleFactor;

    ctx.save();
    ctx.beginPath();
    ctx.arc(this.exportSize / 2, this.exportSize / 2, this.exportSize / 2, 0, Math.PI * 2);
    ctx.closePath();
    ctx.clip();
    ctx.translate(this.exportSize / 2 + offsetX, this.exportSize / 2 + offsetY);
    ctx.drawImage(this.sourceImage, -drawnWidth / 2, -drawnHeight / 2, drawnWidth, drawnHeight);
    ctx.restore();

    exportCanvas.toBlob(
      (blob) => {
        if (!blob) {
          console.error('Failed to generate cropped avatar image.');
          return;
        }
        const croppedFile = new File([blob], `avatar-${Date.now()}.png`, { type: 'image/png' });
        this.uploadCroppedAvatar(croppedFile);
      },
      'image/png',
      0.95
    );
  }

  private uploadCroppedAvatar(file: File): void {
    this.isUploadingAvatar = true;
    this.cdr.markForCheck();

    this.ecoIdentityService.uploadAvatar(file).subscribe({
      next: () => {
        this.isUploadingAvatar = false;
        this.showCropModal = false;
        this.sourceImage = null;
        this.cdr.markForCheck();
      },
      error: (err: HttpErrorResponse) => {
        console.error('Failed to upload avatar image:', err);
        this.isUploadingAvatar = false;
        this.cdr.markForCheck();
      },
    });
  }

  // --- Report Export ---
  downloadPdf(): void {
    this.downloadReportAs('pdf');
  }

  downloadExcel(): void {
    this.downloadReportAs('xlsx');
  }

  downloadCsv(): void {
    this.downloadReportAs('csv');
  }

  private downloadReportAs(format: 'pdf' | 'xlsx' | 'csv'): void {
    const loadingFlag = this.loadingFlagFor(format);
    if (this[loadingFlag]) return;

    this[loadingFlag] = true;
    this.downloadError = null;
    this.cdr.markForCheck();

    this.ecoIdentityService.downloadReport(format).subscribe({
      next: (response) => this.saveBlobResponse(response, format),
      error: (err: HttpErrorResponse) => {
        console.error(`Failed to download ${format} report:`, err);
        this.downloadError = 'Something went wrong generating your report. Please try again.';
        this[loadingFlag] = false;
        this.cdr.markForCheck();
      },
      complete: () => {
        this[loadingFlag] = false;
        this.cdr.markForCheck();
      },
    });
  }

  private loadingFlagFor(format: 'pdf' | 'xlsx' | 'csv'): 'isDownloadingPdf' | 'isDownloadingExcel' | 'isDownloadingCsv' {
    switch (format) {
      case 'pdf':
        return 'isDownloadingPdf';
      case 'xlsx':
        return 'isDownloadingExcel';
      case 'csv':
        return 'isDownloadingCsv';
    }
  }

  // Triggers a browser save-as for the blob, naming the file from the
  // server's Content-Disposition header when present, otherwise falling
  // back to a sensible default.
  private saveBlobResponse(response: HttpResponse<Blob>, format: string): void {
    const body = response.body;
    if (!body) return;

    const disposition = response.headers.get('Content-Disposition') ?? '';
    const match = /filename="?([^"]+)"?/i.exec(disposition);
    const filename = match?.[1] ?? `sustainability-report.${format}`;

    const blobUrl = window.URL.createObjectURL(body);
    const link = document.createElement('a');
    link.href = blobUrl;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(blobUrl);
  }

  // --- Delete Account Modal ---
  openDeleteModal(): void {
    this.deleteConfirmText = '';
    this.deleteError = null;
    this.showDeleteModal = true;
    this.cdr.markForCheck();
  }

  cancelDeleteModal(): void {
    if (this.isDeletingAccount) return;
    this.showDeleteModal = false;
    this.deleteConfirmText = '';
    this.deleteError = null;
    this.cdr.markForCheck();
  }

  get isDeleteConfirmed(): boolean {
    return this.deleteConfirmText.trim() === 'DELETE';
  }

  confirmDeleteAccount(): void {
    if (!this.isDeleteConfirmed || this.isDeletingAccount) return;

    this.isDeletingAccount = true;
    this.deleteError = null;
    this.cdr.markForCheck();

    this.ecoIdentityService.deleteAccount().subscribe({
      next: () => {
        this.authService.logout();
        this.router.navigate(['/auth']);
      },
      error: (err: HttpErrorResponse) => {
        console.error('Failed to delete account:', err);
        this.isDeletingAccount = false;
        this.deleteError = 'Something went wrong deleting your account. Please try again.';
        this.cdr.markForCheck();
      },
    });
  }
}