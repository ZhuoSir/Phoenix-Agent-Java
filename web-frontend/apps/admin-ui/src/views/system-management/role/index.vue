<script lang="ts" setup>
import type {FormInstance, FormRules} from 'element-plus';
import {ElButton, ElMessage, ElMessageBox, ElTree,} from 'element-plus';
import type {Recordable} from '@vben/types';

import type {AclPvalueItem, AclTreeNode, PrivilegeRole} from '#/api';
import {
  createRoleApi,
  deleteRoleApi,
  getRolePageApi,
  saveAllAclApi,
  saveModuleAclApi,
  updateRoleApi,
} from '#/api';
import type {VxeGridProps} from '#/adapter/vxe-table';
import {useVbenVxeGrid, VbenTableAction} from '#/adapter/vxe-table';
import type {VbenFormProps} from '@vben/common-ui';
import {Page, useVbenModal} from '@vben/common-ui';
import {useColumns, useSearchFormSchema} from './data';
import Form from './form.vue';
import AssignMenu from './assign-menu.vue';
import {computed, onMounted, reactive, ref} from 'vue';
import {useVbenForm} from '#/adapter/form';

const PerPrefix = "Role:";

const loading = ref(false);
const tableData = ref<PrivilegeRole[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const dialogVisible = ref(false);
const isEditMode = ref(false);
const submitting = ref(false);
const formRef = ref<FormInstance>();

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: Form,
  destroyOnClose: true,
});

const [AssignMenuModal, assignMenuModalApi] = useVbenModal({
  connectedComponent: AssignMenu,
  destroyOnClose: true,
});

const formOptions: VbenFormProps = {
  showCollapseButton: false,
  submitOnEnter: true,
  commonConfig: {
    labelWidth: 60,
  },
  wrapperClass: 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4',
  actionWrapperClass: 'pl-2 !justify-end md:!justify-start',
  actionPosition: 'left',
  actionLayout: 'inline',
  submitButtonOptions: { content: '查询' },
  resetButtonOptions: { plain: true },
  schema: useSearchFormSchema(),
};

const gridOptions: VxeGridProps = {
  checkboxConfig: {
    highlight: true,
    labelField: 'name',
  },
  columns: useColumns(onStatusChange),
  columnConfig: {resizable: true},
  height: 'auto',
  keepSource: true,
  border: false,
  stripe: true,
  showOverflow: false,
  proxyConfig: {
    ajax: {
      query: async ({page}, formValues) => {
        return await getRolePageApi({
          page: page.currentPage,
          size: page.pageSize,
          ...formValues,
        });
      },
    },
  },
};

const [Grid, gridApi] = useVbenVxeGrid({formOptions, gridOptions});

function onCreate() {
  formModalApi.setData(null).open();
}
function onEdit(row: any) {
  formModalApi.setData(row).open();
}
/**
 * 状态开关即将改变
 * @param newStatus 期望改变的状态值
 * @param row 行数据
 * @returns 返回false则中止改变，返回其他值（undefined、true）则允许改变
 */
async function onStatusChange(
    newStatus: number,
    row: any,
) {
  const status: Recordable<string> = {
    0: '禁用',
    1: '启用',
  };
  try {
    await confirm(
        `你要将${row.name}的状态切换为 【${status[newStatus.toString()]}】 吗？`,
        `切换状态`,
    );
    await updateUser(row.id, { status: newStatus });
    return true;
  } catch {
    return false;
  }
}

const [FilterForm] = useVbenForm({
  commonConfig: { componentProps: { clearable: true } },
  layout: 'inline',
  wrapperClass: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3',
  submitButtonOptions: { content: '查询' },
  resetButtonOptions: { plain: true },
  schema: [
    {
      fieldName: 'name',
      component: 'Input',
      label: '角色名称',
      labelWidth: 60,
      componentProps: { placeholder: '请输入角色名称' },
    },
    {
      fieldName: 'sn',
      component: 'Input',
      label: '角色标识',
      componentProps: { placeholder: '请输入角色标识' },
    },
  ],
  handleSubmit: (values) => {
    page.value = 1;
    loadData(values);
  },
  handleReset: () => {
    page.value = 1;
    loadData({});
  },
});

const formData = reactive<Record<string, any>>({
  name: '',
  sn: '',
});

const formRules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  sn: [{ required: true, message: '请输入角色标识', trigger: 'blur' }],
};

const assignDialogVisible = ref(false);
const aclTreeData = ref<AclTreeNode[]>([]);
const currentRole = ref<null | PrivilegeRole>(null);
const treeRef = ref<InstanceType<typeof ElTree>>();

async function loadData(params: Record<string, any> = {}) {
  loading.value = true;
  try {
    const res = (await getRolePageApi({
      page: page.value,
      size: pageSize.value,
      ...params,
    })) as any;
    const pageResult = res?.data || res;
    tableData.value = pageResult?.records || [];
    total.value = pageResult?.totalRow || 0;
  } catch {
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function showAddDialog() {
  isEditMode.value = false;
  formData.name = '';
  formData.sn = '';
  dialogVisible.value = true;
}

function handleEdit(row: PrivilegeRole) {
  isEditMode.value = true;
  formData.name = row.name;
  formData.sn = row.sn;
  formData.id = row.id;
  dialogVisible.value = true;
}

async function handleSubmit() {
  if (!formRef.value) return;
  try {
    await formRef.value.validate();
    submitting.value = true;
    if (isEditMode.value) {
      await updateRoleApi({ ...formData });
      ElMessage.success('角色更新成功');
    } else {
      await createRoleApi({ ...formData });
      ElMessage.success('角色创建成功');
    }
    dialogVisible.value = false;
    loadData();
  } catch {
    // validation or API error
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(id: string, name: string) {
  try {
    await ElMessageBox.confirm(
      `确定要删除角色 "${name}" 吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        confirmButtonType: 'danger',
      },
    );
    await deleteRoleApi(id);
    ElMessage.success('角色删除成功');
    loadData();
  } catch {
    // cancelled or error
  }
}

async function handleAssignMenu(row: PrivilegeRole) {
  // R-10（BUG-116）：授权入口统一走 AssignMenu 弹窗（见上方 connectedComponent: AssignMenu），
  // 由该组件读取并保存 ACL。此处原先在 open() 之后紧跟 `return;`，其后的旧 ElDialog 加载逻辑
  // 是不可达死代码——真因 currentRole 从未赋值正是被这段死代码掩盖的，故一并清除。
  assignMenuModalApi.setData(row).open();
}

const treeProps = {
  children: 'children',
  label: 'name',
};

function calcAclState(pvalues: AclPvalueItem[]): string {
  let state = 0;
  for (const pv of pvalues) {
    if (pv.enabled) {
      state |= 1 << pv.position;
    }
  }
  return String(state);
}

function updateNodeState(data: AclTreeNode) {
  data.state = calcAclState(data.pvalues);
}

function handlePvalueChange(data: AclTreeNode) {
  updateNodeState(data);
  const role = currentRole.value;
  if (!role?.id || !role?.sn) return;
  const aclState = Number(calcAclState(data.pvalues));
  saveModuleAclApi({
    releaseId: role.id,
    // BUG-138：release_sn 是释放类型（恒 'role'），不是角色业务 sn；后端亦会强制归正
    releaseSn: 'role',
    moduleId: data.id,
    moduleSn: data.sn,
    aclState,
    status: aclState > 0 ? 'check' : 'uncheck',
  });
}

function traversePvalues(nodes: AclTreeNode[]): AclPvalueItem[] {
  const result: AclPvalueItem[] = [];
  for (const node of nodes) {
    if (node.pvalues?.length) result.push(...node.pvalues);
    if (node.children?.length) result.push(...traversePvalues(node.children));
  }
  return result;
}

const allPvalues = computed(() => traversePvalues(aclTreeData.value));

const headerAllSelected = computed(() => {
  const list = allPvalues.value;
  return list.length > 0 && list.every((pv) => pv.enabled);
});

const headerIndeterminate = computed(() => {
  const list = allPvalues.value;
  return list.some((pv) => pv.enabled) && !headerAllSelected.value;
});

function handleHeaderSelectAll(val: string | number | boolean) {
  const checked = !!val;
  function traverse(nodes: AclTreeNode[]) {
    for (const node of nodes) {
      if (node.pvalues?.length) {
        node.pvalues.forEach((pv) => {
          pv.enabled = checked;
        });
        updateNodeState(node);
      }
      if (node.children?.length) {
        traverse(node.children);
      }
    }
  }
  traverse(aclTreeData.value);
  const roleId = currentRole.value?.id;
  if (roleId) {
    saveAllAclApi(roleId, checked);
  }
}

async function handleSaveAcl() {
  assignDialogVisible.value = false;
}

async function handleCancelAcl() {
  assignDialogVisible.value = false;
}

function handlePageChange(val: number) {
  page.value = val;
  loadData();
}

function handleSizeChange(val: number) {
  pageSize.value = val;
  page.value = 1;
  loadData();
}
/**
 * 刷新表格
 */
function refreshGrid() {
  gridApi.query();
}


function onDelete(row: SystemUserApi.SystemUser) {
  /*const hideLoading = message.loading({
    content: '加载中...',
    duration: 0,
    key: 'action_process_msg',
  });*/
  deleteRoleApi(row.id)
      .then((res) => {
        ElMessage.success('删除成功');
        /*message.success({
          content: $t('ui.actionMessage.deleteSuccess', [row.name]),
          key: 'action_process_msg',
        });*/
        refreshGrid();
      })
      .catch(() => {
        // hideLoading();
      });
}

function getActions(row: any) {
  return [
    {
      text: '分配权限',
      icon: 'lucide:edit',
      onClick: () => handleAssignMenu(row),
    },
    {
      text: '编辑',
      icon: 'lucide:edit',
      onClick: () => onEdit(row),
    },
    {
      text: '删除',
      icon: 'lucide:trash-2',
      danger: true,
      popConfirm: {
        title: `确定要删除【${row.name}】吗？`,
        confirm: () => onDelete(row),
        okText: '确定',
        cancelText: '取消',
      },
    },
  ];
}

onMounted(() => {
  // loadData();
});
</script>

<template>
  <Page auto-content-height>
    <AssignMenuModal @success="refreshGrid" />
    <FormModal @success="refreshGrid" />
    <Grid table-title="角色列表">
      <template #toolbar-tools>
        <ElButton type="primary" @click="onCreate">新建</ElButton>
      </template>

      <template #action="{ row }">
        <VbenTableAction
            align="center"
            :actions="getActions(row)"
        />
      </template>
    </Grid>


  <!-- R-10（BUG-116）历史说明：此处原有整套「旧 ElDialog 授权界面」实现
       （FilterForm + 分配权限对话框 + 其辅助函数调用点）。该路径早已被 AssignMenu 组件
       （见 useVbenModal connectedComponent）取代，且正是它的存在掩盖了
       「assign-menu.vue 从未给 currentRole 赋值 ⇒ 授权不落库」这一真因。
       旧实现整段删除，历史版本见 git。 -->
  </Page>
</template>

<style scoped>
.acl-body :deep(.el-tree-node) {
  padding-left: 0 !important;
}

.acl-body :deep(.el-tree-node__content) {
  height: auto;
  padding: 0.35rem 0.5rem;
  padding-left: 8px !important;
}
</style>
