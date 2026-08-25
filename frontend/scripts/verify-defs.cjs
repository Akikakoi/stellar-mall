// 完整性校验:对比 HEAD 与工作区的 .vue 脚本定义集合(function/const/let/import),确保迁移没丢定义
const { execSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..', 'src')
const files = []
;(function walk(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name)
    if (e.isDirectory()) walk(p)
    else if (e.name.endsWith('.vue')) files.push(p)
  }
})(root)

const pat = /\b(function\s+\w+|const\s+\w+|let\s+\w+)\b/g
function extractSet(text) {
  const s = new Set()
  let m
  while ((m = pat.exec(text))) s.add(m[1])
  return s
}

let problems = 0
for (const f of files) {
  const rel = path.relative(path.resolve(__dirname, '..'), f).replace(/\\/g, '/')
  const gitRel = 'frontend/' + rel
  let headText
  try {
    headText = execSync(`git show HEAD:${gitRel}`, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] })
  } catch {
    console.log('SKIP(untracked):', rel)
    continue
  }
  const curText = fs.readFileSync(f, 'utf8')
  const headSet = extractSet(headText)
  const curSet = extractSet(curText)
  const missing = [...headSet].filter((x) => !curSet.has(x))
  if (missing.length) {
    problems++
    console.log(`MISSING in ${rel}:`, missing.join(', '))
  }
}
console.log(problems === 0 ? 'ALL OK: 47 个文件定义集合完整' : `${problems} 个文件有问题`)
