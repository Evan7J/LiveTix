<template>
  <div class="sp" @mousedown.stop @click.stop>
    <div class="sp-top">
      <span class="sp-lbl">框选座位区域</span>
      <span v-if="sel.length" class="sp-cnt">已选 {{ sel.length }} 座
        <button type="button" class="sp-btn" @mousedown.stop @click.stop="addSection">+ 设为看区</button>
        <button type="button" class="sp-btn ghost" @mousedown.stop @click.stop="sel=[]">清空</button>
      </span>
      <button type="button" class="sp-btn ghost" @mousedown.stop @click.stop="undo">撤销</button>
    </div>
    <div class="sp-main">
      <div class="sp-grid-wrap">
        <div class="sp-stage">演 出 舞 台</div>
        <div class="sp-grid"
          @mousedown="onDown" @mousemove="onMove" @mouseup="onUp" @mouseleave="onUp">
          <div v-for="r in rows" :key="r" class="sp-row">
            <span class="sp-rl">{{ rl(r) }}</span>
            <span v-for="c in cols" :key="c"
              :class="['sp-c', cellCls(r,c)]"
              :style="cellSty(r,c)"
              :data-r="r" :data-c="c"
            ></span>
          </div>
        </div>
      </div>
      <!-- sections panel -->
      <div class="sp-panel">
        <div v-if="!secs.length" class="sp-empty">框选座位后点击「设为看区」<br/>已设看区将显示在此处</div>
        <div v-for="(s,i) in secs" :key="i" class="sp-sec">
          <span class="sp-dot" :style="{background:s.color}"></span>
          <input v-model="s.name" class="sp-sn" placeholder="名称" @change="emit" />
          <span class="sp-sc">{{ s.cells.length }}座</span>
          <span class="sp-sp">¥</span>
          <input v-model.number="s.price" type="number" min="0" class="sp-spi" placeholder="0" @change="emit" />
          <button type="button" class="sp-sx" @mousedown.stop @click.stop="delSec(i)">✕</button>
        </div>
      </div>
    </div>
    <!-- 设为看区弹窗 -->
    <Teleport to="body">
      <div v-if="adding" class="sp-add-overlay" @mousedown.self="adding=false">
        <div class="sp-add-card" @mousedown.stop>
          <h4>设置看区信息</h4>
          <p class="sp-add-subtitle">将选中的 {{ sel.length }} 个座位设置为一个看区</p>
          <div class="sp-add-row">
            <label>看区名称</label>
            <input v-model="aname" class="sp-an" placeholder="如: VIP区 / A区" @keydown.enter="confirmAdd" ref="anameRef" />
          </div>
          <div class="sp-add-row">
            <label>票价 (¥)</label>
            <input v-model.number="aprice" type="number" min="0" class="sp-ap" placeholder="如: 680" @keydown.enter="confirmAdd" />
          </div>
          <div class="sp-add-btns">
            <button type="button" class="sp-btn ghost" @mousedown.stop @click.stop="adding=false">取消</button>
            <button type="button" class="sp-btn" @mousedown.stop @click.stop="confirmAdd">确认创建</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const p = defineProps({ modelValue:{type:Array,default:()=>[]}, rows:{type:Number,default:20}, cols:{type:Number,default:16} })
const em = defineEmits(['update:modelValue'])

const secs = ref([])
const sel = ref([])
const adding = ref(false)
const aname = ref('')
const aprice = ref(0)
const anameRef = ref(null)
const hist = ref([])
const dragging = ref(false)
const dragS = ref(null)
const dragE = ref(null)
// Track if mouse actually moved during drag (to distinguish click from drag)
const didMove = ref(false)

const CL = ['#C8A45A','#E85D75','#5B8FF9','#5AD8A6','#FF9845','#945FB4','#F6BD16','#6DC8EC']

function k(r,c){return r+'-'+c}
function rl(r){return String.fromCharCode(64+r)}
function find(r,c){return secs.value.find(s=>s.cells.includes(k(r,c)))}

function inDrag(r,c){
  if(!dragging.value||!dragS.value||!dragE.value) return false
  const [r1,c1]=[dragS.value.r,dragS.value.c]; const [r2,c2]=[dragE.value.r,dragE.value.c]
  return r>=Math.min(r1,r2)&&r<=Math.max(r1,r2)&&c>=Math.min(c1,c2)&&c<=Math.max(c1,c2)
}

function cellCls(r,c){
  if(find(r,c)) return 't'
  if(inDrag(r,c)) return 'h'
  if(sel.value.includes(k(r,c))) return 's'
  return ''
}
function cellSty(r,c){
  const s=find(r,c); if(s) return {background:s.color,borderColor:s.color}
  if(sel.value.includes(k(r,c))||inDrag(r,c)) return {background:'rgba(200,164,90,.4)',borderColor:'var(--gold-primary)'}
  return {}
}

// 核心交互：mousedown → (可选 mousemove) → mouseup
// - 仅 mousedown + mouseup（无移动）= 单击：切换该格子的选中状态
// - 有移动 = 拖拽框选：框内格子 toggle 选中

function onDown(e){
  if(!e.target.classList.contains('sp-c')) return
  const r=+e.target.dataset.r, c=+e.target.dataset.c
  if(find(r,c)) return // 已是看区，不处理
  dragging.value = true
  didMove.value = false
  dragS.value = {r,c}
  dragE.value = {r,c}
}

function onMove(e){
  if(!dragging.value) return
  if(!e.target.classList.contains('sp-c')) return
  const nr = +e.target.dataset.r, nc = +e.target.dataset.c
  if(dragE.value && dragE.value.r === nr && dragE.value.c === nc) return
  dragE.value = {r:nr, c:nc}
  didMove.value = true
}

function onUp(){
  if(!dragging.value) return
  dragging.value = false
  if(!dragS.value||!dragE.value) return

  const [r1,c1]=[dragS.value.r,dragS.value.c]; const [r2,c2]=[dragE.value.r,dragE.value.c]
  const minR=Math.min(r1,r2),maxR=Math.max(r1,r2),minC=Math.min(c1,c2),maxC=Math.max(c1,c2)

  if(!didMove.value){
    // 没有移动 = 单击：切换单个格子的选中状态
    const key = k(r1, c1)
    const idx = sel.value.indexOf(key)
    if(idx >= 0){
      const arr = [...sel.value]
      arr.splice(idx, 1)
      sel.value = arr
    } else {
      hist.value.push(JSON.parse(JSON.stringify(secs.value)))
      sel.value = [...sel.value, key]
    }
  } else {
    // 有移动 = 拖拽框选：toggle 所有框内格子
    const cells=[]
    for(let r=minR;r<=maxR;r++) for(let c=minC;c<=maxC;c++){
      if(!find(r,c)) cells.push(k(r,c))
    }
    if(cells.length){
      hist.value.push(JSON.parse(JSON.stringify(secs.value)))
      const newSel = new Set(sel.value)
      cells.forEach(cell => {
        if(newSel.has(cell)) newSel.delete(cell)
        else newSel.add(cell)
      })
      sel.value = [...newSel]
    }
  }
  dragS.value=null; dragE.value=null
  didMove.value = false
}

function addSection(){
  if(!sel.value.length) return
  hist.value.push(JSON.parse(JSON.stringify(secs.value)))
  aname.value = ''
  aprice.value = 0
  adding.value = true
  nextTick(() => {
    if(anameRef.value) anameRef.value.focus()
  })
}

function confirmAdd(){
  if(!adding.value) return
  const name = aname.value.trim() || '区域' + (secs.value.length + 1)
  secs.value.push({
    name,
    price: aprice.value || 0,
    color: CL[secs.value.length % CL.length],
    cells: [...sel.value]
  })
  adding.value = false
  sel.value = []
  emit()
}

function delSec(i){
  hist.value.push(JSON.parse(JSON.stringify(secs.value)))
  secs.value.splice(i, 1)
  emit()
}

function undo(){
  if(hist.value.length){
    secs.value = hist.value.pop()
    emit()
  }
}

function emit(){
  em('update:modelValue', JSON.parse(JSON.stringify(secs.value)))
}

watch(()=>p.modelValue, v=>{
  if(v && v.length && JSON.stringify(v) !== JSON.stringify(secs.value))
    secs.value = JSON.parse(JSON.stringify(v))
},{immediate:true})
</script>

<style scoped>
.sp{border:1px solid var(--border-color);border-radius:8px;background:rgba(255,255,255,.01);}
.sp-top{display:flex;align-items:center;gap:8px;padding:8px 12px;border-bottom:1px solid var(--border-color);flex-wrap:wrap;}
.sp-lbl{font-weight:600;color:var(--gold-primary);font-size:13px;}
.sp-cnt{font-size:12px;color:var(--gold-primary);display:flex;align-items:center;gap:6px;}
.sp-btn{padding:3px 10px;background:var(--gold-gradient);color:#000;border:none;border-radius:3px;cursor:pointer;font-size:11px;font-weight:600;}
.sp-btn.ghost{background:rgba(255,255,255,.08);color:var(--text-secondary);}
.sp-main{display:flex;gap:12px;padding:10px 12px;}
.sp-grid-wrap{flex:1;overflow:auto;}
.sp-stage{text-align:center;padding:8px;background:#111;color:#555;border-radius:4px;margin-bottom:4px;font-size:11px;letter-spacing:6px;}
.sp-grid{user-select:none;}
.sp-row{display:flex;align-items:center;gap:1px;margin-bottom:1px;}
.sp-rl{font-size:8px;color:#444;width:14px;text-align:center;flex-shrink:0;}
.sp-c{width:14px;height:12px;border-radius:2px;border:1px solid rgba(255,255,255,.08);background:rgba(255,255,255,.02);cursor:pointer;transition:.1s;}
.sp-c:hover{border-color:rgba(200,164,90,.5);transform:scale(1.3);z-index:2;}
.sp-c.t{cursor:default;border-color:transparent;}
.sp-c.s,.sp-c.h{transform:scale(1.15);z-index:2;}
.sp-panel{width:170px;flex-shrink:0;max-height:280px;overflow-y:auto;}
.sp-empty{font-size:11px;color:var(--text-muted);text-align:center;padding:20px 0;}
.sp-sec{display:flex;align-items:center;gap:4px;padding:4px 6px;background:rgba(255,255,255,.03);border-radius:4px;margin-bottom:4px;}
.sp-dot{width:8px;height:8px;border-radius:2px;flex-shrink:0;}
.sp-sn{width:50px;background:transparent;border:none;color:var(--text-primary);font-size:11px;font-weight:600;outline:none;}
.sp-sn:focus{border-bottom:1px solid var(--gold-primary);}
.sp-sc{font-size:10px;color:var(--text-muted);}
.sp-sp{font-size:10px;color:var(--text-muted);}
.sp-spi{width:44px;background:rgba(255,255,255,.05);border:1px solid var(--border-color);border-radius:3px;padding:1px 4px;color:var(--gold-primary);font-size:11px;outline:none;}
.sp-spi:focus{border-color:var(--gold-primary);}
.sp-sx{background:none;border:none;color:#E74C3C;cursor:pointer;font-size:12px;padding:0 2px;}
</style>

<!-- 非 scoped: Teleport 到 body 的弹窗需要全局样式 -->
<style>
.sp-add-overlay{position:fixed;inset:0;background:rgba(0,0,0,.55);display:flex;align-items:center;justify-content:center;z-index:9999;}
.sp-add-card{background:#1a1a1a;border:1px solid #333;border-radius:12px;padding:28px 32px;width:400px;box-shadow:0 20px 80px rgba(0,0,0,.6);}
.sp-add-card h4{font-size:17px;font-weight:700;color:#fff;margin:0 0 4px;}
.sp-add-subtitle{font-size:12px;color:#999;margin:0 0 20px;}
.sp-add-row{margin-bottom:14px;}
.sp-add-row label{display:block;font-size:12px;color:#888;margin-bottom:5px;}
.sp-add-row .sp-an,.sp-add-row .sp-ap{width:100%;padding:10px 12px;background:rgba(255,255,255,.06);border:1px solid #333;border-radius:6px;color:#fff;font-size:14px;outline:none;box-sizing:border-box;}
.sp-add-row .sp-an:focus,.sp-add-row .sp-ap:focus{border-color:#C8A45A;}
.sp-add-btns{display:flex;gap:10px;justify-content:flex-end;margin-top:8px;}
.sp-add-btns .sp-btn{padding:9px 24px;font-size:13px;border-radius:6px;}
.sp-add-btns .sp-btn.ghost{background:rgba(255,255,255,.08);color:#aaa;border:1px solid #333;}
</style>
