// 通用 detail 页面
// 使用通用初始化函数
initVideoPage({
    autoSendState: false,          // 不需要定时发送视频状态
    handleUjs: true,               // 处理 ujs 参数
    handleULink: true,             // 处理 u-link=1 跳转
    onVideoReady: function(video) {
        // 通用页面也支持画质切换（如果 _data 已定义）
        if (typeof _data !== 'undefined' && _data.hzList) {
            _data.hzList(video);
        }
    }
});
