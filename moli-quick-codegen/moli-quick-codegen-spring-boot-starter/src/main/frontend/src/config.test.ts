import { describe, expect, it } from 'vitest'
import { apiPath, cleanConfig, columnConfig, defaults, fileTree, initializeColumns } from './config'
describe('portable configuration', () => {
  it('removes blank optional overrides without changing the original', () => {
    const config = defaults(); config.tables = [{ name: 'sys_user', className: '', columns: { id: { property: '', javaType: '', insert: true, update: true, query: true, queryType: 'EQ' } } }]
    const cleaned = cleanConfig(config)
    expect(cleaned.tables[0].columns.id.javaType).toBeUndefined()
    expect(config.tables[0].columns.id.javaType).toBe('')
  })
  it('groups preview files by directory without dropping common prefixes', () => {
    const nodes = fileTree([{ relativePath: 'src/A.java', content: '' }, { relativePath: 'src/B.java', content: '' }])
    expect(nodes).toHaveLength(1); expect(nodes[0].children?.map(n => n.label)).toEqual(['A.java', 'B.java'])
  })
  it('does not persist overrides for placeholder rows during table measurement', () => {
    const table = { name: 'user', columns: {} }
    columnConfig(table, undefined)
    columnConfig(table, 'id')
    expect(table.columns).toEqual({})
  })
  it('does not treat Object prototype members as field overrides', () => {
    const table = { name: 'user', columns: {} }
    for (const name of ['constructor', 'toString', '__proto__']) expect(columnConfig(table, name).queryType).toBe('EQ')
    expect(Object.keys(table.columns)).toEqual([])
    initializeColumns(table, ['constructor', 'toString', '__proto__'])
    expect(Object.keys(table.columns)).toEqual(['constructor', 'toString', '__proto__'])
    columnConfig(table, '__proto__').property = 'payload'
    expect(Object.getPrototypeOf(table.columns)).toBe(Object.prototype)
    expect(Object.prototype).not.toHaveProperty('property')
  })
  it('preserves context paths that contain the module name', () => {
    expect(apiPath('/moli-codegen-host/moli-codegen/index.html')).toBe('/moli-codegen-host/moli-codegen/api')
    expect(apiPath('/nested/app/moli-codegen/index.html')).toBe('/nested/app/moli-codegen/api')
    expect(apiPath('/')).toBe('/moli-codegen/api')
  })
})
