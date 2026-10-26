-- V2.1.0_04 品牌外观配置（brand-configuration R-01~R-05 / T-01）
-- 目标库：PostgreSQL（phoenix）
-- 幂等：CREATE TABLE IF NOT EXISTS + ON CONFLICT DO NOTHING；可重跑
-- 回滚：见 rollback/V2.1.0_04__brand_config_rollback.sql
--
-- 语义注记：
--   · 单行表：id 固定为 1（CHECK 约束），一套部署一套品牌（多租户多品牌为 Non-goal）
--   · 默认值 = 升级前的实际表现（R-04）：
--       system_name   'Phoenix Agent'   （登录页品牌区现状文案）
--       logo_url      '/imgs/logo.png'  （现状静态图）
--       favicon_url   '/favicon.ico'    （现状 favicon）
--       primary_color '#006BE6'         （等价于现状 vben 默认 hsl(212 100% 45%)）
--   · 已知偏差（已在升级说明标注）：浏览器标签标题由现状 'Phoenix Admin' 变为 '<system_name> - 页面名'，
--     这是 R-01「三处统一」的必然结果，非缺陷

CREATE TABLE IF NOT EXISTS tbl_platform_brand_config (
    id            smallint     PRIMARY KEY,
    system_name   varchar(64)  NOT NULL,
    logo_url      varchar(512),
    favicon_url   varchar(512),
    primary_color varchar(16)  NOT NULL,
    updated_by    varchar(64),
    create_time   timestamp    NOT NULL DEFAULT now(),
    update_time   timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT ck_brand_config_single_row CHECK (id = 1),
    CONSTRAINT ck_brand_config_color CHECK (primary_color ~ '^#[0-9a-fA-F]{6}$')
);

COMMENT ON TABLE tbl_platform_brand_config IS '品牌外观配置（单行，id=1）：系统名称/Logo/favicon/主题主色';
COMMENT ON COLUMN tbl_platform_brand_config.system_name IS '系统名称（登录页品牌区、侧边栏标题、浏览器标签统一使用）';
COMMENT ON COLUMN tbl_platform_brand_config.primary_color IS '主题主色 #RRGGBB；色阶由前端派生';

INSERT INTO tbl_platform_brand_config (id, system_name, logo_url, favicon_url, primary_color, updated_by)
VALUES (1, 'Phoenix Agent', '/imgs/logo.png', '/favicon.ico', '#006BE6', 'system')
ON CONFLICT (id) DO NOTHING;

-- 菜单：系统管理 → 外观设置（幂等；超管可见，ACL 由既有角色-模块授权体系控制）
INSERT INTO tbl_privilege_module
    (id, name, url, sn, state, component, system_id, status, image, order_no, is_show,
     create_time, create_by, update_time, del_flag, pid, category_id, type)
VALUES
    ('b7a1c2d3e4f5061728394a5b6c7d8e9f', '外观设置', '/system-management/brand-settings',
     'PlatformBrandSettings', '32', '#/views/system-management/brand-settings/index.vue',
     110, 1, 'lucide:palette', 9, 1, now(), 'system', now(), 0,
     '02b733aa08774219a23c2f21f1b3f6b5', 111, '1')
ON CONFLICT (id) DO NOTHING;
