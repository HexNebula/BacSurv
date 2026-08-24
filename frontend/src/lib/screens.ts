/**
 * Where each readiness step is done.
 *
 * <p>The server answers with the screen a step belonged to when it was written
 * — "center" for the rooms, "schedule" for the filières — and the interface has
 * since given both their own place. The server should not have to know how the
 * screens are arranged, so the step's own key decides where it leads, and its
 * `screen` is the fallback for anything added later.
 */
const BY_STEP: Record<string, string> = {
  rooms: '/rooms',
  teachers: '/teachers',
  filieres: '/streams',
  timetable: '/schedule',
  staffing: '/teachers',
  distribution: '/results',
}

export function screenOf(step: { key: string; screen: string }): string {
  return BY_STEP[step.key] ?? `/${step.screen}`
}
