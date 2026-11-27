// 央视频 画质切换配置
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
        let currentId = null;

        $$(".bei-list-inner").find(".item").each(function(i, item) {
            let id = "xhz-" + i;
            $$(item).attr("id", id);
            
            let isCurrent = $$(item).hasClass("active");
            let hzName = $$(item).text().trim().replace(/\s*/g, "");
            
            // 跳过 VIP 画质
            if (hzName.includes("VIP")) {
                return;
            }

            qualities.push({
                id: id,
                name: hzName,
                isVip: false,
                isCurrent: isCurrent,
                action: `_data.hzChoose("${id}","${hzName}")`,
                level: _tvFunc.hzLevel(hzName, 2)
            });

            if (isCurrent) {
                currentId = id;
            }
        });

        // 自动切换到用户之前选择的画质
        let chooseHzId = localStorage.getItem("chooseHz");
        if (options.autoRestore !== false && chooseHzId && currentId !== chooseHzId) {
            this.hzChoose(chooseHzId, localStorage.getItem("chooseHzName"), { notify: false });
            this.scheduleQualityRefresh();
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
        // 央视频切换后 active class 更新较慢，上报时优先使用用户选择的画质。
        this.qualityRefreshTimer = setTimeout(() => {
            this.sendQualities({ autoRestore: false });
        }, 800);
    }
};

// 使用通用初始化函数
initVideoPage({
    autoSendState: true,           // 需要定时发送视频状态
    handleUjs: false,              // 不需要 ujs 处理
    handleULink: false,            // 不需要 u-link 处理
    onVideoReady: function(video) {
        _data.sendQualities();     // 视频就绪后发送画质列表
    }
});
