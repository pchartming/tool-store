package com.toolstore.installer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Artwork used for application identification. Everything is bundled as a
 * local resource: the store never loads images from the network and the
 * installer keeps working offline. Installation facts stay in Catalog/UiData.
 * StudyOnTV uses a generic placeholder because its license restricts use of
 * its name, logo and branding for promotion.
 */
final class MediaCatalog {

    static final class Shot {
        final int image;
        final String caption;

        Shot(int image, String caption) {
            this.image = image;
            this.caption = caption;
        }
    }

    static final class Media {
        final int icon;
        final Shot[] shots;
        final String credit;

        Media(int icon, String credit, Shot... shots) {
            this.icon = icon;
            this.credit = credit;
            this.shots = shots;
        }
    }

    private static final Map<String, Media> MEDIA;

    static {
        Map<String, Media> map = new LinkedHashMap<String, Media>();

        map.put(Catalog.PLAN_YOUR_TV, new Media(
                R.drawable.app_icon_planyourtv,
                "planyourtv.me · 官方素材",
                new Shot(R.drawable.app_shot_planyourtv_1, "儿童主页"),
                new Shot(R.drawable.app_shot_planyourtv_2, "答题解锁"),
                new Shot(R.drawable.app_shot_planyourtv_3, "完成奖励")));

        map.put(Catalog.AERIAL, new Media(
                R.drawable.app_icon_aerial,
                "aerial.qust.me · 官方素材",
                new Shot(R.drawable.app_shot_aerial_1, "tvOS 风格主页"),
                new Shot(R.drawable.app_shot_aerial_2, "聚焦编辑"),
                new Shot(R.drawable.app_shot_aerial_3, "文件与应用")));

        map.put(Catalog.QUIET_TV, new Media(
                R.drawable.app_icon_quiettv,
                "beatsad.github.io/quiet-tv · 官方素材",
                new Shot(R.drawable.app_shot_quiettv_1, "极简主页"),
                new Shot(R.drawable.app_shot_quiettv_2, "应用管理"),
                new Shot(R.drawable.app_shot_quiettv_3, "壁纸设置")));

        map.put(Catalog.HALO_TV, new Media(
                R.drawable.app_icon_halotv,
                "halo-tv.pages.dev · 官方素材",
                new Shot(R.drawable.app_shot_halotv_1, "自定义主页"),
                new Shot(R.drawable.app_shot_halotv_2, "控制中心"),
                new Shot(R.drawable.app_shot_halotv_3, "HaloTV 设置")));

        map.put(Catalog.CLEAN_YOUR_TV, new Media(
                R.drawable.app_icon_cleanyourtv,
                "cleanyour.tv · 官方素材",
                new Shot(R.drawable.app_shot_cleanyourtv_1, "精简首页"),
                new Shot(R.drawable.app_shot_cleanyourtv_2, "应用库"),
                new Shot(R.drawable.app_shot_cleanyourtv_3, "快速切换")));

        // StudyOnTV's Source Available License restricts use of its name, logo
        // and branding for promotion, so no official artwork is bundled.
        map.put(Catalog.STUDY_ON_TV, new Media(
                R.mipmap.ic_launcher,
                "StudyOnTV · 按上游许可证不打包官方图标"));

        MEDIA = java.util.Collections.unmodifiableMap(map);
    }

    private MediaCatalog() {
    }

    static Media forApp(String appId) {
        Media media = MEDIA.get(appId == null ? "" : appId.trim());
        if (media != null) {
            return media;
        }
        return new Media(R.mipmap.ic_launcher, "工具市场 · 通用占位图");
    }

    static int icon(String appId) {
        return forApp(appId).icon;
    }

    static String credit(String appId) {
        return forApp(appId).credit;
    }
}