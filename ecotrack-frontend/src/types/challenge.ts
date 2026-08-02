// ====================================================================
// CHALLENGE ENUMS & CORE TYPES
// ====================================================================

export type ChallengeType = 'CARBON_REDUCTION' | 'ACTIVITY_BASED';

export type TrackingType = 'COUNT' | 'DISTANCE' | 'DURATION' | 'BOOLEAN';

export type ChallengeCategory = 
  | 'OVERALL'
  | 'TRANSPORTATION' 
  | 'ELECTRICITY' 
  | 'FOOD' 
  | 'WASTE' 
  | 'WATER' 
  | 'FUEL';

export type ChallengeTab = 'ALL' | 'ACTIVE' | 'UPCOMING' | 'JOINED' | 'COMPLETED';

export type DerivedChallengeStatus = 'UPCOMING' | 'ACTIVE' | 'ENDED' | 'CANCELLED';

export type UserChallengeStatus = 'JOINED' | 'IN_PROGRESS' | 'COMPLETED' | 'EXPIRED' | 'CANCELLED';

// ====================================================================
// ASSET TYPES
// ====================================================================

export interface AssetOption {
  id: string;
  label: string;
  url: string;
}

export interface MilestoneBadgeOption extends AssetOption {
  description?: string;
}

// ====================================================================
// API PAYLOADS & ENTITY MODELS
// ====================================================================

export interface CreateChallengePayload {
  title: string;
  description: string;
  category: ChallengeCategory;
  challengeType: ChallengeType;
  trackingType?: TrackingType | null;
  targetValue: number;
  unit: string;
  rewardXp: number;
  cardImageUrl: string;
  badgeImageUrl: string;
  startDate: string;
  endDate: string;
  createdBy: number;
}

export interface Challenge {
  challengeId: number;
  title: string;
  description: string;
  category: ChallengeCategory;
  challengeType: ChallengeType;
  trackingType: TrackingType | null;
  targetValue: number;
  unit: string;
  rewardXp: number;
  badgeImageUrl: string;
  cardImageUrl: string;
  startDate: string;
  endDate: string;
  createdBy: number;
  cancelled: boolean;
  cancelledAt?: string | null;
  createdAt: string;
  updatedAt: string;
  status?: DerivedChallengeStatus;
}

export interface UserChallenge {
  userChallengeId: number;
  userId: number;
  challengeId: number;
  baselineValue?: number | null;
  startEntryId?: number | null;
  effectiveTarget?: number | null;
  currentValue: number;
  completionPercentage: number;
  status: UserChallengeStatus;
  joinedAt: string;
  completedAt?: string | null;
  challenge?: Challenge;
}