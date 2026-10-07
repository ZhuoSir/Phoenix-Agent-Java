<script lang="ts" setup>
import { computed, nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm } from '#/adapter/form';
import {
  createAccountInfoApi,
  updateAccountInfoApi,
} from '#/api';

import { useSchema } from './data';

defineOptions({ name: 'AccountInfoForm' });

const emit = defineEmits(['success']);
const formData = ref<any>();

const getTitle = computed(() => {
  return formData.value?.id ? '编辑账号' : '新增账号';
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
        await (formData.value?.id
          ? updateAccountInfoApi(values)
          : createAccountInfoApi(values));
        modalApi.close();
        emit('success');
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
        nextTick(() => {
          formApi.setValues(formData.value);
          if (formData.value?.id) {
            formApi.setValues({ password: '', confirmPassword: '' });
          }
        });
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
