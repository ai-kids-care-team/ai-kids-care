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

/** Guardian profile reads remain closed until relationship authorization exists. */
export async function getGuardianByUserId(userId: number): Promise<GuardianVO | null> {
  void userId;
  return null;
}

export async function getGuardianByLoginId(loginId: string): Promise<GuardianVO | null> {
  void loginId;
  return null;
}

export async function resolveGuardianNameFromUserKeys(opts: {
  userId?: number | null;
  loginId?: string | null;
}): Promise<string | null> {
  void opts;
  return null;
}
