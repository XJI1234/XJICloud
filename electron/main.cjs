const { app, BrowserWindow, dialog, ipcMain } = require('electron')
const fs = require('node:fs/promises')
const path = require('node:path')

const DEV_SERVER_URL = process.env.ELECTRON_RENDERER_URL || 'http://127.0.0.1:5174'

function toArrayBuffer(buffer) {
  return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength)
}

function normalizeBinaryPayload(bytes) {
  if (bytes instanceof ArrayBuffer) {
    return Buffer.from(bytes)
  }

  if (ArrayBuffer.isView(bytes)) {
    return Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  }

  return Buffer.from(bytes)
}

async function pickModelDirectory() {
  const result = await dialog.showOpenDialog({
    title: '选择模型目录',
    properties: ['openDirectory'],
  })

  return result.canceled ? null : (result.filePaths[0] ?? null)
}

async function pickExportPath(defaultFileName) {
  const result = await dialog.showSaveDialog({
    title: '导出 SPZ 模型',
    defaultPath: defaultFileName,
    filters: [
      { name: 'Spark Point Cloud', extensions: ['spz'] },
    ],
  })

  return result.canceled ? null : (result.filePath ?? null)
}

async function listModelCandidates(directoryPath) {
  const entries = await fs.readdir(directoryPath, { withFileTypes: true })
  return entries
    .filter((entry) => entry.isFile() && /\.(ply|spz)$/i.test(entry.name))
    .map((entry) => ({
      name: entry.name,
      path: path.join(directoryPath, entry.name),
    }))
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
}

async function readModelFile(filePath) {
  const bytes = await fs.readFile(filePath)
  return {
    name: path.basename(filePath),
    path: filePath,
    directoryPath: path.dirname(filePath),
    bytes: toArrayBuffer(bytes),
  }
}

async function readTextFile(filePath) {
  try {
    return await fs.readFile(filePath, 'utf8')
  } catch (error) {
    if (error && typeof error === 'object' && error.code === 'ENOENT') {
      return null
    }

    throw error
  }
}

async function writeTextFile(filePath, contents) {
  await fs.mkdir(path.dirname(filePath), { recursive: true })
  await fs.writeFile(filePath, contents, 'utf8')
}

async function writeBinaryFile(filePath, bytes) {
  await fs.mkdir(path.dirname(filePath), { recursive: true })
  await fs.writeFile(filePath, normalizeBinaryPayload(bytes))
}

function registerIpcHandlers() {
  ipcMain.handle('viewer:pickModelDirectory', pickModelDirectory)
  ipcMain.handle('viewer:pickExportPath', (_event, defaultFileName) => pickExportPath(defaultFileName))
  ipcMain.handle('viewer:listModelCandidates', (_event, directoryPath) => listModelCandidates(directoryPath))
  ipcMain.handle('viewer:readModelFile', (_event, filePath) => readModelFile(filePath))
  ipcMain.handle('viewer:readTextFile', (_event, filePath) => readTextFile(filePath))
  ipcMain.handle('viewer:writeTextFile', (_event, filePath, contents) => writeTextFile(filePath, contents))
  ipcMain.handle('viewer:writeBinaryFile', (_event, filePath, bytes) => writeBinaryFile(filePath, bytes))
}

async function createMainWindow() {
  const mainWindow = new BrowserWindow({
    title: '3D高斯模型查看器',
    width: 1480,
    height: 960,
    minWidth: 1120,
    minHeight: 760,
    backgroundColor: '#111111',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  })

  if (app.isPackaged) {
    await mainWindow.loadFile(path.join(__dirname, '..', 'dist', 'index.html'))
    return
  }

  await mainWindow.loadURL(DEV_SERVER_URL)
}

app.whenReady().then(async () => {
  registerIpcHandlers()
  await createMainWindow()

  app.on('activate', async () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      await createMainWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})