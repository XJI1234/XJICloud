import { mkdir, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { gzipSync } from 'node:zlib'

// Small deterministic Gaussian sphere: real binary models for browser smoke tests, not a renderer mock.
const points = Array.from({ length: 4096 }, (_, index) => {
  const y = 1 - 2 * (index + 0.5) / 4096
  const radius = Math.sqrt(1 - y * y)
  const angle = index * Math.PI * (3 - Math.sqrt(5))
  return [radius * Math.cos(angle), y, radius * Math.sin(angle)]
})
const properties = ['x', 'y', 'z', 'nx', 'ny', 'nz', 'f_dc_0', 'f_dc_1', 'f_dc_2', 'opacity', 'scale_0', 'scale_1', 'scale_2', 'rot_0', 'rot_1', 'rot_2', 'rot_3']
const header = Buffer.from(`ply\nformat binary_little_endian 1.0\nelement vertex ${points.length}\n${properties.map((name) => `property float ${name}`).join('\n')}\nend_header\n`)
const body = Buffer.alloc(points.length * properties.length * 4)
points.forEach(([x, y, z], index) => {
  const color = [0.35 + x * 0.2, 0.55 + y * 0.2, 0.7 + z * 0.2]
  const values = [x, y, z, 0, 0, 0, ...color.map((v) => (v - 0.5) / 0.2820947918), 3, -3.4, -3.4, -3.4, 1, 0, 0, 0]
  values.forEach((value, offset) => body.writeFloatLE(value, (index * properties.length + offset) * 4))
})
const spz = Buffer.alloc(16 + points.length * (9 + 1 + 3 + 3 + 3))
spz.writeUInt32LE(0x5053474e, 0)
spz.writeUInt32LE(2, 4)
spz.writeUInt32LE(points.length, 8)
spz[12] = 0
spz[13] = 12
const alpha = 16 + points.length * 9
const color = alpha + points.length
const scale = color + points.length * 3
const rotation = scale + points.length * 3
points.forEach((position, index) => {
  position.forEach((value, axis) => spz.writeIntLE(Math.round(value * 4096), 16 + index * 9 + axis * 3, 3))
  spz[alpha + index] = 243
  for (let axis = 0; axis < 3; axis += 1) {
    spz[color + index * 3 + axis] = Math.round(128 + position[axis] * 20)
    spz[scale + index * 3 + axis] = Math.round(-3.4 * 16 + 160)
    spz[rotation + index * 3 + axis] = 128
  }
})
const destination = process.argv[2] ?? join(tmpdir(), 'xjicloud-viewer-models')
await mkdir(destination, { recursive: true })
await writeFile(join(destination, 'sphere.ply'), Buffer.concat([header, body]))
await writeFile(join(destination, 'sphere.spz'), gzipSync(spz))
console.log(`Created 4096-point PLY/SPZ smoke-test models in ${destination}`)
