<script lang="ts" setup>
import {computed, ref} from 'vue';

import {useVbenModal} from '@vben/common-ui';
import {getAclsByReleaseIdApi, getRoleAclsApi, saveModuleAclApi,} from '#/api';
import {IconifyIcon} from '@vben/icons';

import {ElCheckbox, ElIcon, ElMessage, ElTree,} from 'element-plus';

const aclLoading = ref(false);
const aclTreeData = ref<any[]>([]);
const existingAclMap = ref(new Map<string, any>());

const emit = defineEmits(['success']);
const formData = ref<any>();
const getTitle = computed(() => {
  return formData.value?.id
      ? '编辑角色'
      : '创建角色';
});

const [Modal, modalApi] = useVbenModal({
  // BUG-137：保存时机从「勾选即时保存」改为「**确定时批量提交**」。
  // 原实现每个 checkbox 的 change 就发 saveModuleAclApi，导致「确定」无提交职责（只触发父列表 page 刷新）、
  // 且「取消/关闭」无法撤销已勾选的修改（取消是骗人的）。
  // 现在：勾选只改本地状态；点「确定」才把**有改动的模块**批量提交，成功后关窗+刷新；失败不关窗可重试。
  async onConfirm() {
    const role = currentRole.value;
    if (!role?.id || !role?.sn) {
      modalApi.close();
      return;
    }
    const dirty: { cur: number; node: any }[] = [];
    const walk = (nodes: any[]) => {
      for (const n of nodes || []) {
        if (n.pvalues?.length) {
          const cur = Number(calcAclState(n.pvalues));
          const init = initialStateMap.value.get(n.id);
          if (init !== undefined && init !== cur) dirty.push({ node: n, cur });
        }
        if (n.children?.length) walk(n.children);
      }
    };
    walk(aclTreeData.value);
    // 无改动：直接关闭（不发提交请求）
    if (dirty.length === 0) {
      modalApi.close();
      emit('success');
      return;
    }
    modalApi.lock();
    try {
      const results = await Promise.all(
        dirty.map(({ node, cur }) =>
          saveModuleAclApi({
            releaseId: role.id,
            releaseSn: role.sn,
            systemSn: '',
            moduleId: node.id,
            moduleSn: node.sn,
            aclState: cur,
            status: cur > 0 ? 'check' : 'uncheck',
          }),
        ),
      );
      const failed = results.some((r: any) => r && r.success === false);
      if (failed) {
        ElMessage.error('部分权限保存失败，请重试');
        return;
      }
      ElMessage.success(`权限保存成功（${dirty.length} 项）`);
      modalApi.close();
      emit('success');
    } catch {
      ElMessage.error('权限保存失败');
    } finally {
      modalApi.unlock();
    }
  },
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<any>();
      if (data) {
        // R-10 修复（BUG-116）：此前只读 data 却从未写入 currentRole，
        // 导致 handlePvalueChange / handleHeaderSelectAll 里的 `if (!role?.id ...) return`
        // 永远提前返回 ⇒ 授权与撤销**都不落库**（界面看着能勾，其实什么都没保存）。
        currentRole.value = data;
        aclLoading.value = true;
        try {
          modalApi.lock();
          const [treeRes, aclRes] = await Promise.all([
            getRoleAclsApi(data.id!),
            getAclsByReleaseIdApi(data.id!),
          ]);
          const tree = ((treeRes as any) || []) as any[];
          aclTreeData.value = tree;
          assignLevels(tree);
          snapshotInitialStates(tree);
          const aclList = ((aclRes as any) || []) as any[];
          const map = new Map<string, any>();
          for (const item of aclList) {
            if (item.moduleId) map.set(item.moduleId, item);
          }
          existingAclMap.value = map;
        } catch {
          aclTreeData.value = [];
          ElMessage.error('获取菜单权限失败');
        } finally {
          aclLoading.value = false;
          modalApi.unlock();
        }
      }
    }
  },
});
function assignLevels(nodes: any[], level = 0) {
  for (const node of nodes) {
    (node as any)._level = level;
    if (node.children?.length) {
      assignLevels(node.children, level + 1);
    }
  }
}
function traversePvalues(nodes: any[]): any[] {
  const result: any[] = [];
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
const currentRole = ref<null | any>(null);
// BUG-137：打开弹窗时对每个模块的初始授权位做快照，「确定」时据此算出**有改动的模块**再批量提交
const initialStateMap = ref(new Map<string, number>());

function snapshotInitialStates(nodes: any[]) {
  const map = new Map<string, number>();
  const walk = (ns: any[]) => {
    for (const n of ns || []) {
      if (n.pvalues?.length) map.set(n.id, Number(calcAclState(n.pvalues)));
      if (n.children?.length) walk(n.children);
    }
  };
  walk(nodes);
  initialStateMap.value = map;
}


function handleHeaderSelectAll(val: string | number | boolean) {
  const checked = !!val;
  function traverse(nodes: any[]) {
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
  // BUG-137：表头全选只改本地状态，提交统一在「确定」时批量进行（不再即时 saveAllAclApi）
}
function calcAclState(pvalues: any[]): string {
  let state = 0;
  for (const pv of pvalues) {
    if (pv.enabled) {
      state |= 1 << pv.position;
    }
  }
  return String(state);
}


function updateNodeState(data: any) {
  data.state = calcAclState(data.pvalues);
}

const treeProps = {
  children: 'children',
  label: 'name',
};

function handlePvalueChange(data: any) {
  // BUG-137：只更新本地位掩码；提交统一在「确定」时批量进行（不再即时 saveModuleAclApi）
  updateNodeState(data);
}
</script>

<template>
  <Modal :title="getTitle" class="w-200 ">
    <div v-loading="aclLoading" class="acl-body max-h-[600px] overflow-y-auto">
      <div v-if="aclTreeData.length === 0 && !aclLoading" class="py-8 text-center text-gray-400">
        暂无菜单数据
      </div>
      <div v-else>
        <div
            class="grid grid-cols-[220px_100px_1fr] items-center px-2 py-2 pl-6 mb-1 text-xs font-semibold text-gray-500 border-b border-gray-200">
          <div>菜单名称</div>
          <div class="text-left">
            <ElCheckbox
                :model-value="headerAllSelected"
                :indeterminate="headerIndeterminate"
                @change="handleHeaderSelectAll"
            >
              {{ headerAllSelected ? '取消全选' : '全选' }}
            </ElCheckbox>
          </div>
          <div>操作权限</div>
        </div>
        <ElTree
            ref="treeRef"
            :data="aclTreeData"
            :props="treeProps"
            node-key="id"
            :default-expand-all="true"
        >
          <template #default="{ data } = { data: null }">
            <div v-if="data" class="grid grid-cols-[220px_100px_1fr] gap-2 items-center w-full min-w-0">
              <div class="flex gap-2 items-center min-w-0"
                   :style="{ paddingLeft: ((data as any)._level || 0) * 24 + 'px' }">
                <ElIcon class="flex shrink-0 items-center text-base">
                  <IconifyIcon
                      :icon="data.image || (data.type === '0' ? 'lucide:folder' : 'lucide:file-text')"
                  />
                </ElIcon>
                <span class="truncate text-sm font-medium">{{ data.name }}</span>
              </div>
              <div v-if="data.pvalues?.length" class="flex items-center text-xs">
                <ElCheckbox
                    :model-value="data.pvalues.every((pv) => pv.enabled)"
                    :indeterminate="
                      data.pvalues.some((pv) => pv.enabled) &&
                      !data.pvalues.every((pv) => pv.enabled)
                    "
                    @click.stop
                    @change="(val: string | number | boolean) => {
                      // BUG-137：行内全选只改本地状态，提交统一在「确定」时批量进行
                      data.pvalues.forEach((pv) => { pv.enabled = !!val; });
                      updateNodeState(data);
                    }"
                >
                  全选
                </ElCheckbox>
              </div>
              <div v-else class="flex items-center text-xs"/>
              <div v-if="data.pvalues?.length" class="flex flex-wrap gap-1 gap-x-3 items-center">
                <ElCheckbox
                    v-for="pv in data.pvalues"
                    :key="pv.pvalueId"
                    :model-value="pv.enabled"
                    @click.stop
                    @change="(val: string | number | boolean) => {
                      pv.enabled = !!val;
                      handlePvalueChange(data);
                    }"
                    class="text-xs"
                >
                  {{ pv.pvalueName || pv.name }}
                </ElCheckbox>
              </div>
            </div>
          </template>
        </ElTree>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
:deep(.el-tree-node__content) {
  height: auto;
  min-height: 32px;
}
</style>
