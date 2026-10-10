<script lang="ts" setup>
/**
 * 用户表单（R-03 三维度化：用户 · 角色 · 组）。
 *
 * - 组织字段（公司/部门/人员选择器）已随 R-02 下线（T-11 摘除）
 * - 新增：角色/组多选，随创建/更新一次提交（后端 PrivilegeUserDTO.roleIds/groupIds 同事务落库）
 * - 角色不选时后端自动补默认角色（BUG-123 已修：大小写不敏感匹配 sn=common）
 */
import { ElMessage, ElOption, ElSelect } from 'element-plus';
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  createUserApi,
  getAccountGroupInfoByAccountApi,
  getGroupInfoPageApi,
  getRolePageApi,
  getUserRolesByUserIdApi,
  updateUserApi,
} from '#/api';

import { useVbenForm } from '#/adapter/form';

import { useSchema } from './data';

const emit = defineEmits(['success']);
const formData = ref<any>();
const isEdit = computed(() => !!formData.value?.id);

const roleOptions = ref<any[]>([]);
const groupOptions = ref<any[]>([]);
const selectedRoleIds = ref<string[]>([]);
const selectedGroupIds = ref<string[]>([]);

const getTitle = computed(() => {
  return isEdit.value ? '编辑账号' : '新增账号';
});

const [Form, formApi] = useVbenForm({
  layout: 'horizontal',
  schema: useSchema(),
  showDefaultActions: false,
  wrapperClass: 'grid-cols-2',
});

function onRoleChange(val: any) {
  selectedRoleIds.value = (val ?? []) as string[];
  formApi.setValues({ roleIds: selectedRoleIds.value });
}

function onGroupChange(val: any) {
  selectedGroupIds.value = (val ?? []) as string[];
  formApi.setValues({ groupIds: selectedGroupIds.value });
}

/** 复用「分配角色/分配组」弹窗所用的同一批接口，保证口径一致 */
async function loadOptions() {
  if (roleOptions.value.length === 0) {
    try {
      const res: any = await getRolePageApi({ pageNumber: 1, pageSize: 999 });
      roleOptions.value = res?.records ?? [];
    } catch {
      roleOptions.value = [];
    }
  }
  if (groupOptions.value.length === 0) {
    try {
      const res: any = await getGroupInfoPageApi(1, 999);
      groupOptions.value = res?.data?.records ?? [];
    } catch {
      groupOptions.value = [];
    }
  }
}

const [Modal, modalApi] = useVbenModal({
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (valid) {
      modalApi.lock();
      const values = await formApi.getValues();
      delete values.confirmPassword;
      if (!values.password) {
        delete values.password;
      }
      // 三维度显式带上（避免 slot 值未被 formApi 采集时漏传）
      values.roleIds = selectedRoleIds.value;
      values.groupIds = selectedGroupIds.value;
      try {
        const { success, msg } = await (formData.value?.id
          ? updateUserApi(values)
          : createUserApi(values));
        if (success) {
          modalApi.close();
          emit('success');
          ElMessage.success(isEdit.value ? '更新成功' : '新增成功');
        } else {
          ElMessage.error(msg || '操作失败');
        }
      } finally {
        modalApi.lock(false);
      }
    }
  },
  async onOpenChange(isOpen) {
    if (isOpen) {
      const modalData = modalApi.getData<any>();
      if (modalData) {
        formData.value = modalData;
        void loadOptions();
        selectedRoleIds.value = [];
        selectedGroupIds.value = [];
        formApi.setValues(modalData);
        if (modalData.id) {
          // 编辑：回显既有角色与组
          try {
            const [rolesRes, groupsRes] = await Promise.all([
              getUserRolesByUserIdApi(modalData.id),
              getAccountGroupInfoByAccountApi(modalData.id),
            ]);
            const roles: any = rolesRes;
            const groups: any = groupsRes;
            selectedRoleIds.value = (roles?.data ?? roles ?? [])
              .map((r: any) => r.roleId)
              .filter(Boolean);
            selectedGroupIds.value = (groups?.data ?? groups ?? [])
              .map((g: any) => g.groupId)
              .filter(Boolean);
            formApi.setValues({
              roleIds: selectedRoleIds.value,
              groupIds: selectedGroupIds.value,
            });
          } catch {
            // 回显失败不阻断编辑（保存时以用户当前选择为准）
          }
        }
      }
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-200">
    <Form class="mx-4">
      <template #roleIds>
        <ElSelect
          v-model="selectedRoleIds"
          multiple
          collapse-tags
          collapse-tags-tooltip
          filterable
          placeholder="不选则由系统自动补默认角色"
          class="w-full"
          @change="onRoleChange"
        >
          <ElOption
            v-for="r in roleOptions"
            :key="r.id"
            :label="`${r.name}${r.sn ? `（${r.sn}）` : ''}`"
            :value="r.id"
          />
        </ElSelect>
      </template>
      <template #groupIds>
        <ElSelect
          v-model="selectedGroupIds"
          multiple
          collapse-tags
          collapse-tags-tooltip
          filterable
          placeholder="可留空"
          class="w-full"
          @change="onGroupChange"
        >
          <ElOption
            v-for="g in groupOptions"
            :key="g.id"
            :label="g.name"
            :value="g.id"
          />
        </ElSelect>
      </template>
    </Form>
  </Modal>
</template>
