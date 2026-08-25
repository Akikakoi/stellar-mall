// Phase 3 辅助脚本:给所有 <script setup> 加上 lang="ts"(幂等,可重复执行)
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..', 'src')
let count = 0

function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(p)
    } else if (entry.name.endsWith('.vue')) {
      let s = fs.readFileSync(p, 'utf8')
      if (s.includes('<script setup>') && !s.includes('<script setup lang="ts">')) {
        s = s.replace('<script setup>', '<script setup lang="ts">')
        fs.writeFileSync(p, s)
        count++
        console.log('patched:', p)
      }
    }
  }
}

walk(root)
console.log(`done, patched ${count} files`)
