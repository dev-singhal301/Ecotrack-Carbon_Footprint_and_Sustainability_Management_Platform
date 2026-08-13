import { AssetOption, MilestoneBadgeOption } from '../types/challenge';

export const CARD_HEADER_IMAGES: AssetOption[] = Array.from({ length: 10 }, (_, index) => ({
  id: `card_${index + 1}`,
  label: `Card Header ${index + 1}`,
  url: `/assets/images/ch_card_${index + 1}.avif`,
}));

export const CHALLENGE_BADGES: AssetOption[] = Array.from({ length: 6 }, (_, index) => ({
  id: `ch_${index + 1}`,
  label: `Challenge Badge ${index + 1}`,
  url: `/assets/badges/ch_${index + 1}.avif`,
}));

export const LEADERBOARD_BADGES = {
  gold: {
    id: 'lb_gold',
    label: 'Gold Rank (1st Place)',
    url: '/assets/badges/lb_gold.avif',
  },
  silver: {
    id: 'lb_silver',
    label: 'Silver Rank (2nd Place)',
    url: '/assets/badges/lb_silver.avif',
  },
  bronze: {
    id: 'lb_bronze',
    label: 'Bronze Rank (3rd Place)',
    url: '/assets/badges/lb_bronze.avif',
  },
} as const;

export const MILESTONE_BADGES: MilestoneBadgeOption[] = [
  { id: 'ms_eco_1', label: 'Novice Eco', url: '/assets/badges/ms_eco_1.avif', description: 'Level 1 Rank' },
  { id: 'ms_eco_2', label: 'Eco Scout', url: '/assets/badges/ms_eco_2.avif', description: 'Level 2 Rank' },
  { id: 'ms_eco_3', label: 'Eco Explorer', url: '/assets/badges/ms_eco_3.avif', description: 'Level 3 Rank' },
  { id: 'ms_eco_4', label: 'Eco Pioneer', url: '/assets/badges/ms_eco_4.avif', description: 'Level 4 Rank' },
  { id: 'ms_eco_5', label: 'Eco Guardian', url: '/assets/badges/ms_eco_5.avif', description: 'Level 5 Rank' },
  { id: 'ms_eco_6', label: 'Eco Sentinel', url: '/assets/badges/ms_eco_6.avif', description: 'Level 6 Rank' },
  { id: 'ms_eco_7', label: 'Eco Champion', url: '/assets/badges/ms_eco_7.avif', description: 'Level 7 Rank' },
  { id: 'ms_eco_8', label: 'Eco Master', url: '/assets/badges/ms_eco_8.avif', description: 'Level 8 Rank' },
  { id: 'ms_eco_9', label: 'Eco Hero', url: '/assets/badges/ms_eco_9.avif', description: 'Level 9 Rank' },
  { id: 'ms_eco_10', label: 'Mythic Eco', url: '/assets/badges/ms_eco_10.avif', description: 'Level 10 Rank' },
];

export const CHALLENGE_CATEGORIES = [
  'OVERALL',
  'TRANSPORTATION',
  'ELECTRICITY',
  'FOOD',
  'WASTE',
  'WATER',
  'FUEL',
] as const;