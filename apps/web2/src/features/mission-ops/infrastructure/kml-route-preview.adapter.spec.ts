import { describe, expect, it } from 'vitest'
import { createKmlRoutePreview } from './kml-route-preview.adapter'

const file = (body: string, name = 'route.kml') => Object.assign(new File([body], name), { text: async () => body })
const kml = (coordinates: string) => `<?xml version="1.0"?><kml xmlns="http://www.opengis.net/kml/2.2"><Document><Placemark><LineString><coordinates>${coordinates}</coordinates></LineString></Placemark></Document></kml>`

describe('KML route preview', () => {
  it('reads a valid LineString without uploading it', async () => {
    const [error, result] = await createKmlRoutePreview().importKml(file(kml('120.1,30.2,50 120.3,30.4,60')))
    expect(error).toBeNull()
    expect(result?.points).toEqual([
      { longitude: 120.1, latitude: 30.2, altitude: 50 },
      { longitude: 120.3, latitude: 30.4, altitude: 60 },
    ])
  })

  it.each([
    ['wrong extension', 'a.kmz', kml('1,2 3,4')],
    ['malformed XML', 'a.kml', '<kml><LineString>'],
    ['missing track', 'a.kml', '<kml><Placemark /></kml>'],
    ['too few points', 'a.kml', kml('1,2')],
    ['invalid coordinate', 'a.kml', kml('181,2 3,4')],
    ['non numeric', 'a.kml', kml('x,2 3,4')],
    ['extra track', 'a.kml', `<kml><LineString><coordinates>1,2 3,4</coordinates></LineString><LineString><coordinates>5,6 7,8</coordinates></LineString></kml>`],
  ])('rejects %s', async (_case, name, body) => {
    const [error, result] = await createKmlRoutePreview().importKml(file(body, name))
    expect(error?.code).toBe('ROUTE_PREVIEW_INVALID')
    expect(result).toBeNull()
  })

  it('accepts styled preview tracks without duplicating waypoint placemarks', async () => {
    const body = `<kml xmlns="http://www.opengis.net/kml/2.2"><Document>
      <name>Preview only</name><Style id="track"><LineStyle><color>ff6af1d7</color><width>5</width></LineStyle></Style>
      <Placemark><styleUrl>#track</styleUrl><LineString><altitudeMode>absolute</altitudeMode><coordinates>
        120.16720400471,30.3221040027,43.25 120.16731556356,30.32079230001,43.25
        120.16579600541,30.32069599353,43.25 120.16568442632,30.32200769493,43.25
      </coordinates></LineString></Placemark>
      <Folder><Placemark><Point><coordinates>120.16720400471,30.3221040027,43.25</coordinates></Point></Placemark></Folder>
    </Document></kml>`
    const [error, preview] = await createKmlRoutePreview().importKml(file(body, '航线预览示例.kml'))
    expect(error).toBeNull()
    expect(preview?.points).toHaveLength(4)
    expect(preview?.points[0]).toEqual({ longitude: 120.16720400471, latitude: 30.3221040027, altitude: 43.25 })
  })

  it('rejects oversized input', async () => {
    const [error] = await createKmlRoutePreview().importKml(file(' '.repeat(2 * 1024 * 1024 + 1)))
    expect(error?.code).toBe('ROUTE_PREVIEW_INVALID')
  })
})
