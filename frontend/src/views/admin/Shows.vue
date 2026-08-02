<template>
  <div class="admin-page">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索演出..." style="width: 240px;" clearable @clear="search" @keyup.enter="search" />
      <el-select v-model="statusFilter" placeholder="状态筛选" style="width: 140px;" clearable @change="load">
        <el-option label="待上架" :value="0" />
        <el-option label="在售" :value="1" />
        <el-option label="售罄" :value="2" />
        <el-option label="已结束" :value="3" />
      </el-select>
      <el-button class="btn-gold" @click="openCreate">新增演出</el-button>
    </div>

    <el-table :data="shows" style="width: 100%;" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="演出名称" min-width="200" />
      <el-table-column prop="artists" label="艺人" width="120" />
      <el-table-column label="演出时间" width="140">
        <template #default="{ row }">{{ fmt(row.showTime) }}</template>
      </el-table-column>
      <el-table-column label="库存" width="100">
        <template #default="{ row }">{{ row.availableStock }}/{{ row.totalStock }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type" size="small">{{ statusMap[row.status]?.text }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="editShow(row)">编辑</el-button>
          <el-button size="small" text type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" background @current-change="load" />
    </div>

    <!-- Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑演出' : '新增演出'" width="960px" :close-on-click-modal="false" @opened="onDialogOpened">
      <el-form :model="form" label-width="110px">
        <el-form-item label="封面图片">
          <div class="cover-upload">
            <!-- 预览区域（可交互调节） -->
            <div class="cover-preview" v-if="form.coverImage" :class="{ adjusting: showAdjust }">
              <img :src="form.coverImage"
                :style="{ objectFit: coverFit, objectPosition: coverPos }"
                @error="e => e.target.style.display='none'" />
              <!-- 调节工具栏 -->
              <div class="cover-toolbar">
                <el-tooltip content="铺满裁剪" placement="top"><button type="button" :class="{active:coverFit==='cover'}" @click="coverFit='cover'">⊞</button></el-tooltip>
                <el-tooltip content="完整显示" placement="top"><button type="button" :class="{active:coverFit==='contain'}" @click="coverFit='contain'">⊟</button></el-tooltip>
                <el-tooltip content="拉伸填充" placement="top"><button type="button" :class="{active:coverFit==='fill'}" @click="coverFit='fill'">⬒</button></el-tooltip>
                <span class="tb-sep"></span>
                <span class="nudge-group">
                  <button type="button" @click="nudgePos(0,-5)" title="上移">▲</button>
                  <span class="nudge-row">
                    <button type="button" @click="nudgePos(-5,0)" title="左移">◁</button>
                    <button type="button" @click="nudgePos(0,0)" title="复位" class="nudge-rst">↺</button>
                    <button type="button" @click="nudgePos(5,0)" title="右移">▷</button>
                  </span>
                  <button type="button" @click="nudgePos(0,5)" title="下移">▼</button>
                </span>
                <span class="pos-val">{{ coverPosX }}% {{ coverPosY }}%</span>
              </div>
            </div>
            <!-- 上传按钮（直接替换，不需先删除） -->
            <input ref="coverFileInput" type="file" accept="image/*" style="display:none" @change="onCoverFileChange" />
            <el-button :loading="uploading" @click="$refs.coverFileInput.click()" :type="form.coverImage ? 'default' : 'primary'">
              {{ uploading ? '上传中...' : (form.coverImage ? '更换封面图片' : '点击上传封面') }}
            </el-button>
            <span class="form-hint">支持 JPG/PNG/GIF/WebP，最大10MB · 上传即替换</span>
            <el-input v-model="form.coverImage" placeholder="或手动输入/粘贴图片URL" style="width: 340px; margin-top: 6px;" size="small" />
          </div>
        </el-form-item>
        <el-form-item label="演出标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="艺人">
          <el-input v-model="form.artists" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="form.categoryId">
                <el-option label="演唱会" :value="1" />
                <el-option label="音乐节" :value="2" />
                <el-option label="话剧歌剧" :value="3" />
                <el-option label="体育赛事" :value="4" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="场馆ID">
              <el-input-number v-model="form.venueId" :min="1" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="演出时间">
          <el-date-picker v-model="form.showTime" type="datetime" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开售时间">
              <el-date-picker v-model="form.saleStartTime" type="datetime" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="停售时间">
              <el-date-picker v-model="form.saleEndTime" type="datetime" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- ===== 票档 & 座位配置（集成） ===== -->
        <el-divider content-position="left">票档 & 座位配置</el-divider>
        <div class="seat-ticket-editor">
          <!-- 左侧：座位网格 -->
          <div class="ste-left">
            <div class="ste-stage">演 出 舞 台</div>
            <div class="ste-grid-wrap">
              <div class="ste-grid" @mousedown="onDown" @mousemove="onMove" @mouseup="onUp" @mouseleave="onUp">
                <div v-for="r in GRID_ROWS" :key="r" class="ste-row">
                  <span class="ste-rl">{{ rowLabel(r) }}</span>
                  <span v-for="c in GRID_COLS" :key="c"
                    :class="['ste-cell', cellClass(r,c)]"
                    :style="cellStyle(r,c)"
                    :data-r="r" :data-c="c"
                  ></span>
                </div>
              </div>
            </div>
            <div class="ste-legend">
              <span v-for="(tt, i) in ticketTypes" :key="i" class="ste-lg">
                <i :style="{background: ticketColors[i % ticketColors.length]}"></i>{{ tt.name || '未命名' }}
              </span>
              <span v-if="!ticketTypes.length" class="ste-lg" style="color:#666">选择座位后设为票档</span>
            </div>
          </div>
          <!-- 右侧：票档列表 -->
          <div class="ste-right">
            <div v-if="selCells.length" class="ste-sel-bar">
              已选 {{ selCells.length }} 座
              <button type="button" class="ste-btn" @click="promptTicketType">+ 设为票档</button>
              <button type="button" class="ste-btn ghost" @click="selCells=[];selByRc.clear()">清空</button>
            </div>
            <div v-if="!ticketTypes.length && !selCells.length" class="ste-empty">
              点击或拖拽选择座位<br/>然后点击「设为票档」
            </div>
            <div v-for="(tt, i) in ticketTypes" :key="i" class="ste-tt-card"
              @click="highlightTicket(i)"
              :class="{ hl: highlightIdx === i }">
              <span class="ste-tt-dot" :style="{background: ticketColors[i % ticketColors.length]}"></span>
              <div class="ste-tt-info">
                <input v-model="tt.name" class="ste-tt-name" placeholder="票档名称" />
                <span class="ste-tt-count">{{ tt.cells?.length || 0 }}座</span>
              </div>
              <div class="ste-tt-price-row">
                <span>¥</span>
                <input v-model.number="tt.price" type="number" min="0" class="ste-tt-price" placeholder="票价" />
              </div>
              <button type="button" class="ste-tt-del" @click.stop="removeTicket(i)">✕</button>
            </div>
            <!-- 库存汇总 -->
            <div v-if="ticketTypes.length" class="ste-summary">
              <span>总票量: {{ computedTotalStock }}</span>
              <span class="form-hint">保存时自动同步</span>
            </div>
          </div>
        </div>

        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>

        <!-- 业务规则 -->
        <el-divider content-position="left">业务规则配置</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="限购数量">
              <el-input-number v-model="form.buyLimit" :min="0" size="small" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="实名制">
              <el-switch v-model="form.isRealName" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="允许退票">
              <el-switch v-model="form.allowRefund" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="退票时限(h)">
              <el-input-number v-model="form.refundDeadlineHours" :min="0" size="small" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退票手续费(%)">
              <el-input-number v-model="form.refundFeePercent" :min="0" :max="100" :precision="1" size="small" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="支付超时(m)">
              <el-input-number v-model="form.payTimeoutMinutes" :min="1" :max="120" size="small" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开售提醒">
              <el-switch v-model="form.enableReminder" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="是否热门">
              <el-switch v-model="form.isHot" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="演出状态">
          <el-select v-model="form.showStatus">
            <el-option label="即将开售" value="upcoming" />
            <el-option label="预售中" value="presale" />
            <el-option label="售票中" value="onsale" />
            <el-option label="已售罄" value="soldout" />
            <el-option label="已结束" value="ended" />
          </el-select>
        </el-form-item>
        <el-form-item label="票务规则">
          <el-input v-model="form.rules" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="观演须知">
          <el-input v-model="form.notice" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="退票政策">
          <el-input v-model="form.refundPolicy" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button class="btn-gold" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 票档命名弹窗 -->
    <Teleport to="body">
      <div v-if="showTicketPrompt" class="tt-overlay" @mousedown.self="showTicketPrompt=false">
        <div class="tt-card" @mousedown.stop>
          <h4>设置票档信息</h4>
          <p class="tt-sub">为选中的 {{ selCells.length }} 个座位创建票档</p>
          <div class="tt-row">
            <label>票档名称</label>
            <input v-model="newTicketName" class="tt-inp" placeholder="VIP区 / A区 / 看台..." @keydown.enter="confirmTicketType" ref="ticketNameRef" />
          </div>
          <div class="tt-row">
            <label>票价 (¥)</label>
            <input v-model.number="newTicketPrice" type="number" min="0" class="tt-inp" placeholder="680" @keydown.enter="confirmTicketType" />
          </div>
          <div class="tt-btns">
            <button type="button" class="ste-btn ghost" @click="showTicketPrompt=false">取消</button>
            <button type="button" class="ste-btn" @click="confirmTicketType">确认创建</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { ElMessageBox } from 'element-plus'; import { toast } from '@/utils/message'
import { adminGetShows, adminCreateShow, adminUpdateShow, adminDeleteShow, uploadImage } from '@/api'
import dayjs from 'dayjs'

// ======================== Upload ========================
const coverFileInput = ref(null)
const uploading = ref(false)
const coverFit = ref('cover')
const coverPosX = ref(50)
const coverPosY = ref(50)
const coverPos = computed(() => coverPosX.value + '% ' + coverPosY.value + '%')
const showAdjust = ref(false)
function nudgePos(dx, dy) {
  if (dx === 0 && dy === 0) { coverPosX.value = 50; coverPosY.value = 50; return }
  coverPosX.value = Math.max(0, Math.min(100, coverPosX.value + dx))
  coverPosY.value = Math.max(0, Math.min(100, coverPosY.value + dy))
}

function onCoverFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { toast.error('只能上传图片文件'); return }
  if (file.size > 10 * 1024 * 1024) { toast.error('图片不能超过10MB'); return }
  uploading.value = true
  uploadImage(file).then(res => {
    if (res.code === 200 && res.data?.url) {
      form.value.coverImage = res.data.url
      toast.success('封面上传成功！')
    } else {
      toast.error(res.message || '上传失败')
    }
  }).catch(err => {
    toast.error('上传失败: ' + (err?.message || '网络错误'))
  }).finally(() => {
    uploading.value = false
    // 清空 input 以便重复上传同一文件
    if (coverFileInput.value) coverFileInput.value.value = ''
  })
}

// ======================== Seat Grid Constants ========================
const GRID_ROWS = 16
const GRID_COLS = 20
const ticketColors = ['#C8A45A','#E85D75','#5B8FF9','#5AD8A6','#FF9845','#945FB4','#F6BD16','#6DC8EC']

function rowLabel(r) { return String.fromCharCode(64 + r) }
function key(r,c) { return r + '-' + c }

// ======================== Data ========================
const shows = ref([])
const loading = ref(false)
const saving = ref(false)
const page = ref(1); const pageSize = ref(10); const total = ref(0)
const keyword = ref(''); const statusFilter = ref(null)
const dialogVisible = ref(false); const editing = ref(null)
const form = ref({})
const ticketTypes = ref([])
const highlightIdx = ref(-1)

// Seat grid state
const selCells = ref([])
const selByRc = ref(new Map()) // r-c => true for quick lookup
const dragging = ref(false)
const dragS = ref(null); const dragE = ref(null)
const didMove = ref(false)

// Ticket prompt
const showTicketPrompt = ref(false)
const newTicketName = ref('')
const newTicketPrice = ref(0)
const ticketNameRef = ref(null)

const statusMap = {
  0: { text: '待上架', type: 'info' },
  1: { text: '在售', type: 'success' },
  2: { text: '售罄', type: 'warning' },
  3: { text: '已结束', type: 'info' },
  4: { text: '已取消', type: 'danger' },
}

const computedTotalStock = computed(() => ticketTypes.value.reduce((s, t) => s + (t.cells?.length || 0), 0))

function fmt(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '' }

// ======================== Seat Grid Logic ========================
function getTicketForCell(r,c){
  for(let i = 0; i < ticketTypes.value.length; i++){
    if(ticketTypes.value[i].cells && ticketTypes.value[i].cells.includes(key(r,c))) return i
  }
  return -1
}

function cellClass(r,c){
  const ti = getTicketForCell(r,c)
  if(ti >= 0) return 'assigned'
  if(dragging.value && inDragRect(r,c)) return 'hover'
  if(selByRc.value.has(key(r,c))) return 'sel'
  return ''
}

function cellStyle(r,c){
  const ti = getTicketForCell(r,c)
  if(ti >= 0) return {background: ticketColors[ti % ticketColors.length], borderColor: 'transparent'}
  return {}
}

function inDragRect(r,c){
  if(!dragS.value||!dragE.value) return false
  const [r1,c1]=[dragS.value.r,dragS.value.c]; const [r2,c2]=[dragE.value.r,dragE.value.c]
  return r>=Math.min(r1,r2) && r<=Math.max(r1,r2) && c>=Math.min(c1,c2) && c<=Math.max(c1,c2)
}

function toggleCell(r,c){
  if(getTicketForCell(r,c) >= 0) return
  const k = key(r,c)
  if(selByRc.value.has(k)){
    selByRc.value.delete(k)
  } else {
    selByRc.value.set(k, true)
  }
  selCells.value = [...selByRc.value.keys()]
}

function onDown(e){
  if(!e.target.classList.contains('ste-cell')) return
  const r=+e.target.dataset.r, c=+e.target.dataset.c
  if(getTicketForCell(r,c) >= 0) return
  dragging.value = true; didMove.value = false
  dragS.value = {r,c}; dragE.value = {r,c}
}

function onMove(e){
  if(!dragging.value) return
  if(!e.target.classList.contains('ste-cell')) return
  const nr=+e.target.dataset.r, nc=+e.target.dataset.c
  if(dragE.value && dragE.value.r===nr && dragE.value.c===nc) return
  dragE.value = {r:nr, c:nc}
  didMove.value = true
}

function onUp(){
  if(!dragging.value) return
  dragging.value = false
  if(!dragS.value) return

  const [r1,c1]=[dragS.value.r,dragS.value.c]
  const [r2,c2]=[dragE.value.r,dragE.value.c]

  if(!didMove.value){
    // 单击：切换单个座位
    toggleCell(r1, c1)
  } else {
    // 拖拽框选：toggle 矩形区域内所有座位
    const minR=Math.min(r1,r2),maxR=Math.max(r1,r2),minC=Math.min(c1,c2),maxC=Math.max(c1,c2)
    for(let r=minR;r<=maxR;r++){
      for(let c=minC;c<=maxC;c++){
        if(getTicketForCell(r,c) < 0){
          const k = key(r,c)
          if(selByRc.value.has(k)) selByRc.value.delete(k)
          else selByRc.value.set(k, true)
        }
      }
    }
    selCells.value = [...selByRc.value.keys()]
  }
  dragS.value=null; dragE.value=null; didMove.value=false
}

function highlightTicket(i){
  highlightIdx.value = highlightIdx.value === i ? -1 : i
}

function promptTicketType(){
  if(!selCells.value.length) return
  newTicketName.value = ''
  newTicketPrice.value = 0
  showTicketPrompt.value = true
  nextTick(() => { if(ticketNameRef.value) ticketNameRef.value.focus() })
}

function confirmTicketType(){
  if(!showTicketPrompt.value) return
  const name = newTicketName.value.trim() || '票档' + (ticketTypes.value.length + 1)
  ticketTypes.value.push({
    name,
    price: newTicketPrice.value || 0,
    cells: [...selCells.value],
  })
  showTicketPrompt.value = false
  selCells.value = []
  selByRc.value.clear()
}

function removeTicket(i){
  ticketTypes.value.splice(i, 1)
  if(highlightIdx.value === i) highlightIdx.value = -1
}

// ======================== Form Logic ========================
function resetForm(){
  form.value = {
    title:'',artists:'',categoryId:1,venueId:1, showTime:'',saleStartTime:'',saleEndTime:'',
    totalStock:0,availableStock:0,status:1,description:'', coverImage:'',
    buyLimit:1,isRealName:1,allowRefund:1, refundDeadlineHours:48,refundFeePercent:10,
    payTimeoutMinutes:15,enableReminder:1,isHot:0,sort:0,
    showStatus:'upcoming',rules:'',notice:'',refundPolicy:'',
  }
  ticketTypes.value = []
  selCells.value = []
  selByRc.value.clear()
}

function openCreate(){
  editing.value = null; resetForm(); dialogVisible.value = true
}

function onDialogOpened(){
  // dialog 打开后重新计算seat grid的渲染（解决初始显示问题）
}

async function load(){
  loading.value = true
  try {
    const res = await adminGetShows({ page:page.value, pageSize:pageSize.value, keyword:keyword.value, status:statusFilter.value })
    shows.value = res.data.records || []; total.value = res.data.total || 0
  } finally { loading.value = false }
}

function search(){ page.value = 1; load() }

function editShow(row){
  editing.value = row.id
  form.value = {
    title:row.title||'', artists:row.artists||'', categoryId:row.categoryId||1, venueId:row.venueId||1,
    showTime:row.showTime||'', saleStartTime:row.saleStartTime||'', saleEndTime:row.saleEndTime||'',
    totalStock:row.totalStock??0, availableStock:row.availableStock??0, status:row.status??1,
    description:row.description||'', coverImage:row.coverImage||'',
    buyLimit:row.buyLimit??1, isRealName:row.isRealName??1, allowRefund:row.allowRefund??1,
    refundDeadlineHours:row.refundDeadlineHours??48, refundFeePercent:row.refundFeePercent??10,
    payTimeoutMinutes:row.payTimeoutMinutes??15,
    enableReminder:row.enableReminder??1, isHot:row.isHot??0, sort:row.sort??0,
    showStatus:row.showStatus||'upcoming', rules:row.rules||'', notice:row.notice||'', refundPolicy:row.refundPolicy||'',
  }
  selCells.value = []
  selByRc.value.clear()
  highlightIdx.value = -1

  // 从 ticketTypes JSON 还原票档（含 cells 座位数据）
  try {
    const raw = typeof row.ticketTypes === 'string' ? JSON.parse(row.ticketTypes) : (row.ticketTypes || [])
    ticketTypes.value = raw.map(x => ({
      name: x.name || '',
      price: x.price || 0,
      cells: x.cells || [],
    }))
  } catch {
    ticketTypes.value = []
  }
  dialogVisible.value = true
}

async function handleSave(){
  // 校验
  const hasTickets = ticketTypes.value.some(t => t.name && t.name.trim() && (t.cells?.length > 0 || t.price > 0))
  if(!hasTickets && ticketTypes.value.length === 0){
    toast.error('请至少配置一个票档（选择座位后设为票档）'); return
  }

  // 构建 ticketTypes JSON
  const tickets = ticketTypes.value.map(t => ({
    name: t.name || '未命名',
    price: t.price || 0,
    stock: t.cells?.length || 0,
    cells: t.cells || [],
  }))

  const payload = { ...form.value, ticketTypes: JSON.stringify(tickets) }
  payload.totalStock = computedTotalStock.value
  payload.availableStock = computedTotalStock.value

  saving.value = true
  try {
    if(editing.value){
      await adminUpdateShow(editing.value, payload); toast.success('更新成功')
    } else {
      await adminCreateShow(payload); toast.success('创建成功')
    }
    dialogVisible.value = false; load()
  } catch { /* handled */ }
  finally { saving.value = false }
}

async function handleDelete(id){
  try {
    await ElMessageBox.confirm('确定删除？', '提示', { type:'warning' })
    await adminDeleteShow(id); toast.success('已删除'); load()
  } catch { /* cancelled */ }
}

onMounted(() => load())
</script>

<style scoped>
.admin-page{min-height:100%}
.toolbar{display:flex;gap:12px;margin-bottom:20px}
.pagination-wrap{display:flex;justify-content:center;margin-top:20px}
.form-hint{margin-left:8px;font-size:11px;color:var(--text-muted);white-space:nowrap}

/* ===== Cover Upload ===== */
.cover-upload{display:flex;flex-direction:column;gap:4px}
.cover-preview{position:relative;width:360px;height:180px;border-radius:8px;overflow:hidden;border:1px solid var(--border-color);margin-bottom:8px;background:#111}
.cover-preview img{width:100%;height:100%;object-fit:cover;object-position:center center;transition:object-position .2s}
.cover-preview.adjusting .cover-toolbar{opacity:1}
.cover-toolbar{position:absolute;bottom:0;left:0;right:0;padding:6px 10px;background:linear-gradient(transparent, rgba(0,0,0,.85));display:flex;align-items:center;gap:4px;opacity:0;transition:opacity .2s}
.cover-preview:hover .cover-toolbar{opacity:1}
.cover-toolbar button{width:26px;height:24px;border:1px solid rgba(255,255,255,.2);background:rgba(0,0,0,.5);color:#ccc;border-radius:4px;cursor:pointer;font-size:13px;display:flex;align-items:center;justify-content:center;transition:all .15s}
.cover-toolbar button:hover,.cover-toolbar button.active{background:var(--gold-primary);color:#000;border-color:var(--gold-primary)}
.tb-sep{width:1px;height:18px;background:rgba(255,255,255,.15);margin:0 4px}
.nudge-group{display:flex;flex-direction:column;align-items:center;gap:1px}
.nudge-group button{width:24px;height:20px;font-size:10px;border-radius:3px}
.nudge-row{display:flex;gap:1px}
.nudge-rst{font-size:11px!important;color:#E6A23C!important}
.pos-val{font-size:9px;color:#888;min-width:48px;text-align:center}
.cover-remove{position:absolute;top:4px;right:4px;opacity:.8;z-index:2}

/* ===== Seat + Ticket Editor ===== */
.seat-ticket-editor{display:flex;gap:16px;margin-bottom:12px;border:1px solid var(--border-color);border-radius:8px;padding:12px;background:rgba(255,255,255,.01)}
.ste-left{flex:1;overflow:auto;min-width:0}
.ste-stage{text-align:center;padding:8px;background:#111;color:#555;border-radius:4px;margin-bottom:4px;font-size:11px;letter-spacing:8px}
.ste-grid-wrap{overflow:auto}
.ste-grid{user-select:none;display:inline-block}
.ste-row{display:flex;align-items:center;gap:1px;margin-bottom:1px}
.ste-rl{font-size:9px;color:#444;width:16px;text-align:center;flex-shrink:0}
.ste-cell{width:15px;height:13px;border-radius:2px;border:1px solid rgba(255,255,255,.08);background:rgba(255,255,255,.025);cursor:pointer;transition:transform .1s}
.ste-cell:hover{transform:scale(1.35);z-index:2;border-color:rgba(200,164,90,.6)}
.ste-cell.assigned{cursor:default}
.ste-cell.sel,.ste-cell.hover{transform:scale(1.2);z-index:2;background:rgba(200,164,90,.45)!important;border-color:var(--gold-primary)!important}
.ste-legend{display:flex;gap:12px;margin-top:8px;flex-wrap:wrap;justify-content:center}
.ste-lg{display:flex;align-items:center;gap:4px;font-size:10px;color:#999}
.ste-lg i{width:10px;height:10px;border-radius:2px;display:inline-block}

.ste-right{width:200px;flex-shrink:0;display:flex;flex-direction:column;gap:6px;max-height:380px;overflow-y:auto}
.ste-empty{font-size:11px;color:var(--text-muted);text-align:center;padding:30px 0}
.ste-sel-bar{font-size:11px;color:var(--gold-primary);display:flex;align-items:center;gap:6px;flex-wrap:wrap;padding:4px 0}
.ste-btn{padding:4px 10px;background:var(--gold-gradient);color:#000;border:none;border-radius:3px;cursor:pointer;font-size:11px;font-weight:600}
.ste-btn.ghost{background:rgba(255,255,255,.08);color:var(--text-secondary);border:1px solid var(--border-color)}

.ste-tt-card{display:flex;align-items:center;gap:6px;padding:6px 8px;background:rgba(255,255,255,.03);border-radius:6px;border:1px solid transparent;cursor:pointer;transition:all .2s}
.ste-tt-card:hover,.ste-tt-card.hl{border-color:rgba(200,164,90,.4);background:rgba(200,164,90,.06)}
.ste-tt-dot{width:10px;height:10px;border-radius:2px;flex-shrink:0}
.ste-tt-info{flex:1;min-width:0}
.ste-tt-name{width:100%;background:transparent;border:none;color:var(--text-primary);font-size:12px;font-weight:600;outline:none;padding:2px 0}
.ste-tt-name:focus{border-bottom:1px solid var(--gold-primary)}
.ste-tt-count{font-size:10px;color:var(--text-muted)}
.ste-tt-price-row{display:flex;align-items:center;gap:2px;font-size:12px;color:var(--text-muted)}
.ste-tt-price{width:50px;background:rgba(255,255,255,.05);border:1px solid var(--border-color);border-radius:3px;padding:2px 4px;color:var(--gold-primary);font-size:11px;outline:none;text-align:right}
.ste-tt-price:focus{border-color:var(--gold-primary)}
.ste-tt-del{background:none;border:none;color:#E74C3C;cursor:pointer;font-size:14px;padding:0 2px}
.ste-summary{font-size:11px;color:var(--text-muted);text-align:center;padding:6px 0;border-top:1px solid var(--border-color);display:flex;flex-direction:column;gap:2px}
</style>

<!-- Ticket prompt overlay (global) -->
<style>
.tt-overlay{position:fixed;inset:0;background:rgba(0,0,0,.55);display:flex;align-items:center;justify-content:center;z-index:10000}
.tt-card{background:#1a1a1a;border:1px solid #333;border-radius:12px;padding:28px 32px;width:400px;box-shadow:0 20px 80px rgba(0,0,0,.6)}
.tt-card h4{font-size:17px;font-weight:700;color:#fff;margin:0 0 4px}
.tt-sub{font-size:12px;color:#999;margin:0 0 18px}
.tt-row{margin-bottom:14px}
.tt-row label{display:block;font-size:12px;color:#888;margin-bottom:5px}
.tt-inp{width:100%;padding:10px 12px;background:rgba(255,255,255,.06);border:1px solid #333;border-radius:6px;color:#fff;font-size:14px;outline:none;box-sizing:border-box}
.tt-inp:focus{border-color:#C8A45A}
.tt-btns{display:flex;gap:10px;justify-content:flex-end;margin-top:8px}
.tt-btns .ste-btn{padding:9px 24px;font-size:13px;border-radius:6px}
</style>
