<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElButton, ElInput, ElMessage } from 'element-plus';
import {
  getBrandConfigApi,
  updateBrandConfigApi,
  uploadBrandAssetApi,
  type BrandConfig,
} from '#/api/core/brand-config';
import { brand, applyBrand } from '#/stores/brand';

const form = reactive<BrandConfig>({
  systemName: brand.systemName,
  logoUrl: brand.logoUrl,
  faviconUrl: brand.faviconUrl,
  primaryColor: brand.primaryColor,
});
const saving = ref(false);
const uploading = ref('');

onMounted(async () => {
  try {
    const cfg = await getBrandConfigApi();
    Object.assign(form, cfg);
  } catch (e: any) {
    ElMessage.error(`读取品牌配置失败：${e?.message ?? e}`);
  }
});

async function pickFile(kind: 'favicon' | 'logo', file?: File) {
  if (!file) return;
  uploading.value = kind;
  try {
    const url = await uploadBrandAssetApi(file);
    if (kind === 'logo') form.logoUrl = url;
    else form.faviconUrl = url;
    ElMessage.success('上传成功');
  } catch (e: any) {
    ElMessage.error(`上传失败：${e?.message ?? e}`);
  } finally {
    uploading.value = '';
  }
}

function onFile(kind: 'favicon' | 'logo', ev: Event) {
  const input = ev.target as HTMLInputElement;
  pickFile(kind, input.files?.[0] ?? undefined);
}

async function save() {
  saving.value = true;
  try {
    const saved = await updateBrandConfigApi({ ...form });
    Object.assign(form, saved);
    applyBrand(saved); // 即时预览（R-05：刷新后同样生效）
    ElMessage.success('保存成功（刷新页面后对所有人生效）');
  } catch (e: any) {
    ElMessage.error(`保存失败：${e?.message ?? e}`);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="p-5">
    <h2 class="mb-1 text-lg font-semibold">外观设置</h2>
    <p class="mb-5 text-sm text-gray-500">
      修改系统名称、Logo、favicon 与主题主色；保存后**刷新页面**即生效（无需重新构建、无需重启）。
    </p>

    <div class="grid max-w-3xl grid-cols-1 gap-5 md:grid-cols-2">
      <div class="md:col-span-2">
        <label class="mb-1 block text-sm font-medium">系统名称（1~40 字）</label>
        <ElInput v-model="form.systemName" maxlength="40" show-word-limit placeholder="例如：××标准院智能平台" />
      </div>

      <div>
        <label class="mb-1 block text-sm font-medium">主题主色</label>
        <div class="flex items-center gap-3">
          <input v-model="form.primaryColor" type="color" class="h-9 w-14 cursor-pointer rounded border" />
          <ElInput v-model="form.primaryColor" placeholder="#006BE6" />
        </div>
        <p class="mt-1 text-xs text-gray-500">hover/active 等色阶由系统自动派生</p>
      </div>

      <div>
        <label class="mb-1 block text-sm font-medium">Logo（png/jpg/webp，≤2MB）</label>
        <div class="flex items-center gap-3">
          <img :src="form.logoUrl" alt="logo" class="h-10 w-10 rounded border object-contain" />
          <input type="file" accept=".png,.jpg,.jpeg,.webp" @change="onFile('logo', $event)" />
        </div>
        <p v-if="uploading === 'logo'" class="mt-1 text-xs text-blue-500">上传中…</p>
      </div>

      <div>
        <label class="mb-1 block text-sm font-medium">favicon（png/ico，≤2MB）</label>
        <div class="flex items-center gap-3">
          <img :src="form.faviconUrl" alt="favicon" class="h-6 w-6 rounded border object-contain" />
          <input type="file" accept=".png,.ico" @change="onFile('favicon', $event)" />
        </div>
        <p v-if="uploading === 'favicon'" class="mt-1 text-xs text-blue-500">上传中…</p>
      </div>

      <div class="md:col-span-2">
        <ElButton type="primary" :loading="saving" @click="save">保存</ElButton>
      </div>
    </div>
  </div>
</template>
