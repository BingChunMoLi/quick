<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { apiPath, artifactNames, cleanConfig, columnConfig, defaults, fileTree, initializeColumns, type GenerationConfig, type TableMetadata, type ColumnConfig, type GeneratedFile, type FileNode } from './config'

const config = reactive(defaults())
const step = ref('connection'), busy = ref(false), message = ref(''), tableSearch = ref('')
const tables = ref<TableMetadata[]>([]), metadata = ref<TableMetadata[]>([]), beanNames = ref<string[]>([])
const activeTable = ref(''), builtinTemplates = ref<Record<string, string>>({}), templateKey = ref('controller')
const variablesText = ref('{}'), directoriesText = ref('{}'), files = ref<GeneratedFile[]>([]), selectedPath = ref('')
const importInput = ref<HTMLInputElement>(), newTemplate = ref(false), extraName = ref('dto'), extraPath = ref('src/main/java/${packagePath}/dto/${className}Dto.java')
const filteredTables = computed(() => tables.value.filter(t => `${t.name} ${t.comment ?? ''}`.toLowerCase().includes(tableSearch.value.toLowerCase())))
const currentTable = computed(() => config.tables.find(t => t.name === activeTable.value))
const currentColumns = computed(() => metadata.value.find(t => t.name === activeTable.value)?.columns ?? [])
const templateKeys = computed(() => [...new Set([...Object.keys(builtinTemplates.value), ...Object.keys(config.templates)])])
const templateContent = computed({ get: () => config.templates[templateKey.value]?.content ?? builtinTemplates.value[templateKey.value] ?? '', set: value => { const t = config.templates[templateKey.value] ??= {}; t.content = value; delete t.file } })
const outputPath = computed({ get: () => config.templates[templateKey.value]?.outputPath ?? '', set: value => { (config.templates[templateKey.value] ??= {}).outputPath = value } })
const selectedFile = computed(() => files.value.find(f => f.relativePath === selectedPath.value))
const nodes = computed(() => fileTree(files.value))
// Derive from the page URL so an embedded application can have a servlet context path.
const apiRoot = apiPath(window.location.pathname)

async function request(path: string, body?: unknown, text = false): Promise<Response> {
  const response = await fetch(apiRoot + path, body === undefined ? {} : { method: 'POST', headers: { 'Content-Type': text ? 'text/plain; charset=utf-8' : 'application/json' }, body: text ? String(body) : JSON.stringify(body) })
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}))
    throw new Error(payload.diagnostics?.join('\n') ?? `请求失败 (${response.status})`)
  }
  return response
}
async function task(action: () => Promise<void>) {
  busy.value = true; message.value = ''
  try { await action() } catch (error) { message.value = error instanceof Error ? error.message : '操作失败' } finally { busy.value = false }
}
function payload() {
  try { config.variables = JSON.parse(variablesText.value); config.output.directories = JSON.parse(directoriesText.value) }
  catch { throw new Error('自定义变量和分类输出目录必须是 JSON 对象') }
  if (!config.variables || Array.isArray(config.variables) || typeof config.variables !== 'object' || !config.output.directories || Array.isArray(config.output.directories) || typeof config.output.directories !== 'object') throw new Error('请输入 JSON 对象')
  return cleanConfig(config)
}
async function connect() { await task(async () => { const result = await (await request('/connection', payload())).json(); ElMessage.success(`连接成功：${result.database}`); await loadTables(); step.value = 'tables' }) }
async function loadTables() { tables.value = await (await request('/tables', payload())).json() }
function toggleTable(table: TableMetadata, checked: boolean) {
  if (checked && !config.tables.some(t => t.name === table.name)) config.tables.push({ name: table.name, columns: {} })
  if (!checked) config.tables = config.tables.filter(t => t.name !== table.name)
}
function selectAll() { for (const table of filteredTables.value) toggleTable(table, true) }
async function loadColumns() {
  await task(async () => {
    metadata.value = await (await request('/columns', payload())).json()
    for (const table of config.tables) initializeColumns(table, (metadata.value.find(t => t.name === table.name)?.columns ?? []).map(column => column.name))
    activeTable.value = config.tables[0]?.name ?? ''; step.value = 'fields'
  })
}
function field(name: string): ColumnConfig { return columnConfig(currentTable.value, name) }
async function loadTemplates() { builtinTemplates.value = await (await request(`/templates?orm=${encodeURIComponent(config.orm)}`)).json() }
function addTemplate() {
  if (!/^[A-Za-z][A-Za-z0-9]*$/.test(extraName.value) || templateKeys.value.includes(extraName.value) || !extraPath.value) { ElMessage.error('请输入唯一的英文模板名和输出路径'); return }
  config.templates[extraName.value] = { content: 'package ${basePackage}.dto;\n\npublic class ${className}Dto { }\n', outputPath: extraPath.value }
  templateKey.value = extraName.value; newTemplate.value = false
}
async function preview() { await task(async () => { const result = await (await request('/preview', payload())).json(); files.value = result.files; selectedPath.value = files.value[0]?.relativePath ?? ''; step.value = 'preview' }) }
function download(blob: Blob, name: string) { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = name; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
async function downloadZip() { await task(async () => download(await (await request('/export', payload())).blob(), 'codegen.zip')) }
async function exportYaml() { await task(async () => download(await (await request('/config/export', payload())).blob(), 'codegen.yml')) }
async function importYaml(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]; if (!file) return
  await task(async () => {
    const imported: GenerationConfig = await (await request('/config/import', await file.text(), true)).json()
    Object.assign(config, imported); variablesText.value = JSON.stringify(config.variables, null, 2); directoriesText.value = JSON.stringify(config.output.directories, null, 2)
    files.value = []; metadata.value = []; activeTable.value = ''; await loadTemplates(); step.value = 'connection'; ElMessage.success('配置已导入')
  }); input.value = ''
}
function chooseFile(node: FileNode) { if (!node.children) selectedPath.value = node.path }
watch(() => config.orm, () => task(loadTemplates))
onMounted(() => task(async () => { beanNames.value = await (await request('/datasources')).json(); await loadTemplates() }))
</script>

<template>
  <main v-loading="busy">
    <header><div><span class="eyebrow">MOLI QUICK / DEVELOPER TOOLS</span><h1>代码生成器</h1><p>配置一次，通过浏览器或命令生成项目代码。</p></div>
      <div class="actions"><input ref="importInput" type="file" accept=".yml,.yaml" hidden @change="importYaml"><el-button @click="importInput?.click()">导入 YAML</el-button><el-button @click="exportYaml">导出 YAML</el-button><el-button type="primary" :disabled="!config.tables.length" @click="preview">预览代码</el-button></div>
    </header>
    <el-alert v-if="message" :title="message" type="error" show-icon :closable="false" class="diagnostic" />
    <el-tabs v-model="step">
      <el-tab-pane label="1 · 数据源" name="connection">
        <section class="panel"><h2>连接数据库</h2><el-form label-position="top" class="form-grid">
          <el-form-item label="JDBC URL" class="wide"><el-input v-model="config.datasource.url" placeholder="jdbc:postgresql://127.0.0.1:5432/demo" /></el-form-item>
          <el-form-item label="用户名"><el-input v-model="config.datasource.username" /></el-form-item><el-form-item label="密码 / ${环境变量}"><el-input v-model="config.datasource.password" type="password" show-password autocomplete="off" /></el-form-item>
          <el-form-item label="Catalog（MySQL 数据库）"><el-input v-model="config.datasource.catalog" /></el-form-item><el-form-item label="Schema（PostgreSQL）"><el-input v-model="config.datasource.schema" placeholder="public" /></el-form-item>
          <el-form-item v-if="beanNames.length" label="或使用宿主 DataSource" class="wide"><el-select v-model="config.datasource.beanName" clearable><el-option v-for="name in beanNames" :key="name" :value="name" :label="name" /></el-select></el-form-item>
        </el-form><p class="hint">支持 MySQL 和 PostgreSQL。密码仅保留在当前页面内存中；导出 YAML 会清除明文密码。</p><el-button type="primary" @click="connect">测试连接并读取表</el-button></section>
      </el-tab-pane>
      <el-tab-pane label="2 · 表与生成选项" name="tables">
        <div class="split"><section class="panel"><div class="section-title"><h2>选择数据表</h2><el-button @click="task(loadTables)">刷新</el-button></div>
          <el-input v-model="tableSearch" placeholder="搜索表名或注释" clearable /><div class="table-toolbar"><span>已选 {{ config.tables.length }} 张表</span><el-button text @click="selectAll">选择筛选结果</el-button><el-button text @click="config.tables = []">清空</el-button></div>
          <el-table :data="filteredTables" max-height="420"><el-table-column width="50"><template #default="{ row }"><el-checkbox :model-value="config.tables.some(t => t.name === row.name)" @change="(value: unknown) => toggleTable(row, !!value)" /></template></el-table-column><el-table-column prop="name" label="表名" /><el-table-column prop="comment" label="注释" /></el-table>
          <el-button type="primary" class="next" :disabled="!config.tables.length" @click="loadColumns">读取字段并配置</el-button>
        </section><section class="panel"><h2>生成选项</h2><el-form label-position="top" class="form-grid">
          <el-form-item label="ORM 风格"><el-select v-model="config.orm"><el-option label="MyBatis" value="mybatis" /><el-option label="MyBatis-Plus" value="mybatis-plus" /></el-select></el-form-item><el-form-item label="响应风格"><el-select v-model="config.responseStyle"><el-option label="通用 Spring Boot" value="generic" /><el-option label="Quick ResultVO" value="quick" /></el-select></el-form-item>
          <el-form-item label="基础包名" class="wide"><el-input v-model="config.basePackage" /></el-form-item><el-form-item label="移除表前缀"><el-input v-model="config.tablePrefix" placeholder="sys_" /></el-form-item><el-form-item label="作者"><el-input v-model="config.author" /></el-form-item>
          <el-form-item label="生成文件" class="wide"><el-checkbox-group v-model="config.artifacts"><el-checkbox v-for="(label, key) in artifactNames" :key="key" :value="key">{{ label }}</el-checkbox></el-checkbox-group></el-form-item>
          <el-form-item label="附加选项" class="wide"><el-checkbox v-model="config.lombok">Lombok</el-checkbox><el-checkbox v-model="config.openApi">OpenAPI 注解</el-checkbox></el-form-item>
          <el-form-item label="Java 输出目录"><el-input v-model="config.output.javaDirectory" /></el-form-item><el-form-item label="XML 输出目录"><el-input v-model="config.output.xmlDirectory" /></el-form-item>
          <el-form-item label="分类输出目录（JSON）" class="wide"><el-input v-model="directoriesText" type="textarea" :rows="3" placeholder='{"controller":"web/src/main/java"}' /></el-form-item>
        </el-form></section></div>
      </el-tab-pane>
      <el-tab-pane label="3 · 字段" name="fields"><section class="panel">
        <div class="section-title"><h2>字段与类型映射</h2><el-select v-model="activeTable" placeholder="先选择表并读取字段"><el-option v-for="t in config.tables" :key="t.name" :value="t.name" /></el-select></div>
        <el-form v-if="currentTable" label-position="top"><el-form-item label="自定义实体类名（留空自动推导）"><el-input v-model="currentTable.className" /></el-form-item></el-form>
        <el-table v-if="currentTable" :data="currentColumns" style="width:100%" max-height="500">
          <el-table-column prop="name" label="数据库字段" min-width="150"><template #default="{ row }"><strong>{{ row.name }}</strong><div class="hint">{{ row.typeName }}{{ row.primaryKey ? ' · PK' : '' }}{{ row.autoIncrement ? ' · 自增' : '' }}</div></template></el-table-column>
          <el-table-column label="Java 属性" min-width="140"><template #default="{ row }"><el-input v-model="field(row.name).property" placeholder="自动" /></template></el-table-column>
          <el-table-column label="Java 类型" min-width="170"><template #default="{ row }"><el-input v-model="field(row.name).javaType" placeholder="自动" /></template></el-table-column>
          <el-table-column label="JDBC 类型" min-width="135"><template #default="{ row }"><el-input v-model="field(row.name).jdbcType" placeholder="自动" /></template></el-table-column>
          <el-table-column label="TypeHandler" min-width="190"><template #default="{ row }"><el-input v-model="field(row.name).typeHandler" placeholder="完整类名" /></template></el-table-column>
          <el-table-column label="插入" width="65"><template #default="{ row }"><el-checkbox v-model="field(row.name).insert" :disabled="row.autoIncrement || row.generated" /></template></el-table-column>
          <el-table-column label="更新" width="65"><template #default="{ row }"><el-checkbox v-model="field(row.name).update" :disabled="row.primaryKey || row.generated || row.autoIncrement" /></template></el-table-column>
          <el-table-column label="查询" width="65"><template #default="{ row }"><el-checkbox v-model="field(row.name).query" /></template></el-table-column>
          <el-table-column label="条件" width="100"><template #default="{ row }"><el-select v-model="field(row.name).queryType"><el-option value="EQ" /><el-option value="LIKE" /></el-select></template></el-table-column>
        </el-table><p class="hint">完整 CRUD 需要单列主键。复杂数据库类型须配置 Java 类型及 TypeHandler。</p>
      </section></el-tab-pane>
      <el-tab-pane label="4 · 模板" name="templates"><div class="split templates"><section class="panel"><div class="section-title"><h2>模板</h2><el-button @click="newTemplate = true">新增</el-button></div>
        <el-select v-model="templateKey"><el-option v-for="key in templateKeys" :key="key" :value="key" /></el-select><p class="hint">编辑内容会随 YAML 导出，命令行可复现相同结果。</p><el-form label-position="top"><el-form-item label="输出路径模板（内置文件可留空）"><el-input v-model="outputPath" /></el-form-item></el-form>
        <el-button @click="delete config.templates[templateKey]">{{ builtinTemplates[templateKey] ? '恢复内置模板' : '删除自定义模板' }}</el-button>
        <h3>自定义变量（JSON）</h3><el-input v-model="variablesText" type="textarea" :rows="8" /><p class="hint">模板可使用 className、basePackage、columns 和 variables。模板不包含数据库凭证。</p>
      </section><section class="panel"><div class="section-title"><h2>{{ templateKey }}.ftl</h2><el-tag>{{ config.templates[templateKey] ? '已自定义' : '内置' }}</el-tag></div><el-input v-model="templateContent" type="textarea" :rows="25" class="code-editor" spellcheck="false" /><el-button type="primary" class="next" :disabled="!config.tables.length" @click="preview">试渲染并预览</el-button></section></div></el-tab-pane>
      <el-tab-pane label="5 · 预览与下载" name="preview"><div class="section-title"><div><h2>生成结果</h2><p class="hint">{{ files.length }} 个文件。下载会根据当前配置重新生成。</p></div><el-button type="primary" :disabled="!config.tables.length" @click="downloadZip">下载 ZIP</el-button></div>
        <div v-if="files.length" class="split preview"><section class="panel file-tree"><el-tree :data="nodes" node-key="path" default-expand-all highlight-current @node-click="chooseFile" /></section><section class="panel"><h3 class="filename">{{ selectedPath }}</h3><pre>{{ selectedFile?.content }}</pre></section></div><el-empty v-else description="选择表并点击预览代码" />
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="newTemplate" title="新增输出模板" width="520"><el-form label-position="top"><el-form-item label="模板名"><el-input v-model="extraName" /></el-form-item><el-form-item label="相对输出路径"><el-input v-model="extraPath" /></el-form-item></el-form><template #footer><el-button @click="newTemplate = false">取消</el-button><el-button type="primary" @click="addTemplate">添加模板</el-button></template></el-dialog>
  </main>
</template>
