<template>
  <div>
    <div class="page-header">
      <h3>权限管理</h3>
      <el-button class="btn-gold" @click="showRoleDialog = true; editingRoleId = null; roleForm.name = ''">+ 新增角色</el-button>
    </div>

    <el-table :data="roles" style="width: 100%;" v-loading="loading">
      <el-table-column prop="roleCode" label="角色编码" width="150" />
      <el-table-column prop="roleName" label="角色名称" width="150" />
      <el-table-column prop="description" label="描述" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button text size="small" @click="openPermDialog(row)">分配权限</el-button>
          <el-button text size="small" type="danger" @click="handleDeleteRole(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Permission Dialog -->
    <el-dialog v-model="permDialog" title="分配权限" width="500px">
      <el-tree
        :data="permTree"
        :props="{ label: 'permName', children: 'children' }"
        node-key="id"
        show-checkbox
        default-expand-all
        ref="permTreeRef"
      />
      <template #footer>
        <el-button @click="permDialog = false">取消</el-button>
        <el-button class="btn-gold" @click="savePermissions" :loading="savingPerm">保存</el-button>
      </template>
    </el-dialog>

    <!-- Role Dialog -->
    <el-dialog v-model="showRoleDialog" :title="editingRoleId ? '编辑角色' : '新增角色'" width="400px">
      <el-form :model="roleForm" label-width="80px">
        <el-form-item label="角色编码"><el-input v-model="roleForm.roleCode" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="roleForm.roleName" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="roleForm.description" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRoleDialog = false">取消</el-button>
        <el-button class="btn-gold" @click="saveRole" :loading="savingRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'; import { toast } from '@/utils/message'
import {
  adminGetRoles, adminCreateRole, adminUpdateRole, adminDeleteRole,
  adminGetPermissions, adminGetRolePermissions, adminUpdateRolePermissions
} from '@/api'

const roles = ref([])
const perms = ref([])
const permTreeRef = ref(null)
const loading = ref(false)
const permDialog = ref(false)
const showRoleDialog = ref(false)
const savingPerm = ref(false)
const savingRole = ref(false)
const currentRoleId = ref(null)
const editingRoleId = ref(null)
const roleForm = reactive({ roleCode: '', roleName: '', description: '' })

const permTree = ref([])

function buildTree(items) {
  const map = {}
  items.forEach(i => { map[i.id] = { ...i, children: [] } })
  const roots = []
  items.forEach(i => {
    if (i.parentId && map[i.parentId]) map[i.parentId].children.push(map[i.id])
    else if (!i.parentId || i.parentId === 0) roots.push(map[i.id])
  })
  return roots
}

async function loadData() {
  loading.value = true
  try {
    const r = await adminGetRoles()
    roles.value = r.data || []
    const p = await adminGetPermissions()
    perms.value = p.data || []
    permTree.value = buildTree(perms.value)
  } finally { loading.value = false }
}

async function openPermDialog(row) {
  currentRoleId.value = row.id
  try {
    const res = await adminGetRolePermissions(row.id)
    const checkedIds = res.data || []
    permDialog.value = true
    // Need to wait for tree render, then set checked keys
    setTimeout(() => {
      if (permTreeRef.value) permTreeRef.value.setCheckedKeys(checkedIds)
    }, 200)
  } catch { /* ignore */ }
}

async function savePermissions() {
  savingPerm.value = true
  try {
    const checked = permTreeRef.value.getCheckedKeys()
    const halfChecked = permTreeRef.value.getHalfCheckedKeys()
    await adminUpdateRolePermissions(currentRoleId.value, { permissionIds: [...checked, ...halfChecked] })
    toast.success('权限更新成功')
    permDialog.value = false
  } finally { savingPerm.value = false }
}

async function saveRole() {
  savingRole.value = true
  try {
    if (editingRoleId.value) {
      await adminUpdateRole(editingRoleId.value, { ...roleForm })
    } else {
      await adminCreateRole({ ...roleForm })
    }
    toast.success('保存成功')
    showRoleDialog.value = false
    loadData()
  } catch { /* interceptor handles error toast */ }
  finally { savingRole.value = false }
}

async function handleDeleteRole(row) {
  try {
    await ElMessageBox.confirm('确定删除该角色？', '提示', { type: 'warning' })
    await adminDeleteRole(row.id)
    toast.success('删除成功')
    loadData()
  } catch { /* cancelled */ }
}

onMounted(() => loadData())
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h3 { font-size: 18px; }
</style>
