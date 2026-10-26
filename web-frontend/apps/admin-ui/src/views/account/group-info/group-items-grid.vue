<script setup lang="ts">
import { watch } from 'vue';
import { useVbenVxeGrid } from '#/adapter/vxe-table';

defineOptions({ name: 'GroupItemsGrid' });

const props = defineProps<{
  data: any[];
  field: string;
  title?: string;
}>();

// 与列表页同一网格组件 ⇒ 样式统一（表头/行高/边框/空态）
const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [{ field: props.field, title: props.title || '名称' }],
    loading: false,
    maxHeight: 260,
    border: false,
    stripe: false,
    emptyText: '暂无数据',
    pagerConfig: { enabled: false },
    data: [] as any[],
  },
});

watch(
  () => props.data,
  (d) => gridApi.setGridOptions({ data: d || [], loading: false }),
  { immediate: true, deep: true },
);
</script>

<template>
  <Grid />
</template>
