<template>
  <div class="admin-page">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索用户名/昵称/手机..." style="width: 280px;" clearable @clear="search" @keyup.enter="search" />
    </div>

    <el-table :data="users" style="width: 100%;">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="nickname" label="昵称" width="120" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="角色" width="80">
        <template #default="{ row }">
          <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="会员等级" width="100">
        <template #default="{ row }">{{ memberLevels[row.memberLevel] }}</template>
      </el-table-column>
      <el-table-column label="余额" width="100">
        <template #default="{ row }">¥{{ row.balance }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="160">
        <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button
            size="small"
            :type="row.status === 1 ? 'danger' : 'success'"
            text
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" background @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { toast } from '@/utils/message'
import { adminGetUsers, adminToggleUserStatus } from '@/api'
import dayjs from 'dayjs'

const users = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')

const memberLevels = { 0: '普通', 1: '银卡', 2: '金卡', 3: '钻石' }

function formatDate(d) { return dayjs(d).format('YYYY-MM-DD HH:mm') }

async function load() {
  const res = await adminGetUsers({ page: page.value, pageSize: pageSize.value, keyword: keyword.value })
  users.value = res.data.records
  total.value = res.data.total
}

function search() { page.value = 1; load() }

async function toggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  await adminToggleUserStatus(row.id, newStatus)
  toast.success('更新成功')
  load()
}

onMounted(() => load())
</script>

<style scoped>
.admin-page { min-height: 100%; }
.toolbar { display: flex; gap: 12px; margin-bottom: 20px; }
.pagination-wrap { display: flex; justify-content: center; margin-top: 20px; }
</style>
