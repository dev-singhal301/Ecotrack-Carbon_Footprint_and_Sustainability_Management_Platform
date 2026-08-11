// goal-insight-messages.ts

export type GoalStatus = 'ON_TRACK' | 'BEHIND' | 'OVER_BUDGET' | 'MISSED' | 'COMPLETED';

export interface GoalInsightContext {
  currentDisplay: string;   // e.g. "58.8 km"
  targetDisplay: string;    // e.g. "50 km"
  endDateDisplay: string;   // e.g. "Aug 24, 2026"
  daysRemaining: number;    // 0 once the goal has ended
}

const CATEGORY_LABELS_FOR_INSIGHT: Record<string, string> = {
  REDUCE_CARBON_EMISSIONS: 'overall emissions',
  REDUCE_TRANSPORTATION_EMISSIONS: 'transportation',
  REDUCE_ELECTRICITY_CONSUMPTION: 'electricity',
  REDUCE_FOOD_EMISSIONS: 'food',
  REDUCE_WASTE_GENERATION: 'waste',
  REDUCE_WATER_USAGE: 'water',
  REDUCE_FUEL_CONSUMPTION: 'fuel',
};

// 7 categories × 5 statuses = 35 tips, each phrased to fit that specific situation
const CATEGORY_TIPS: Record<string, Record<GoalStatus, string>> = {
  REDUCE_CARBON_EMISSIONS: {
    ON_TRACK: 'keeping up the mix of habits across categories that\u2019s been driving your total down',
    BEHIND: 'checking which single category has grown the most since you started and focusing there first',
    OVER_BUDGET: 'cutting back sharply in whichever category is contributing the largest share right now',
    MISSED: 'starting your next attempt with a narrower, single-category goal instead of one broad target',
    COMPLETED: 'setting a slightly tighter overall target to build on this result',
  },
  REDUCE_TRANSPORTATION_EMISSIONS: {
    ON_TRACK: 'keeping favoring public transport or carpooling over solo drives',
    BEHIND: 'swapping one upcoming car trip for public transport or cycling',
    OVER_BUDGET: 'switching to public transport or cycling for the rest of the period wherever possible',
    MISSED: 'trying a smaller weekly distance target next time instead of one long-term number',
    COMPLETED: 'setting a slightly lower distance target to keep the streak going',
  },
  REDUCE_ELECTRICITY_CONSUMPTION: {
    ON_TRACK: 'keeping unplugging idle devices and using off-peak hours where you can',
    BEHIND: 'switching a few more lights to LEDs this week to help close the gap',
    OVER_BUDGET: 'cutting non-essential usage \u2014 AC, heaters, or standby devices \u2014 for the rest of the period',
    MISSED: 'running a home energy audit before your next attempt to find where usage is highest',
    COMPLETED: 'locking in the habit with a new, slightly more ambitious target',
  },
  REDUCE_FOOD_EMISSIONS: {
    ON_TRACK: 'keeping favoring plant-based meals and planning ahead to cut waste',
    BEHIND: 'shifting one more meal this week toward a plant-based option',
    OVER_BUDGET: 'leaning heavily plant-based for the remaining days to limit further impact',
    MISSED: 'trying a smaller, more specific target next time, like "3 plant-based meals/week"',
    COMPLETED: 'keeping meals both sustainable and consistent going forward',
  },
  REDUCE_WASTE_GENERATION: {
    ON_TRACK: 'keeping sorting recyclables and composting where possible',
    BEHIND: 'separating recyclables more consistently for the rest of this week',
    OVER_BUDGET: 'cutting back on single-use and packaging waste for the remaining days',
    MISSED: 'starting with just packaging waste next time to make the goal more manageable',
    COMPLETED: 'keeping the composting or recycling routine that got you here',
  },
  REDUCE_WATER_USAGE: {
    ON_TRACK: 'keeping showers short and taps off when not in use',
    BEHIND: 'checking for any leaks and shaving a minute off your showers',
    OVER_BUDGET: 'cutting non-essential water use \u2014 shorter showers, full loads only \u2014 for the rest of the period',
    MISSED: 'checking for leaks first next time, since that often gives the biggest single improvement',
    COMPLETED: 'checking if a low-flow fixture upgrade could lock in the gains',
  },
  REDUCE_FUEL_CONSUMPTION: {
    ON_TRACK: 'keeping combining errands and carpooling where you can',
    BEHIND: 'combining a couple of errands into a single trip this week',
    OVER_BUDGET: 'minimizing non-essential trips for the rest of the period',
    MISSED: 'carpooling for even a few trips next time, since that can meaningfully move the number',
    COMPLETED: 'building on this streak with a new target',
  },
};

const DEFAULT_TIPS: Record<GoalStatus, string> = {
  ON_TRACK: 'keeping up your current habits',
  BEHIND: 'making a small adjustment soon to close the gap',
  OVER_BUDGET: 'cutting back sharply for the remaining days',
  MISSED: 'setting a more specific target next time',
  COMPLETED: 'building on this result with your next goal',
};

export function getGoalStatus(
  startDate: string,
  endDate: string,
  completionPercentage: number,
  completed: boolean
): GoalStatus {
  if (completed) return 'COMPLETED';

  const now = new Date();
  const end = new Date(endDate);

  if (now > end) return 'MISSED';
  if (completionPercentage >= 100) return 'OVER_BUDGET'; // cap already exceeded, deadline still ahead

  const start = new Date(startDate);
  const totalDays = (end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24);
  const daysElapsed = (now.getTime() - start.getTime()) / (1000 * 60 * 60 * 24);
  const expectedProgress = totalDays > 0 ? (daysElapsed / totalDays) * 100 : 0;

  return completionPercentage <= expectedProgress ? 'ON_TRACK' : 'BEHIND';
}

export function getGoalInsightMessage(
  goalType: string,
  status: GoalStatus,
  ctx: GoalInsightContext
): string {
  const label = CATEGORY_LABELS_FOR_INSIGHT[goalType] ?? 'this';
  const tip = CATEGORY_TIPS[goalType]?.[status] ?? DEFAULT_TIPS[status];
  const { currentDisplay: cur, targetDisplay: tgt, endDateDisplay: end, daysRemaining: days } = ctx;
  const dayWord = days === 1 ? 'day' : 'days';

  switch (status) {
    case 'ON_TRACK':
      return `You're on pace with your ${label} goal. So far you've used ${cur} of your ${tgt} limit, with ${days} ${dayWord} left until ${end} \u2014 right about where you'd expect to be at this point. Keep ${tip} and you're on track to finish comfortably under target.`;
    case 'BEHIND':
      return `You're pacing behind on your ${label} goal. You've already used ${cur} of your ${tgt} limit, which is more than expected with ${days} ${dayWord} remaining until ${end}. If this rate continues you're likely to go over before the deadline \u2014 ${tip} would help bring you back on pace.`;
    case 'OVER_BUDGET':
      return `You've already gone over your ${label} target \u2014 ${cur} used against a ${tgt} limit, with ${days} ${dayWord} still left until ${end}. The goal is unlikely to complete successfully unless usage drops sharply for the rest of the period, but ${tip} can still help limit the damage.`;
    case 'MISSED':
      return `This ${label} goal closed on ${end} without hitting its target \u2014 it ended at ${cur} against a ${tgt} limit. For your next attempt, ${tip} could make the difference.`;
    case 'COMPLETED':
      return `Goal met! You kept ${label} usage to ${cur}, staying under your ${tgt} limit by the ${end} deadline. ${tip.charAt(0).toUpperCase() + tip.slice(1)} to carry this momentum forward.`;
    default:
      return 'Keep an eye on this goal \u2014 check back after your next entry to see updated progress.';
  }
}

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
