<template>
  <div>
    <h3>操作日志</h3>
    <div class="filters">
      <el-select v-model="filterModule" placeholder="操作模块" clearable style="width: 140px;" @change="loadList">
        <el-option v-for="m in modules" :key="m.value" :label="m.label" :value="m.value" />
      </el-select>
      <el-select v-model="filterAction" placeholder="操作类型" clearable style="width: 140px;" @change="loadList">
        <el-option v-for="a in actions" :key="a.value" :label="a.label" :value="a.value" />
      </el-select>
      <el-input v-model="filterAdmin" placeholder="操作人" clearable style="width: 160px;" @change="loadList" />
    </div>

    <el-table :data="list" style="width: 100%; margin-top: 16px;" v-loading="loading">
      <el-table-column prop="adminName" label="操作人" width="120" />
      <el-table-column prop="module" label="模块" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ moduleName(row.module) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="action" label="操作" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.action === 'delete' ? 'danger' : row.action.includes('approve') ? 'success' : 'info'">{{ actionName(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="详情" min-width="200">
        <template #default="{ row }">{{ truncate(row.detail, 80) }}</template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column prop="createTime" label="时间" width="160">
        <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminGetLogs } from '@/api'
import dayjs from 'dayjs'

const list = ref([])
const loading = ref(false)
const filterModule = ref('')
const filterAction = ref('')
const filterAdmin = ref('')

const modules = [
  { value: 'show', label: '演出管理' }, { value: 'order', label: '订单管理' },
  { value: 'user', label: '用户管理' }, { value: 'coupon', label: '优惠券' },
  { value: 'category', label: '分类管理' }, { value: 'refund', label: '退票审核' }, { value: 'system', label: '系统设置' },
]
const actions = [
  { value: 'create', label: '创建' }, { value: 'update', label: '修改' },
  { value: 'delete', label: '删除' }, { value: 'approve', label: '通过' }, { value: 'reject', label: '拒绝' },
]

function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm:ss') : '--' }
function truncate(s, len) { return s && s.length > len ? s.substring(0, len) + '...' : s }
function moduleName(m) { return modules.find(x => x.value === m)?.label || m }
function actionName(a) { return actions.find(x => x.value === a)?.label || a }

async function loadList() {
  loading.value = true
  try {
    const res = await adminGetLogs({
      page: 1, pageSize: 50,
      module: filterModule.value || undefined,
      action: filterAction.value || undefined,
      adminName: filterAdmin.value || undefined,
    })
    list.value = res.data?.records || []
  } finally { loading.value = false }
}

onMounted(() => loadList())
</script>

<style scoped>
h3 { font-size: 18px; margin-bottom: 16px; }
.filters { display: flex; gap: 12px; }
</style>
