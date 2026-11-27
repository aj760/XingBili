// CCTV 画质切换配置
_data = {
    // 发送画质列表消息（参考 mgtv/bili 格式）
    sendQualities(options) {
        try {
            const qualities = this.fetchQualities(options || {});
            if (qualities && qualities.length > 0) {
                this.postQualities(qualities);
            }
        } catch (e) {
            console.error("Send qualities error", e);
        }
    },

    postQualities(qualities) {
        _apiX.postMessage({
            type: 'qualities',
            data: { qualities }
        });
    },

    // 获取画质列表
    fetchQualities(options) {
        options = options || {};
        let qualities = [];
        let current = $$("#player_resolution_show_player").attr("activeresolution");
        let newIndex = 0;

        $$("#player_resolution_bar_player").find("[itemvalue]").each(function(i, item) {
            let hzLevel = $$(item).attr("itemvalue");
            let hzName = $$(item).text().trim().replace(/\s*/g, "");
           // let id = newIndex.toString();
            // 设置可点击元素的 ID
            let  id  = $$(item).attr("id");
            qualities.push({
                id: id,
                name: hzName,
                isVip: false,                    // CCTV 无 VIP 限制
                isCurrent: hzLevel === current,
                action: `_data.hzChoose("${id}","${hzName}")`,
                level: _tvFunc.hzLevel(hzName, 2)
            });
            newIndex++;
        });

        // 自动切换到用户之前选择的画质
        let chooseHzId = localStorage.getItem("chooseHz");
        if (options.autoRestore !== false && chooseHzId) {
            let currentHz = qualities.find(q => q.isCurrent);
            if (currentHz && currentHz.id !== chooseHzId) {
                this.hzChoose(chooseHzId, localStorage.getItem("chooseHzName"), { notify: false });
                this.scheduleQualityRefresh();
            }
        }
        if (chooseHzId && qualities.some(q => q.id === chooseHzId)) {
            this.markCurrentQuality(qualities, chooseHzId);
        }

        return qualities;
    },

    // 切换画质
    hzChoose(id, name, options) {
        options = options || {};
        $$("#" + id).click();
        localStorage.setItem("chooseHz", id);
        localStorage.setItem("chooseHzName", name);
        _apiX.toast("画质切换到 " + name);

        if (options.notify !== false) {
            let qualities = this.fetchQualities({ autoRestore: false });
            this.markCurrentQuality(qualities, id);
            this.postQualities(qualities);
            this.scheduleQualityRefresh();
        }
    },

    markCurrentQuality(qualities, id) {
        qualities.forEach(q => {
            q.isCurrent = q.id === id;
        });
    },

    scheduleQualityRefresh() {
        clearTimeout(this.qualityRefreshTimer);
        // CCTV 切换画质后 DOM 的 activeresolution 会稍晚更新，延迟同步一次真实状态。
        this.qualityRefreshTimer = setTimeout(() => {
            this.sendQualities({ autoRestore: false });
        }, 800);
    }
};
// 使用通用初始化函数
initVideoPage({
    autoSendState: true,           // CCTV 需要定时发送视频状态
    handleUjs: false,              // 不需要 ujs 处理
    handleULink: false,            // 不需要 u-link 处理
    onVideoReady: function(video) {
        _data.sendQualities();     // 视频就绪后发送画质列表
    }
});

// CCTV 页面播放器容器比 video 更早出现，Gecko 内无法依赖系统全屏事件。
// 这里保留通用视频初始化，同时对 #player 做应用内铺满兜底。
(function(){
    _tvFunc.fixedW("body");
    _tvFunc.check(function (){return null!=document.getElementById("player");},function (){
        console.log("[CCTV] apply #player fullscreen fallback");
        _tvFunc.fullscreenW("#player");
        _data.sendQualities();
        $$(".floatNav").hide();
        $$(".nav_wrapper_bg").hide();
        $$(".header_nav").hide();
        $$(".playingVideo").css("width","100%");

    });
    _tvFunc.volume100();
})();
