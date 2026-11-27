// IAPP2 直播 - 从 URL 参数获取 orgid 和 id
let queryParams = _tvFunc.getQueryParams();
let orgId = queryParams["orgid"] || "113";  // 默认值 113
let id = queryParams["id"] || "131";        // 默认值 131

console.log("orgId:", orgId, "id:", id);

let playUrl = null;

/**
 * 初始化播放器
 * 请求 API 获取直播流地址并播放
 */
let initPlayer = function() {
    // 构建 API 请求 URL（只带 orgid 参数，不带 id）
    let apiUrl = `https://app.litenews.cn/v1/app/play/tv/live?orgid=${orgId}`;
    console.log("请求 API:", apiUrl);
    
    // 请求 JSON API
    _apiX.getJson(
        apiUrl,
        { 
            "User-Agent": _apiX.userAgent(false)
        },
        function(text) {
            console.log("API 返回数据:", text);
            
            try {
                // 解析 JSON 数据
                let result = JSON.parse(text);
                
                // 检查返回格式
                if (!result || !result.data || !Array.isArray(result.data)) {
                    console.error("API 返回数据格式错误:", result);
                    return;
                }
                
                // 从 data 数组中查找 id 匹配的项
                let targetId = parseInt(id, 10);
                let matchedItem = result.data.find(function(item) {
                    return item.id === targetId;
                });
                
                if (!matchedItem) {
                    console.error("未找到匹配的频道，id:", targetId);
                    return;
                }
                
                // 提取 stream 字段
                playUrl = matchedItem.stream;
                console.log("提取的播放地址:", playUrl);
                
                if (!playUrl || playUrl.trim() === "") {
                    console.error("播放地址为空");
                    return;
                }
                
                // 发送视频 URL 到原生层播放
                console.log("iapp2: sending videoUrl to native player:", playUrl);
                _apiX.msgStr("videoUrl", playUrl);
                
            } catch (e) {
                console.error("iapp2 解析 JSON 数据失败:", e);
            }
        },
        function(error) {
            console.error("API 请求失败:", error);
        }
    );
};

// 初始化播放器
initPlayer();
