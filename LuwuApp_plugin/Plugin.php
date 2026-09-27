<?php
/**
 * 陆伍App控制台
 *
 * @package LuwuApp
 * @author 陆伍博客
 * @version 1.8
 * @link https://www.65gw.com
 */

namespace TypechoPlugin\LuwuApp;

use Typecho\Db;
use Typecho\Plugin\PluginInterface;
use Typecho\Widget\Helper\Form;
use Typecho\Widget\Helper\Form\Element\Select;
use Typecho\Widget\Helper\Form\Element\Text;
use Typecho\Widget\Helper\Form\Element\Textarea;

if (!defined('__TYPECHO_ROOT_DIR__')) exit;

class Plugin implements PluginInterface
{
    /** 默认配置：与 App 内置兜底一致（游侠云推广渠道不可改） */
    public static function defaults()
    {
        return array(
            'luwuapp_announce_enabled' => '0',
            'luwuapp_announce_title' => '网站公告',
            'luwuapp_announce_content' => '',
            'luwuapp_announce_link' => '',
            'luwuapp_ad_home_text' => '免实名免备案 高性能虚拟主机',
            'luwuapp_ad_home_link' => 'https://cloud.uxw.net/aff/NUOZHCHB',
            'luwuapp_ad_home_type' => 'text',
            'luwuapp_ad_home_img' => '',
            'luwuapp_ad_home_video' => '',
            'luwuapp_ad_feed_text' => '极速域名注册 好记又便宜',
            'luwuapp_ad_feed_link' => 'https://name.uxw.net',
            'luwuapp_ad_feed_type' => 'text',
            'luwuapp_ad_feed_img' => '',
            'luwuapp_ad_feed_video' => '',
            'luwuapp_ad_feed_every' => '5',
            'luwuapp_ad_category_text' => '免实名免备案 高性能虚拟主机',
            'luwuapp_ad_category_link' => 'https://cloud.uxw.net/aff/NUOZHCHB',
            'luwuapp_ad_category_type' => 'text',
            'luwuapp_ad_category_img' => '',
            'luwuapp_ad_category_video' => '',
            'luwuapp_ad_article_text' => '免实名免备案 稳定高速虚拟主机',
            'luwuapp_ad_article_link' => 'https://cloud.uxw.net/aff/NUOZHCHB',
            'luwuapp_ad_article_type' => 'text',
            'luwuapp_ad_article_img' => '',
            'luwuapp_ad_article_video' => '',
            'luwuapp_update_enabled' => '0',
            'luwuapp_update_version_name' => '',
            'luwuapp_update_version_code' => '',
            'luwuapp_update_url' => '',
            'luwuapp_update_changelog' => '',
            'luwuapp_update_force' => '0',
            'luwuapp_theme_default' => 'system',
            'luwuapp_splash_enable' => '0',
            'luwuapp_splash_image' => '',
            'luwuapp_splash_target' => '',
            'luwuapp_splash_duration' => '3',
            'luwuapp_splash_skip_text' => '跳过',
            'luwuapp_splash_once' => '0',
            'luwuapp_report_notice' => '',
            'luwuapp_changelog' => "v5.3|2026-09-11|修复更换头像闪退；首页分类卡片支持后台自定义；评论新增回复/表情/图片；商务合作内容后台可配置；顶部按钮改透明背景；详情页正文下方新增评论区预览
v5.2|2026-09-10|新增主题色设置（全局顶栏/底部导航跟随）；新增意见反馈功能；头像支持 App 内上传更换；新增表情/图片评论；商务合作改原生页面；网盘下载样式优化
v5.1|2026-09-10|优化广告图片显示；插件设置页移动端适配；首页新增分类卡片控件；支持多广告与广告总开关
v5.0|2026-09-09|搜索新增标题/内容切换；列表新增排序；发帖新增短代码工具栏；底部导航改五宫格；点赞支持取消",
        );
    }

    /**
     * 激活插件：注册 /action/luwu-app 路由，并写入 Typecho 1.2 标准插件配置行 plugin:LuwuApp
     */
    public static function activate()
    {
        \Utils\Helper::addRoute('luwu_app_action', '/action/luwu-app', 'TypechoPlugin\\LuwuApp\\Action', 'action');
        self::ensureConfigRow();
        self::ensureTokenTable();
        self::ensureNotifyTable();
        return _t('插件已启用：App 数据接口地址为 /action/luwu-app，请到「设置」中配置公告、广告与更新信息。');
    }

    /** 登录 token 表：登录态安全存储（避免每次请求明文传输密码） */
    public static function ensureTokenTable()
    {
        $db = Db::get();
        $prefix = $db->getPrefix();
        $db->query("CREATE TABLE IF NOT EXISTS {$prefix}luwu_tokens (
            uid INT NOT NULL,
            token CHAR(64) NOT NULL,
            created INT NOT NULL,
            PRIMARY KEY (uid),
            KEY idx_token (token)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    /** 互动通知表：被赞/新粉丝（评论通知走实时查询，无需落表） */
    public static function ensureNotifyTable()
    {
        $db = Db::get();
        $prefix = $db->getPrefix();
        $db->query("CREATE TABLE IF NOT EXISTS {$prefix}luwu_notify (
            id INT AUTO_INCREMENT PRIMARY KEY,
            uid INT NOT NULL,
            type VARCHAR(20) NOT NULL,
            from_uid INT NOT NULL DEFAULT 0,
            from_name VARCHAR(50) NOT NULL DEFAULT '',
            cid INT NOT NULL DEFAULT 0,
            content VARCHAR(255) NOT NULL DEFAULT '',
            created INT NOT NULL,
            KEY idx_uid_created (uid, created)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    /** 确保 options 表存在 plugin:LuwuApp 配置行（Typecho 1.2 后台「设置」读取的就是这一行） */
    public static function ensureConfigRow()
    {
        $db = Db::get();
        $row = $db->fetchRow($db->select()->from('table.options')->where('name = ?', 'plugin:LuwuApp'));
        if (empty($row)) {
            $db->query($db->insert('table.options')->rows(array(
                'name'  => 'plugin:LuwuApp',
                'value' => serialize(self::defaults()),
                'user'  => 0,
            )));
        }
    }

    /**
     * 禁用插件：移除路由
     */
    public static function deactivate()
    {
        \Utils\Helper::removeRoute('luwu_app_action');
    }

    /**
     * 后台配置面板：公告 / 广告（文字·图片·视频） / App更新
     * 全部存于 Typecho options 表 plugin:LuwuApp 行，不占用额外资源
     */
    public static function config(Form $form)
    {
        /* ============ 待处理举报（从 luwu_report 表查询） ============ */
        $pluginVersion = '1.6';
        $reports = array();
        $reportCount = 0;
        try {
            $db = Db::get();
            $prefix = $db->getPrefix();
            $reports = $db->fetchAll(
                $db->select('id', 'uid', 'type', 'target_id', 'reason', 'status', 'ip', 'created')
                    ->from('table.luwu_report')
                    ->where('status = ?', 'pending')
                    ->order('created', Db::SORT_DESC)
                    ->limit(30)
            );
        } catch (\Exception $e) {
            $reports = array();
        }
        $reportCount = count($reports);
        $reportsHtml = '<div style="font-size:13px;color:#94A3B8;padding:8px 0;">暂无待处理举报 🎉</div>';
        if ($reportCount > 0) {
            $typeNames = array('post' => '文章', 'comment' => '评论', 'user' => '用户');
            $rowsHtml = '';
            foreach ($reports as $r) {
                $tn = isset($typeNames[$r['type']]) ? $typeNames[$r['type']] : htmlspecialchars($r['type']);
                $rowsHtml .= '<tr>'
                    . '<td data-label="类型" style="padding:8px;border-bottom:1px solid #F1F5F9;">' . $tn . '</td>'
                    . '<td data-label="目标ID" style="padding:8px;border-bottom:1px solid #F1F5F9;">' . (int)$r['target_id'] . '</td>'
                    . '<td data-label="理由" style="padding:8px;border-bottom:1px solid #F1F5F9;max-width:220px;word-break:break-all;">' . htmlspecialchars($r['reason']) . '</td>'
                    . '<td data-label="时间" style="padding:8px;border-bottom:1px solid #F1F5F9;">' . date('m-d H:i', (int)$r['created']) . '</td>'
                    . '<td data-label="IP" style="padding:8px;border-bottom:1px solid #F1F5F9;">' . htmlspecialchars($r['ip']) . '</td>'
                    . '</tr>';
            }
            $reportsHtml = '<div style="overflow-x:auto;"><table style="width:100%;border-collapse:collapse;font-size:12.5px;"><thead><tr style="color:#64748B;text-align:left;">'
                . '<th style="padding:8px;border-bottom:1px solid #E2E8F0;">类型</th><th style="padding:8px;border-bottom:1px solid #E2E8F0;">目标ID</th>'
                . '<th style="padding:8px;border-bottom:1px solid #E2E8F0;">理由</th><th style="padding:8px;border-bottom:1px solid #E2E8F0;">时间</th><th style="padding:8px;border-bottom:1px solid #E2E8F0;">IP</th></tr></thead>'
                . '<tbody>' . $rowsHtml . '</tbody></table></div>';
        }

        /* ============ 开屏广告数据统计（从 ad_log 表实时汇总） ============ */
        $adStats = array('show' => 0, 'click' => 0, 'skip' => 0);
        try {
            $db = Db::get();
            $prefix = $db->getPrefix();
            $rows = $db->fetchAll("SELECT event_type, COUNT(*) AS n FROM {$prefix}luwu_ad_log GROUP BY event_type");
            foreach ($rows as $r) {
                if (isset($adStats[$r['event_type']])) {
                    $adStats[$r['event_type']] = (int)$r['n'];
                }
            }
        } catch (\Exception $e) {
            // 表尚未创建时忽略
        }

        /* ============ 推送历史（从 luwu_push 表查询） ============ */
        $pushRows = array();
        try {
            $db = Db::get();
            $pushRows = $db->fetchAll(
                $db->select('id', 'title', 'content', 'target_type', 'target_id', 'target_url', 'created')
                    ->from('table.luwu_push')
                    ->order('id', Db::SORT_DESC)
                    ->limit(10)
            );
        } catch (\Exception $e) {
            $pushRows = array();
        }
        $pushHistoryHtml = '<div style="font-size:13px;color:#94A3B8;padding:8px 0;">暂无推送记录 · 在下方填写内容后点击「立即推送」</div>';
        if (!empty($pushRows)) {
            $targetNames = array('none' => '仅通知', 'post' => '文章', 'category' => '分类', 'url' => '链接');
            $rowsHtml = '';
            foreach ($pushRows as $r) {
                $tn = isset($targetNames[$r['target_type']]) ? $targetNames[$r['target_type']] : htmlspecialchars($r['target_type']);
                $rowsHtml .= '<tr>'
                    . '<td data-label="标题" style="padding:8px;border-bottom:1px solid #F1F5F9;">' . htmlspecialchars($r['title']) . '</td>'
                    . '<td data-label="内容" style="padding:8px;border-bottom:1px solid #F1F5F9;max-width:200px;word-break:break-all;color:#64748B;">' . htmlspecialchars($r['content']) . '</td>'
                    . '<td data-label="目标" style="padding:8px;border-bottom:1px solid #F1F5F9;">' . $tn . '</td>'
                    . '<td data-label="时间" style="padding:8px;border-bottom:1px solid #F1F5F9;">' . date('m-d H:i', (int)$r['created']) . '</td>'
                    . '</tr>';
            }
            $pushHistoryHtml = '<div style="overflow-x:auto;"><table style="width:100%;border-collapse:collapse;font-size:12.5px;"><thead><tr style="color:#64748B;text-align:left;">'
                . '<th style="padding:8px;border-bottom:1px solid #E2E8F0;">标题</th><th style="padding:8px;border-bottom:1px solid #E2E8F0;">内容</th>'
                . '<th style="padding:8px;border-bottom:1px solid #E2E8F0;">目标</th><th style="padding:8px;border-bottom:1px solid #E2E8F0;">时间</th></tr></thead>'
                . '<tbody>' . $rowsHtml . '</tbody></table></div>';
        }

        /* ============ 数据看板（实时聚合） ============ */
        $dashStats = array('posts' => 0, 'comments' => 0, 'users' => 0, 'todayPosts' => 0, 'todayComments' => 0, 'week' => array());
        try {
            $db = Db::get();
            $prefix = $db->getPrefix();
            $d = $db->fetchRow("SELECT COUNT(*) AS n FROM {$prefix}contents WHERE type='post'");
            $dashStats['posts'] = $d ? (int)$d['n'] : 0;
            $d = $db->fetchRow("SELECT COUNT(*) AS n FROM {$prefix}comments");
            $dashStats['comments'] = $d ? (int)$d['n'] : 0;
            $d = $db->fetchRow("SELECT COUNT(*) AS n FROM {$prefix}users");
            $dashStats['users'] = $d ? (int)$d['n'] : 0;
            $d = $db->fetchRow("SELECT COUNT(*) AS n FROM {$prefix}contents WHERE type='post' AND created >= UNIX_TIMESTAMP(CURDATE())");
            $dashStats['todayPosts'] = $d ? (int)$d['n'] : 0;
            $d = $db->fetchRow("SELECT COUNT(*) AS n FROM {$prefix}comments WHERE created >= UNIX_TIMESTAMP(CURDATE())");
            $dashStats['todayComments'] = $d ? (int)$d['n'] : 0;
            $week = $db->fetchAll("SELECT DATE(FROM_UNIXTIME(created)) AS d, COUNT(*) AS n FROM {$prefix}contents WHERE type='post' AND created >= UNIX_TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 6 DAY)) GROUP BY d ORDER BY d ASC");
            $dayMap = array();
            foreach ($week as $w) {
                $dayMap[$w['d']] = (int)$w['n'];
            }
            for ($i = 6; $i >= 0; $i--) {
                $key = date('Y-m-d', strtotime("-{$i} day"));
                $dashStats['week'][] = array('d' => $key, 'n' => isset($dayMap[$key]) ? $dayMap[$key] : 0);
            }
        } catch (\Exception $e) {
            // 查询失败时保持 0
        }
        $weekMax = 1;
        foreach ($dashStats['week'] as $w) { if ($w['n'] > $weekMax) $weekMax = $w['n']; }
        $weekHtml = '<div style="display:flex;align-items:flex-end;gap:8px;height:110px;padding:8px 4px 0;">';
        foreach ($dashStats['week'] as $w) {
            $h = $w['n'] > 0 ? max(8, (int)round($w['n'] / $weekMax * 88)) : 3;
            $barColor = $w['d'] === date('Y-m-d') ? 'linear-gradient(135deg,#0F766E,#14B8A6)' : '#DDE7E7';
            $weekHtml .= '<div style="flex:1;display:flex;flex-direction:column;align-items:center;justify-content:flex-end;gap:4px;height:100%;">'
                . '<div style="font-size:10.5px;color:#0F766E;font-weight:700;">' . $w['n'] . '</div>'
                . '<div style="width:100%;max-width:34px;height:' . $h . 'px;border-radius:6px 6px 0 0;background:' . $barColor . ';"></div>'
                . '<div style="font-size:9.5px;color:#94A3B8;white-space:nowrap;">' . substr($w['d'], 5) . '</div>'
                . '</div>';
        }
        $weekHtml .= '</div>';

        /* ============ 全新控制台面板（卡片化布局） ============ */
        echo <<<HTML
<style>
/* ===== 陆伍App 控制台 · 全面重设计 ===== */
.typecho-page-main { background: #F4F7F7; border-radius: 14px; padding: 22px; }
.typecho-page-main form > ul[role="form"] { display: block; background: transparent; box-shadow: none; padding: 0; }
.lw-hero { background: linear-gradient(135deg, #0F766E 0%, #14B8A6 60%, #2DD4BF 100%); border-radius: 16px; padding: 24px 26px; color: #fff; margin-bottom: 18px; box-shadow: 0 10px 26px rgba(13,148,136,.25); position: relative; overflow: hidden; }
.lw-hero::after { content: ""; position: absolute; right: -40px; top: -60px; width: 200px; height: 200px; background: rgba(255,255,255,.08); border-radius: 50%; }
.lw-hero h2 { margin: 0 0 6px; font-size: 22px; font-weight: 700; letter-spacing: .5px; }
.lw-hero p { margin: 4px 0; font-size: 12.5px; color: rgba(255,255,255,.85); }
.lw-badges { display: flex; gap: 8px; margin-top: 12px; flex-wrap: wrap; }
.lw-badge { display: inline-block; background: rgba(255,255,255,.16); border: 1px solid rgba(255,255,255,.25); padding: 3px 12px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.lw-card { background: #fff; border-radius: 14px; box-shadow: 0 2px 10px rgba(15,23,42,.05); margin-bottom: 14px; overflow: hidden; border: 1px solid #E8EFEF; }
.lw-card-head { display: flex; align-items: center; gap: 12px; padding: 14px 18px; background: #FAFDFD; border-bottom: 1px solid #EDF3F3; }
.lw-ico { width: 38px; height: 38px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 18px; flex: none; color: #fff; }
.lw-card-head b { display: block; font-size: 15px; color: #134E4A; }
.lw-card-head span { display: block; font-size: 11.5px; color: #94A3B8; margin-top: 2px; }
.lw-body { padding: 4px 18px 10px; }
.lw-body ul.typecho-option { background: transparent !important; box-shadow: none !important; border: none !important; border-bottom: 1px dashed #EEF2F2 !important; border-radius: 0 !important; padding: 14px 0 !important; margin: 0 !important; }
.lw-body ul.typecho-option:last-child { border-bottom: none !important; }
.lw-body .typecho-option-title { color: #1F2937 !important; font-size: 13.5px !important; font-weight: 600; width: 220px; }
.lw-body .typecho-option-detail .description { color: #94A3B8 !important; font-size: 11.5px !important; }
.lw-body input[type="text"], .lw-body input[type="number"], .lw-body textarea, .lw-body select {
  border: 1px solid #DCE7E7 !important; border-radius: 8px !important; padding: 8px 12px !important; font-size: 13px !important; background: #FBFDFD !important; transition: all .18s; width: 88% !important; max-width: 640px;
}
.lw-body input:focus, .lw-body textarea:focus, .lw-body select:focus { border-color: #0D9488 !important; box-shadow: 0 0 0 3px rgba(13,148,136,.12); background: #fff !important; outline: none; }
.lw-body select { width: 260px !important; }
.lw-save { background: #fff; border-radius: 14px; padding: 16px 20px; text-align: center; border: 1px solid #E8EFEF; box-shadow: 0 2px 10px rgba(15,23,42,.05); }
.lw-save .btn.primary { background: linear-gradient(135deg, #0F766E, #14B8A6) !important; border: none !important; padding: 11px 42px !important; border-radius: 999px !important; font-size: 14px; font-weight: 700; box-shadow: 0 6px 16px rgba(13,148,136,.3); }
.lw-save .btn.primary:hover { transform: translateY(-1px); box-shadow: 0 8px 20px rgba(13,148,136,.4); }
#lw-panel .typecho-option-tabs { display: none; }
/* ===== 移动端适配：表单与文字完整显示 ===== */
@media (max-width: 768px) {
  .typecho-page-main { padding: 12px; border-radius: 10px; }
  .lw-hero { padding: 18px 16px; border-radius: 12px; }
  .lw-hero h2 { font-size: 18px; }
  .lw-card-head { padding: 12px 14px; flex-wrap: wrap; }
  .lw-card-head span { font-size: 11px; line-height: 1.5; }
  .lw-body { padding: 4px 12px 8px; }
  .lw-body ul.typecho-option { padding: 12px 0 !important; display: block !important; }
  .lw-body .typecho-option-title { width: 100% !important; display: block !important; margin-bottom: 6px !important; font-size: 13px !important; line-height: 1.5 !important; white-space: normal !important; }
  .lw-body input[type="text"], .lw-body input[type="number"], .lw-body textarea, .lw-body select {
    width: 100% !important; max-width: none !important; font-size: 14px !important; box-sizing: border-box !important; min-height: 42px !important;
  }
  .lw-body .typecho-option-detail .description { font-size: 11px !important; line-height: 1.6 !important; white-space: normal !important; }
  .lw-save .btn.primary { width: 100% !important; padding: 13px 0 !important; }
  .lw-badge { font-size: 11px; }
}
/* ===== 分类导航 Tab ===== */
.lw-nav { display: flex; flex-wrap: wrap; gap: 8px; background: #fff; border-radius: 14px; padding: 10px 14px; margin-bottom: 14px; border: 1px solid #E8EFEF; box-shadow: 0 2px 10px rgba(15,23,42,.05); position: sticky; top: 8px; z-index: 50; }
.lw-nav a { display: inline-block; padding: 6px 14px; border-radius: 999px; font-size: 12.5px; font-weight: 600; color: #475569; background: #F1F5F5; cursor: pointer; text-decoration: none; transition: all .18s; border: 1px solid transparent; }
.lw-nav a:hover { color: #0D9488; }
.lw-nav a.on { background: linear-gradient(135deg, #0F766E, #14B8A6); color: #fff; box-shadow: 0 4px 10px rgba(13,148,136,.3); }
@media (max-width: 768px) {
  .lw-nav { padding: 8px 10px; gap: 6px; border-radius: 12px; top: 4px; }
  .lw-nav a { padding: 5px 11px; font-size: 12px; }
}
/* ===== 三端自适应增强（平板 / 手机） ===== */
/* 平板（769-1023px）：表单收窄、导航紧凑 */
@media (min-width: 769px) and (max-width: 1023px) {
  .typecho-page-main { padding: 16px; border-radius: 12px; }
  .lw-hero { padding: 20px 18px; }
  .lw-body input[type="text"], .lw-body input[type="number"], .lw-body textarea { width: 100% !important; max-width: 560px; }
  .lw-nav a { padding: 5px 11px; font-size: 12px; }
  .lw-card-head { padding: 12px 14px; }
}
/* 手机（≤768px）：表格卡片化 + 导航横滑 + hero 精简 + 双栏单栏 */
@media (max-width: 768px) {
  /* 导航：单行横向滚动（吸顶不变） */
  .lw-nav { flex-wrap: nowrap; overflow-x: auto; -webkit-overflow-scrolling: touch; scrollbar-width: none; }
  .lw-nav::-webkit-scrollbar { display: none; }
  .lw-nav a { flex: none; }
  /* hero：精简间距，长接口地址折行不溢出 */
  .lw-hero { padding: 16px 14px; }
  .lw-hero p { font-size: 12px; }
  .lw-hero p[style*="monospace"] { font-size: 11px !important; white-space: normal; word-break: break-all; line-height: 1.5; }
  .lw-badges { gap: 6px; margin-top: 10px; }
  .lw-badge { padding: 2px 9px; font-size: 10.5px; }
  /* 表格 → 卡片列表（td 带 data-label 显示字段名） */
  #lw-panel .lw-body table { display: block; }
  #lw-panel .lw-body thead { display: none; }
  #lw-panel .lw-body tbody { display: block; }
  #lw-panel .lw-body tr { display: block; background: #FBFDFD; border: 1px solid #EDF3F3; border-radius: 10px; padding: 6px 12px; margin-bottom: 8px; }
  #lw-panel .lw-body td { display: flex; justify-content: space-between; gap: 10px; padding: 7px 0 !important; border-bottom: none !important; font-size: 12.5px !important; max-width: none !important; }
  #lw-panel .lw-body td::before { content: attr(data-label); color: #94A3B8; font-weight: 600; flex: none; width: 56px; }
  #lw-panel .lw-body td:last-child { border-bottom: none !important; }
  /* 表单与操作区全宽 */
  .lw-body .typecho-option-title { width: 100% !important; display: block !important; margin-bottom: 6px !important; font-size: 13px !important; line-height: 1.5 !important; white-space: normal !important; }
  .lw-body input[type="text"], .lw-body input[type="number"], .lw-body textarea, .lw-body select {
    width: 100% !important; max-width: none !important; font-size: 14px !important; box-sizing: border-box !important; min-height: 42px !important;
  }
  /* 推送 / 付费双栏改单栏 */
  #lw-panel .lw-body div[style*="min-width:280px"] { min-width: 100% !important; }
  /* 保存按钮全宽 */
  .lw-save .btn.primary { width: 100% !important; padding: 13px 0 !important; }
}
</style>
<div id="lw-panel">
  <div class="lw-hero">
    <h2>陆伍App 控制台</h2>
    <p>原生 Android App 配套控制台 · 公告 / 广告 / 更新一站式管理</p>
    <p style="font-family:monospace;font-size:12px;opacity:.85">数据接口：https://www.65gw.com/action/luwu-app</p>
    <div class="lw-badges">
      <span class="lw-badge">v{$pluginVersion}</span>
      <span class="lw-badge">● 运行中</span>
      <span class="lw-badge">Typecho 1.2</span>
      <span class="lw-badge">文字 / 图片 / 视频广告</span>
    </div>
  </div>
  <div class="lw-nav" id="lw_nav">
    <a class="on" data-t="all">全部</a>
    <a data-t="biz">商务合作</a>
    <a data-t="ad_global">广告开关</a>
    <a data-t="theme">主题设置</a>
    <a data-t="home_cats">分类控件</a>
    <a data-t="announce">公告</a>
    <a data-t="splash">开屏广告</a>
    <a data-t="ad_stats">广告数据</a>
    <a data-t="reports">举报管理</a>
    <a data-t="push">推送通知</a>
    <a data-t="dashboard">数据看板</a>
    <a data-t="ad_home">首页广告</a>
    <a data-t="ad_feed">信息流广告</a>
    <a data-t="ad_category">分类页广告</a>
    <a data-t="ad_article">内容页广告</a>
    <a data-t="paid">付费解锁</a>
    <a data-t="update">App更新</a>
  </div>
  <div class="lw-card" data-group="dashboard"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#8B5CF6,#6D28D9)">📈</div><div><b>数据看板</b><span>内容 / 评论 / 用户实时统计 · 近 7 日发文趋势</span></div></div><div class="lw-body" style="padding:18px;">
  <div style="display:flex;gap:12px;flex-wrap:wrap;margin-bottom:16px;">
    <div style="flex:1;min-width:110px;background:#F5F3FF;border:1px solid #EDE9FE;border-radius:10px;padding:14px;text-align:center;"><div style="font-size:22px;font-weight:800;color:#6D28D9;">{$dashStats['posts']}</div><div style="font-size:12px;color:#64748B;margin-top:4px;">文章总数</div></div>
    <div style="flex:1;min-width:110px;background:#F0FDF4;border:1px solid #D1FAE5;border-radius:10px;padding:14px;text-align:center;"><div style="font-size:22px;font-weight:800;color:#059669;">{$dashStats['comments']}</div><div style="font-size:12px;color:#64748B;margin-top:4px;">评论总数</div></div>
    <div style="flex:1;min-width:110px;background:#EFF6FF;border:1px solid #DBEAFE;border-radius:10px;padding:14px;text-align:center;"><div style="font-size:22px;font-weight:800;color:#2563EB;">{$dashStats['users']}</div><div style="font-size:12px;color:#64748B;margin-top:4px;">注册用户</div></div>
  </div>
  <div style="display:flex;gap:12px;flex-wrap:wrap;margin-bottom:18px;">
    <div style="flex:1;min-width:110px;background:#FFF7ED;border:1px solid #FFEDD5;border-radius:10px;padding:14px;text-align:center;"><div style="font-size:22px;font-weight:800;color:#F97316;">{$dashStats['todayPosts']}</div><div style="font-size:12px;color:#64748B;margin-top:4px;">今日发文</div></div>
    <div style="flex:1;min-width:110px;background:#FEF2F2;border:1px solid #FEE2E2;border-radius:10px;padding:14px;text-align:center;"><div style="font-size:22px;font-weight:800;color:#EF4444;">{$dashStats['todayComments']}</div><div style="font-size:12px;color:#64748B;margin-top:4px;">今日评论</div></div>
  </div>
  <div style="font-size:13px;font-weight:700;color:#0F172A;margin-bottom:2px;">近 7 日发文趋势</div>
  {$weekHtml}
  </div></div>
  <div class="lw-card" data-group="biz"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#F97316,#EF4444)">🤝</div><div><b>商务合作内容</b><span>App 商务合作页 · 广告介绍 / 价格 / 联系方式 / 注意事项</span></div></div><div class="lw-body"></div></div>
  <div class="lw-card" data-group="ad_global"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#64748B,#475569)">🎛️</div><div><b>广告总开关</b><span>一键关闭 / 开启 App 全部广告位</span></div></div><div class="lw-body"></div></div>
  <div class="lw-card" data-group="theme"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#6366F1,#8B5CF6)">🌗</div><div><b>App 主题默认设置</b><span>新用户首次安装默认主题 · 用户可在设置页自行切换</span></div></div><div class="lw-body"></div></div>
  <div class="lw-card" data-group="splash"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#F59E0B,#F97316)">🖼️</div><div><b>开屏广告</b><span>App 启动全屏广告 · 图片 / 跳转链接 / 倒计时跳过</span></div></div><div class="lw-body"></div></div>
  <div class="lw-card" data-group="reports"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#EF4444,#DC2626)">🚩</div><div><b>举报管理</b><span>待处理举报（{$reportCount} 条）· 请到 Typecho 后台删除违规内容</span></div></div><div class="lw-body" style="padding:16px;">{$reportsHtml}</div></div>
  <div class="lw-card" data-group="push"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#0EA5E9,#0284C7)">📣</div><div><b>推送通知</b><span>后台一键推送 · App 收到系统通知并可跳转文章 / 分类 / 链接</span></div></div><div class="lw-body" style="padding:16px;">
  <div style="display:flex;gap:14px;flex-wrap:wrap;">
    <div style="flex:1;min-width:280px;background:#F8FAFC;border:1px solid #E2E8F0;border-radius:12px;padding:16px;">
      <div style="font-size:13px;font-weight:700;color:#0F172A;margin-bottom:12px;">✍️ 发送新推送</div>
      <div style="margin-bottom:10px;"><div style="font-size:12px;color:#475569;margin-bottom:4px;">推送标题（限 100 字）</div><input type="text" id="lw_push_title" placeholder="例：App 重大更新 v6.3 已上线" /></div>
      <div style="margin-bottom:10px;"><div style="font-size:12px;color:#475569;margin-bottom:4px;">推送内容（限 300 字）</div><textarea id="lw_push_content" rows="3" placeholder="通知栏显示的正文内容…"></textarea></div>
      <div style="margin-bottom:10px;"><div style="font-size:12px;color:#475569;margin-bottom:4px;">点击通知跳转目标</div>
        <select id="lw_push_target" style="width:100%!important;max-width:none!important;">
          <option value="none">仅通知（不跳转）</option>
          <option value="post">文章（填写文章 ID）</option>
          <option value="category">分类（填写分类 ID）</option>
          <option value="url">自定义链接（填写完整 URL）</option>
        </select>
      </div>
      <div style="margin-bottom:12px;"><div style="font-size:12px;color:#475569;margin-bottom:4px;">目标值</div><input type="text" id="lw_push_target_val" placeholder="文章/分类 ID 或 https:// 链接" /></div>
      <button id="lw_push_send" style="background:linear-gradient(135deg,#0EA5E9,#0284C7);border:none;color:#fff;padding:11px 30px;border-radius:999px;font-size:13px;font-weight:700;cursor:pointer;box-shadow:0 6px 16px rgba(2,132,199,.3);">📣 立即推送</button>
      <div id="lw_push_status" style="font-size:12px;margin-top:10px;color:#64748B;"></div>
    </div>
    <div style="flex:1;min-width:280px;">
      <div style="font-size:13px;font-weight:700;color:#0F172A;margin-bottom:12px;">🕘 最近推送记录</div>
      {$pushHistoryHtml}
      <div style="font-size:11.5px;color:#94A3B8;margin-top:8px;">App 每 15 分钟自动检查一次，新推送会以系统通知形式展示。</div>
    </div>
  </div>
  </div></div>
  <div class="lw-card" data-group="ad_stats"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#10B981,#059669)">📊</div><div><b>开屏广告数据</b><span>曝光 / 点击 / 跳过统计（实时）</span></div></div><div class="lw-body" style="padding:18px;"><div style="display:flex;gap:12px;flex-wrap:wrap;"><div style="flex:1;min-width:110px;background:#F0FDF4;border:1px solid #D1FAE5;border-radius:10px;padding:14px;text-align:center;"><div style="font-size:22px;font-weight:800;color:#059669;">{$adStats['show']}</div><div style="font-size:12px;color:#64748B;margin-top:4px;">总曝光</div></div><div style="flex:1;min-width:110px;background:#EFF6FF;border:1px solid #DBEAFE;border-radius:10px;padding:14px;text-align:center;"><div style="font-size:22px;font-weight:800;color:#2563EB;">{$adStats['click']}</div><div style="font-size:12px;color:#64748B;margin-top:4px;">总点击</div></div><div style="flex:1;min-width:110px;background:#FFF7ED;border:1px solid #FFEDD5;border-radius:10px;padding:14px;text-align:center;"><div style="font-size:22px;font-weight:800;color:#F97316;">{$adStats['skip']}</div><div style="font-size:12px;color:#64748B;margin-top:4px;">总跳过</div></div></div></div></div>
  <div class="lw-card" data-group="home_cats"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#F59E0B,#F97316)">🗂️</div><div><b>首页分类控件</b><span>App 首页轮播下方的分类卡片 · 每行一个：分类标识|名称|简介</span></div></div><div class="lw-body"></div></div>
  <div class="lw-card" data-group="announce"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#F59E0B,#F97316)">📢</div><div><b>公告中心</b><span>App 启动弹窗 · 支持标题、内容与跳转链接</span></div></div><div class="lw-body"></div></div>
  <div class="lw-card" data-group="ad_home"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#0D9488,#14B8A6)">🏠</div><div><b>首页顶部广告</b><span>App 首页轮播图下方横幅 · 文字 / 图片 / 视频</span></div></div><div class="lw-body"></div></div>
  <div class="lw-card" data-group="ad_feed"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#3B82F6,#6366F1)">📰</div><div><b>信息流广告</b><span>文章列表每 N 条插入一条 · 支持间隔设置</span></div></div><div class="lw-body"></div></div>
  <div class="lw-card" data-group="ad_category"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#8B5CF6,#A855F7)">🗂️</div><div><b>分类页广告</b><span>分类文章列表顶部横幅 · 文字 / 图片 / 视频</span></div></div><div class="lw-body"></div></div>
  <div class="lw-card" data-group="ad_article"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#EC4899,#F43F5E)">📄</div><div><b>内容页广告</b><span>文章详情页底部横幅 · 文字 / 图片 / 视频</span></div></div><div class="lw-body"></div></div>
    <div class="lw-card" data-group="paid"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#F59E0B,#D97706)">🔓</div><div><b>付费解锁</b><span>付费资源管理 · 设置付费文章 / 生成解锁码 / 查看使用记录</span></div></div><div class="lw-body" style="padding:16px;">
  <div style="display:flex;gap:14px;flex-wrap:wrap;margin-bottom:14px;">
    <div style="flex:1;min-width:280px;background:#FFFBF0;border:1px solid #FDE68A;border-radius:12px;padding:16px;">
      <div style="font-size:14px;font-weight:700;color:#92400E;margin-bottom:10px;">① 把文章设为付费资源</div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:8px;">
        <input id="paid_cid" placeholder="文章ID（如 17011）" style="flex:1;min-width:110px;border:1px solid #E5E7EB;border-radius:8px;padding:9px 12px;font-size:13px;">
        <input id="paid_price" placeholder="售价(默认9.9)" style="width:96px;border:1px solid #E5E7EB;border-radius:8px;padding:9px 12px;font-size:13px;">
        <button type="button" onclick="paidSave()" style="border:none;border-radius:8px;padding:9px 16px;font-size:13px;font-weight:700;color:#fff;background:linear-gradient(135deg,#F59E0B,#F97316);cursor:pointer;">保存</button>
      </div>
      <div id="paid_save_msg" style="font-size:12px;color:#B45309;min-height:18px;"></div>
      <div style="font-size:12px;color:#B45309;line-height:1.7;background:#FFFBEB;border-radius:8px;padding:10px 12px;">说明：自动提取正文中的 <b>{cloud ...}</b> 网盘区块存入付费库，正文下载地址替换为付费卡。买家在 App 输入解锁码即可查看下载。</div>
    </div>
    <div style="flex:1;min-width:280px;background:#F0FDF4;border:1px solid #BBF7D0;border-radius:12px;padding:16px;">
      <div style="font-size:14px;font-weight:700;color:#166534;margin-bottom:10px;">② 生成解锁码（发给买家）</div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:8px;">
        <input id="gen_cid" placeholder="文章ID" style="flex:1;min-width:100px;border:1px solid #E5E7EB;border-radius:8px;padding:9px 12px;font-size:13px;">
        <input id="gen_num" placeholder="数量(默认5)" style="width:96px;border:1px solid #E5E7EB;border-radius:8px;padding:9px 12px;font-size:13px;">
        <button type="button" onclick="paidGen()" style="border:none;border-radius:8px;padding:9px 16px;font-size:13px;font-weight:700;color:#fff;background:linear-gradient(135deg,#10B981,#059669);cursor:pointer;">生成</button>
      </div>
      <div id="gen_codes" style="font-size:12px;color:#166534;min-height:18px;word-break:break-all;"></div>
    </div>
  </div>
  <div style="font-size:14px;font-weight:700;color:#0F172A;margin:6px 0 8px;">解锁码使用记录（最近 100 条）</div>
  <div id="paid_list" style="font-size:12.5px;color:#475569;line-height:1.8;">加载中…</div>
  </div></div>
  <div class="lw-card" data-group="update"><div class="lw-card-head"><div class="lw-ico" style="background:linear-gradient(135deg,#10B981,#059669)">🚀</div><div><b>App 更新管理</b><span>版本号 / 下载地址 / 更新说明 / 强制更新</span></div></div><div class="lw-body"></div></div>
  <div class="lw-save"></div>
</div>
<script>
(function () {
  function ready(fn) { if (document.readyState !== 'loading') { fn(); } else { document.addEventListener('DOMContentLoaded', fn); } }
  ready(function () {
    var form = document.querySelector('.typecho-page-main form');
    if (!form) return;
    var panel = document.getElementById('lw-panel');
    form.insertBefore(panel, form.firstChild);
    var groups = { biz: [], ad_global: [], theme: [], splash: [], home_cats: [], announce: [], ad_home: [], ad_feed: [], ad_category: [], ad_article: [], update: [] };
    var els = form.querySelectorAll('ul.typecho-option');
    els.forEach(function (el) {
      var id = el.id || '';
      var name = (id.match(/typecho-option-item-([\w]+)-/) || [])[1] || '';
      if (name.indexOf('luwuapp_biz') === 0) { groups.biz.push(el); }
      else if (name.indexOf('luwuapp_ad_global') === 0) { groups.ad_global.push(el); }
      else if (name.indexOf('luwuapp_theme_default') === 0) { groups.theme.push(el); }
      else if (name.indexOf('luwuapp_splash_') === 0) { groups.splash.push(el); }
      else if (name.indexOf('luwuapp_home_cats') === 0) { groups.home_cats.push(el); }
      else if (name.indexOf('luwuapp_announce') === 0) { groups.announce.push(el); }
      else if (name.indexOf('luwuapp_ad_home') === 0) { groups.ad_home.push(el); }
      else if (name.indexOf('luwuapp_ad_feed') === 0) { groups.ad_feed.push(el); }
      else if (name.indexOf('luwuapp_ad_category') === 0) { groups.ad_category.push(el); }
      else if (name.indexOf('luwuapp_ad_article') === 0) { groups.ad_article.push(el); }
      else if (name.indexOf('luwuapp_update') === 0) { groups.update.push(el); }
    });
    Object.keys(groups).forEach(function (g) {
      var body = panel.querySelector('.lw-card[data-group="' + g + '"] .lw-body');
      if (body && groups[g].length) { groups[g].forEach(function (el) { body.appendChild(el); }); }
    });
    var saveBtn = form.querySelector('.btn.primary, button[type="submit"]');
    var saveBox = panel.querySelector('.lw-save');
    if (saveBtn && saveBox) { saveBox.appendChild(saveBtn); }
    // ===== 分类导航 Tab 切换 =====
    var nav = document.getElementById('lw_nav');
    if (nav) {
      var cards = panel.querySelectorAll('.lw-card[data-group]');
      var navs = nav.querySelectorAll('a');
      navs.forEach(function (a) {
        a.addEventListener('click', function () {
          navs.forEach(function (x) { x.classList.remove('on'); });
          a.classList.add('on');
          var t = a.getAttribute('data-t');
          cards.forEach(function (c) {
            c.style.display = (t === 'all' || c.getAttribute('data-group') === t) ? '' : 'none';
          });
        });
      });
    }
    // ===== 图片 / 视频地址实时预览 =====
    form.querySelectorAll('input[type="text"][name*="luwuapp_ad_"]').forEach(function (inp) {
      var nm = inp.name;
      if (nm.indexOf('_img') < 0 && nm.indexOf('_video') < 0) return;
      var wrap = document.createElement('div');
      wrap.style.cssText = 'margin-top:6px;';
      var img = document.createElement('img');
      img.style.cssText = 'max-width:180px;max-height:90px;border-radius:8px;border:1px solid #DCE7E7;display:none;';
      var video = document.createElement('video');
      video.style.cssText = 'max-width:220px;max-height:110px;border-radius:8px;border:1px solid #DCE7E7;display:none;';
      video.setAttribute('controls', '');
      wrap.appendChild(img); wrap.appendChild(video);
      inp.parentNode.appendChild(wrap);
      function refresh() {
        var v = (inp.value || '').trim();
        if (nm.indexOf('_video') >= 0) {
          if (/^https?:\/\//.test(v)) { video.src = v; video.style.display = 'inline'; } else { video.style.display = 'none'; }
        } else if (/^https?:\/\//.test(v)) {
          img.onload = function () { img.style.display = 'inline'; };
          img.onerror = function () { img.style.display = 'none'; };
          img.src = v;
        } else { img.style.display = 'none'; }
      }
      inp.addEventListener('blur', refresh);
      refresh();
    });
    // ===== 接口自检按钮 =====
    var hero = panel.querySelector('.lw-hero');
    var apiUrl = 'https://www.65gw.com/action/luwu-app';
    if (hero) {
      var check = document.createElement('button');
      check.type = 'button';
      check.textContent = '测试数据接口';
      check.style.cssText = 'margin-top:12px;background:rgba(255,255,255,.18);border:1px solid rgba(255,255,255,.3);color:#fff;padding:7px 16px;border-radius:999px;font-size:12.5px;font-weight:600;cursor:pointer;';
      var stat = document.createElement('span');
      stat.style.cssText = 'margin-left:10px;font-size:12px;color:#fff;';
      check.addEventListener('click', function () {
        check.disabled = true;
        stat.textContent = '检测中…';
        fetch(apiUrl + '?api=status')
          .then(function (r) { return r.json(); })
          .then(function (j) {
            stat.textContent = j && j.ok ? '✓ 接口正常' : '✗ 接口异常: ' + (j && j.error ? j.error : '未知');
            stat.style.color = j && j.ok ? '#A7F3D0' : '#FECACA';
            check.disabled = false;
          })
          .catch(function () {
            stat.textContent = '✗ 连接失败，请检查服务器';
            stat.style.color = '#FECACA';
            check.disabled = false;
          });
      });
      hero.appendChild(check);
      hero.appendChild(stat);
    }
  });

  // ===== 推送通知：发送 =====
  var pushSend = panel.querySelector('#lw_push_send');
  if (pushSend) {
    var pushStatus = panel.querySelector('#lw_push_status');
    pushSend.addEventListener('click', function () {
      var title = (panel.querySelector('#lw_push_title').value || '').trim();
      var content = (panel.querySelector('#lw_push_content').value || '').trim();
      var target = panel.querySelector('#lw_push_target').value;
      var val = (panel.querySelector('#lw_push_target_val').value || '').trim();
      if (!title) { pushStatus.textContent = '请填写推送标题'; pushStatus.style.color = '#DC2626'; return; }
      if (!content) { pushStatus.textContent = '请填写推送内容'; pushStatus.style.color = '#DC2626'; return; }
      var params = 'title=' + encodeURIComponent(title) + '&content=' + encodeURIComponent(content) + '&target_type=' + encodeURIComponent(target);
      if (target === 'post' || target === 'category') {
        if (!/^\d+$/.test(val)) { pushStatus.textContent = '文章/分类目标请填写数字 ID'; pushStatus.style.color = '#DC2626'; return; }
        params += '&target_id=' + val;
      } else if (target === 'url') {
        if (!/^https?:\/\//i.test(val)) { pushStatus.textContent = '链接需以 http(s):// 开头'; pushStatus.style.color = '#DC2626'; return; }
        params += '&target_url=' + encodeURIComponent(val);
      }
      pushSend.disabled = true;
      pushStatus.textContent = '推送发送中…';
      pushStatus.style.color = '#64748B';
      fetch(apiUrl + '?api=send_push', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params
      })
        .then(function (r) { return r.json(); })
        .then(function (j) {
          pushSend.disabled = false;
          if (j && j.ok) {
            pushStatus.textContent = '✓ 推送已发送，App 将在 15 分钟内收到通知（请刷新页面查看记录）';
            pushStatus.style.color = '#059669';
            panel.querySelector('#lw_push_title').value = '';
            panel.querySelector('#lw_push_content').value = '';
            panel.querySelector('#lw_push_target_val').value = '';
          } else {
            pushStatus.textContent = '✗ ' + (j && j.error ? j.error : '发送失败，请确认已登录网站后台');
            pushStatus.style.color = '#DC2626';
          }
        })
        .catch(function () {
          pushSend.disabled = false;
          pushStatus.textContent = '✗ 请求失败，请检查网络';
          pushStatus.style.color = '#DC2626';
        });
    });
  }
})();
    // ===== 付费解锁管理 =====
    function paidSave(){
      var cid=document.getElementById('paid_cid').value.trim();
      var price=document.getElementById('paid_price').value.trim()||'9.9';
      if(!cid){alert('请填写文章ID');return;}
      var msg=document.getElementById('paid_save_msg'); msg.style.color='#B45309'; msg.textContent='保存中…';
      fetch('https://www.65gw.com/action/luwu-app?api=paid_save',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'cid='+encodeURIComponent(cid)+'&price='+encodeURIComponent(price)})
        .then(function(r){return r.json();}).then(function(j){msg.style.color=j.ok?'#166534':'#DC2626';msg.textContent=(j.ok?'✅ ':'❌ ')+(j.msg||j.error||'操作失败');if(j.ok){paidLoadList();}}).catch(function(){msg.style.color='#DC2626';msg.textContent='请求失败';});
    }
    function paidGen(){
      var cid=document.getElementById('gen_cid').value.trim();
      var num=document.getElementById('gen_num').value.trim()||'5';
      if(!cid){alert('请填写文章ID');return;}
      var box=document.getElementById('gen_codes'); box.style.color='#166534'; box.textContent='生成中…';
      fetch('https://www.65gw.com/action/luwu-app?api=paid_gen',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'cid='+encodeURIComponent(cid)+'&num='+encodeURIComponent(num)})
        .then(function(r){return r.json();}).then(function(j){
          if(j.ok){box.textContent='已生成 '+j.codes.length+' 个解锁码：'+j.codes.join('  ');paidLoadList();}
          else {box.style.color='#DC2626';box.textContent=j.error||'生成失败';}
        }).catch(function(){box.style.color='#DC2626';box.textContent='请求失败';});
    }
    function paidLoadList(){
      var box=document.getElementById('paid_list'); box.textContent='加载中…';
      fetch('https://www.65gw.com/action/luwu-app?api=paid_list')
        .then(function(r){return r.json();}).then(function(j){
          if(!j.ok){box.textContent=j.error||'加载失败';return;}
          if(!j.items||!j.items.length){box.innerHTML='<span style="color:#9CA3AF;">暂无解锁码记录</span>';return;}
          var h='<table style="width:100%;border-collapse:collapse;font-size:12.5px;"><tr style="color:#64748B;text-align:left;border-bottom:1px solid #E2E8F0;"><td style="padding:6px;">ID</td><td>文章</td><td>解锁码</td><td>状态</td><td>时间</td></tr>';
          j.items.forEach(function(it){
            var used=it.used?('已用 · UID '+it.used_uid):'未使用';
            var tm=new Date((it.used?it.used_at:it.created)*1000).toLocaleString();
            h+='<tr style="border-bottom:1px solid #F1F5F9;"><td style="padding:6px;">'+it.id+'</td><td>'+it.cid+'</td><td style="font-family:monospace;">'+it.code+'</td><td>'+used+'</td><td>'+tm+'</td></tr>';
          });
          box.innerHTML=h+'</table>';
        }).catch(function(){box.textContent='加载失败';});
    }
    paidLoadList();
</script>
HTML;

        /* ============ 商务合作内容 ============ */
        $form->addInput(new Textarea(
            'luwuapp_biz_intro', NULL, '陆伍官网（65gw.com）创立于 2026 年 1 月，是一个专注于网络搜集各种网站资源、源码分享和技术教程分享的博客资源网站，全面打造汇集全网最新最全免费资源分享和技术资源交流社区。', _t('商务合作 · 广告介绍'), _t('App 商务合作页「广告介绍」段落')
        ));
        $form->addInput(new Text(
            'luwuapp_biz_p1_name', NULL, '首页横幅', _t('商务合作 · 项目一名称'), _t('例如：首页横幅')
        ));
        $form->addInput(new Text(
            'luwuapp_biz_p1_price', NULL, '368 元/月', _t('商务合作 · 项目一价格'), _t('例如：368 元/月')
        ));
        $form->addInput(new Text(
            'luwuapp_biz_p2_name', NULL, '侧边栏横幅广告', _t('商务合作 · 项目二名称'), _t('例如：侧边栏横幅广告')
        ));
        $form->addInput(new Text(
            'luwuapp_biz_p2_price', NULL, '180 元/月', _t('商务合作 · 项目二价格'), _t('')
        ));
        $form->addInput(new Text(
            'luwuapp_biz_p3_name', NULL, '全站底部友情链接', _t('商务合作 · 项目三名称'), _t('例如：全站底部友情链接')
        ));
        $form->addInput(new Text(
            'luwuapp_biz_p3_price', NULL, '30 元/月', _t('商务合作 · 项目三价格'), _t('')
        ));
        $form->addInput(new Text(
            'luwuapp_biz_extra', NULL, '图片位置（可接受任意合法广告），价格合适即可，可联系 QQ 自行洽谈', _t('商务合作 · 补充说明'), _t('价格明细下方的补充文字')
        ));
        $form->addInput(new Text(
            'luwuapp_biz_qq', NULL, '615806139', _t('商务合作 · 联系 QQ'), _t('点击可复制')
        ));
        $form->addInput(new Textarea(
            'luwuapp_biz_notice', NULL, "1.本站用户活跃度、点击量领先同行，广告价格偏高但效果绝对不错。\n2.广告不提供试用，款到账后开始投放。\n3.本站不负责广告图片制作，请自备图片。\n4.图片广告价格会不定期调整，具体以咨询客服为准。\n5.最低 1 个月起投，多月投放可适当优惠，投放期限最长不超过 12 个月。", _t('商务合作 · 注意事项'), _t('每行一条')
        ));

        /* ============ 广告总开关 ============ */
        $adGlobal = new Select(
            'luwuapp_ad_global', array('on' => '开启', 'off' => '关闭'),
            'on', _t('广告 · 总开关'), _t('关闭后 App 所有广告位（首页/信息流/分类页/内容页）全部不显示')
        );
        $form->addInput($adGlobal);

        /* ============ App 主题默认设置 ============ */
        $themeDefault = new Select(
            'luwuapp_theme_default', array('system' => '跟随系统', 'light' => '浅色', 'dark' => '深色'),
            'system', _t('主题 · 全局默认'), _t('新安装用户首次启动使用该主题；App 内设置页可自行切换，登录用户偏好会同步到云端')
        );
        $form->addInput($themeDefault);

        /* ============ 开屏广告 ============ */
        $splashEnable = new Select(
            'luwuapp_splash_enable', array('0' => '关闭', '1' => '开启'),
            '0', _t('开屏广告 · 开关'), _t('开启后 App 启动展示全屏广告，倒计时结束或点击跳过进入首页')
        );
        $form->addInput($splashEnable);

        $form->addInput(new Text(
            'luwuapp_splash_image', NULL, '', _t('开屏广告 · 图片 URL'), _t('建议尺寸 1080×1920 竖版全屏图，支持 jpg / png / webp')
        ));

        $form->addInput(new Text(
            'luwuapp_splash_target', NULL, '', _t('开屏广告 · 跳转链接'), _t('点击广告后跳转的链接，留空则不跳转')
        ));

        $form->addInput(new Text(
            'luwuapp_splash_duration', NULL, '3', _t('开屏广告 · 倒计时（秒）'), _t('1~10 秒，默认 3 秒')
        ));

        $form->addInput(new Text(
            'luwuapp_splash_skip_text', NULL, '跳过', _t('开屏广告 · 跳过按钮文字'), _t('默认「跳过」')
        ));

        $splashOnce = new Select(
            'luwuapp_splash_once', array('0' => '每次启动都展示', '1' => '每台设备每天仅一次'),
            '0', _t('开屏广告 · 展示频率'), _t('选择「每天仅一次」可减少打扰，提高留存')
        );
        $form->addInput($splashOnce);

        /* ============ 首页分类控件 ============ */
        $form->addInput(new Textarea(
            'luwuapp_home_cats', NULL,
            "default|网站源码|汇集优质源码资源\njsjc|技术教程|高端技术教程分享\nyingyong|绿色软件|丰富软件资源\nfulihuodong|活动线报|创意无限，福利不断",
            _t('首页 · 分类卡片'), _t('每行一个，支持两种格式：① 分类标识|名称|简介；② 名称--简介--[icon](c-color) || /category/分类标识/（网页端格式，可整行复制）。留空则 App 使用内置默认分类')
        ));

        /* ============ 开屏公告 ============ */
        $announce = new Select(
            'luwuapp_announce_enabled', array('0' => '关闭', '1' => '开启'),
            '0', _t('公告 · 开关'), _t('开启后，App 每次启动弹出公告窗口')
        );
        $form->addInput($announce);

        $form->addInput(new Text(
            'luwuapp_announce_title', NULL, '', _t('公告 · 标题'), _t('例如：网站公告')
        ));

        $form->addInput(new Textarea(
            'luwuapp_announce_content', NULL, '', _t('公告 · 内容'), _t('支持换行，可填写活动通知、维护说明等')
        ));

        $form->addInput(new Text(
            'luwuapp_announce_link', NULL, '', _t('公告 · 跳转链接（可选）'), _t('留空则不跳转，例如 https://www.65gw.com/archives/1.html')
        ));

        /* ============ 首页顶部广告 ============ */
        $form->addInput(new Select(
            'luwuapp_ad_home_enable', array('on' => '启用', 'off' => '停用'),
            'on', _t('首页广告 · 开关'), _t('停用后首页广告位不显示')
        ));
        $form->addInput(new Textarea(
            'luwuapp_ad_home_items', NULL, '', _t('首页广告 · 多条广告（可选）'), _t('每行一条，格式：类型|文案|图片URL|视频URL|跳转链接；例如 text|免实名免备案|（图片类型填图片地址）|（视频类型填视频地址）|https://cloud.uxw.net/aff/NUOZHCHB。留空则使用下方单条广告设置')
        ));
        $form->addInput(self::adTypeSelect('home', _t('首页广告 · 类型（单条）')));
        $form->addInput(new Text(
            'luwuapp_ad_home_text', NULL, '免实名免备案 高性能虚拟主机', _t('首页广告 · 文案'), _t('文字类型时显示')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_home_img', NULL, '', _t('首页广告 · 图片（可选）'), _t('图片/视频类型时显示的封面图 URL')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_home_video', NULL, '', _t('首页广告 · 视频地址（可选）'), _t('视频类型时点击播放的视频 URL')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_home_link', NULL, 'https://cloud.uxw.net/aff/NUOZHCHB', _t('首页广告 · 跳转链接'), _t('点击横幅跳转的推广链接')
        ));

        /* ============ 信息流广告 ============ */
        $form->addInput(new Select(
            'luwuapp_ad_feed_enable', array('on' => '启用', 'off' => '停用'),
            'on', _t('信息流广告 · 开关'), _t('停用后信息流不插入广告')
        ));
        $form->addInput(new Textarea(
            'luwuapp_ad_feed_items', NULL, '', _t('信息流 · 多条广告（可选）'), _t('每行一条，格式同首页广告；留空则使用下方单条广告设置')
        ));
        $form->addInput(self::adTypeSelect('feed', _t('信息流 · 类型（单条）')));
        $form->addInput(new Text(
            'luwuapp_ad_feed_text', NULL, '极速域名注册 好记又便宜', _t('信息流 · 文案'), _t('文字类型时显示')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_feed_img', NULL, '', _t('信息流 · 图片（可选）'), _t('图片/视频类型时显示的图片 URL')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_feed_video', NULL, '', _t('信息流 · 视频地址（可选）'), _t('视频类型时点击播放的视频 URL')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_feed_link', NULL, 'https://name.uxw.net', _t('信息流 · 跳转链接'), _t('点击广告跳转的推广链接')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_feed_every', NULL, '5', _t('每 N 条文章插 1 条广告'), _t('填写数字，例如 5 表示每 5 篇文章插入一条广告')
        ));

        /* ============ 分类页广告 ============ */
        $form->addInput(new Select(
            'luwuapp_ad_category_enable', array('on' => '启用', 'off' => '停用'),
            'on', _t('分类页广告 · 开关'), _t('停用后分类页广告不显示')
        ));
        $form->addInput(new Textarea(
            'luwuapp_ad_category_items', NULL, '', _t('分类页 · 多条广告（可选）'), _t('每行一条，格式同首页广告；留空则使用下方单条广告设置')
        ));
        $form->addInput(self::adTypeSelect('category', _t('分类页 · 类型（单条）')));
        $form->addInput(new Text(
            'luwuapp_ad_category_text', NULL, '免实名免备案 高性能虚拟主机', _t('分类页 · 文案'), _t('文字类型时显示')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_category_img', NULL, '', _t('分类页 · 图片（可选）'), _t('图片/视频类型时显示的图片 URL')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_category_video', NULL, '', _t('分类页 · 视频地址（可选）'), _t('视频类型时点击播放的视频 URL')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_category_link', NULL, 'https://cloud.uxw.net/aff/NUOZHCHB', _t('分类页 · 跳转链接'), _t('点击广告跳转的推广链接')
        ));

        /* ============ 内容页广告 ============ */
        $form->addInput(new Select(
            'luwuapp_ad_article_enable', array('on' => '启用', 'off' => '停用'),
            'on', _t('内容页广告 · 开关'), _t('停用后文章详情页广告不显示')
        ));
        $form->addInput(new Textarea(
            'luwuapp_ad_article_items', NULL, '', _t('内容页 · 多条广告（可选）'), _t('每行一条，格式同首页广告；留空则使用下方单条广告设置')
        ));
        $form->addInput(self::adTypeSelect('article', _t('内容页 · 类型（单条）')));
        $form->addInput(new Text(
            'luwuapp_ad_article_text', NULL, '免实名免备案 稳定高速虚拟主机', _t('内容页 · 文案'), _t('文字类型时显示')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_article_img', NULL, '', _t('内容页 · 图片（可选）'), _t('图片/视频类型时显示的图片 URL')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_article_video', NULL, '', _t('内容页 · 视频地址（可选）'), _t('视频类型时点击播放的视频 URL')
        ));
        $form->addInput(new Text(
            'luwuapp_ad_article_link', NULL, 'https://cloud.uxw.net/aff/NUOZHCHB', _t('内容页 · 跳转链接'), _t('点击广告跳转的推广链接')
        ));

        /* ============ App 更新管理 ============ */
        $update = new Select(
            'luwuapp_update_enabled', array('0' => '关闭', '1' => '开启'),
            '0', _t('App更新 · 开关'), _t('开启后，App 启动时检查更新；把新版 APK 上传到网站后填写下方信息')
        );
        $form->addInput($update);

        $form->addInput(new Text(
            'luwuapp_update_version_name', NULL, '', _t('App更新 · 版本号'), _t('例如 3.2')
        ));
        $form->addInput(new Text(
            'luwuapp_update_version_code', NULL, '', _t('App更新 · 版本代码（数字）'), _t('必须比已安装版本大才会提示更新，例如 6')
        ));
        $form->addInput(new Text(
            'luwuapp_update_url', NULL, '', _t('App更新 · APK 下载地址'), _t('例如 https://www.65gw.com/usr/uploads/luwu-app.apk')
        ));
        $form->addInput(new Textarea(
            'luwuapp_update_changelog', NULL, '', _t('App更新 · 更新说明'), _t('换行分隔，例如：新增开屏公告功能')
        ));
        $form->addInput(new Textarea(
            'luwuapp_changelog', NULL, '', _t('App更新 · 历史更新日志'), _t('每行一个版本：版本号|日期|更新内容（内容用 ；分隔）。显示在 App「关于」页')
        ));
        $force = new Select(
            'luwuapp_update_force', array('0' => '否', '1' => '是'),
            '0', _t('App更新 · 强制更新'), _t('开启后用户必须更新才能继续使用')
        );
        $form->addInput($force);
    }

    /** 广告类型下拉（text 文字 / image 图片 / video 视频） */
    private static function adTypeSelect($pos, $label)
    {
        return new Select(
            'luwuapp_ad_' . $pos . '_type',
            array('text' => '文字', 'image' => '图片', 'video' => '视频'),
            'text',
            _t($label),
            _t('图片需填图片 URL；视频需填封面图 URL 和视频 URL')
        );
    }

    public static function personalConfig(Form $form) {}
}
