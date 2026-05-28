const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('viewerDesktop', {
  pickModelDirectory: () => ipcRenderer.invoke('viewer:pickModelDirectory'),
  pickExportPath: (defaultFileName) => ipcRenderer.invoke('viewer:pickExportPath', defaultFileName),
  listModelCandidates: (directoryPath) => ipcRenderer.invoke('viewer:listModelCandidates', directoryPath),
  readModelFile: (filePath) => ipcRenderer.invoke('viewer:readModelFile', filePath),
  readTextFile: (filePath) => ipcRenderer.invoke('viewer:readTextFile', filePath),
  writeTextFile: (filePath, contents) => ipcRenderer.invoke('viewer:writeTextFile', filePath, contents),
  writeBinaryFile: (filePath, bytes) => ipcRenderer.invoke('viewer:writeBinaryFile', filePath, bytes),
})