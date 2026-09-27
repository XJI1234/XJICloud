export const LOCAL_INSTALLER_PATH = '/downloads/Sky-Command-Setup-0.1.0.exe'

export type InstallerAssetPort = {
  isAvailable: () => Promise<boolean>
}
