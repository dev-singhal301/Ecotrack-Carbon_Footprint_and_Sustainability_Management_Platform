// goal-insight-messages.ts

export type GoalStatus = 'ON_TRACK' | 'BEHIND' | 'MISSED' | 'COMPLETED';

export const GOAL_INSIGHT_MESSAGES: Record<string, Record<GoalStatus, string>> = {
  REDUCE_CARBON_EMISSIONS: {
    ON_TRACK: "You're on pace on your overall emissions goal — keep up the mix of habits that's getting you there.",
    BEHIND: "You're falling behind on your overall emissions goal — check which category is contributing most and start there.",
    MISSED: "Your overall emissions goal didn't close in time — a narrower, category-specific goal might be easier to hit next time.",
    COMPLETED: "Goal met! Your overall emissions are down — a new, slightly tighter target could keep the momentum going.",
  },
  REDUCE_TRANSPORTATION_EMISSIONS: {
    ON_TRACK: "You're on pace on your transportation goal — keep favoring public transport or carpooling over solo drives.",
    BEHIND: "You're falling behind on your transportation goal — try swapping one car trip this week for public transport or cycling.",
    MISSED: "Your transportation goal didn't close in time — consider setting a smaller weekly target instead of one long-term number.",
    COMPLETED: "Goal met! Your transportation emissions are down — a new, slightly tighter target could keep the momentum going.",
  },
  REDUCE_ELECTRICITY_CONSUMPTION: {
    ON_TRACK: "You're on pace on your electricity goal — keep unplugging idle devices and using off-peak hours where you can.",
    BEHIND: "You're falling behind on your electricity goal — switching remaining lights to LEDs is a quick way to close the gap.",
    MISSED: "Your electricity goal didn't close in time — a home energy audit could reveal where the biggest savings are hiding.",
    COMPLETED: "Goal met! Your electricity usage is down — try locking in the habit with a new, more ambitious target.",
  },
  REDUCE_FOOD_EMISSIONS: {
    ON_TRACK: "You're on pace on your food goal — keep favoring plant-based meals and planning ahead to cut waste.",
    BEHIND: "You're falling behind on your food goal — shifting even one more meal a week to plant-based could help close the gap.",
    MISSED: "Your food goal didn't close in time — smaller, more specific targets (like '3 plant-based meals/week') tend to stick better.",
    COMPLETED: "Goal met! Your food-related emissions are down — nice work keeping meals both sustainable and consistent.",
  },
  REDUCE_WASTE_GENERATION: {
    ON_TRACK: "You're on pace on your waste goal — keep sorting recyclables and composting where possible.",
    BEHIND: "You're falling behind on your waste goal — try separating recyclables more consistently this week to catch up.",
    MISSED: "Your waste goal didn't close in time — starting with just packaging waste can make the next attempt more manageable.",
    COMPLETED: "Goal met! Your waste output is down — composting or recycling habits are clearly paying off.",
  },
  REDUCE_WATER_USAGE: {
    ON_TRACK: "You're on pace on your water goal — keep showers short and taps off when not in use.",
    BEHIND: "You're falling behind on your water goal — check for any leaks and try shaving a minute off your showers.",
    MISSED: "Your water goal didn't close in time — fixing leaks first often gives the biggest single improvement.",
    COMPLETED: "Goal met! Your water usage is down — worth checking if a low-flow fixture upgrade could lock in the gains.",
  },
  REDUCE_FUEL_CONSUMPTION: {
    ON_TRACK: "You're on pace on your fuel goal — keep combining errands and carpooling where you can.",
    BEHIND: "You're falling behind on your fuel goal — try combining a couple of errands into a single trip this week.",
    MISSED: "Your fuel goal didn't close in time — carpooling for even a few trips a week can meaningfully move the number.",
    COMPLETED: "Goal met! Your fuel usage is down — a new target could help you build on this streak.",
  },
};

export function getGoalStatus(
  startDate: string,
  endDate: string,
  completionPercentage: number,
  completed: boolean
): GoalStatus {
  if (completed) return 'COMPLETED';

  const now = new Date();
  const start = new Date(startDate);
  const end = new Date(endDate);

  if (now > end) return 'MISSED';

  const totalDays = (end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24);
  const daysElapsed = (now.getTime() - start.getTime()) / (1000 * 60 * 60 * 24);
  const expectedProgress = totalDays > 0 ? (daysElapsed / totalDays) * 100 : 0;

  return completionPercentage >= expectedProgress ? 'ON_TRACK' : 'BEHIND';
}

export function getGoalInsightMessage(goalType: string, status: GoalStatus): string {
  return GOAL_INSIGHT_MESSAGES[goalType]?.[status]
    ?? "Keep an eye on this goal — check back after your next entry to see updated progress.";
}

/**
 * Maps a Goal's GoalType (Dev's enum, e.g. REDUCE_TRANSPORTATION_EMISSIONS)
 * to the CarbonCategory value used everywhere else in ai-tips.ts
 * (categoryLabels, categoryUnits) — the two enums don't share values directly.
 * REDUCE_CARBON_EMISSIONS has no single matching category, so it maps to null.
 */
export const GOAL_TYPE_TO_CATEGORY: Record<string, string | null> = {
  REDUCE_CARBON_EMISSIONS: null,
  REDUCE_TRANSPORTATION_EMISSIONS: 'TRANSPORTATION',
  REDUCE_ELECTRICITY_CONSUMPTION: 'ELECTRICITY',
  REDUCE_FOOD_EMISSIONS: 'FOOD',
  REDUCE_WASTE_GENERATION: 'WASTE',
  REDUCE_WATER_USAGE: 'WATER',
  REDUCE_FUEL_CONSUMPTION: 'FUEL',
};

export function getCategoryForGoalType(goalType: string): string | null {
  return GOAL_TYPE_TO_CATEGORY[goalType] ?? null;
}
