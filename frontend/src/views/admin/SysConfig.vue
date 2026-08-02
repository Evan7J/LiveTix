<template>
  <div class="admin-page">
    <el-table :data="configs" style="width: 100%; max-width: 800px;">
      <el-table-column prop="configKey" label="配置键" width="200" />
      <el-table-column prop="configValue" label="配置值" min-width="300" />
      <el-table-column prop="description" label="描述" width="200" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button size="small" text @click="editConfig(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="编辑配置" width="400px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="配置键">
          <el-input v-model="form.configKey" disabled />
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="form.configValue" />
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
import { ref, onMounted } from 'vue'
import { toast } from '@/utils/message'
import { adminGetConfigs, adminUpdateConfig } from '@/api'

const configs = ref([])
const dialogVisible = ref(false)
const form = ref({})
const saving = ref(false)

async function load() {
  const res = await adminGetConfigs()
  configs.value = res.data
}

function editConfig(row) {
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  try {
    await adminUpdateConfig(form.value.id, { configValue: form.value.configValue })
    toast.success('更新成功')
    dialogVisible.value = false
    load()
  } catch { /* interceptor handles error toast */ }
  finally { saving.value = false }
}

onMounted(() => load())
</script>

<style scoped>
.admin-page { min-height: 100%; }
</style>
