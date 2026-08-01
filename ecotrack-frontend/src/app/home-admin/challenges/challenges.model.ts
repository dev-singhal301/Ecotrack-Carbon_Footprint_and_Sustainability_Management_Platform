import { CreateChallengePayload } from '../../../types/challenge';

// Admin create/edit payload — identical to the shared CreateChallengePayload
// except createdBy is omitted: AdminController always derives the admin
// from the authenticated JWT (Authentication.getName()), never from the
// client, so including it here would just be dead weight that's silently
// ignored server-side.
export type ChallengeAdminRequest = Omit<CreateChallengePayload, 'createdBy'>;