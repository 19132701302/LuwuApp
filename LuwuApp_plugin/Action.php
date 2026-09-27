<?php
/**
 * 陆伍App控制台 - 接口处理
 *
 * 所有接口统一入口：/action/luwu-app
 * GET  ?api=home|categories|category|article|search|announcement|ads|update
 * POST ?api=login|publish|mine
 *
 * 返回 JSON：{"ok":true,...} 或 {"ok":false,"error":"..."}
 */

namespace TypechoPlugin\LuwuApp;

use Typecho\Common;
use Typecho\Db;
use Typecho\Router;
use Typecho\Widget;
use Utils\PasswordHash;

if (!defined('__TYPECHO_ROOT_DIR__')) exit;

if (!defined('__TYPECHO_ROOT_DIR__')) exit;

class Action extends Widget
{
    private $db;
    private $settings = array();

    public function __construct($request, $response, $params = NULL)
    {
        parent::__construct($request, $response, $params);
        $this->db = Db::get();
        $this->loadSettings();
    }

    public function action()
    {
        @header('Content-Type: application/json; charset=utf-8');
        @header('Access-Control-Allow-Origin: *');
        @header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
        @header('Access-Control-Allow-Headers: Content-Type');
        @header('Cache-Control: no-cache');

        if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
            exit;
        }

        try {
            $api = $this->request->get('api', 'home');
            $method = 'api_' . preg_replace('/[^a-z0-9_]/', '', strtolower($api));
            if (!method_exists($this, $method)) {
                $this->json(array('ok' => false, 'error' => '接口不存在: ' . $api));
                return;
            }
            $this->$method();
        } catch (\Exception $e) {
            $this->json(array('ok' => false, 'error' => $e->getMessage()));
        }
    }

    /* ================= 数据接口 ================= */

    /** 接口自检：App 用于确认插件是否在线 */
    private function api_status()
    {
        $version = '1.8';
        $this->json(array(
            'ok' => true,
            'plugin' => 'LuwuApp',
            'version' => $version,
            'site' => '65gw.com',
            'time' => date('Y-m-d H:i:s'),
        ));
    }

    /** 站点统计：今日发帖 / 帖子总数 / 会员数量 */
    private function api_stats()
    {
        $db = $this->db;
        // 帖子总数（已发布）
        $total = (int)$db->fetchObject(
            $db->select('COUNT(*) AS c')->from('table.contents')
                ->where('type = ? AND status = ?', 'post', 'publish')
        )->c;
        // 今日发帖
        $todayStart = strtotime(date('Y-m-d 00:00:00'));
        $today = (int)$db->fetchObject(
            $db->select('COUNT(*) AS c')->from('table.contents')
                ->where('type = ? AND status = ? AND created >= ?', 'post', 'publish', $todayStart)
        )->c;
        // 会员数量
        try {
            $members = (int)$db->fetchObject($db->select('COUNT(*) AS c')->from('table.users'))->c;
        } catch (\Exception $e) {
            $members = 0;
        }
        $this->json(array(
            'ok' => true,
            'today' => $today,
            'total' => $total,
            'members' => $members,
        ));
    }

    /** 首页信息流：最新文章 */
    private function api_home()
    {
        $page = max(1, (int)$this->request->get('page', 1));
        $pageSize = min(50, max(1, (int)$this->request->get('pageSize', 20)));
        $sort = $this->request->get('sort', 'latest');
        $items = $this->queryPosts(null, $page, $pageSize, $sort);
        $total = $this->countPosts(null);
        $this->json(array(
            'ok' => true,
            'items' => $items,
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $total,
            'hasMore' => ($page * $pageSize) < $total,
        ));
    }

    /** 分类列表 */
    /** 分类图标：统一图标集（不再用文章封面当图标） */
    private function catIconUrl($name)
    {
        static $map = array(
            '网站源码' => 'cat_source', '技术教程' => 'cat_tutorial', '绿色软件' => 'cat_soft',
            '活动线报' => 'cat_huodong', '福利活动' => 'cat_welfare', '游戏相关' => 'cat_game',
            '热点资讯' => 'cat_hot', '网赚项目' => 'cat_earn', 'AI工具' => 'cat_ai',
            '影视' => 'cat_film', '影音娱乐' => 'cat_film', '影视音乐' => 'cat_film',
            '音乐' => 'cat_music', '歌曲分享' => 'cat_music',
        );
        $key = isset($map[$name]) ? $map[$name] : 'cat_default';
        return 'https://www.65gw.com/usr/uploads/icons/' . $key . '.png';
    }

    private function api_categories()
    {
        $rows = $this->db->fetchAll(
            $this->db->select('mid', 'name', 'slug', 'description', 'count')
                ->from('table.metas')
                ->where('type = ?', 'category')
                ->order('order', Db::SORT_ASC)
        );
        $items = array();
        foreach ($rows as $r) {
            // 分类图标：取该分类最新一篇带封面且域名稳定的文章封面，避免 App 端用首字占位
            $latest = $this->db->fetchAll(
                $this->db->select('table.contents.text')
                    ->from('table.contents')
                    ->join('table.relationships', 'table.contents.cid = table.relationships.cid', Db::INNER_JOIN)
                    ->where('table.contents.type = ? AND table.contents.status = ?', 'post', 'publish')
                    ->where('table.relationships.mid = ?', (int)$r['mid'])
                    ->order('table.contents.created', Db::SORT_DESC)
                    ->limit(20)
            );
            // 统一图标集（分类名 → 图标文件），不再使用文章封面
            $icon = $this->catIconUrl($r['name']);
            $items[] = array(
                'id' => $r['mid'],
                'name' => $r['name'],
                'slug' => $r['slug'],
                'description' => $r['description'],
                'count' => (int)$r['count'],
                'icon' => $icon,
            );
        }
        $this->json(array('ok' => true, 'items' => $items));
    }

    /** 分类文章列表 */
    private function api_category()
    {
        $slug = trim($this->request->get('slug', ''));
        $page = max(1, (int)$this->request->get('page', 1));
        $pageSize = min(50, max(1, (int)$this->request->get('pageSize', 20)));
        $sort = $this->request->get('sort', 'latest');
        if ($slug === '') {
            $this->json(array('ok' => false, 'error' => '缺少分类标识'));
            return;
        }
        $meta = $this->db->fetchRow(
            $this->db->select()->from('table.metas')
                ->where('type = ? AND slug = ?', 'category', $slug)
        );
        if (!$meta) {
            $this->json(array('ok' => false, 'error' => '分类不存在'));
            return;
        }
        $cids = $this->postIdsInMeta((int)$meta['mid']);
        $total = count($cids);
        if ($sort !== 'latest') {
            $pageCids = $this->sortCids($cids, $sort, $page, $pageSize);
        } else {
            $pageCids = array_slice($cids, ($page - 1) * $pageSize, $pageSize);
        }
        $items = $pageCids ? $this->queryPostsByIds($pageCids) : array();
        $this->json(array(
            'ok' => true,
            'items' => $items,
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $total,
            'hasMore' => ($page * $pageSize) < $total,
        ));
    }

    /** 文章详情 */
    private function api_article()
    {
        $id = (int)$this->request->get('id', 0);
        if ($id <= 0) {
            $this->json(array('ok' => false, 'error' => '缺少文章编号'));
            return;
        }
        $row = $this->db->fetchRow(
            $this->db->select()->from('table.contents')
                ->where('cid = ? AND type = ? AND status = ?', $id, 'post', 'publish')
        );
        if (!$row) {
            $this->json(array('ok' => false, 'error' => '文章不存在'));
            return;
        }
        $author = '';
        $authorId = (int)$row['authorId'];
        $authorAvatar = '';
        $u = $this->db->fetchRow(
            $this->db->select('screenName', 'name', 'avatar', 'mail')->from('table.users')
                ->where('uid = ?', $row['authorId'])
        );
        if ($u) {
            $author = $u['screenName'] ? $u['screenName'] : $u['name'];
            $authorAvatar = $this->userAvatar($u);
        }
        $tags = $this->tagsOfPost($row['cid']);
        $category = '';
        $rels = $this->db->fetchAll(
            $this->db->select('table.metas.name')
                ->from('table.relationships')
                ->join('table.metas', 'table.relationships.mid = table.metas.mid', Db::LEFT_JOIN)
                ->where('table.relationships.cid = ? AND table.metas.type = ?', $row['cid'], 'category')
        );
        if ($rels) {
            $category = $rels[0]['name'];
        }

        $item = array(
            'id' => $row['cid'],
            'title' => $row['title'],
            'slug' => $row['slug'],
            'date' => date('Y-m-d H:i', $row['created']),
            'category' => $category,
            'author' => $author,
            'authorId' => $authorId,
            'authorAvatar' => $authorAvatar,
            'content' => $this->maskPaidContent($row['text'], $row['cid']),
            'tags' => $tags,
            'thumb' => $this->extractThumb($row['text']),
            'link' => $this->postLink($row),
            'likes' => $this->postLikes($row['cid']),
            'commentsNum' => $this->postCommentsNum($row['cid']),
        );

        // 相关推荐：同分类最新 6 篇（排除当前），分类缺失/无结果时用同作者兜底
        $related = array();
        $relRows = array();
        if ($category !== '') {
            $mids = $this->db->fetchAll(
                $this->db->select('mid')->from('table.relationships')->where('cid = ?', $row['cid'])
            );
            $midList = array();
            foreach ($mids as $m) {
                $midList[] = (int)$m['mid'];
            }
            if (!empty($midList)) {
                $inMids = implode(',', $midList);
                try {
                    $relRows = $this->db->fetchAll(
                        $this->db->select('cid', 'title', 'text', 'created')
                            ->from('table.contents')
                            ->where("cid IN (SELECT cid FROM table.relationships WHERE mid IN ($inMids) AND cid <> ?) AND type = ? AND status = ?", $id, 'post', 'publish')
                            ->order('created', Db::SORT_DESC)
                            ->limit(6)
                    );
                } catch (\Exception $e) {
                    $relRows = array();
                }
            }
        }
        if (empty($relRows)) {
            $relRows = $this->db->fetchAll(
                $this->db->select('cid', 'title', 'text', 'created')->from('table.contents')
                    ->where('cid <> ? AND authorId = ? AND type = ? AND status = ?', $id, $authorId, 'post', 'publish')
                    ->order('created', Db::SORT_DESC)->limit(6)
            );
        }
        foreach ($relRows as $rr) {
            $related[] = array(
                'id' => (int)$rr['cid'],
                'title' => $rr['title'],
                'date' => date('Y-m-d', (int)$rr['created']),
                'likes' => $this->postLikes((int)$rr['cid']),
                'commentsNum' => $this->postCommentsNum((int)$rr['cid']),
                'thumb' => $this->extractThumb($rr['text']),
            );
        }

        $this->json(array('ok' => true, 'item' => $item, 'related' => $related));
    }

    /** 关注：add/remove/check/list（需登录，存 options 表） */
    private function api_follow()
    {
        $user = $this->authUser();
        if (!$user) {
            return;
        }
        $uid = (int)$user['uid'];
        $target = (int)$this->request->get('target', 0);
        $act = $this->request->get('act', 'list');
        $key = 'luwu_follow_' . $uid;
        $list = json_decode($this->readOption($key, '[]'), true);
        if (!is_array($list)) {
            $list = array();
        }
        if ($act === 'add') {
            if ($target > 0 && $target !== $uid && !in_array($target, $list)) {
                $list[] = $target;
                $this->setOption($key, json_encode($list));
                // 互动通知：新粉丝提醒（被关注者）
                $myName = $user['screenName'] ? $user['screenName'] : $user['name'];
                $this->addNotify($target, 'follow', $uid, $myName, 0, '关注了你');
            }
            $this->json(array('ok' => true, 'following' => in_array($target, $list)));
            return;
        }
        if ($act === 'remove') {
            $list = array_values(array_diff($list, array($target)));
            $this->setOption($key, json_encode($list));
            $this->json(array('ok' => true, 'following' => false));
            return;
        }
        if ($act === 'check') {
            $this->json(array('ok' => true, 'following' => in_array($target, $list)));
            return;
        }
        // list：关注作者列表
        $items = array();
        foreach ($list as $tid) {
            $tid = (int)$tid;
            if ($tid <= 0) continue;
            $u = $this->db->fetchRow(
                $this->db->select('uid', 'screenName', 'name', 'mail', 'avatar')->from('table.users')
                    ->where('uid = ?', $tid)
            );
            if (!$u) continue;
            $cnt = $this->db->fetchRow(
                $this->db->select('COUNT(*) AS c')->from('table.contents')
                    ->where('type = ? AND authorId = ?', 'post', $tid)
            );
            $items[] = array(
                'uid' => $tid,
                'name' => $u['screenName'] ? $u['screenName'] : $u['name'],
                'avatar' => $this->userAvatar($u),
                'posts' => $cnt ? (int)$cnt['c'] : 0,
            );
        }
        $this->json(array('ok' => true, 'items' => $items));
    }

    /** 作者主页：用户资料 + TA 发布的文章（需登录取 uid，或用 author 名） */
    private function api_user()
    {
        $uid = (int)$this->request->get('uid', 0);
        $name = trim($this->request->get('name', ''));
        if ($uid <= 0 && $name === '') {
            $this->json(array('ok' => false, 'error' => '缺少用户参数'));
            return;
        }
        $user = null;
        if ($uid > 0) {
            $user = $this->db->fetchRow(
                $this->db->select('uid', 'screenName', 'name', 'mail', 'avatar', 'created')
                    ->from('table.users')->where('uid = ?', $uid)
            );
        } else {
            $user = $this->db->fetchRow(
                $this->db->select('uid', 'screenName', 'name', 'mail', 'avatar', 'created')
                    ->from('table.users')->where('name = ? OR screenName = ?', $name, $name)
            );
        }
        if (!$user) {
            $this->json(array('ok' => false, 'error' => '用户不存在'));
            return;
        }
        $rows = $this->db->fetchAll(
            $this->db->select()->from('table.contents')
                ->where('type = ? AND authorId = ?', 'post', $user['uid'])
                ->order('created', Db::SORT_DESC)
                ->limit(100)
        );
        // 统计：关注数 / 粉丝数 / 获赞数 / 文章数
        $authorUid = (int)$user['uid'];
        $followingList = json_decode($this->readOption('luwu_follow_' . $authorUid, '[]'), true);
        $followingCount = is_array($followingList) ? count($followingList) : 0;
        $followerCount = 0;
        $cids = array();
        foreach ($rows as $r) {
            $cids[] = (int)$r['cid'];
        }
        try {
            $opts = $this->db->fetchAll(
                $this->db->select('value')->from('table.options')->where('name LIKE ?', 'luwu_follow_%')
            );
            foreach ($opts as $o) {
                $arr = json_decode($o['value'], true);
                if (is_array($arr) && in_array($authorUid, $arr)) {
                    $followerCount++;
                }
            }
        } catch (\Exception $e) {
            $followerCount = 0;
        }
        $likesReceived = 0;
        if (!empty($cids)) {
            $in = implode(',', $cids);
            try {
                $fl = $this->db->fetchRow(
                    $this->db->select('IFNULL(SUM(int_value),0) AS s')->from('table.fields')
                        ->where("cid IN ($in) AND name = ?", 'luwu_likes')
                );
                $likesReceived = $fl ? (int)$fl['s'] : 0;
            } catch (\Exception $e) {
                $likesReceived = 0;
            }
        }
        $postCnt = $this->db->fetchRow(
            $this->db->select('COUNT(*) AS c')->from('table.contents')
                ->where('type = ? AND authorId = ?', 'post', $authorUid)
        );
        $joined = $user['created'] ? date('Y-m', (int)$user['created']) : '';
        $this->json(array(
            'ok' => true,
            'user' => array(
                'uid' => (int)$user['uid'],
                'name' => $user['screenName'] ? $user['screenName'] : $user['name'],
                'avatar' => $this->userAvatar($user),
                'joined' => $joined,
                'following_count' => $followingCount,
                'follower_count' => $followerCount,
                'likes_received' => $likesReceived,
                'posts_count' => $postCnt ? (int)$postCnt['c'] : 0,
            ),
            'items' => $this->decoratePosts($rows),
        ));
    }

    /** 评论列表 */
    private function api_comments()
    {
        $id = (int)$this->request->get('id', 0);
        if ($id <= 0) {
            $this->json(array('ok' => false, 'error' => '缺少文章编号'));
            return;
        }
        $rows = $this->db->fetchAll(
            $this->db->select('author', 'created', 'text', 'coid', 'parent')
                ->from('table.comments')
                ->where('cid = ? AND type = ? AND status = ?', $id, 'comment', 'approved')
                ->order('created', Db::SORT_DESC)
        );
        // 父评论作者映射（回复 @xxx）
        $parentMap = array();
        foreach ($rows as $r) {
            $pc = (int)$r['parent'];
            if ($pc > 0 && !isset($parentMap[$pc])) {
                $pr = $this->db->fetchRow(
                    $this->db->select('author')->from('table.comments')->where('coid = ?', $pc)
                );
                $parentMap[$pc] = $pr ? ($pr['author'] ? $pr['author'] : '游客') : '';
            }
        }
        $items = array();
        foreach ($rows as $r) {
            $pc = (int)$r['parent'];
            $items[] = array(
                'coid' => (int)$r['coid'],
                'author' => $r['author'] ? $r['author'] : '游客',
                'parent' => isset($parentMap[$pc]) ? $parentMap[$pc] : '',
                'date' => date('Y-m-d H:i', (int)$r['created']),
                'content' => $r['text'],
            );
        }
        $this->json(array('ok' => true, 'id' => $id, 'items' => $items, 'total' => count($items)));
    }

    /** 发表评论（支持 parent 回复） */
    private function api_comment()
    {
        $id = (int)$this->request->get('id', 0);
        $parent = (int)$this->request->get('parent', 0);
        $author = trim(strip_tags((string)$this->request->get('author', '')));
        $content = trim(strip_tags((string)$this->request->get('content', '')));
        if ($id <= 0 || $content === '') {
            $this->json(array('ok' => false, 'error' => '缺少文章编号或评论内容'));
            return;
        }
        if (function_exists('mb_strlen') && mb_strlen($content, 'utf-8') > 500) {
            $this->json(array('ok' => false, 'error' => '评论不能超过500字'));
            return;
        }
        if ($parent > 0) {
            $pr = $this->db->fetchRow(
                $this->db->select('coid')->from('table.comments')->where('coid = ?', $parent)
            );
            if (!$pr) $parent = 0;
        }
        if ($author === '') {
            $author = '游客' . mt_rand(100, 999);
        }
        if (mb_strlen($author, 'utf-8') > 20) {
            $author = mb_substr($author, 0, 20, 'utf-8');
        }
        // 文章归属者
        $post = $this->db->fetchRow(
            $this->db->select('authorId')->from('table.contents')->where('cid = ?', $id)
        );
        $ownerId = $post ? (int)$post['authorId'] : 0;
        // 简单频控：同一 IP 60 秒内只允许一条
        $ip = isset($_SERVER['REMOTE_ADDR']) ? $_SERVER['REMOTE_ADDR'] : '0.0.0.0';
        $agent = isset($_SERVER['HTTP_USER_AGENT']) ? substr($_SERVER['HTTP_USER_AGENT'], 0, 255) : '';
        $last = $this->db->fetchRow(
            $this->db->select('created')->from('table.comments')
                ->where('ip = ? AND status = ?', $ip, 'approved')
                ->order('created', Db::SORT_DESC)
        );
        if ($last && (time() - (int)$last['created']) < 60) {
            $this->json(array('ok' => false, 'error' => '评论太频繁，请稍后再试'));
            return;
        }
        $this->db->query($this->db->insert('table.comments')->rows(array(
            'cid' => $id,
            'created' => time(),
            'author' => $author,
            'authorId' => 0,
            'ownerId' => $ownerId,
            'mail' => '',
            'url' => '',
            'ip' => $ip,
            'agent' => $agent,
            'text' => $content,
            'type' => 'comment',
            'status' => 'approved',
            'parent' => $parent,
        )));
        $this->json(array('ok' => true, 'author' => $author, 'total' => $this->postCommentsNum($id)));
    }

    /** 点赞 */
    private function api_like()
    {
        $id = (int)$this->request->get('id', 0);
        $act = $this->request->get('action', 'like'); // like 点赞 / cancel 取消点赞
        if (!in_array($act, array('like', 'cancel'), true)) {
            $this->json(array('ok' => false, 'error' => '未知操作'));
            return;
        }
        if ($id <= 0) {
            $this->json(array('ok' => false, 'error' => '缺少文章编号'));
            return;
        }
        // 简单频控：同一 IP 10 秒内仅允许一次点赞操作
        $ip = isset($_SERVER['REMOTE_ADDR']) ? $_SERVER['REMOTE_ADDR'] : '0.0.0.0';
        $lastLike = (int)$this->readOption('luwu_like_last_' . md5($ip), 0);
        if ($lastLike > 0 && (time() - $lastLike) < 10) {
            $this->json(array('ok' => false, 'error' => '操作太频繁，请稍后再试'));
            return;
        }
        $this->setOption('luwu_like_last_' . md5($ip), (string)time());
        $f = $this->db->fetchRow(
            $this->db->select('int_value')->from('table.fields')
                ->where('cid = ? AND name = ?', $id, 'luwu_likes')
        );
        $current = $f ? (int)$f['int_value'] : 0;
        if ($act === 'cancel') {
            $likes = max(0, $current - 1);
            if ($f) {
                $this->db->query(
                    $this->db->update('table.fields')
                        ->rows(array('int_value' => $likes))
                        ->where('cid = ? AND name = ?', $id, 'luwu_likes')
                );
            }
        } else {
            $likes = $current + 1;
            if ($f) {
                $this->db->query(
                    $this->db->update('table.fields')
                        ->rows(array('int_value' => $likes))
                        ->where('cid = ? AND name = ?', $id, 'luwu_likes')
                );
            } else {
                $this->db->query($this->db->insert('table.fields')->rows(array(
                    'cid' => $id, 'name' => 'luwu_likes', 'type' => 'int', 'int_value' => 1,
                )));
            }
        }
        // 互动通知：登录用户点赞 → 通知文章作者（匿名点赞不通知）
        $likerUid = 0;
        $likerName = '';
        $token = trim((string)$this->request->get('token', ''));
        if ($token !== '') {
            $lu = $this->userByToken($token);
            if ($lu) {
                $likerUid = (int)$lu['uid'];
                $likerName = $lu['screenName'] ? $lu['screenName'] : $lu['name'];
            }
        }
        $post = $this->db->fetchRow(
            $this->db->select('authorId')->from('table.contents')->where('cid = ?', $id)
        );
        $authorId = $post ? (int)$post['authorId'] : 0;
        if ($act === 'like' && $likerUid > 0 && $authorId > 0 && $likerUid !== $authorId) {
            $this->addNotify($authorId, 'like', $likerUid, $likerName, $id, '赞了你的文章');
        }
        $this->json(array('ok' => true, 'id' => $id, 'likes' => $likes, 'action' => $act));
    }

    /** 文章点赞数 */
    private function postLikes($cid)
    {
        $f = $this->db->fetchRow(
            $this->db->select('int_value')->from('table.fields')
                ->where('cid = ? AND name = ?', $cid, 'luwu_likes')
        );
        return $f ? (int)$f['int_value'] : 0;
    }

    /** 文章评论数 */
    private function postCommentsNum($cid)
    {
        $row = $this->db->fetchRow(
            $this->db->select('COUNT(*) AS n')->from('table.comments')
                ->where('cid = ? AND type = ? AND status = ?', $cid, 'comment', 'approved')
        );
        return $row ? (int)$row['n'] : 0;
    }

    /** 搜索 */
    private function api_search()
    {
        $q = trim($this->request->get('q', ''));
        $page = max(1, (int)$this->request->get('page', 1));
        $pageSize = min(50, max(1, (int)$this->request->get('pageSize', 20)));
        $field = $this->request->get('field', 'title'); // title 只搜标题 / content 搜内容关键词
        if ($q === '') {
            $this->json(array('ok' => true, 'items' => array(), 'total' => 0, 'page' => 1, 'pageSize' => $pageSize, 'hasMore' => false));
            return;
        }
        // 记录搜索词（热门搜索统计；失败不影响搜索）
        try {
            $this->ensureSearchLogTable();
            // Typecho 1.2 无 onDuplicateUpdate：查→改/插 两段式（Query 链式绑定自动转义）
            $exists = $this->db->fetchRow(
                $this->db->select('id', 'count')
                    ->from('table.luwu_search_log')
                    ->where('word = ?', $q)
            );
            if ($exists) {
                $this->db->query(
                    $this->db->update('table.luwu_search_log')
                        ->rows(array('count' => ((int)$exists['count']) + 1, 'updated' => date('Y-m-d H:i:s')))
                        ->where('word = ?', $q)
                );
            } else {
                $this->db->query(
                    $this->db->insert('table.luwu_search_log')
                        ->rows(array('word' => $q, 'count' => 1, 'updated' => date('Y-m-d H:i:s')))
                );
            }
        } catch (\Throwable $e) {
        }
        $like = '%' . $q . '%';
        if ($field === 'content') {
            $where = 'text LIKE ?';
            $params = array($like);
        } else {
            $where = 'title LIKE ?';
            $params = array($like);
        }
        $total = $this->db->fetchObject(
            $this->db->select(array('COUNT(cid)' => 'num'))
                ->from('table.contents')
                ->where('type = ? AND status = ?', 'post', 'publish')
                ->where($where, ...$params)
        )->num;
        $rows = $this->db->fetchAll(
            $this->db->select()->from('table.contents')
                ->where('type = ? AND status = ?', 'post', 'publish')
                ->where($where, ...$params)
                ->order('created', Db::SORT_DESC)
                ->page($page, $pageSize)
        );
        $this->json(array(
            'ok' => true,
            'items' => $this->decoratePosts($rows),
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => (int)$total,
            'hasMore' => ($page * $pageSize) < (int)$total,
        ));
    }

    /** 热门搜索：按搜索次数聚合返回 TOP 8（自动建表，兼容已上线站点） */
    private function ensureSearchLogTable()
    {
        static $done = false;
        if ($done) {
            return;
        }
        $done = true;
        $prefix = $this->db->getPrefix();
        $this->db->query("CREATE TABLE IF NOT EXISTS {$prefix}luwu_search_log (
            id INT AUTO_INCREMENT PRIMARY KEY,
            word VARCHAR(60) NOT NULL,
            count INT NOT NULL DEFAULT 1,
            updated DATETIME NOT NULL,
            UNIQUE KEY uniq_word (word)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private function api_hot_search()
    {
        try {
            $this->ensureSearchLogTable();
            // 热度按时间衰减排序：count 除以 2^(距今天数/7)（7 天半衰期），近期热词优先
            $rows = $this->db->fetchAll(
                "SELECT word, count FROM {$this->db->getPrefix()}luwu_search_log ORDER BY count / POW(2, DATEDIFF(NOW(), updated) / 7.0) DESC, count DESC, updated DESC LIMIT 8"
            );
        } catch (\Exception $e) {
            $rows = array();
        }
        $words = array();
        foreach ($rows as $r) {
            $words[] = array('word' => $r['word'], 'count' => (int)$r['count']);
        }
        $this->json(array('ok' => true, 'items' => $words));
    }

    /** 我的发布 */
    private function api_mine()
    {
        $user = $this->authUser();
        if (!$user) {
            return;
        }
        $rows = $this->db->fetchAll(
            $this->db->select()->from('table.contents')
                ->where('type = ? AND authorId = ?', 'post', $user['uid'])
                ->order('created', Db::SORT_DESC)
                ->limit(100)
        );
        $this->json(array(
            'ok' => true,
            'user' => array(
                'uid' => $user['uid'],
                'name' => $user['screenName'] ? $user['screenName'] : $user['name'],
                'avatar' => $this->userAvatar($user),
            ),
            'items' => $this->decoratePosts($rows),
        ));
    }

    /** 消息中心：我的文章被评论 / 回复我的评论（需登录） */
    /* ================= 互动通知（被赞 / 新粉丝） ================= */

    /** 通知表：首次使用自动创建（兼容已上线站点，无需重新激活插件） */
    private function ensureNotifyTable()
    {
        static $done = false;
        if ($done) {
            return;
        }
        $done = true;
        $prefix = $this->db->getPrefix();
        $this->db->query("CREATE TABLE IF NOT EXISTS {$prefix}luwu_notify (
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

    /** 写入互动通知：同一操作者对同一目标仅保留最新一条（防刷屏） */
    private function addNotify($targetUid, $type, $fromUid, $fromName, $cid, $content)
    {
        if ((int)$targetUid <= 0) {
            return;
        }
        $this->ensureNotifyTable();
        $this->db->query($this->db->delete('table.luwu_notify')
            ->where('uid = ? AND type = ? AND from_uid = ? AND cid = ?', (int)$targetUid, $type, (int)$fromUid, (int)$cid));
        $this->db->query($this->db->insert('table.luwu_notify')->rows(array(
            'uid' => (int)$targetUid,
            'type' => $type,
            'from_uid' => (int)$fromUid,
            'from_name' => $fromName,
            'cid' => (int)$cid,
            'content' => mb_substr($content, 0, 200, 'utf-8'),
            'created' => time(),
        )));
    }

    /** 消息中心：评论/回复 + 赞 + 新粉丝 统一合并，按时间倒序，含未读数 */
    private function api_notify()
    {
        $user = $this->authUser();
        if (!$user) {
            return;
        }
        $uid = (int)$user['uid'];
        $act = $this->request->get('act', 'list');
        $seenKey = 'luwu_notify_seen_' . $uid;
        if ($act === 'read') {
            $this->setOption($seenKey, (string)time());
            $this->json(array('ok' => true));
            return;
        }
        $items = array();

        // 1) 评论/回复：实时查我文章下的评论（排除自己）
        $posts = $this->db->fetchAll(
            $this->db->select('cid', 'title')->from('table.contents')
                ->where('type = ? AND authorId = ?', 'post', $uid)
        );
        $cids = array();
        $titleMap = array();
        foreach ($posts as $p) {
            $cids[] = (int)$p['cid'];
            $titleMap[(int)$p['cid']] = $p['title'];
        }
        if (!empty($cids)) {
            $in = implode(',', $cids);
            $rows = $this->db->fetchAll(
                $this->db->select('coid', 'cid', 'author', 'authorId', 'created', 'text', 'parent')
                    ->from('table.comments')
                    ->where("cid IN ($in) AND type = 'comment' AND status = 'approved' AND authorId <> $uid")
                    ->order('created', Db::SORT_DESC)
                    ->limit(60)
            );
            foreach ($rows as $r) {
                $ts = (int)$r['created'];
                $items[] = array(
                    'coid' => (int)$r['coid'],
                    'cid' => (int)$r['cid'],
                    'type' => (int)$r['parent'] > 0 ? 'reply' : 'comment',
                    'author' => $r['author'] ? $r['author'] : '游客',
                    'from_uid' => 0,
                    'title' => isset($titleMap[(int)$r['cid']]) ? $titleMap[(int)$r['cid']] : '文章',
                    'content' => mb_substr($r['text'], 0, 80, 'utf-8'),
                    'time' => date('Y-m-d H:i', $ts),
                    'ts' => $ts,
                    'read' => 0,
                );
            }
        }

        // 2) 赞/关注：通知表
        $this->ensureNotifyTable();
        $rows2 = $this->db->fetchAll(
            $this->db->select('id', 'type', 'from_uid', 'from_name', 'cid', 'content', 'created')
                ->from('table.luwu_notify')
                ->where('uid = ?', $uid)
                ->order('created', Db::SORT_DESC)
                ->limit(60)
        );
        foreach ($rows2 as $r) {
            $ts = (int)$r['created'];
            $title = '';
            if ($r['type'] === 'like' && (int)$r['cid'] > 0) {
                $p = $this->db->fetchRow(
                    $this->db->select('title')->from('table.contents')->where('cid = ?', (int)$r['cid'])
                );
                $title = $p ? $p['title'] : '文章';
            }
            $items[] = array(
                'coid' => 0,
                'cid' => (int)$r['cid'],
                'type' => $r['type'],
                'author' => $r['from_name'] ? $r['from_name'] : '用户',
                'from_uid' => (int)$r['from_uid'],
                'title' => $title,
                'content' => $r['content'],
                'time' => date('Y-m-d H:i', $ts),
                'ts' => $ts,
                'read' => 0,
            );
        }

        // 3) 合并排序 + 未读标记
        $seen = (int)$this->readOption($seenKey, 0);
        usort($items, function ($a, $b) {
            return $b['ts'] - $a['ts'];
        });
        $unread = 0;
        foreach ($items as &$it) {
            $it['read'] = $it['ts'] <= $seen ? 1 : 0;
            if ($it['read'] == 0) {
                $unread++;
            }
        }
        unset($it);
        $items = array_slice($items, 0, 60);
        $this->json(array('ok' => true, 'items' => $items, 'total' => count($items), 'unread' => $unread));
    }

    /* ================= 后台推送 ================= */

    /** 拉取后台推送消息（匿名可读，App 轮询用） */
    private function api_push()
    {
        $rows = $this->db->fetchAll(
            $this->db->select('id', 'title', 'content', 'target_type', 'target_id', 'target_url', 'created')
                ->from('table.luwu_push')
                ->order('id', Db::SORT_DESC)
                ->limit(10)
        );
        $items = array();
        foreach ($rows as $r) {
            $items[] = array(
                'id' => (int)$r['id'],
                'title' => $r['title'],
                'content' => $r['content'],
                'target_type' => $r['target_type'],
                'target_id' => (int)$r['target_id'],
                'target_url' => $r['target_url'],
                'ts' => (int)$r['created'],
                'time' => date('Y-m-d H:i', (int)$r['created']),
            );
        }
        $this->json(array('ok' => true, 'items' => $items));
    }

    /** 后台发送推送（管理员 cookie 校验：__typecho_uid + __typecho_authCode） */
    private function api_send_push()
    {
        if (!$this->adminAuth()) {
            $this->json(array('ok' => false, 'error' => '仅网站管理员可发送推送（请先登录网站后台）'));
            return;
        }
        $title = trim(strip_tags((string)$this->request->get('title', '')));
        $content = trim(strip_tags((string)$this->request->get('content', '')));
        $targetType = $this->request->get('target_type', 'none');
        if (!in_array($targetType, array('none', 'post', 'category', 'url'), true)) {
            $this->json(array('ok' => false, 'error' => '目标类型不合法'));
            return;
        }
        if ($title === '' || $content === '') {
            $this->json(array('ok' => false, 'error' => '请填写推送标题和内容'));
            return;
        }
        if (function_exists('mb_strlen') && (mb_strlen($title, 'utf-8') > 100 || mb_strlen($content, 'utf-8') > 300)) {
            $this->json(array('ok' => false, 'error' => '标题限 100 字、内容限 300 字'));
            return;
        }
        $targetId = (int)$this->request->get('target_id', 0);
        $targetUrl = trim((string)$this->request->get('target_url', ''));
        if ($targetType === 'url' && $targetUrl !== '' && !preg_match('#^https?://#i', $targetUrl)) {
            $this->json(array('ok' => false, 'error' => '链接需以 http(s):// 开头'));
            return;
        }
        // 原生 SQL 插入（options 无此表约束，仍做转义防注入）
        $title = addslashes($title);
        $content = addslashes($content);
        $targetUrl = addslashes($targetUrl);
        $now = time();
        $db = $this->db;
        $sql = "INSERT INTO typecho_luwu_push (title, content, target_type, target_id, target_url, created) VALUES ('$title', '$content', '$targetType', $targetId, '$targetUrl', $now)";
        try {
            $db->query($sql);
        } catch (Exception $e) {
            $this->json(array('ok' => false, 'error' => '推送保存失败'));
            return;
        }
        $this->json(array('ok' => true, 'error' => ''));
    }

    /**
     * 后台管理员校验：比对 Typecho 登录 cookie（__typecho_uid + __typecho_authCode）
     * authCode 算法与 Typecho\Common::hash 一致（$T$盐 + md5）
     */
    private function adminAuth()
    {
        $uid = (int)\Typecho\Cookie::get('__typecho_uid');
        $cookieAuth = (string)\Typecho\Cookie::get('__typecho_authCode');
        if ($uid <= 0 || $cookieAuth === '' || substr($cookieAuth, 0, 3) !== '$T$') {
            return false;
        }
        $user = $this->db->fetchRow(
            $this->db->select('uid', 'name', 'authCode', 'group')->from('table.users')->where('uid = ?', $uid)
        );
        if (!$user || $user['group'] !== 'administrator') {
            return false;
        }
        $string = $user['authCode'];
        $length = strlen($string);
        if ($length === 0) {
            return false;
        }
        $salt = substr($cookieAuth, 3, 9);
        if (strlen($salt) !== 9) {
            return false;
        }
        $hash = '';
        $last = ord($string[$length - 1]);
        $pos = 0;
        while ($pos < $length) {
            $asc = ord($string[$pos]);
            $last = ($last * ord($salt[($last % $asc) % 9]) + $asc) % 95 + 32;
            $hash .= chr($last);
            $pos++;
        }
        return ('$T$' . $salt . md5($hash)) === $cookieAuth;
    }

    /* ================= 头像与图片上传 ================= */

    /** 用户头像：优先 users.avatar 自定义，否则邮箱 Gravatar/Cravatar */
    private function userAvatar($user)
    {
        if (isset($user['avatar']) && trim((string)$user['avatar']) !== '') {
            return trim($user['avatar']);
        }
        $row = $this->db->fetchRow(
            $this->db->select('avatar')->from('table.users')->where('uid = ?', $user['uid'])
        );
        if ($row && trim((string)$row['avatar']) !== '') {
            return trim($row['avatar']);
        }
        $mail = isset($user['mail']) ? strtolower(trim($user['mail'])) : '';
        if ($mail !== '') {
            return 'https://cravatar.cn/avatar/' . md5($mail) . '?d=identicon&s=200';
        }
        return '';
    }

    /** 上传头像（multipart file=图片，需登录） */
    private function api_avatar()
    {
        $user = $this->authUser();
        if (!$user) {
            return;
        }
        $url = $this->saveUpload('avatar');
        if (!$url) {
            return;
        }
        $this->db->query($this->db->update('table.users')->rows(array('avatar' => $url))->where('uid = ?', $user['uid']));
        $this->json(array('ok' => true, 'avatar' => $url));
    }

    /** 上传图片（评论配图等通用，需登录） */
    private function api_upload()
    {
        $user = $this->authUser();
        if (!$user) {
            return;
        }
        $type = trim($this->request->get('type', 'image')); // image / video
        if (!in_array($type, array('image', 'video'), true)) {
            $this->json(array('ok' => false, 'error' => '上传类型不合法'));
            return;
        }
        $url = $this->saveUpload($type === 'video' ? 'videos' : 'images', 'file', $type);
        if (!$url) {
            return;
        }
        $this->json(array('ok' => true, 'url' => $url, 'type' => $type));
    }

    /** 商务合作页内容（后台可改） */
    private function api_biz()
    {
        $this->json(array(
            'ok' => true,
            'intro' => $this->opt('luwuapp_biz_intro', '陆伍官网（65gw.com）创立于 2026 年 1 月，是一个专注于网络搜集各种网站资源、源码分享和技术教程分享的博客资源网站，全面打造汇集全网最新最全免费资源分享和技术资源交流社区。'),
            'plans' => array(
                array('name' => $this->opt('luwuapp_biz_p1_name', '首页横幅'), 'price' => $this->opt('luwuapp_biz_p1_price', '368 元/月')),
                array('name' => $this->opt('luwuapp_biz_p2_name', '侧边栏横幅广告'), 'price' => $this->opt('luwuapp_biz_p2_price', '180 元/月')),
                array('name' => $this->opt('luwuapp_biz_p3_name', '全站底部友情链接'), 'price' => $this->opt('luwuapp_biz_p3_price', '30 元/月')),
            ),
            'extra' => $this->opt('luwuapp_biz_extra', '图片位置（可接受任意合法广告），价格合适即可，可联系 QQ 自行洽谈'),
            'qq' => $this->opt('luwuapp_biz_qq', '615806139'),
            'notice' => $this->opt('luwuapp_biz_notice', ''),
        ));
    }

    /** 用户反馈：标题 + 内容 + 联系方式 + 截图（最多 3 张，无需登录） */
    private function api_feedback()
    {
        $title = trim(strip_tags((string)$this->request->get('title', '')));
        $content = trim(strip_tags((string)$this->request->get('content', '')));
        $contact = trim(strip_tags((string)$this->request->get('contact', '')));
        if ($title === '' || mb_strlen($title) > 60) {
            $this->json(array('ok' => false, 'error' => '请填写标题（60 字以内）'));
            return;
        }
        if ($content === '' || mb_strlen($content) > 2000) {
            $this->json(array('ok' => false, 'error' => '请填写反馈内容（2000 字以内）'));
            return;
        }
        $root = dirname(dirname(dirname(__DIR__)));
        $dir = $root . '/usr/uploads/feedback';
        if (!is_dir($dir)) {
            @mkdir($dir, 0755, true);
        }
        // 保存截图（file0 ~ file2，非必填）
        $imgs = array();
        for ($i = 0; $i < 3; $i++) {
            $k = 'file' . $i;
            if (empty($_FILES[$k]) || !is_uploaded_file($_FILES[$k]['tmp_name'])) continue;
            $f = $_FILES[$k];
            $size = (int)$f['size'];
            if ($size <= 0 || $size > 5 * 1024 * 1024) continue;
            $ext = strtolower(pathinfo($f['name'], PATHINFO_EXTENSION));
            if (!in_array($ext, array('jpg', 'jpeg', 'png', 'gif', 'webp'))) continue;
            $name = date('YmdHis') . '_' . mt_rand(1000, 9999) . '_' . $i . '.' . $ext;
            if (move_uploaded_file($f['tmp_name'], $dir . '/' . $name)) {
                @chmod($dir . '/' . $name, 0644);
                $base = rtrim($this->opt('luwuapp_site_url', 'https://www.65gw.com'), '/');
                $imgs[] = $base . '/usr/uploads/feedback/' . $name;
            }
        }
        // 反馈正文存档
        $txt = "时间：" . date('Y-m-d H:i:s') . "\n"
            . "标题：" . $title . "\n"
            . "联系方式：" . $contact . "\n"
            . "内容：" . $content . "\n"
            . "截图：" . implode(' ', $imgs) . "\n";
        $fn = $dir . '/' . date('YmdHis') . '_' . mt_rand(1000, 9999) . '.txt';
        @file_put_contents($fn, $txt);
        $this->json(array('ok' => true, 'message' => '反馈已提交，感谢您的建议'));
    }

    /** 保存 multipart 图片到站点目录，返回可访问 URL；失败时输出 json 并返回空串 */
    private function saveUpload($subDir, $field = 'file', $type = 'image')
    {
        if (empty($_FILES[$field]) || !is_uploaded_file($_FILES[$field]['tmp_name'])) {
            $this->json(array('ok' => false, 'error' => '未收到上传文件'));
            return '';
        }
        $f = $_FILES[$field];
        $size = (int)$f['size'];
        $isVideo = ($type === 'video');
        $maxSize = $isVideo ? 50 * 1024 * 1024 : 5 * 1024 * 1024;
        if ($size <= 0 || $size > $maxSize) {
            $this->json(array('ok' => false, 'error' => $isVideo ? '视频大小需在 50MB 以内' : '图片大小需在 5MB 以内'));
            return '';
        }
        $ext = strtolower(pathinfo($f['name'], PATHINFO_EXTENSION));
        if ($isVideo) {
            if (!in_array($ext, array('mp4', 'webm', 'mov', 'mkv', 'avi'), true)) {
                $this->json(array('ok' => false, 'error' => '仅支持 mp4 / webm / mov 等视频格式'));
                return '';
            }
            // 校验真实视频内容（防伪装恶意文件）：finfo 优先，兼容 mime_content_type 对 mp4 误判为 octet-stream
            $mime = '';
            if (function_exists('finfo_open')) {
                $fi = @finfo_open(FILEINFO_MIME_TYPE);
                if ($fi) { $mime = @finfo_file($fi, $f['tmp_name']); @finfo_close($fi); }
            }
            if ($mime === '' || $mime === false) { $mime = @mime_content_type($f['tmp_name']); }
            if (!$mime) { $mime = ''; }
            $okMime = (strpos($mime, 'video/') === 0)
                || in_array($mime, array('application/octet-stream', 'application/mp4', 'video/mp4'), true);
            if (!$okMime) {
                $this->json(array('ok' => false, 'error' => '视频文件校验失败，请上传真实视频'));
                return '';
            }
        } else {
            if (!in_array($ext, array('jpg', 'jpeg', 'png', 'gif', 'webp'))) {
                $this->json(array('ok' => false, 'error' => '仅支持 jpg / png / gif / webp 图片'));
                return '';
            }
            // 校验真实图片内容（防伪装恶意文件）
            $imgInfo = @getimagesize($f['tmp_name']);
            if ($imgInfo === false) {
                $this->json(array('ok' => false, 'error' => '图片文件校验失败，请上传真实图片'));
                return '';
            }
        }
        // 站点根目录：/www/wwwroot/65gw.com（插件位于 usr/plugins/LuwuApp）
        $root = dirname(dirname(dirname(__DIR__)));
        $dir = $root . '/usr/uploads/' . $subDir;
        if (!is_dir($dir)) {
            @mkdir($dir, 0755, true);
        }
        $name = date('YmdHis') . '_' . mt_rand(1000, 9999) . '.' . $ext;
        $path = $dir . '/' . $name;
        if (!move_uploaded_file($f['tmp_name'], $path)) {
            $this->json(array('ok' => false, 'error' => '图片保存失败，请检查目录权限'));
            return '';
        }
        @chmod($path, 0644);
        $base = rtrim($this->opt('luwuapp_site_url', 'https://www.65gw.com'), '/');
        return $base . '/usr/uploads/' . $subDir . '/' . $name;
    }

    /* ================= 控制接口 ================= */

    /** 开屏公告 */
    private function api_announcement()
    {
        $this->json(array(
            'ok' => true,
            'enabled' => (bool)$this->opt('luwuapp_announce_enabled', '0'),
            'title' => $this->opt('luwuapp_announce_title', '网站公告'),
            'content' => $this->opt('luwuapp_announce_content', ''),
            'link' => $this->opt('luwuapp_announce_link', ''),
        ));
    }

    /** 各位置广告配置 */
    private function api_ads()
    {
        $every = max(1, (int)$this->opt('luwuapp_ad_feed_every', '5'));
        $global = $this->opt('luwuapp_ad_global', 'on');
        $this->json(array(
            'ok' => true,
            'enable' => $global,
            'homeTop' => $this->adItems('home'),
            'feed' => $this->adItems('feed'),
            'feedEvery' => $every,
            'category' => $this->adItems('category'),
            'article' => $this->adItems('article'),
            'homeCats' => $this->homeCats(),
        ));
    }

    /** 某广告位多条广告：优先 items 多行，空则回退单条 */
    private function adItems($pos)
    {
        if ($this->opt('luwuapp_ad_' . $pos . '_enable', 'on') === 'off') {
            return array();
        }
        $raw = trim((string)$this->opt('luwuapp_ad_' . $pos . '_items', ''));
        $list = array();
        if ($raw !== '') {
            foreach (preg_split('/\r?\n/', $raw) as $line) {
                $line = trim($line);
                if ($line === '') continue;
                $parts = explode('|', $line);
                if (count($parts) < 2) continue;
                $list[] = array(
                    'type' => trim($parts[0]),
                    'text' => trim($parts[1]),
                    'img' => isset($parts[2]) ? trim($parts[2]) : '',
                    'video' => isset($parts[3]) ? trim($parts[3]) : '',
                    'link' => isset($parts[4]) ? trim($parts[4]) : '',
                );
            }
        }
        if (empty($list)) {
            $one = $this->adItem($pos);
            if ($one) $list[] = $one;
        }
        return $list;
    }

    /** 首页分类卡片：支持两种格式，每行一个
     *  格式1：slug|名称|简介
     *  格式2：名称--简介--[icon](c-color) || /category/slug/ （网页端链接格式）
     */
    private function homeCats()
    {
        $raw = trim((string)$this->opt('luwuapp_home_cats', ''));
        $list = array();
        foreach (preg_split('/\r?\n/', $raw) as $line) {
            $line = trim($line);
            if ($line === '') continue;
            // 格式2（先判）：名称--简介--[icon](c-color) || /category/slug/（含双竖线）
            $right = '';
            $left = $line;
            if (strpos($line, '||') !== false) {
                $segs = preg_split('/\s*\|\|\s*/', $line, 2);
                $left = trim($segs[0]);
                $right = isset($segs[1]) ? trim($segs[1]) : '';
                $slug = '';
                if (preg_match('#/category/([^/]+)#', $right, $m)) {
                    $slug = trim($m[1]);
                }
                if ($slug === '') continue;
                $lp = explode('--', $left, 2);
                $name = trim($lp[0]);
                if ($name === '') continue;
                $desc = isset($lp[1]) ? trim($lp[1]) : '';
                // 提取图标与颜色 shortcode：名称--简介--[icon-xxx](c-color) || /category/slug/
                $icon = '';
                $color = '';
                if (preg_match('#\[icon-([a-z0-9_-]+)\]#', $desc, $im)) { $icon = $im[1]; }
                if (preg_match('#\(c-([a-z0-9]+)\)#', $desc, $cm)) { $color = $cm[1]; }
                $desc = trim(preg_replace('#\[[^\]]*\]#', '', $desc));
                $desc = trim(preg_replace('#\(c-[a-z0-9]+\)#', '', $desc));
                $list[] = array('slug' => $slug, 'name' => $name, 'desc' => $desc, 'icon' => ($icon !== '' ? ('icon-' . $icon) : $this->catIconUrl($name)), 'color' => $color);
                continue;
            }
            // 格式1：slug|名称|简介（单竖线）
            $parts = explode('|', $line);
            if (count($parts) >= 2 && trim($parts[0]) !== '') {
                $list[] = array(
                    'slug' => trim($parts[0]),
                    'name' => trim($parts[1]),
                    'desc' => isset($parts[2]) ? trim($parts[2]) : '',
                );
                continue;
            }
        }
        return $list;
    }

    /** App 更新信息 */
    private function api_update()
    {
        // 历史更新日志：每行 版本号|日期|更新内容（未配置时用内置默认）
        $defaultLog = "v5.3|2026-09-11|修复更换头像闪退；首页分类卡片支持后台自定义；评论新增回复/表情/图片；商务合作内容后台可配置；顶部按钮改透明背景；详情页正文下方新增评论区预览\nv5.2|2026-09-10|新增主题色设置（全局顶栏/底部导航跟随）；新增意见反馈功能；头像支持 App 内上传更换；新增表情/图片评论；商务合作改原生页面；网盘下载样式优化\nv5.1|2026-09-10|优化广告图片显示；插件设置页移动端适配；首页新增分类卡片控件；支持多广告与广告总开关\nv5.0|2026-09-09|搜索新增标题/内容切换；列表新增排序；发帖新增短代码工具栏；底部导航改五宫格；点赞支持取消";
        $logList = array();
        foreach (preg_split('/\r?\n/', $this->opt('luwuapp_changelog', $defaultLog)) as $line) {
            $line = trim($line);
            if ($line === '') continue;
            $parts = explode('|', $line, 3);
            if (count($parts) < 3) continue;
            $logList[] = array(
                'ver' => trim($parts[0]),
                'date' => trim($parts[1]),
                'content' => trim($parts[2]),
            );
        }
        $this->json(array(
            'ok' => true,
            'enabled' => (bool)$this->opt('luwuapp_update_enabled', '0'),
            'versionName' => $this->opt('luwuapp_update_version_name', ''),
            'versionCode' => (int)$this->opt('luwuapp_update_version_code', '0'),
            'url' => $this->opt('luwuapp_update_url', ''),
            'changelog' => $this->opt('luwuapp_update_changelog', ''),
            'force' => (bool)$this->opt('luwuapp_update_force', '0'),
            'changelogList' => $logList,
        ));
    }

    /** 登录验证 */
    private function api_login()
    {
        $name = trim($this->request->get('username', ''));
        $password = $this->request->get('password', '');
        if ($name === '' || $password === '') {
            $this->json(array('ok' => false, 'error' => '请输入账号和密码'));
            return;
        }
        $user = $this->db->fetchRow(
            $this->db->select('uid', 'name', 'mail', 'screenName', 'password', 'avatar')
                ->from('table.users')
                ->where('name = ? OR mail = ?', $name, $name)
        );
        if (!$user) {
            $this->json(array('ok' => false, 'error' => '账号不存在'));
            return;
        }
        $hasher = new PasswordHash(8, true);
        if (!$hasher->CheckPassword($password, $user['password'])) {
            $this->json(array('ok' => false, 'error' => '密码错误'));
            return;
        }
        $result = array(
            'ok' => true,
            'uid' => $user['uid'],
            'name' => $user['screenName'] ? $user['screenName'] : $user['name'],
            'avatar' => $this->userAvatar($user),
            'token' => $this->issueToken((int)$user['uid']),
        );
        $this->json($result);
    }

    /** 发布文章（App 发帖） */
    private function api_publish()
    {
        $user = $this->authUser();
        if (!$user) {
            return;
        }
        // 发帖频控：同一用户 60 秒内仅允许发布一篇
        $lastPublish = (int)$this->readOption('luwu_publish_last_' . (int)$user['uid'], 0);
        if ($lastPublish > 0 && (time() - $lastPublish) < 60) {
            $this->json(array('ok' => false, 'error' => '发布太频繁，请稍后再试'));
            return;
        }
        $this->setOption('luwu_publish_last_' . (int)$user['uid'], (string)time());
        $title = trim($this->request->get('title', ''));
        $content = trim($this->request->get('content', ''));
        $categorySlug = trim($this->request->get('category', ''));
        $tags = trim($this->request->get('tags', ''));
        if ($title === '') {
            $this->json(array('ok' => false, 'error' => '标题不能为空'));
            return;
        }
        if ($content === '') {
            $this->json(array('ok' => false, 'error' => '内容不能为空'));
            return;
        }

        $time = time();
        $db = $this->db;
        $slug = Common::slugName($title) . '-' . substr(md5(uniqid()), 0, 4);
        $insert = $db->insert('table.contents')->rows(array(
            'title' => $title,
            'slug' => $slug,
            'created' => $time,
            'modified' => $time,
            'text' => $content,
            'password' => '',
            'type' => 'post',
            'status' => 'publish',
            'commentsNum' => 0,
            'allowComment' => 1,
            'allowPing' => 1,
            'allowFeed' => 1,
            'parent' => 0,
            'authorId' => $user['uid'],
            'template' => NULL,
        ));
        $cid = $db->query($insert);
        if (!$cid) {
            $this->json(array('ok' => false, 'error' => '发布失败，请稍后再试'));
            return;
        }

        // 关联分类（默认取第一个分类）
        $mid = 0;
        if ($categorySlug !== '') {
            $meta = $db->fetchRow(
                $db->select('mid')->from('table.metas')
                    ->where('type = ? AND slug = ?', 'category', $categorySlug)
            );
            if ($meta) {
                $mid = (int)$meta['mid'];
            }
        }
        if ($mid === 0) {
            $first = $db->fetchRow(
                $db->select('mid')->from('table.metas')
                    ->where('type = ?', 'category')->order('order', Db::SORT_ASC)->limit(1)
            );
            if ($first) {
                $mid = (int)$first['mid'];
            }
        }
        if ($mid > 0) {
            $db->query($db->insert('table.relationships')->rows(array('cid' => $cid, 'mid' => $mid)));
            $curMeta = $db->fetchRow($db->select('count')->from('table.metas')->where('mid = ?', $mid));
            $db->query($db->update('table.metas')->rows(array('count' => ((int)$curMeta['count']) + 1))->where('mid = ?', $mid));
        }

        // 标签
        $tagList = array_filter(array_map('trim', explode(',', $tags)));
        foreach ($tagList as $tag) {
            $tagMeta = $db->fetchRow(
                $db->select('mid')->from('table.metas')->where('type = ? AND name = ?', 'tag', $tag)
            );
            if ($tagMeta) {
                $tid = (int)$tagMeta['mid'];
            } else {
                $tid = $db->query($db->insert('table.metas')->rows(array(
                    'name' => $tag,
                    'slug' => Common::slugName($tag),
                    'type' => 'tag',
                    'description' => '',
                    'count' => 1,
                    'order' => 0,
                )));
            }
            $db->query($db->insert('table.relationships')->rows(array('cid' => $cid, 'mid' => $tid)));
        }

        $this->json(array('ok' => true, 'cid' => $cid, 'message' => '发布成功'));
    }

    /* ================= 内部工具 ================= */

    /** 读取插件配置项 */
    private function opt($name, $default = '')
    {
        if (array_key_exists($name, $this->settings)) {
            return $this->settings[$name] === '' ? $default : $this->settings[$name];
        }
        return $default;
    }

    /** 直接读 options 表（用于运行时状态，如关注列表） */
    private function readOption($name, $default = '')
    {
        $row = $this->db->fetchRow(
            $this->db->select('value')->from('table.options')->where('name = ?', $name)
        );
        return $row ? $row['value'] : $default;
    }

    /** 写插件自有配置（options 表） */
    private function setOption($name, $value)
    {
        $row = $this->db->fetchRow(
            $this->db->select()->from('table.options')->where('name = ?', $name)
        );
        if ($row) {
            $this->db->query(
                $this->db->update('table.options')->rows(array('value' => $value))->where('name = ?', $name)
            );
        } else {
            $this->db->query(
                $this->db->insert('table.options')->rows(array('name' => $name, 'value' => $value, 'user' => 0))
            );
        }
    }

    private function loadSettings()
    {
        // Typecho 1.2 标准：后台「设置」保存的配置都在 options 表 plugin:LuwuApp 单行（serialize 数组）
        $row = $this->db->fetchRow(
            $this->db->select('value')->from('table.options')->where('name = ?', 'plugin:LuwuApp')
        );
        $this->settings = array();
        if ($row && !empty($row['value'])) {
            $decoded = @unserialize($row['value']);
            if (is_array($decoded)) {
                $this->settings = $decoded;
            }
        }
        // 兜底：老版本扁平行（luwuapp_* 单行键）兼容读取
        if (empty($this->settings)) {
            $names = array(
                'luwuapp_announce_enabled', 'luwuapp_announce_title', 'luwuapp_announce_content', 'luwuapp_announce_link',
                'luwuapp_ad_home_text', 'luwuapp_ad_home_link', 'luwuapp_ad_home_img',
                'luwuapp_ad_home_type', 'luwuapp_ad_home_video',
                'luwuapp_ad_feed_text', 'luwuapp_ad_feed_link', 'luwuapp_ad_feed_every',
                'luwuapp_ad_feed_type', 'luwuapp_ad_feed_img', 'luwuapp_ad_feed_video',
                'luwuapp_ad_category_text', 'luwuapp_ad_category_link',
                'luwuapp_ad_category_type', 'luwuapp_ad_category_img', 'luwuapp_ad_category_video',
                'luwuapp_ad_article_text', 'luwuapp_ad_article_link',
                'luwuapp_ad_article_type', 'luwuapp_ad_article_img', 'luwuapp_ad_article_video',
                'luwuapp_update_enabled', 'luwuapp_update_version_name', 'luwuapp_update_version_code',
                'luwuapp_update_url', 'luwuapp_update_changelog', 'luwuapp_update_force',
            );
            foreach ($names as $n) {
                $r = $this->db->fetchRow(
                    $this->db->select('value')->from('table.options')->where('name = ?', $n)
                );
                $this->settings[$n] = $r ? $r['value'] : '';
            }
        }
    }

    /** 输出 JSON 并结束 */
    private function json($data)
    {
        echo json_encode($data, JSON_UNESCAPED_UNICODE);
        exit;
    }

    /** 广告项（文字 / 图片 / 视频） */
    private function adItem($pos)
    {
        $prefix = 'luwuapp_ad_' . $pos;
        return array(
            'text' => $this->opt($prefix . '_text', ''),
            'link' => $this->opt($prefix . '_link', ''),
            'type' => $this->opt($prefix . '_type', 'text'),
            'img' => $this->opt($prefix . '_img', ''),
            'video' => $this->opt($prefix . '_video', ''),
        );
    }

    /** 文章详情链接（绝对地址，App 可直接打开） */
    private function postLink($row)
    {
        $options = Widget::widget('Widget_Options');
        $site = rtrim($options->siteUrl, '/');
        try {
            $routeExists = Router::get('post');
            if ($routeExists) {
                return Common::url(Router::url('post', $row), $site);
            }
        } catch (\Throwable $e) {
            // 忽略，走默认拼接
        }
        return $site . '/archives/' . $row['cid'] . '.html';
    }

    /** 某分类下的文章 cid 列表（按发布时间倒序） */
    private function postIdsInMeta($mid)
    {
        $rows = $this->db->fetchAll(
            $this->db->select('table.contents.cid')
                ->from('table.contents')
                ->join('table.relationships', 'table.contents.cid = table.relationships.cid', Db::INNER_JOIN)
                ->where('table.contents.type = ? AND table.contents.status = ?', 'post', 'publish')
                ->where('table.relationships.mid = ?', $mid)
                ->order('table.contents.created', Db::SORT_DESC)
        );
        $ids = array();
        foreach ($rows as $r) {
            $ids[] = (int)$r['cid'];
        }
        return $ids;
    }

    /** 按 cid 批量查文章并保持给定顺序 */
    private function queryPostsByIds($cids)
    {
        if (!$cids) {
            return array();
        }
        $rows = $this->db->fetchAll(
            $this->db->select()->from('table.contents')
                ->where('cid IN ?', $cids)
                ->where('type = ? AND status = ?', 'post', 'publish')
        );
        $map = array();
        foreach ($rows as $r) {
            $map[$r['cid']] = $r;
        }
        $ordered = array();
        foreach ($cids as $cid) {
            if (isset($map[$cid])) {
                $ordered[] = $map[$cid];
            }
        }
        return $this->decoratePosts($ordered);
    }

    /** 通用文章查询（按时间倒序） */
    private function queryPosts($categorySlug, $page, $pageSize, $sort = 'latest')
    {
        if ($sort !== 'latest') {
            if ($categorySlug !== null) {
                $meta = $this->db->fetchRow(
                    $this->db->select('mid')->from('table.metas')
                        ->where('type = ? AND slug = ?', 'category', $categorySlug)
                );
                $ids = $meta ? $this->postIdsInMeta((int)$meta['mid']) : array();
                $pageIds = $this->sortCids($ids, $sort, $page, $pageSize);
                return $this->queryPostsByIds($pageIds);
            }
            $prefix = $this->db->getPrefix();
            $offset = ($page - 1) * $pageSize;
            $likesExpr = "(SELECT COALESCE(f.int_value, 0) FROM {$prefix}fields f WHERE f.cid = c.cid AND f.name = 'luwu_likes')";
            $commentsExpr = "(SELECT COUNT(*) FROM {$prefix}comments cm WHERE cm.cid = c.cid AND cm.status = 'approved')";
            if ($sort === 'hot') {
                $order = "({$likesExpr} * 2 + {$commentsExpr}) DESC, c.created DESC";
            } else { // likes
                $order = "{$likesExpr} DESC, c.created DESC";
            }
            $sql = "SELECT c.cid FROM {$prefix}contents c
                    WHERE c.type = 'post' AND c.status = 'publish'
                    ORDER BY {$order}
                    LIMIT {$offset}, {$pageSize}";
            $rows = $this->db->fetchAll($sql);
            return $this->queryPostsByIds(array_map(function ($r) { return (int)$r['cid']; }, $rows));
        }
        if ($categorySlug !== null) {
            $meta = $this->db->fetchRow(
                $this->db->select('mid')->from('table.metas')
                    ->where('type = ? AND slug = ?', 'category', $categorySlug)
            );
            $ids = $meta ? $this->postIdsInMeta((int)$meta['mid']) : array();
            $pageIds = array_slice($ids, ($page - 1) * $pageSize, $pageSize);
            return $this->queryPostsByIds($pageIds);
        }
        $rows = $this->db->fetchAll(
            $this->db->select()->from('table.contents')
                ->where('type = ? AND status = ?', 'post', 'publish')
                ->order('created', Db::SORT_DESC)
                ->page($page, $pageSize)
        );
        return $this->decoratePosts($rows);
    }

    /** 对已取出的 cid 列表按热度/点赞排序后分页 */
    private function sortCids($cids, $sort, $page, $pageSize)
    {
        if (empty($cids)) return array();
        $prefix = $this->db->getPrefix();
        $ids = implode(',', array_map('intval', $cids));
        $likesExpr = "(SELECT COALESCE(f.int_value, 0) FROM {$prefix}fields f WHERE f.cid = c.cid AND f.name = 'luwu_likes')";
        $commentsExpr = "(SELECT COUNT(*) FROM {$prefix}comments cm WHERE cm.cid = c.cid AND cm.status = 'approved')";
        if ($sort === 'hot') {
            $order = "({$likesExpr} * 2 + {$commentsExpr}) DESC, c.created DESC";
        } elseif ($sort === 'comments') {
            $order = "{$commentsExpr} DESC, c.created DESC";
        } else {
            $order = "{$likesExpr} DESC, c.created DESC";
        }
        $sql = "SELECT c.cid FROM {$prefix}contents c WHERE c.cid IN ({$ids}) ORDER BY {$order}";
        $rows = $this->db->fetchAll($sql);
        $sorted = array_map(function ($r) { return (int)$r['cid']; }, $rows);
        return array_slice($sorted, ($page - 1) * $pageSize, $pageSize);
    }

    /** 文章总数（可按分类） */
    private function countPosts($categorySlug)
    {
        if ($categorySlug !== null) {
            $meta = $this->db->fetchRow(
                $this->db->select('mid')->from('table.metas')
                    ->where('type = ? AND slug = ?', 'category', $categorySlug)
            );
            return $meta ? (int)$meta['count'] : 0;
        }
        $obj = $this->db->fetchObject(
            $this->db->select(array('COUNT(cid)' => 'num'))
                ->from('table.contents')
                ->where('type = ? AND status = ?', 'post', 'publish')
        );
        return (int)$obj->num;
    }

    /** 批量补齐文章的分类名 */
    /** 生成摘要：去短代码/HTML/Markdown 符号/免责模板句后截取 */
    private function makeExcerpt($text)
    {
        $t = (string)$text;
        $t = preg_replace('/\{[^}]*\}/', ' ', $t);          // Joe 短代码
        $t = preg_replace('/```.*?```/s', ' ', $t);           // 代码块
        $t = preg_replace('/<[^>]+>/', ' ', $t);              // HTML 标签
        $t = preg_replace('/!\[[^\]]*\]\([^)]*\)/', ' ', $t); // 图片
        $t = preg_replace('/\[([^\]]*)\]\([^)]*\)/', '$1', $t); // 链接 → 文字
        $t = preg_replace('/^#{1,6}\s*/m', '', $t);         // 标题符号（允许 ## 后无空格）
        $t = preg_replace('/#{2,}/', ' ', $t);                 // 行内残留的 ## 标题标记
        $t = preg_replace('/^>\s+/m', '', $t);               // 引用
        $t = preg_replace('/^\s*[-*+]\s+/m', '', $t);       // 无序列表
        $t = preg_replace('/[*_`]/', '', $t);                 // 粗斜体/行内代码
        // 去掉"热搜指数：xxxx" 模板前缀
        $t = preg_replace('/热搜指数[：:]\s*\d+/u', ' ', $t);
        // 去掉免责模板句（"本文…来源于互联网/仅供学习交流/24小时内删除"）
        $t = preg_replace('/[^。；;\n]*?(?:本文(?:内容|资源)来源[^。；;\n]*|仅供学习交流[^。；;\n]*|请于\s*24小时内删除)[^。；;\n]*[。；;]?/u', ' ', $t);
        $t = trim(preg_replace('/\s+/u', ' ', $t));
        if (function_exists('mb_substr')) {
            $t = mb_substr($t, 0, 72, 'utf-8');
        } else {
            $t = substr($t, 0, 72);
        }
        return trim($t);
    }

    /** 提取文章首图（Markdown 图片 / HTML img / [img] 标签） */
    private function extractThumb($text)
    {
        if (preg_match('/!\[[^\]]*\]\(([^)\s]+)\)/', (string)$text, $m)) {
            return $m[1];
        }
        if (preg_match('/<img[^>]+src=["\']([^"\']+)["\']/i', (string)$text, $m)) {
            return $m[1];
        }
        if (preg_match('/\[img\]([^\]]+)\[\/img\]/i', (string)$text, $m)) {
            return $m[1];
        }
        return '';
    }

    private function decoratePosts($rows)
    {
        if (!$rows) {
            return array();
        }
        $cids = array();
        foreach ($rows as $r) {
            $cids[] = (int)$r['cid'];
        }
        // 一次性取分类关系
        $catMap = array();
        $rels = $this->db->fetchAll(
            $this->db->select('table.relationships.cid', 'table.metas.name')
                ->from('table.relationships')
                ->join('table.metas', 'table.relationships.mid = table.metas.mid', Db::LEFT_JOIN)
                ->where('table.relationships.cid IN ?', $cids)
                ->where('table.metas.type = ?', 'category')
        );
        foreach ($rels as $rel) {
            $catMap[(int)$rel['cid']] = $rel['name'];
        }

        $items = array();
        foreach ($rows as $r) {
            $items[] = array(
                'id' => (int)$r['cid'],
                'title' => $r['title'],
                'slug' => $r['slug'],
                'date' => date('Y-m-d', (int)$r['created']),
                'excerpt' => $this->makeExcerpt($r['text']),
                'category' => isset($catMap[(int)$r['cid']]) ? $catMap[(int)$r['cid']] : '',
                'thumb' => $this->extractThumb($r['text']),
                'link' => $this->postLink($r),
                'likes' => $this->postLikes((int)$r['cid']),
                'commentsNum' => $this->postCommentsNum((int)$r['cid']),
                'authorId' => (int)$r['authorId'],
            );
        }
        return $items;
    }

    /** 文章的标签 */
    private function tagsOfPost($cid)
    {
        $rows = $this->db->fetchAll(
            $this->db->select('table.metas.name')
                ->from('table.relationships')
                ->join('table.metas', 'table.relationships.mid = table.metas.mid', Db::LEFT_JOIN)
                ->where('table.relationships.cid = ? AND table.metas.type = ?', $cid, 'tag')
        );
        $tags = array();
        foreach ($rows as $r) {
            $tags[] = $r['name'];
        }
        return $tags;
    }

    /** 校验登录态：优先 token，兼容旧版账号+密码，失败直接输出并结束 */
    private function authUser()
    {
        $token = trim((string)$this->request->get('token', ''));
        if ($token !== '') {
            $user = $this->userByToken($token);
            if ($user) {
                return $user;
            }
            $this->json(array('ok' => false, 'error' => '登录已过期，请重新登录'));
            return null;
        }
        $name = trim($this->request->get('username', ''));
        $password = $this->request->get('password', '');
        if ($name === '' || $password === '') {
            $this->json(array('ok' => false, 'error' => '请先登录（填写账号和密码）'));
            return null;
        }
        $user = $this->db->fetchRow(
            $this->db->select('uid', 'name', 'screenName', 'password')
                ->from('table.users')
                ->where('name = ? OR mail = ?', $name, $name)
        );
        if (!$user) {
            $this->json(array('ok' => false, 'error' => '账号不存在'));
            return null;
        }
        $hasher = new PasswordHash(8, true);
        if (!$hasher->CheckPassword($password, $user['password'])) {
            $this->json(array('ok' => false, 'error' => '密码错误'));
            return null;
        }
        return $user;
    }

    /** 生成登录 token（64 位随机串），30 天有效，同一账号仅保留最新 token */
    private function issueToken($uid)
    {
        $this->ensureTokenTable();
        $token = bin2hex(random_bytes(32));
        $this->db->query($this->db->delete('table.luwu_tokens')->where('uid = ?', $uid));
        $this->db->query($this->db->insert('table.luwu_tokens')->rows(array(
            'uid' => $uid, 'token' => $token, 'created' => time(),
        )));
        return $token;
    }

    /** 按 token 取用户，不存在或过期返回 null */
    private function userByToken($token)
    {
        $row = $this->db->fetchRow(
            $this->db->select('uid', 'created')->from('table.luwu_tokens')->where('token = ?', $token)
        );
        if (!$row) {
            return null;
        }
        if (time() - (int)$row['created'] > 2592000) { // 30 天有效
            $this->db->query($this->db->delete('table.luwu_tokens')->where('uid = ?', (int)$row['uid']));
            return null;
        }
        $user = $this->db->fetchRow(
            $this->db->select('uid', 'name', 'screenName', 'password')
                ->from('table.users')->where('uid = ?', (int)$row['uid'])
        );
        return $user ? $user : null;
    }

    /** 确保 token 表存在（幂等，未激活插件时也能自动建表） */
    private function ensureTokenTable()
    {
        $prefix = $this->db->getPrefix();
        $this->db->query("CREATE TABLE IF NOT EXISTS {$prefix}luwu_tokens (
            uid INT NOT NULL,
            token CHAR(64) NOT NULL,
            created INT NOT NULL,
            PRIMARY KEY (uid),
            KEY idx_token (token)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    /** App 主题配置：未登录返回站点默认主题，登录返回用户个人偏好 */
    private function api_theme_config()
    {
        $default = $this->opt('luwuapp_theme_default', 'system'); // light / dark / system
        $uid = 0;
        $userTheme = '';
        $token = trim((string)$this->request->get('token', ''));
        if ($token !== '') {
            $u = $this->userByToken($token);
            if ($u) {
                $uid = (int)$u['uid'];
                $userTheme = $this->readOption('luwu_theme_' . $uid, '');
            }
        }
        $this->json(array(
            'ok' => true,
            'default' => $default,
            'userTheme' => $userTheme !== '' ? $userTheme : $default,
            'uid' => $uid,
        ));
    }

    /** 保存用户主题偏好（需登录） */
    private function api_save_user_theme()
    {
        $user = $this->authUser();
        if (!$user) {
            return;
        }
        $mode = trim((string)$this->request->get('mode', ''));
        if (!in_array($mode, array('light', 'dark', 'system'), true)) {
            $this->json(array('ok' => false, 'error' => '无效的主题模式'));
            return;
        }
        $this->setOption('luwu_theme_' . (int)$user['uid'], $mode);
        $this->json(array('ok' => true));
    }

    /** 开屏广告配置（匿名可访问，App 启动拉取） */
    private function api_splash_ad()
    {
        $this->json(array(
            'ok' => true,
            'enable' => (bool)$this->opt('luwuapp_splash_enable', '0'),
            'image_url' => $this->opt('luwuapp_splash_image', ''),
            'target_url' => $this->opt('luwuapp_splash_target', ''),
            'duration' => max(1, min(10, (int)$this->opt('luwuapp_splash_duration', '3'))),
            'skip_text' => $this->opt('luwuapp_splash_skip_text', '跳过'),
            'once_per_day' => (bool)$this->opt('luwuapp_splash_once', '0'),
        ));
    }

    /** 广告事件埋点：show / click / skip */
    private function api_ad_log()
    {
        $event = trim((string)$this->request->get('event', ''));
        if (!in_array($event, array('show', 'click', 'skip'), true)) {
            $this->json(array('ok' => false, 'error' => '无效事件'));
            return;
        }
        $uid = 0;
        $token = trim((string)$this->request->get('token', ''));
        if ($token !== '') {
            $u = $this->userByToken($token);
            if ($u) {
                $uid = (int)$u['uid'];
            }
        }
        $ip = isset($_SERVER['REMOTE_ADDR']) ? $_SERVER['REMOTE_ADDR'] : '0.0.0.0';
        try {
            $this->db->query($this->db->insert('table.luwu_ad_log')->rows(array(
                'uid' => $uid,
                'event_type' => $event,
                'ip' => $ip,
                'created' => time(),
            )));
        } catch (\Exception $e) {
            // 表不存在时忽略，不阻塞 App
        }
        $this->json(array('ok' => true));
    }

    /** 举报：文章 / 评论 / 用户（登录或匿名均可，IP 频控防刷） */
    private function api_report()
    {
        $type = trim((string)$this->request->get('type', ''));
        $targetId = (int)$this->request->get('target_id', 0);
        $reason = trim((string)$this->request->get('reason', ''));
        if (!in_array($type, array('post', 'comment', 'user'), true) || $targetId <= 0) {
            $this->json(array('ok' => false, 'error' => '举报参数不合法'));
            return;
        }
        if ($reason === '' || mb_strlen($reason, 'utf-8') > 200) {
            $this->json(array('ok' => false, 'error' => '请填写举报理由（200字内）'));
            return;
        }
        $uid = 0;
        $token = trim((string)$this->request->get('token', ''));
        if ($token !== '') {
            $u = $this->userByToken($token);
            if ($u) {
                $uid = (int)$u['uid'];
            }
        }
        $ip = isset($_SERVER['REMOTE_ADDR']) ? $_SERVER['REMOTE_ADDR'] : '0.0.0.0';
        // IP 频控：60 秒内最多 3 条举报（key 控制在 options.name 32 字符内）
        $cntKey = 'luwr_' . date('YmdHi', time()) . '_' . substr(md5($ip), 0, 8);
        $cnt = (int)$this->readOption($cntKey, 0);
        if ($cnt >= 3) {
            $this->json(array('ok' => false, 'error' => '举报太频繁，请稍后再试'));
            return;
        }
        $this->setOption($cntKey, (string)($cnt + 1));
        // 原生 SQL 插入（type 白名单、数字 int 化、字符串 addslashes 转义）
        $ipEsc = addslashes($ip);
        $reasonEsc = addslashes($reason);
        $sql = "INSERT INTO `{$this->db->getPrefix()}luwu_report`
                (`uid`,`type`,`target_id`,`reason`,`status`,`ip`,`created`)
                VALUES ({$uid}, '{$type}', {$targetId}, '{$reasonEsc}', 'pending', '{$ipEsc}', " . time() . ")";
        try {
            $this->db->query($sql);
        } catch (\Exception $e) {
            $this->json(array('ok' => false, 'error' => '举报提交失败'));
            return;
        }
        $this->json(array('ok' => true, 'message' => '举报已提交，我们会尽快处理'));
    }

    /** 获取被屏蔽用户列表（登录用户） */
    private function api_blocks()
    {
        $user = $this->authUser();
        if (!$user) {
            return;
        }
        $raw = $this->readOption('luwu_blocked_' . (int)$user['uid'], '');
        $ids = $raw === '' ? array() : array_values(array_filter(array_map('intval', explode(',', $raw))));
        $this->json(array('ok' => true, 'ids' => $ids));
    }

    /** 屏蔽 / 取消屏蔽用户（登录用户） */
    private function api_block()
    {
        $user = $this->authUser();
        if (!$user) {
            return;
        }
        $targetId = (int)$this->request->get('target_id', 0);
        $act = trim((string)$this->request->get('action', 'block')); // block / unblock
        if ($targetId <= 0 || $targetId === (int)$user['uid']) {
            $this->json(array('ok' => false, 'error' => '参数不合法'));
            return;
        }
        if (!in_array($act, array('block', 'unblock'), true)) {
            $this->json(array('ok' => false, 'error' => '未知操作'));
            return;
        }
        $key = 'luwu_blocked_' . (int)$user['uid'];
        $raw = $this->readOption($key, '');
        $ids = $raw === '' ? array() : array_filter(array_map('intval', explode(',', $raw)));
        if ($act === 'block') {
            if (!in_array($targetId, $ids, true)) {
                $ids[] = $targetId;
            }
        } else {
            $ids = array_values(array_diff($ids, array($targetId)));
        }
        $this->setOption($key, implode(',', array_unique($ids)));
        $this->json(array('ok' => true, 'blocked' => in_array($targetId, $ids, true)));
    }

    /** 退出登录：删除 token */
    private function api_logout()
    {
        $token = trim((string)$this->request->get('token', ''));
        if ($token !== '') {
            $this->db->query($this->db->delete('table.luwu_tokens')->where('token = ?', $token));
        }
        $this->json(array('ok' => true));
    }

    /** 保存付费文章（管理员）：自动提取正文下载区块存入付费表，正文替换为付费卡占位 */
    private function api_paid_save()
    {
        if (!$this->adminAuth()) {
            $this->json(array('ok' => false, 'error' => '无权限'));
            return;
        }
        $cid = (int)$this->request->get('cid', 0);
        $price = max(0.1, (float)$this->request->get('price', 9.9));
        if ($cid <= 0) {
            $this->json(array('ok' => false, 'error' => '请填写文章 ID'));
            return;
        }
        $post = $this->db->fetchRow(
            $this->db->select('cid', 'title', 'text')->from('table.contents')->where('cid = ? AND type = ?', $cid, 'post')
        );
        if (!$post) {
            $this->json(array('ok' => false, 'error' => '文章不存在'));
            return;
        }
        $text = $post['text'];
        // 提取正文下载区块（### 资源下载 标题 + {cloud ...} 或仅 {cloud ...}）
        if (!preg_match('#(###\s*[^\n]*资源[^\n]*\n?\s*)?\{cloud[^}]*\}#u', $text, $m)) {
            $this->json(array('ok' => false, 'error' => '正文未找到 {cloud ...} 网盘下载区块，请先确认文章带下载链接'));
            return;
        }
        $block = $m[0];
        $cloud = trim($block);
        // 存入付费表（幂等）
        $ex = $this->db->fetchRow($this->db->select()->from('table.paid')->where('cid = ?', $cid));
        if ($ex) {
            $this->db->query($this->db->update('table.paid')->rows(array('content' => $cloud, 'price' => $price, 'created' => time()))->where('cid = ?', $cid));
        } else {
            $this->db->query($this->db->insert('table.paid')->rows(array('cid' => $cid, 'content' => $cloud, 'price' => $price, 'created' => time())));
        }
        // 正文替换为付费卡占位（保留标题行）
        $paidTag = '{paid cid="' . $cid . '" price="' . rtrim(rtrim(sprintf('%.2f', $price), '0'), '.') . '" /}';
        $newText = str_replace($block, $paidTag, $text);
        $this->db->query($this->db->update('table.contents')->rows(array('text' => $newText))->where('cid = ?', $cid));
        $this->json(array('ok' => true, 'cid' => $cid, 'msg' => '已设为付费资源，正文下载区块已隐藏'));
    }

    /** App 端付费掩码：付费文章正文下载区块替换为付费卡占位（网页端正文保持原样） */
    private function maskPaidContent($text, $cid)
    {
        $p = $this->db->fetchRow($this->db->select()->from('table.paid')->where('cid = ?', (int)$cid));
        if (!$p) {
            return $text;
        }
        $price = rtrim(rtrim(sprintf('%.2f', (float)$p['price']), '0'), '.');
        $paid = '{paid cid="' . (int)$cid . '" price="' . $price . '" /}';
        // 先替换 {hide}...{/hide} 付费隐藏块（内含下载区块）
        $text = preg_replace('~\{hide\}[\s\S]*?\{/hide\}~u', $paid, $text);
        // 再替换剩余裸 {cloud} 下载区块
        $text = preg_replace('~\{cloud[^}]*\}~u', $paid, $text);
        return $text;
    }

    /** 付费资源信息：返回价格与锁定状态 */
    private function api_paid_info()
    {
        $cid = (int)$this->request->get('cid', 0);
        if ($cid <= 0) {
            $this->json(array('ok' => false, 'error' => '缺少 cid'));
            return;
        }
        $p = $this->db->fetchRow(
            $this->db->select()->from('table.paid')->where('cid = ?', $cid)
        );
        if (!$p) {
            $this->json(array('ok' => false, 'error' => '该文章暂无付费资源'));
            return;
        }
        $this->json(array('ok' => true, 'cid' => $cid, 'price' => $p['price']));
    }

    /** 解锁付费资源：验证解锁码后返回下载区块 */
    private function api_unlock()
    {
        $cid = (int)$this->request->get('cid', 0);
        $code = trim((string)$this->request->get('code', ''));
        if ($cid <= 0 || $code === '') {
            $this->json(array('ok' => false, 'error' => '参数不完整'));
            return;
        }
        $p = $this->db->fetchRow(
            $this->db->select()->from('table.paid')->where('cid = ?', $cid)
        );
        if (!$p) {
            $this->json(array('ok' => false, 'error' => '该文章暂无付费资源'));
            return;
        }
        $c = $this->db->fetchRow(
            $this->db->select()->from('table.paid_codes')->where('cid = ? AND code = ?', $cid, $code)
        );
        if (!$c) {
            $this->json(array('ok' => false, 'error' => '解锁码不正确，请核对后重试'));
            return;
        }
        if ((int)$c['used'] === 1) {
            $this->json(array('ok' => false, 'error' => '该解锁码已被使用'));
            return;
        }
        $uid = 0;
        $token = trim((string)$this->request->get('token', ''));
        if ($token !== '') {
            $u = $this->userByToken($token);
            if ($u) {
                $uid = (int)$u['uid'];
            }
        }
        $this->db->query(
            $this->db->update('table.paid_codes')
                ->rows(array('used' => 1, 'used_uid' => $uid, 'used_at' => time()))
                ->where('id = ?', (int)$c['id'])
        );
        $this->json(array('ok' => true, 'cid' => $cid, 'price' => $p['price'], 'content' => $p['content']));
    }

    /** 支付成功解锁：校验网站订单（orders 表）已支付后返回下载区块（App 直接支付用，订单同步后台） */
    private function api_pay_unlock()
    {
        $cid = (int)$this->request->get('cid', 0);
        $trade_no = trim((string)$this->request->get('trade_no', ''));
        if ($cid <= 0 || $trade_no === '') {
            $this->json(array('ok' => false, 'error' => '参数不完整'));
            return;
        }
        $order = $this->db->fetchRow(
            $this->db->select()->from('table.orders')
                ->where('trade_no = ? AND content_cid = ? AND status = 1', $trade_no, $cid)
        );
        if (!$order) {
            $this->json(array('ok' => false, 'paid' => false, 'error' => '订单未支付或不存在'));
            return;
        }
        // App 端支付成功：把订单绑定到 App 登录用户（我的订单按用户查询）
        $token = trim((string)$this->request->get('token', ''));
        if ($token !== '') {
            $u = $this->userByToken($token);
            if ($u) {
                $this->db->query(
                    $this->db->update('table.orders')
                        ->rows(array('user_id' => (int)$u['uid']))
                        ->where('trade_no = ?', $trade_no)
                );
            }
        }
        $p = $this->db->fetchRow(
            $this->db->select()->from('table.paid')->where('cid = ?', $cid)
        );
        if (!$p) {
            $this->json(array('ok' => false, 'paid' => true, 'error' => '文章暂无付费内容'));
            return;
        }
        $this->json(array('ok' => true, 'paid' => true, 'cid' => $cid, 'price' => $p['price'], 'content' => $p['content']));
    }

    /** 查询网站订单支付状态（App 轮询用：0未支付/1已支付） */
    private function api_pay_status()
    {
        $trade_no = trim((string)$this->request->get('trade_no', ''));
        if ($trade_no === '') {
            $this->json(array('ok' => false, 'error' => '缺少 trade_no'));
            return;
        }
        $order = $this->db->fetchRow(
            $this->db->select()->from('table.orders')->where('trade_no = ?', $trade_no)
        );
        if (!$order) {
            $this->json(array('ok' => false, 'paid' => false, 'error' => '订单不存在'));
            return;
        }
        $this->json(array(
            'ok' => true,
            'paid' => ((int)$order['status'] === 1),
            'status' => (int)$order['status'],
            'cid' => (int)$order['content_cid'],
            'type' => $order['type'],
            'money' => $order['money'],
        ));
    }

    /** 我的订单：当前 App 登录用户的购买记录（标题/订单号/支付方式/金额/时间/状态） */
    private function api_orders()
    {
        $token = trim((string)$this->request->get('token', ''));
        if ($token === '') {
            $this->json(array('ok' => false, 'error' => '请先登录'));
            return;
        }
        $u = $this->userByToken($token);
        if (!$u) {
            $this->json(array('ok' => false, 'error' => '登录状态已失效'));
            return;
        }
        $uid = (int)$u['uid'];
        $rows = $this->db->fetchAll(
            $this->db->select()->from('table.orders')
                ->where('user_id = ?', $uid)
                ->order('id', Db::SORT_DESC)
                ->limit(50)
        );
        $items = array();
        foreach ($rows as $r) {
            $items[] = array(
                'trade_no' => $r['trade_no'],
                'title' => isset($r['content_title']) ? $r['content_title'] : '付费资源',
                'cid' => (int)$r['content_cid'],
                'type' => isset($r['type']) ? $r['type'] : '',
                'money' => isset($r['money']) ? $r['money'] : '0',
                'pay_type' => isset($r['pay_type']) ? $r['pay_type'] : '',
                'status' => (int)$r['status'],
                'time' => isset($r['create_time']) ? $r['create_time'] : '',
            );
        }
        $this->json(array('ok' => true, 'items' => $items));
    }

    /** 生成解锁码（管理员） */
    private function api_paid_gen()
    {
        if (!$this->adminAuth()) {
            $this->json(array('ok' => false, 'error' => '无权限'));
            return;
        }
        $cid = (int)$this->request->get('cid', 0);
        $num = min(50, max(1, (int)$this->request->get('num', 5)));
        $p = $this->db->fetchRow(
            $this->db->select()->from('table.paid')->where('cid = ?', $cid)
        );
        if (!$p) {
            $this->json(array('ok' => false, 'error' => '该文章未配置付费资源，请先设置付费内容'));
            return;
        }
        $codes = array();
        for ($i = 0; $i < $num; $i++) {
            $code = strtoupper(substr(md5(uniqid(mt_rand(), true)), 0, 10));
            $this->db->query(
                $this->db->insert('table.paid_codes')->rows(array(
                    'cid' => $cid, 'code' => $code, 'created' => time(),
                ))
            );
            $codes[] = $code;
        }
        $this->json(array('ok' => true, 'cid' => $cid, 'num' => $num, 'codes' => $codes));
    }

    /** 解锁码列表（管理员） */
    private function api_paid_list()
    {
        if (!$this->adminAuth()) {
            $this->json(array('ok' => false, 'error' => '无权限'));
            return;
        }
        $rows = $this->db->fetchAll(
            $this->db->select()->from('table.paid_codes')
                ->order('id', Db::SORT_DESC)
                ->limit(100)
        );
        $items = array();
        foreach ($rows as $r) {
            $items[] = array(
                'id' => (int)$r['id'],
                'cid' => (int)$r['cid'],
                'title' => '',
                'code' => $r['code'],
                'used' => (int)$r['used'],
                'used_uid' => (int)$r['used_uid'],
                'used_at' => (int)$r['used_at'],
                'created' => (int)$r['created'],
            );
        }
        $this->json(array('ok' => true, 'items' => $items));
    }
}
