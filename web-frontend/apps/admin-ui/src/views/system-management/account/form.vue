<script lang="ts" setup>
import { ElMessage } from 'element-plus';
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  createUserApi,
  updateUserApi,
} from '#/api';

import { useVbenForm } from '#/adapter/form';

import { useSchema } from './data';

const emit = defineEmits(['success']);
const formData = ref<any>();
const isEdit = computed(() => !!formData.value?.id);

const getTitle = computed(() => {
  return isEdit.value ? '编辑账号' : '新增账号';
});

const [Form, formApi] = useVbenForm({
  layout: 'horizontal',
  schema: useSchema(),
  showDefaultActions: false,
  wrapperClass: 'grid-cols-2',
});

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
      try {
        const {success, msg} = await (formData.value?.id
          ? updateUserApi(values)
          : createUserApi(values));
        if(success) {
          modalApi.close();
          emit('success');
          ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
        } else {
          ElMessage.error(msg || '操作失败')
        }

      } finally {
        modalApi.lock(false);
      }
    }
  },
  onOpenChange(isOpen) {
    if (isOpen) {
      const modalData = modalApi.getData<any>();
      if (modalData) {
        formData.value = modalData;
        formApi.setValues(formData.value);
      }
    }
  },
});
</script>

<template>
  <Modal :title="getTitle" class="w-200">
    <Form class="mx-4">
    </Form>
  </Modal>
</template>
