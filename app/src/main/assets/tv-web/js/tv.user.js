// ========== 防重复注入检测 ==========
if (window.__MARMOT_SCRIPT_INJECTED__) {
    console.log('[Marmot] 脚本已注入，跳过重复执行');
} else {
    window.__MARMOT_SCRIPT_INJECTED__ = true;
    console.log('[Marmot] 脚本首次注入，开始初始化');
// ========== 原有脚本内容 ==========
console.log("helloWorld");

// 从 URL 参数获取值
function getUrlParam(name) {
    const url = new URL(window.location.href);
    return url.searchParams.get(name);
}

// 挂到 window 上，确保外部脚本能访问
window._tvState={
    platform:"//platform//"
};

function extractDomain(url) {
    const match = url.match(/^(https?:\/\/[^/?#]+)/i);
    return match ? match[1] : null;
}
const _tvLoadRes={
    js(scrJs){
        let script = document.createElement('script');
        script.setAttribute('type', 'text/javascript');
        //charset="utf-8"
        script.setAttribute('charset','utf-8');
        script.src = scrJs;
        script.async = false;
        document.body.appendChild(script);
    },
    css(scrCss){
        let script = document.createElement('link');
        script.setAttribute('rel', 'stylesheet');
        script.setAttribute('type', 'text/css');
        script.href = scrCss;
        script.async = false;
        document.head.appendChild(script);
    },
    jsBottom(scrJs){
        let script = document.createElement('script');
        script.setAttribute('type', 'text/javascript');
        script.setAttribute('charset','utf-8');
        script.src = scrJs;
        script.async = false;
        document.head.appendChild(script);
    }
};
function loadDetailByUrl(url){
    //tv
    if(url.startsWith("https://tv.cctv.com/live")){
        return "tv/cctv";
    }
     //各大tv
    if(url.startsWith("https://www.yangshipin.cn")){
        return "tv/ysptv"
    }
    if(url.startsWith("https://live.jstv.com")){
        return "tv/jstv"
    }
    if(url.startsWith("https://www.btime.com")){
        return "tv/bjtv"
    }
    if(url.startsWith("https://www.jlntv.cn/")){
        return "tv/jltv"
    }
    if(url.startsWith("https://www.lcxw.cn/")){
        _tvLoadRes.js("https://cdn.bootcdn.net/ajax/libs/hls.js/1.5.13/hls.js");
        return "tv/lctv"
    }
    if(url.startsWith("https://www.nmtv.cn")){
        return "tv/nmtv"
    }
    if(url.startsWith("https://www.mgtv.com/live")){
        return "tv/hntv"
    }
    if(url.startsWith("https://web.guangdianyun.tv")){
        return "tv/gdytv"
    }
    if(url.startsWith("https://www.fengshows.com/")){
        return "tv/fengshows"
    }
    return "tv/common";
}
(function(){
    console.log("_tvState",_tvState);
    let domain =  "marmot://res.vonchange.com";
    if(_tvState.platform==="android"){
       domain=extractDomain(window.location.href);
    }
    //https://www.tcsrm.cn/tvradio/tczhpd.html
    _tvLoadRes.js(domain+"/tv-web/js/zepto.min.js");
    _tvLoadRes.js(domain+"/tv-web/js/common.js");
    let detailPath=loadDetailByUrl(window.location.href);
    _tvLoadRes.js(domain+`/tv-web/js/${detailPath}/detail.js`);
})();

}
