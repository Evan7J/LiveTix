<template>
  <div class="card-dark section-card">
    <div class="section-header">
      <h3>观演人信息管理</h3>
      <el-button class="btn-gold" @click="showDialog = true; editingId = null; resetForm()">+ 添加观演人</el-button>
    </div>
    <p class="hint">根据相关规定，实名制演出需凭身份证件入场。最多可添加 6 位观演人。</p>

    <div v-if="list.length > 0">
      <div v-for="item in list" :key="item.id" class="realname-card">
        <div class="rn-info">
          <span class="rn-name">{{ item.realName }}</span>
          <span class="rn-idcard">{{ item.idCardType === 'ID_CARD' ? '身份证' : item.idCardType === 'PASSPORT' ? '护照' : '港澳通行证' }} {{ item.idCardNumber }}</span>
          <el-tag v-if="item.isDefault" type="warning" size="small">默认</el-tag>
        </div>
        <div class="rn-actions">
          <el-button text size="small" @click="editItem(item)">编辑</el-button>
          <el-button text size="small" type="danger" @click="deleteItem(item)">删除</el-button>
        </div>
      </div>
    </div>
    <el-empty v-else description="暂无观演人信息" />

    <!-- Dialog -->
    <el-dialog v-model="showDialog" :title="editingId ? '编辑观演人' : '添加观演人'" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="真实姓名" required>
          <el-input v-model="form.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="证件类型" required>
          <el-select v-model="form.idCardType" style="width: 100%;">
            <el-option label="身份证" value="ID_CARD" />
            <el-option label="护照" value="PASSPORT" />
            <el-option label="港澳通行证" value="HK_MACAU_PASS" />
          </el-select>
        </el-form-item>
        <el-form-item label="证件号码" required>
          <el-input v-model="form.idCardNumber" placeholder="请输入证件号码" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
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
import { getRealNames, addRealName, updateRealName, deleteRealName } from '@/api'

const list = ref([])
const showDialog = ref(false)
const editingId = ref(null)
const saving = ref(false)
const form = reactive({ realName: '', idCardType: 'ID_CARD', idCardNumber: '', phone: '', isDefault: 0 })

function resetForm() {
  form.realName = ''; form.idCardType = 'ID_CARD'; form.idCardNumber = ''; form.phone = ''; form.isDefault = 0
}

function editItem(item) {
  editingId.value = item.id
  form.realName = item.realName
  form.idCardType = item.idCardType
  form.idCardNumber = item.idCardNumber
  form.phone = item.phone
  form.isDefault = item.isDefault
  showDialog.value = true
}

async function loadList() {
  try {
    const res = await getRealNames()
    list.value = res.data || []
  } catch { /* ignore */ }
}

async function handleSave() {
  if (!form.realName || !form.idCardNumber) {
    toast.warning('请填写完整信息'); return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateRealName(editingId.value, { ...form })
      toast.success('更新成功')
    } else {
      await addRealName({ ...form })
      toast.success('添加成功')
    }
    showDialog.value = false
    loadList()
  } catch { /* interceptor handles error toast */ }
  finally { saving.value = false }
}

async function deleteItem(item) {
  try {
    await ElMessageBox.confirm('确定要删除该观演人信息吗？', '提示', { type: 'warning' })
    await deleteRealName(item.id)
    toast.success('删除成功')
    loadList()
  } catch { /* cancelled */ }
}

onMounted(() => loadList())
</script>

<style scoped>
.section-card { padding: 24px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.section-header h3 { font-size: 16px; }
.hint { color: var(--text-muted); font-size: 13px; margin-bottom: 16px; }
.realname-card { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; background: var(--bg-secondary); border-radius: 8px; margin-bottom: 8px; }
.rn-info { display: flex; align-items: center; gap: 12px; }
.rn-name { font-weight: 600; font-size: 15px; }
.rn-idcard { color: var(--text-secondary); font-size: 13px; }
</style>
