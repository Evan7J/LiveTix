<template>
  <div>
    <div class="page-header">
      <h3>演出分类管理</h3>
      <el-button class="btn-gold" @click="showDialog = true; editingId = null; resetForm()">+ 新增分类</el-button>
    </div>

    <el-table :data="list" style="width: 100%;" v-loading="loading">
      <el-table-column prop="id" label="#" width="60" />
      <el-table-column prop="name" label="分类名称" />
      <el-table-column prop="sort" label="排序" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button text size="small" @click="editItem(row)">编辑</el-button>
          <el-button text size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showDialog" :title="editingId ? '编辑分类' : '新增分类'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="分类名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button class="btn-gold" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'; import { toast } from '@/utils/message'
import { adminGetCategories, adminCreateCategory, adminUpdateCategory, adminDeleteCategory } from '@/api'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const showDialog = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', sort: 0, status: 1 })

function resetForm() { form.name = ''; form.sort = 0; form.status = 1 }

function editItem(item) {
  editingId.value = item.id
  form.name = item.name
  form.sort = item.sort
  form.status = item.status
  showDialog.value = true
}

async function loadList() {
  loading.value = true
  try {
    const res = await adminGetCategories()
    list.value = res.data || []
  } finally { loading.value = false }
}

async function handleSave() {
  saving.value = true
  try {
    if (editingId.value) { await adminUpdateCategory(editingId.value, { ...form }) }
    else { await adminCreateCategory({ ...form }) }
    toast.success(editingId.value ? '更新成功' : '创建成功')
    showDialog.value = false
    loadList()
  } finally { saving.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除该分类？', '提示', { type: 'warning' })
    await adminDeleteCategory(row.id)
    toast.success('删除成功')
    loadList()
  } catch { /* cancelled */ }
}

onMounted(() => loadList())
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h3 { font-size: 18px; }
</style>
