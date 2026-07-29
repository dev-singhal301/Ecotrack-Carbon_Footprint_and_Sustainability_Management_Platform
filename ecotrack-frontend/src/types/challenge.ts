// ====================================================================
// CHALLENGE ENUMS & CORE TYPES
// ====================================================================

export type ChallengeType = 'CARBON_REDUCTION' | 'ACTIVITY_BASED';

export type TrackingType = 'COUNT' | 'DISTANCE' | 'DURATION' | 'BOOLEAN';

/**
 * Categories supported by emission factors and challenge filtering.
 */
export type ChallengeCategory = 
  | 'TRANSPORTATION' 
  | 'ELECTRICITY' 
  | 'FOOD' 
  | 'WASTE' 
  | 'WATER' 
  | 'FUEL';

/**
 * Filter tabs available on the User Challenge Hub UI.
 */
export type ChallengeTab = 'ALL' | 'ACTIVE' | 'UPCOMING' | 'JOINED' | 'COMPLETED';

/**
 * Derived challenge lifecycle state evaluated at runtime based on start_date, end_date, and cancelled flag.
 */
export type DerivedChallengeStatus = 'UPCOMING' | 'ACTIVE' | 'ENDED' | 'CANCELLED';

/**
 * User participation status in user_challenges table.
 */
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

/**
 * Request payload sent from Admin Form when creating a new challenge.
 */
export interface CreateChallengePayload {
  title: string;
  description: string;
  category: ChallengeCategory;
  challengeType: ChallengeType;
  trackingType?: TrackingType | null; // NULL if CARBON_REDUCTION
  targetValue: number;
  unit: string;
  rewardXp: number;
  cardImageUrl: string;
  badgeImageUrl: string;
  startDate: string; // ISO date format: YYYY-MM-DD
  endDate: string;   // ISO date format: YYYY-MM-DD
  createdBy: number; // Admin user ID
}

/**
 * Full Challenge entity returned by backend GET /api/challenges endpoints.
 */
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
  // Derived state calculated by frontend or backend wrapper
  status?: DerivedChallengeStatus;
}

/**
 * User participation & progress entity (user_challenges table).
 */
export interface UserChallenge {
  userChallengeId: number;
  userId: number;
  challengeId: number;
  baselineValue?: number | null;     // Carbon-only frozen baseline
  startEntryId?: number | null;     // Carbon-only anchor entry
  effectiveTarget?: number | null;  // Carbon-only frozen target (kg ceiling)
  currentValue: number;
  completionPercentage: number;
  status: UserChallengeStatus;
  joinedAt: string;
  completedAt?: string | null;
  challenge?: Challenge;            // Embedded challenge info if joined query
}