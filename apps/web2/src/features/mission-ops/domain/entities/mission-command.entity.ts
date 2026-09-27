/** Commands the cloud page may ask the local Sky-Command agent to run. */
export const MISSION_COMMANDS = [
  'mission.upload',
  'mission.start',
  'mission.pause',
  'mission.resume',
  'mission.stop',
  'flight.return-home',
] as const

export type MissionCommandName = (typeof MISSION_COMMANDS)[number]

export function isMissionCommandName(value: string): value is MissionCommandName {
  return (MISSION_COMMANDS as readonly string[]).includes(value)
}

/** Flight commands must carry an explicit operator confirmation. */
export function commandRequiresConfirm(command: MissionCommandName): boolean {
  return command.startsWith('flight.')
}
