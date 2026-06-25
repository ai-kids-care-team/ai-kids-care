export type GuardianVO = {
  guardianId: number;
  kindergartenId: number;
  userId: number;
  name: string;
  gender: string | null;
  status: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

/**
 * Guardian profile reads remain closed — backend GuardianController uses denyAll().
 * These stubs are intentionally left unwired; the console.warn makes the gap observable.
 */
export async function getGuardianByUserId(userId: number): Promise<GuardianVO | null> {
  void userId;
  console.warn('[stub] getGuardianByUserId: not yet wired — guardians endpoint is denyAll on backend');
  return null;
}

export async function getGuardianByLoginId(loginId: string): Promise<GuardianVO | null> {
  void loginId;
  console.warn('[stub] getGuardianByLoginId: not yet wired — guardians endpoint is denyAll on backend');
  return null;
}

export async function resolveGuardianNameFromUserKeys(opts: {
  userId?: number | null;
  loginId?: string | null;
}): Promise<string | null> {
  void opts;
  console.warn('[stub] resolveGuardianNameFromUserKeys: not yet wired — guardians endpoint is denyAll on backend');
  return null;
}
