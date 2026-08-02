<template>
  <div class="sme">
    <div class="sme-toolbar">
      <span class="sme-label">座位图编辑器</span>
      <span class="sme-hint">点击座位选中 → 「设为看区」→ 命名+定价</span>
      <template v-if="selectedCells.length">
        <span class="sme-sel-count">已选 {{ selectedCells.length }} 座</span>
        <button class="sme-btn sm" @click="openNameDialog">设为看区</button>
        <button class="sme-btn sm ghost" @click="selectedCells=[]">清空</button>
      </template>
      <button class="sme-btn ghost" @click="undoLast">撤销</button>
      <button class="sme-btn ghost danger" @click="clearAll">清空全部</button>
    </div>

    <div class="sme-body">
      <div class="sme-grid-wrap">
        <div class="sme-stage">演 出 舞 台</div>
        <div class="sme-grid">
          <div v-for="row in rows" :key="row" class="sme-row">
            <span class="sme-rl">{{ rowLabel(row) }}</span>
            <div
              v-for="col in cols" :key="col"
              :class="cellCls(row,col)"
              :style="cellSty(row,col)"
              @click="toggle(row,col)"
            ></div>
            <span class="sme-rl r">{{ rowLabel(row) }}</span>
          </div>
        </div>
        <div class="sme-col-labels">
          <span class="cl-sp"></span>
          <span v-for="col in cols" :key="col" class="cl-n">{{ col }}</span>
        </div>
      </div>

      <div class="sme-panel">
        <h4>看台区域</h4>
        <div v-if="!sections.length" class="sme-empty">选择座位后设为看区</div>
        <div v-for="(s,i) in sections" :key="i" class="sme-sec">
          <div class="sec-head">
            <span class="sec-dot" :style="{background:s.color}"></span>
            <input v-model="s.name" class="sec-name" placeholder="名称" @change="save" />
            <span class="sec-cnt">{{ s.cells.length }}座</span>
            <button class="sec-del" @click="delSec(i)">✕</button>
          </div>
          <div class="sec-price">
            <span>票价 ¥</span>
            <input v-model.number="s.price" type="number" min="0" class="sec-pi" placeholder="0" @change="save" />
          </div>
        </div>
      </div>
    </div>

    <!-- Name dialog - use teleport to body to avoid parent dialog conflict -->
    <Teleport to="body">
      <div v-if="dialog" class="sme-overlay" @click="dialog=null">
        <div class="sme-dlg" @click.stop>
          <h4>设为看区（{{ dialog.cells.length }}座）</h4>
          <input v-model="dName" class="sme-inp" placeholder="如：VIP区 / 内场A区 / 看台B区" @keydown.enter="confirm" />
          <div class="dlg-price">
            <span>票价 ¥</span>
            <input v-model.number="dPrice" type="number" min="0" class="sme-inp" style="width:120px" placeholder="0" />
          </div>
          <div class="dlg-btns">
            <button @click="dialog=null">取消</button>
            <button class="sme-btn" @click="confirm">确认</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const p = defineProps({
  modelValue: { type: Array, default: () => [] },
  rows: { type: Number, default: 24 },
  cols: { type: Number, default: 20 },
})
const em = defineEmits(['update:modelValue'])

const sections = ref([])
const selectedCells = ref([])
const dialog = ref(null)
const dName = ref('')
const dPrice = ref(0)
const history = ref([])

const CL = ['#C8A45A','#E85D75','#5B8FF9','#5AD8A6','#FF9845','#945FB4','#F6BD16','#6DC8EC','#FF99C3','#1E9493']

function k(r,c) { return r+'-'+c }
function rl(r) { return String.fromCharCode(64+r) }
function find(r,c) { return sections.value.find(s=>s.cells.includes(k(r,c))) }

function cellCls(r,c) {
  const a = []
  if (find(r,c)) a.push('taken')
  else if (selectedCells.value.includes(k(r,c))) a.push('sel')
  return a
}
function cellSty(r,c) {
  const s = find(r,c)
  if (s) return { background:s.color, borderColor:s.color }
  if (selectedCells.value.includes(k(r,c))) return { background:'rgba(200,164,90,.45)', borderColor:'var(--gold-primary)' }
  return {}
}

function toggle(r,c) {
  if (find(r,c)) return
  const key = k(r,c)
  const i = selectedCells.value.indexOf(key)
  if (i>=0) selectedCells.value.splice(i,1)
  else selectedCells.value.push(key)
}

function openNameDialog() {
  if (!selectedCells.value.length) return
  history.value.push(JSON.parse(JSON.stringify(sections.value)))
  dName.value = ''; dPrice.value = 0
  dialog.value = { cells: [...selectedCells.value] }
}

function confirm() {
  if (!dialog.value) return
  sections.value.push({
    name: dName.value.trim() || ('区域'+(sections.value.length+1)),
    price: dPrice.value || 0,
    color: CL[sections.value.length % CL.length],
    cells: dialog.value.cells,
  })
  dialog.value = null; selectedCells.value = []
  save()
}

function delSec(i) { history.value.push(JSON.parse(JSON.stringify(sections.value))); sections.value.splice(i,1); save() }
function undoLast() { if (history.value.length) { sections.value = history.value.pop(); save() } }
function clearAll() { history.value.push(JSON.parse(JSON.stringify(sections.value))); sections.value=[]; selectedCells.value=[]; save() }
function save() { em('update:modelValue', JSON.parse(JSON.stringify(sections.value))) }

watch(()=>p.modelValue, v=>{
  if (v && v.length && JSON.stringify(v)!==JSON.stringify(sections.value)) sections.value=JSON.parse(JSON.stringify(v))
}, {immediate:true})
</script>

<style scoped>
.sme { border:1px solid var(--border-color); border-radius:var(--radius-md); background:var(--bg-card); }
.sme-toolbar { display:flex; align-items:center; gap:10px; padding:10px 16px; border-bottom:1px solid var(--border-color); flex-wrap:wrap; }
.sme-label { font-weight:700; color:var(--gold-primary); font-size:14px; }
.sme-hint { font-size:11px; color:var(--text-muted); flex:1; }
.sme-sel-count { font-size:12px; color:var(--gold-primary); }
.sme-btn { padding:5px 14px; background:var(--gold-gradient); color:#000; border:none; border-radius:4px; cursor:pointer; font-size:12px; font-weight:600; }
.sme-btn.sm { padding:3px 10px; font-size:11px; }
.sme-btn.ghost { background:rgba(255,255,255,.08); color:var(--text-secondary); }
.sme-btn.ghost:hover { background:rgba(255,255,255,.12); }
.sme-btn.danger { color:#E74C3C; }
.sme-body { display:flex; gap:16px; padding:16px; }
.sme-grid-wrap { flex:1; overflow:auto; }
.sme-stage { text-align:center; padding:10px; background:#111; color:#555; border-radius:6px; margin-bottom:4px; font-size:12px; letter-spacing:8px; }
.sme-grid { user-select:none; }
.sme-row { display:flex; align-items:center; gap:1px; margin-bottom:1px; }
.sme-rl { font-size:9px; color:#444; width:16px; text-align:center; flex-shrink:0; }
.sme-rl.r { margin-left:2px; }
.sme-col-labels { display:flex; align-items:center; gap:1px; margin-top:2px; padding-left:16px; }
.cl-sp { width:16px; flex-shrink:0; }
.cl-n { width:16px; text-align:center; font-size:8px; color:#444; flex-shrink:0; }
.sme-cell { width:16px; height:14px; border-radius:2px; border:1px solid rgba(255,255,255,.08); background:rgba(255,255,255,.02); cursor:pointer; transition:all .1s; }
.sme-cell:hover { border-color:rgba(200,164,90,.5); transform:scale(1.3); z-index:2; }
.sme-cell.sel { transform:scale(1.15); z-index:2; }
.sme-cell.taken { cursor:default; border-color:transparent; }

.sme-panel { width:200px; flex-shrink:0; }
.sme-panel h4 { font-size:13px; color:var(--text-primary); margin-bottom:8px; }
.sme-empty { font-size:11px; color:var(--text-muted); }
.sme-sec { background:rgba(255,255,255,.03); border-radius:6px; padding:8px; margin-bottom:6px; }
.sec-head { display:flex; align-items:center; gap:6px; margin-bottom:4px; }
.sec-dot { width:10px; height:10px; border-radius:2px; flex-shrink:0; }
.sec-name { flex:1; background:transparent; border:none; color:var(--text-primary); font-size:12px; font-weight:600; outline:none; min-width:0; }
.sec-name:focus { border-bottom:1px solid var(--gold-primary); }
.sec-cnt { font-size:10px; color:var(--text-muted); }
.sec-del { background:none; border:none; color:#E74C3C; cursor:pointer; font-size:14px; }
.sec-price { display:flex; align-items:center; gap:4px; font-size:11px; color:var(--text-muted); }
.sec-pi { width:60px; background:rgba(255,255,255,.05); border:1px solid var(--border-color); border-radius:4px; padding:2px 6px; color:var(--gold-primary); font-size:12px; outline:none; }
.sec-pi:focus { border-color:var(--gold-primary); }

.sme-overlay { position:fixed; inset:0; background:rgba(0,0,0,.6); z-index:999; display:flex; align-items:center; justify-content:center; }
.sme-dlg { background:var(--bg-card); border:1px solid var(--border-color); border-radius:var(--radius-lg); padding:24px; width:380px; }
.sme-dlg h4 { font-size:15px; color:var(--text-primary); margin-bottom:12px; }
.dlg-price { display:flex; align-items:center; gap:8px; margin-top:10px; }
.sme-inp { width:100%; padding:10px 12px; background:rgba(255,255,255,.05); border:1px solid var(--border-color); border-radius:6px; color:var(--text-primary); font-size:14px; outline:none; }
.sme-inp:focus { border-color:var(--gold-primary); }
.dlg-btns { display:flex; justify-content:flex-end; gap:8px; margin-top:14px; }
.dlg-btns button { padding:7px 18px; border-radius:4px; cursor:pointer; font-size:13px; }
.dlg-btns button:first-child { background:transparent; border:1px solid var(--border-color); color:var(--text-secondary); }
</style>
