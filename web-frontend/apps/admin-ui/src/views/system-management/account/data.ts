import { z } from '#/adapter/form';
import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridColumns } from '#/adapter/vxe-table';

export function useSearchFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'keyword',
      component: 'Input',
      label: '搜索',
      labelWidth: 40,
      componentProps: {
        // R-15（v2.4.0）：关键字检索覆盖 用户名 / 姓名 / 手机号（后端 pageByQuery 已支持三者）
        placeholder: '用户名 / 姓名 / 手机号',
        allowClear: true,
      },
    },
  ];
}

export function useColumns(): VxeTableGridColumns {
  return [
    // R-15（v2.4.0）：勾选列，用于批量启用/禁用
    { type: 'checkbox', width: 44, fixed: 'left' },
    { field: 'username', title: '用户名', minWidth: 120 },
    { field: 'realName', title: '真实姓名', width: 120 },
    { field: 'mobile', title: '手机号', width: 100 },
    {
      field: 'status',
      title: '状态',
      width: 70,
      slots: { default: 'statusSlot' },
    },
    {
      field: 'roles',
      title: '角色',
      width: 180,
      slots: { default: 'rolesSlot' },
    },
    { field: 'createTime', title: '创建时间', width: 180 },
    {
      align: 'center',
      field: 'operation',
      slots: { default: 'action' },
      fixed: 'right',
      title: '操作',
      width: 360,
    },
  ];
}

export function useSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'id',
      label: 'id',
      dependencies: {
        triggerFields: [''],
        show: false,
      },
    },
    {
      // R-03 三维度化：角色多选（插槽渲染 ElSelect multiple；不选=后端补默认角色）
      fieldName: 'roleIds',
      label: '角色',
      component: 'Input',
      formItemClass: 'col-span-2',
    },
    {
      // R-03 三维度化：组多选（插槽渲染 ElSelect multiple）
      fieldName: 'groupIds',
      label: '组',
      component: 'Input',
      formItemClass: 'col-span-2',
    },
    {
      component: 'Input',
      fieldName: 'username',
      label: '用户名',
      rules: z.string().min(1, '请输入用户名'),
    },
    {
      component: 'Input',
      fieldName: 'realName',
      label: '真实姓名',
      rules: z.string().min(1, '请输入真实姓名'),
    },
    {
      component: 'Input',
      fieldName: 'mobile',
      label: '手机号',
      rules: z.string().min(1, '请输入手机号'),
    },
  ];
}
