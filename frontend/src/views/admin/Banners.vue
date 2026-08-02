<template>
  <div class="admin-page">
    <div class="toolbar">
      <el-button class="btn-gold" @click="dialogVisible = true; editing = null; resetForm()">新增轮播图</el-button>
    </div>

    <el-table :data="banners" style="width: 100%;" v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="预览" width="140">
        <template #default="{ row }">
          <img :src="row.imageUrl" style="width:120px;height:60px;object-fit:cover;border-radius:4px;" @error="e => e.target.style.display='none'" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" width="200" />
      <el-table-column prop="imageUrl" label="图片URL" min-width="200" />
      <el-table-column prop="showId" label="关联演出ID" width="100" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'info'" size="small">{{ row.status ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" text @click="editBanner(row)">编辑</el-button>
          <el-button size="small" text type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑轮播图' : '新增轮播图'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="轮播图片">
          <div class="cover-upload">
            <!-- 预览区域 -->
            <div class="cover-preview" v-if="form.imageUrl">
              <img :src="form.imageUrl"
                :style="{ objectFit: fit, objectPosition: pos }"
                @error="e => e.target.style.display='none'" />
              <div class="cover-toolbar">
                <el-tooltip content="铺满裁剪" placement="top"><button type="button" :class="{active:fit==='cover'}" @click="fit='cover'">⊞</button></el-tooltip>
                <el-tooltip content="完整显示" placement="top"><button type="button" :class="{active:fit==='contain'}" @click="fit='contain'">⊟</button></el-tooltip>
                <el-tooltip content="拉伸填充" placement="top"><button type="button" :class="{active:fit==='fill'}" @click="fit='fill'">⬒</button></el-tooltip>
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
                <span class="pos-val">{{ posX }}% {{ posY }}%</span>
              </div>
            </div>
            <input ref="bannerFileInput" type="file" accept="image/*" style="display:none" @change="onBannerFileChange" />
            <el-button :loading="uploading" @click="$refs.bannerFileInput.click()" :type="form.imageUrl ? 'default' : 'primary'">
              {{ uploading ? '上传中...' : (form.imageUrl ? '更换轮播图片' : '点击上传图片') }}
            </el-button>
            <span class="form-hint">建议尺寸 1920×600，最大10MB · 上传即替换</span>
            <el-input v-model="form.imageUrl" placeholder="或手动输入图片URL" style="width: 340px; margin-top: 6px;" size="small" />
          </div>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="关联演出ID"><el-input-number v-model="form.showId" :min="0" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button class="btn-gold" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessageBox } from 'element-plus'; import { toast } from '@/utils/message'
import { adminGetBanners, adminCreateBanner, adminUpdateBanner, adminDeleteBanner, uploadImage } from '@/api'

const banners = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editing = ref(null)
const form = ref({})
const bannerFileInput = ref(null)
const uploading = ref(false)
const fit = ref('cover')
const posX = ref(50)
const posY = ref(50)
const pos = computed(() => posX.value + '% ' + posY.value + '%')
function nudgePos(dx, dy) {
  if (dx === 0 && dy === 0) { posX.value = 50; posY.value = 50; return }
  posX.value = Math.max(0, Math.min(100, posX.value + dx))
  posY.value = Math.max(0, Math.min(100, posY.value + dy))
}

function resetForm() {
  form.value = { title: '', imageUrl: '', showId: null, sort: 0, status: 1 }
}

async function load() {
  loading.value = true
  try {
    const res = await adminGetBanners()
    banners.value = res.data || []
  } finally { loading.value = false }
}

function editBanner(row) {
  editing.value = row.id
  form.value = { ...row }
  dialogVisible.value = true
}

function onBannerFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { toast.error('只能上传图片文件'); return }
  if (file.size > 10 * 1024 * 1024) { toast.error('图片不能超过10MB'); return }
  uploading.value = true
  uploadImage(file).then(res => {
    if (res.code === 200 && res.data?.url) {
      form.value.imageUrl = res.data.url
      toast.success('图片上传成功！')
    } else {
      toast.error(res.message || '上传失败')
    }
  }).catch(err => {
    toast.error('上传失败: ' + (err?.message || '网络错误'))
  }).finally(() => {
    uploading.value = false
    if (bannerFileInput.value) bannerFileInput.value.value = ''
  })
}

async function handleSave() {
  saving.value = true
  try {
    if (editing.value) {
      await adminUpdateBanner(editing.value, form.value)
    } else {
      await adminCreateBanner(form.value)
    }
    toast.success('保存成功')
    dialogVisible.value = false
    load()
  } catch { /* handled */ }
  finally { saving.value = false }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
    await adminDeleteBanner(id)
    toast.success('删除成功')
    load()
  } catch { /* cancelled */ }
}

onMounted(() => load())
</script>

<style scoped>
.admin-page { min-height: 100%; }
.toolbar { display: flex; gap: 12px; margin-bottom: 20px; }
.cover-upload { display: flex; flex-direction: column; gap: 4px; }
.cover-preview { position: relative; width: 400px; height: 130px; border-radius: 8px; overflow: hidden; border: 1px solid var(--border-color); margin-bottom: 8px; background: #111; }
.cover-preview img { width: 100%; height: 100%; object-fit: cover; object-position: center center; transition: object-position .2s; }
.cover-toolbar { position: absolute; bottom: 0; left: 0; right: 0; padding: 6px 10px; background: linear-gradient(transparent, rgba(0,0,0,.85)); display: flex; align-items: center; gap: 4px; opacity: 0; transition: opacity .2s; }
.cover-preview:hover .cover-toolbar { opacity: 1; }
.cover-toolbar button { width: 26px; height: 24px; border: 1px solid rgba(255,255,255,.2); background: rgba(0,0,0,.5); color: #ccc; border-radius: 4px; cursor: pointer; font-size: 13px; display: flex; align-items: center; justify-content: center; transition: all .15s; }
.cover-toolbar button:hover, .cover-toolbar button.active { background: var(--gold-primary); color: #000; border-color: var(--gold-primary); }
.tb-sep { width: 1px; height: 18px; background: rgba(255,255,255,.15); margin: 0 4px; }
.nudge-group { display: flex; flex-direction: column; align-items: center; gap: 1px; }
.nudge-group button { width: 24px; height: 20px; font-size: 10px; border-radius: 3px; }
.nudge-row { display: flex; gap: 1px; }
.nudge-rst { font-size: 11px !important; color: #E6A23C !important; }
.pos-val { font-size: 9px; color: #888; min-width: 48px; text-align: center; }
.cover-remove { position: absolute; top: 4px; right: 4px; opacity: .8; z-index: 2; }
.form-hint { margin-left: 8px; font-size: 11px; color: var(--text-muted); }
</style>
