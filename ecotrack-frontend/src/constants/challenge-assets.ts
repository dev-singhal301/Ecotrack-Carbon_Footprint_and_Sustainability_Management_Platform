import { AssetOption, MilestoneBadgeOption } from '../types/challenge';

// ====================================================================
// 1. CARD HEADER BANNERS (10 PRESET IMAGES)
// ====================================================================
export const CARD_HEADER_IMAGES: AssetOption[] = Array.from({ length: 10 }, (_, index) => ({
  id: `card_${index + 1}`,
  label: `Card Header ${index + 1}`,
  url: `/images/ch_card_${index + 1}.avif`,
}));

// ====================================================================
// 2. CHALLENGE BADGES (ADMIN SELECTION FOR CREATION FORM)
// ====================================================================
export const CHALLENGE_BADGES: AssetOption[] = Array.from({ length: 6 }, (_, index) => ({
  id: `ch_${index + 1}`,
  label: `Challenge Badge ${index + 1}`,
  url: `/badges/ch_${index + 1}.avif`,
}));

// ====================================================================
// 3. LEADERBOARD PODIUM BADGES (TOP 3 SPOTS)
// ====================================================================
export const LEADERBOARD_BADGES = {
  gold: {
    id: 'lb_gold',
    label: 'Gold Rank (1st Place)',
    url: '/badges/lb_gold.avif',
  },
  silver: {
    id: 'lb_silver',
    label: 'Silver Rank (2nd Place)',
    url: '/badges/lb_silver.avif',
  },
  bronze: {
    id: 'lb_bronze',
    label: 'Bronze Rank (3rd Place)',
    url: '/badges/lb_bronze.avif',
  },
} as const;

// ====================================================================
// 4. MILESTONE / LEVEL PROGRESSION BADGES
// ====================================================================
export const MILESTONE_BADGES: MilestoneBadgeOption[] = [
  { id: 'earth_seedling', label: 'Earth Seedling', url: '/badges/ms_earth_seedling.avif', description: 'Level 1 Rank' },
  { id: 'green_scout', label: 'Green Scout', url: '/badges/ms_green_scout.avif', description: 'Level 2 Rank' },
  { id: 'eco_gardener', label: 'Eco Gardener', url: '/badges/ms_eco_gardener.avif', description: 'Level 3 Rank' },
  { id: 'forest_ranger', label: 'Forest Ranger', url: '/badges/ms_forest_ranger.avif', description: 'Level 4 Rank' },
  { id: 'nature_sage', label: 'Nature Sage', url: '/badges/ms_nature_sage.avif', description: 'Level 5 Rank' },
  { id: 'earth_defender', label: 'Earth Defender', url: '/badges/ms_earth_defender.avif', description: 'Level 6 Rank' },
  { id: 'climate_hero', label: 'Climate Hero', url: '/badges/ms_climate_hero.avif', description: 'Level 7 Rank' },
  { id: 'planet_champion', label: 'Planet Champion', url: '/badges/ms_planet_champion.avif', description: 'Level 8 Rank' },
  { id: 'cosmic_guardian', label: 'Cosmic Guardian', url: '/badges/ms_cosmic_guardian.avif', description: 'Level 9 Rank' },
  { id: 'eco_legend', label: 'Eco Legend', url: '/badges/ms_eco_legend.avif', description: 'Level 10 Rank' },
];

// ====================================================================
// 5. STANDARD CATEGORY OPTIONS (MATCHES EMISSION FACTORS)
// ====================================================================
export const CHALLENGE_CATEGORIES = [
  'OVERALL',
  'TRANSPORTATION',
  'ELECTRICITY',
  'FOOD',
  'WASTE',
  'WATER',
  'FUEL',
] as const;