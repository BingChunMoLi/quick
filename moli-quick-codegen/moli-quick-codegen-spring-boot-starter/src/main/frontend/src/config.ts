export interface ColumnConfig { property?: string; javaType?: string; jdbcType?: string; typeHandler?: string; insert: boolean; update: boolean; query: boolean; queryType: string }
export interface TableConfig { name: string; className?: string; columns: Record<string, ColumnConfig> }
export interface TemplateConfig { content?: string; file?: string; outputPath?: string }
export interface GenerationConfig {
  schemaVersion: number
  datasource: { url: string; username: string; password: string; catalog?: string; schema?: string; beanName?: string }
  orm: string; basePackage: string; author: string; tablePrefix: string; responseStyle: string
  lombok: boolean; openApi: boolean; tables: TableConfig[]; artifacts: string[]
  templates: Record<string, TemplateConfig>; variables: Record<string, unknown>
  output: { directory: string; javaDirectory: string; xmlDirectory: string; directories: Record<string, string>; overwrite: boolean }
}
export interface ColumnMetadata { name: string; comment: string; jdbcType: number; typeName: string; nullable: boolean; primaryKey: boolean; autoIncrement: boolean; generated: boolean }
export interface TableMetadata { name: string; comment: string; columns: ColumnMetadata[] }
export interface GeneratedFile { relativePath: string; content: string }
export interface FileNode { label: string; path: string; children?: FileNode[] }
export const artifactNames: Record<string, string> = { entity: 'Entity', controller: 'Controller', service: 'Service', serviceImpl: 'ServiceImpl', mapper: 'Mapper', xml: 'XML' }
export function defaults(): GenerationConfig {
  return { schemaVersion: 1, datasource: { url: 'jdbc:mysql://127.0.0.1:3306/demo', username: 'root', password: '' }, orm: 'mybatis', basePackage: 'com.example.demo', author: '', tablePrefix: '', responseStyle: 'generic', lombok: false, openApi: false,
    tables: [], artifacts: Object.keys(artifactNames), templates: {}, variables: {}, output: { directory: '.', javaDirectory: 'src/main/java', xmlDirectory: 'src/main/resources/mapper', directories: {}, overwrite: false } }
}
export function cleanConfig(config: GenerationConfig): GenerationConfig {
  const result = structuredClone(JSON.parse(JSON.stringify(config))) as GenerationConfig
  for (const table of result.tables) {
    if (!table.className) delete table.className
    for (const field of Object.values(table.columns)) for (const key of ['property', 'javaType', 'jdbcType', 'typeHandler'] as const) if (!field[key]) delete field[key]
  }
  for (const template of Object.values(result.templates)) { if (!template.file) delete template.file; if (!template.outputPath) delete template.outputPath }
  for (const key of ['beanName', 'catalog', 'schema'] as const) if (!result.datasource[key]) delete result.datasource[key]
  return result
}
// Table libraries may invoke cell slots with an empty row while measuring columns.
// Reading a cell must not create an override for that placeholder row.
export function columnConfig(table: TableConfig | undefined, name: string | undefined): ColumnConfig {
  return name && table && Object.hasOwn(table.columns, name) ? table.columns[name] : fieldDefaults()
}
export function fieldDefaults(): ColumnConfig { return { insert: true, update: true, query: true, queryType: 'EQ' } }
export function initializeColumns(table: TableConfig, names: string[]) {
  for (const name of names) if (!Object.hasOwn(table.columns, name)) {
    Object.defineProperty(table.columns, name, { value: fieldDefaults(), enumerable: true, configurable: true, writable: true })
  }
}
export function apiPath(pagePath: string): string {
  const marker = pagePath.lastIndexOf('/moli-codegen')
  return `${marker < 0 ? '' : pagePath.slice(0, marker)}/moli-codegen/api`
}
export function fileTree(files: GeneratedFile[]): FileNode[] {
  const roots: FileNode[] = []
  for (const file of files) {
    let siblings = roots
    const parts = file.relativePath.split('/')
    for (let i = 0; i < parts.length; i++) {
      const path = parts.slice(0, i + 1).join('/')
      let node = siblings.find(n => n.path === path)
      if (!node) { node = { label: parts[i], path, ...(i < parts.length - 1 ? { children: [] } : {}) }; siblings.push(node) }
      if (node.children) siblings = node.children
    }
  }
  return roots
}
