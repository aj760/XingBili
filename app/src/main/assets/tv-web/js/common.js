window.$$=Zepto;
const _tvFunc={
    // 获取url请求参数
    getQueryParamsXX() {
      var query = location.search.substring(1)
      var arr = query.split('&')
      var params = {}
      for (var i = 0; i < arr.length; i++) {
          var pair = arr[i].split('=')
         params[pair[0]] = decodeURI(pair[1]);
      }
     console.log(params)
     return params
   },
    getQueryParamsNew() {
    return Object.fromEntries(
        new URLSearchParams(window.location.search)
    );
   },
    getQueryParams() {
    const queryString = window.location.search;
    const params = new URLSearchParams(queryString);

    // 使用兼容性最好的reduce方法
    return Array.from(params).reduce((acc, [key, value]) => {
        acc[key] = value;
        return acc;
    }, {});
   },
    url(url){
      if(url.startsWith("http")){
          return url;
      }
     let index= window.location.href.lastIndexOf("/")
        return window.location.href.substring(0,index+1)+url;
    },
    link(url){
        if(url.startsWith("http")){
            return url;
        }
        if(url.startsWith("//")){
            return "https:"+url;
        }
        return url;
    },
    getVideoQuality(videoElement) {
    // 确保视频元数据已加载
   /* if (videoElement.readyState < 1) {
        console.warn("Video metadata not loaded. Listen for 'loadedmetadata' event.");
        return "未知";
    }*/
    const width = videoElement.videoWidth;
    const height = videoElement.videoHeight;
    const maxDimension = Math.max(width, height);
    // 主流通用分辨率标准判断 [6,7,8](@ref)
    if (maxDimension >= 3840) return "4K";       // 4K标准：3840x2160或更高
    else if (maxDimension >= 1920) return "1080P"; // 全高清：1920x1080
    else if (maxDimension >= 1280) return "720P";  // 高清：1280x720
    else if (maxDimension >= 640) return "480P";   // 标清：640x480（部分场景归为360P）
    else return "360P";                            // 低清：如640x360
   },
   loadCssCode(code) {
      var style = document.createElement('style')
    // style.type = 'text/css'
      style.rel = 'stylesheet'
      style.appendChild(document.createTextNode(code))
      var head = document.getElementsByTagName('head')[0]
      head.appendChild(style);
    },
    fullscreenQQ(id){
        var css=`
   ${id}{
        position: fixed !important;
       z-index: 99990 !important;
       width: 100% !important;
       height: 100% !important;
      top: 0 !important;
      left: 0 !important;
      right:0 !important;
      bottom: 0 !important;
          background-color: rgb(0, 0, 0); 
    }
 `;
        this.loadCssCode(css);
    },
    fullscreen(id) {
        var css = `${id}{
            position: fixed !important;
            z-index: 99990 !important;
            width: 100% !important;
            height: 100% !important;
            top: 0 !important;
            left: 0 !important;
            right:0 !important;
            background-color: rgb(0, 0, 0);
            bottom: 0 !important;}
            video::-webkit-media-controls {display: none !important;}
video::-webkit-media-controls-enclosure {display: none !important;}
video::-webkit-media-controls-panel {display: none !important;}
video::-webkit-media-controls-play-button {display: none !important;}
video::-webkit-media-controls-start-playback-button {display: none !important;}
video::-moz-media-controls {display: none !important;}`;
        this.loadCssCode(css);
    },
    fixedW(id){
        var css=`
   ${id}{
        position: fixed !important;
       }
 `;
        this.loadCssCode(css);
    },
    fullscreenW(id){
        var css=`
   ${id}{
        position: fixed !important;
       z-index: 99990 !important;
       width: 100vw !important;
       height: 100vh !important;
      top: 0 !important;
      left: 0 !important;
      right:0 !important;
      margin-left:0 !important;
          background-color: rgb(0, 0, 0); 
      bottom: 0 !important;
    }
 `;
        this.loadCssCode(css);
    },
    fullscreenWW(id){
        var css=`
   ${id}{
        position: fixed !important;
       z-index: 99990 !important;
       width: 100vw !important;
       height: 100vh !important;
      top: 0 !important;
      left: 0 !important;
      right:0 !important;
         background-color: rgb(0, 0, 0); 
      bottom: 0 !important;
    }
 `;
        this.loadCssCode(css);
    },
    toggleFullScreen(elem) {
        if (!elem) {
            console.warn('toggleFullScreen: elem is null');
            return;
        }
        if (!document.fullscreenElement) {
            if (elem.requestFullscreen) {
                elem.requestFullscreen();
            }
        } else {
            if (document.exitFullscreen) {
                document.exitFullscreen();
            }
        }
    },
    _fullScreenKeyBound: false, // 防止重复绑定
    _isWebFullScreen: false, // CSS全屏状态标记
    _webFullscreenSnapshot: null,
    _saveWebFullscreenNode(node) {
        if (!node) {
            return;
        }
        if (!this._webFullscreenSnapshot) {
            this._webFullscreenSnapshot = [];
        }
        if (this._webFullscreenSnapshot.some(item => item.node === node)) {
            return;
        }
        this._webFullscreenSnapshot.push({
            node,
            style: node.getAttribute("style"),
            className: node.getAttribute("class")
        });
    },
    _restoreWebFullscreenSnapshot() {
        if (!this._webFullscreenSnapshot) {
            return;
        }
        this._webFullscreenSnapshot.slice().reverse().forEach(item => {
            if (!item.node || !item.node.isConnected) {
                return;
            }
            if (item.style === null || typeof item.style === "undefined") {
                item.node.removeAttribute("style");
            } else {
                item.node.setAttribute("style", item.style);
            }
            if (item.className === null || typeof item.className === "undefined") {
                item.node.removeAttribute("class");
            } else {
                item.node.setAttribute("class", item.className);
            }
        });
        this._webFullscreenSnapshot = null;
    },
    exitAutoWebFullscreen() {
        let root = this._webFullscreenRoot || document.querySelector(".marmot-web-fullscreen-root");
        document.body.classList.remove("marmot-web-fullscreen-active");
        if (root) {
            root.classList.remove("marmot-web-fullscreen-root");
        }
        this._restoreWebFullscreenSnapshot();
        this._webFullscreenRoot = null;
        this._isWebFullScreen = false;
        try {
            window.dispatchEvent(new Event("resize"));
        } catch (e) {
            console.warn("exitAutoWebFullscreen resize failed", e);
        }
    },
    /**
     * CSS 全屏
     * @param {Element} elem - 目标元素，默认为 video
     * @param {boolean} useViewport - true: 使用 100vw/100vh，false: 使用 100%（默认）
     */
    webFullScreen(elem, useViewport) {
        // 使用 CSS 实现全屏效果，避免触发 fullscreenchange 事件
        let target = elem || this.getVideo();
        if (!target) {
            console.warn('webFullScreen: element not found');
            return;
        }
        
        // 如果是 Zepto/jQuery 对象，获取原生 DOM 元素
        if (target.length !== undefined && target[0]) {
            target = target[0];
        }

        // 默认使用 100%
        let widthValue = useViewport ? '100vw' : '100%';
        let heightValue = useViewport ? '100vh' : '100%';

        if (!this._isWebFullScreen) {
            this.exitAutoWebFullscreen();
            // 进入CSS全屏
            this._saveWebFullscreenNode(target);
            target.style.cssText = `
                position: fixed !important;
                z-index: 99999 !important;
                width: ${widthValue} !important;
                height: ${heightValue} !important;
                top: 0 !important;
                left: 0 !important;
                right: 0 !important;
                bottom: 0 !important;
                object-fit: contain !important;
                background-color: #000 !important;
            `;
            this._isWebFullScreen = true;
            console.log('Entered CSS fullscreen, useViewport=' + !!useViewport);
        } else {
            // 退出CSS全屏
            this.exitAutoWebFullscreen();
            this._isWebFullScreen = false;
            console.log('Exited CSS fullscreen');
        }
    },
    findWebFullscreenRoot(video) {
        if (!video) {
            return null;
        }
        let node = video;
        while (node && node !== document.body) {
            let id = (node.id || "").toLowerCase();
            let className = (node.className || "").toString().toLowerCase();
            if (id === "videoh5"
                || id.indexOf("player") >= 0
                || className.indexOf("xgplayer") >= 0
                || className.indexOf("dplayer") >= 0
                || className.indexOf("video-js") >= 0
                || className.indexOf("player") >= 0) {
                return node;
            }
            node = node.parentElement;
        }
        return video;
    },
    autoWebFullscreen(video) {
        video = video || this.getVideo();
        if (!video) {
            return;
        }
        let root = this.findWebFullscreenRoot(video);
        if (!root) {
            return;
        }
        // 有些站点父级带固定层级/布局约束，移动到 body 末尾才能真正盖住页面 UI。
        if (root.parentElement !== document.body) {
            document.body.appendChild(root);
        }
        this._saveWebFullscreenNode(document.documentElement);
        this._saveWebFullscreenNode(document.body);
        this._saveWebFullscreenNode(root);
        this._saveWebFullscreenNode(video);
        let viewport = document.querySelector("meta[name='viewport']");
        if (!viewport) {
            viewport = document.createElement("meta");
            viewport.setAttribute("name", "viewport");
            document.head.appendChild(viewport);
        }
        viewport.setAttribute("content", "width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover");
        this.loadCssCode(`
            html, body {
                width: 100vw !important;
                height: 100vh !important;
                width: 100dvw !important;
                height: 100dvh !important;
                min-width: 0 !important;
                margin: 0 !important;
                padding: 0 !important;
                overflow: hidden !important;
                background: #000 !important;
            }
            body.marmot-web-fullscreen-active > :not(.marmot-web-fullscreen-root) {
                display: none !important;
            }
            .marmot-web-fullscreen-root {
                position: fixed !important;
                z-index: 2147483647 !important;
                left: 0 !important;
                top: 0 !important;
                right: 0 !important;
                bottom: 0 !important;
                width: 100vw !important;
                height: 100vh !important;
                width: 100dvw !important;
                height: 100dvh !important;
                min-width: 0 !important;
                min-height: 0 !important;
                max-width: none !important;
                max-height: none !important;
                margin: 0 !important;
                padding: 0 !important;
                transform: none !important;
                background: #000 !important;
                overflow: hidden !important;
                display: block !important;
            }
            .marmot-web-fullscreen-root .xgplayer,
            .marmot-web-fullscreen-root .xgplayer-root,
            .marmot-web-fullscreen-root .xgplayer-inner {
                position: absolute !important;
                left: 0 !important;
                top: 0 !important;
                min-width: 0 !important;
                min-height: 0 !important;
                max-width: none !important;
                max-height: none !important;
                margin: 0 !important;
                padding: 0 !important;
                transform: none !important;
                background: #000 !important;
            }
            .marmot-web-fullscreen-root video,
            .marmot-web-fullscreen-root .xgplayer-video,
            .marmot-web-fullscreen-root .xgplayer-video-wrap {
                position: absolute !important;
                width: 100% !important;
                height: 100% !important;
                left: 0 !important;
                top: 0 !important;
                right: auto !important;
                bottom: auto !important;
                min-width: 0 !important;
                min-height: 0 !important;
                max-width: 100% !important;
                max-height: 100% !important;
                margin: 0 !important;
                padding: 0 !important;
                transform: none !important;
                object-fit: contain !important;
                object-position: center center !important;
                background: #000 !important;
            }
        `);
        document.body.classList.add("marmot-web-fullscreen-active");
        root.classList.add("marmot-web-fullscreen-root");
        this._webFullscreenRoot = root;
        this._isWebFullScreen = true;
        let applyContainLayout = function() {
            let screenWidth = window.innerWidth || document.documentElement.clientWidth || 0;
            let screenHeight = window.innerHeight || document.documentElement.clientHeight || 0;
            let videoWidth = video.videoWidth || 16;
            let videoHeight = video.videoHeight || 9;
            if (!screenWidth || !screenHeight || !videoWidth || !videoHeight) {
                return;
            }
            let videoRatio = videoWidth / videoHeight;
            let screenRatio = screenWidth / screenHeight;
            let targetWidth = screenWidth;
            let targetHeight = screenHeight;
            if (screenRatio > videoRatio) {
                targetWidth = Math.round(screenHeight * videoRatio);
            } else {
                targetHeight = Math.round(screenWidth / videoRatio);
            }
            let left = Math.round((screenWidth - targetWidth) / 2);
            let top = Math.round((screenHeight - targetHeight) / 2);
            let player = video.closest(".xgplayer") || video.parentElement || video;
            let videoWrap = video.closest(".xgplayer-video-wrap") || video.parentElement;
            let layoutBox = (player && player !== root) ? player : (videoWrap || video);
            _tvFunc._saveWebFullscreenNode(layoutBox);
            _tvFunc._saveWebFullscreenNode(videoWrap);
            _tvFunc._saveWebFullscreenNode(video);
            let setImportantStyle = function(node, styles) {
                if (!node) {
                    return;
                }
                Object.keys(styles).forEach(function(key) {
                    node.style.setProperty(key, styles[key], "important");
                });
            };
            [layoutBox].forEach(function(node) {
                if (!node) {
                    return;
                }
                setImportantStyle(node, {
                    "position": "absolute",
                    "left": left + "px",
                    "top": top + "px",
                    "right": "auto",
                    "bottom": "auto",
                    "width": targetWidth + "px",
                    "height": targetHeight + "px",
                    "margin": "0",
                    "padding": "0",
                    "transform": "none",
                    "overflow": "hidden",
                    "background": "#000"
                });
            });
            [videoWrap, video].forEach(function(node) {
                if (!node || node === layoutBox || node === root) {
                    return;
                }
                setImportantStyle(node, {
                    "position": "absolute",
                    "left": "0",
                    "top": "0",
                    "right": "auto",
                    "bottom": "auto",
                    "width": "100%",
                    "height": "100%",
                    "margin": "0",
                    "padding": "0",
                    "transform": "none",
                    "background": "#000"
                });
            });
            setImportantStyle(video, {
                "object-fit": "contain",
                "object-position": "center center",
                "background-color": "#000"
            });
            let layoutRect = layoutBox && layoutBox.getBoundingClientRect ? layoutBox.getBoundingClientRect() : null;
            let videoRect = video.getBoundingClientRect ? video.getBoundingClientRect() : null;
            console.log("autoWebFullscreen contain:", screenWidth + "x" + screenHeight, videoWidth + "x" + videoHeight, targetWidth + "x" + targetHeight, left + "," + top,
                "layoutRect=" + (layoutRect ? Math.round(layoutRect.width) + "x" + Math.round(layoutRect.height) + "@" + Math.round(layoutRect.left) + "," + Math.round(layoutRect.top) : "null"),
                "videoRect=" + (videoRect ? Math.round(videoRect.width) + "x" + Math.round(videoRect.height) + "@" + Math.round(videoRect.left) + "," + Math.round(videoRect.top) : "null"));
        };
        applyContainLayout();
        video.addEventListener("loadedmetadata", applyContainLayout, { once: true });
        window.addEventListener("resize", applyContainLayout);
        console.log("autoWebFullscreen root:", root.id || root.className || root.tagName);
    },
    addKeyFullScreen(elem){
        if (this._fullScreenKeyBound) {
            console.log('Full screen key already bound, skipping');
            return;
        }
        this._fullScreenKeyBound = true;
        
        document.addEventListener('keydown', function(event) {
            // F 键
            if (event.keyCode === 70) {
                event.preventDefault(); // 阻止浏览器默认行为
                event.stopPropagation(); // 阻止事件冒泡
                console.log("F pressed - toggling fullscreen");
                _tvFunc.toggleFullScreen(elem);
                return false;
            }
        }, true);
    },
    title(title){
          if(isNaN(title)){
              return title;
          }
          return `第${title}集`
    },
    titleQ(titleInt,title){
        if(isNaN(titleInt)){
            return title;
        }
        if(titleInt>2000){
            return title;
        }
        return `第${titleInt}集`
    },
    userAgent(isH5){
        let userAgent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0";
        if(isH5){
            userAgent="Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Cronet Mobile/15E148 Safari/604.1";
        }
        return userAgent;
    },
    video:null,
    volume100(ready){
       this.videoReady(function (video){
           video.volume = 1;
           console.log("video volume set ",video.volume);
       })
    },
    getVideo(){
        if(null!=this.video){
            return this.video;
        }
        let videos = document.getElementsByTagName("video");
            //document.getElementsByTagName("video");
        if(videos.length > 0){
            this.video=videos[0];
        }
        return this.video;
    },
    videoTrueReady(ready,time){
        let video = this.getVideo();
        if(null==video){
            this.check(function (){return document.getElementsByTagName("video").length>0;},function (){
                video=_tvFunc.getVideo();
                _tvFunc.check(function (){return video.readyState>2&& video.duration>time;},function (){
                    console.log("video found ",video.readyState);
                    if(ready){
                        ready(video);
                    }
                })
            })
        }
        if(null!=video){
            this.check(function (){return video.readyState>2 && video.duration>time;},function (){
                console.log("video found ",video.readyState);
                if(ready){
                    ready(video);
                }
            })
        }
    },
    videoPlay(){
        let video = this.getVideo();
        if(video.paused){
            video.play();
        }else{
            video.pause();
        }
    },
    videoReady(ready){
        let video = this.getVideo();
        if(null==video){
            this.check(function (){return document.getElementsByTagName("video").length>0;},function (){
                video=_tvFunc.getVideo();
                _tvFunc.check(function (){return video.readyState>2;},function (){
                    console.log("video found ",video.readyState);
                    if(ready){
                        ready(video);
                    }
                })
            })
        }
        if(null!=video){
            this.check(function (){return video.readyState>2;},function (){
                console.log("video found ",video.readyState);
                if(ready){
                    ready(video);
                }
            })
        }
    },
    currentXj(item){
        //title,site,vodId,url
        _layer.notify("当前"+item.title);
        console.log("currentXj "+item.vodId+"  "+item.site);
        //记录当前
        if(null!=item.vodId&&""!==item.site){
            let remark= item.title;
            _apiX.msg("history.update",
                {site:item.site,vodId:item.vodId,url:item.url,name:item.name,remark:remark});
        }
    },
    hzLevel(name,type){
       if(type===1){
         if(name.includes("4K")){
            return 4096;
         }
         if(name.includes("1080")){
            return 1080;
         }
         if(name.includes("720")){
            return 720;
         }
         return 480;
       }
       if(type===2){
          if(name.includes("超清")){
            return 1080;
         }
         if(name.includes("高清")){
            return 720;
         }
         return 480;
       }

    },
    paramStr(data,start){
        let param="";
        Object.keys(data).forEach(key => {
            param+=`&${key}=${data[key]}`
        });
        if(start){
            return "?"+param.substring(1);
        }
        return  param;
    },
   image(url){
     if(url.includes("?")){
        return url+"&tvImg=1";
     }
    return url+"?tvImg=1";
   },
   show(){
    document.body.style.visibility="visible";
   },
   maxCheck(check,callback,maxNum){
     let num=0;
     let index=setInterval(function(){
        if(check()){
            clearInterval(index);
            callback(index);
        }
        num++;
        if(num>maxNum){
          clearInterval(index);
          callback(index);
        }
     },1000);
   },
   check(check,callback,time,maxNumOrg){
    if(!time){
        time=500;
    }
    let num=0;
    let maxNum=50;
    if(maxNumOrg&&maxNumOrg>0){
       maxNum=maxNumOrg;
    }
    let index=setInterval(function(){
        try{
            if(check()){
                clearInterval(index);
                callback(index);
            }
            console.log("num {} maxNum {} check {}",num,maxNum,check)
            num++;
            if(num>maxNum){
                clearInterval(index);
            }
        }catch (e){
            clearInterval(index);
        }
    },time);
    return index;
   },
   sessionStorageCheckTime(key){
     let time= sessionStorage.getItem(key+"Time");
     if(time){
         let now=  new Date().getTime();
         if(now<(Number(time)+5000)){
            return true;
         }
     }
     return false;
   },
    //网友改
    waitForVideoElement() {
        const timeout = 1000 * 35;  //************************
        return new Promise((resolve) => {
                // 优先查找已存在的 video 元素
                const existing = document.querySelector('video');
                if (existing) {
                    //_apiX.videoFind("ok");
                    resolve(existing);
                    return;
                }

                const startTime = Date.now();
                let intervalId = null;

                // 使用 MutationObserver 监听 DOM 变化
                const observer = new MutationObserver(() => {
                        const video = document.querySelector('video');
                        if (video) {
                            observer.disconnect();
                            clearInterval(intervalId);
                            //_apiX.videoFind("ok");
                            resolve(video);
                        }
                    }
                );

                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });

                // 每隔 200ms 轮询一次作为兜底
                intervalId = setInterval(() => {
                    const video = document.querySelector('video');
                    if (video) {
                        observer.disconnect();
                        clearInterval(intervalId);
                        //_apiX.videoFind("ok");
                        resolve(video);
                    } else if (Date.now() - startTime >= timeout) {
                        observer.disconnect();
                        clearInterval(intervalId);
                        //_apiX.toast(timeout / 1000 + "秒，未找到资源。系统重新加载！");
                        resolve(null);
                    }
                }, 200);
            }
        );
    },
    async waitForVideoPlay(video, timeout) {
        if (!video)
            video = await this.waitForVideoElement();
        if (!video) {
            return Promise.resolve(false);
        }

        if (this.isVideoPlaying(video)) {
            return Promise.resolve(true);
        }

        return new Promise((resolve) => {
                const startTime = Date.now();
                timeout = timeout || 1000 * 35; //************************
                const check = () => {
                    if (this.isVideoPlaying(video)) {
                        resolve(true);

                        // 注册卡顿回调
                        const stallDetector = new VideoStallDetector(video);
                        stallDetector.onStall = (reason) => {
                            if (reason.toLowerCase() != 'buffering' && reason.toLowerCase() != 'readystate') {
                                _apiX.toast('卡顿原因：' + reason + "。系统重新加载！");
                                _apiX.msg("tobackup", "{}");
                            } else {//_apiX.toast('卡顿原因：' + reason);
                            }
                        };

                    } else if (Date.now() - startTime >= timeout) {
                        //_apiX.toast(timeout / 1000 + "秒，视频未播放。系统重新加载！");
                        resolve(false);
                    } else {
                        setTimeout(check, 200); // 每 200ms 检查一次
                    }
                };
                check();
            }
        );
    },
    isVideoPlaying(video) {
        return video.readyState > 2 && !video.paused && !video.ended && video.currentTime > 0;
    }
};
var _apiX={
    userAgent(isH5){
        let userAgent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0";
        if(isH5){
            userAgent="Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Cronet Mobile/15E148 Safari/604.1";
        }
        return userAgent;
    },
    toast(message){
        _marmotBridge.toast(message);
    },
    postMessage(data){
       // 添加 model 标识，表示这是 JS 推送消息，cursor 会自动转发给 APP
       data.model = "jsMessage";
       if(_tvState.platform==="android"){
         _marmotBridge.postMessage(JSON.stringify(data));
       }else{
         _marmotBridge.postMessage(data);
       }
    },
    msgStr(service,dataStr){
        console.log("msgStr",service,dataStr)
        _marmotBridge.message(service,dataStr);
    },
    queryByService:async function (service,param,callback){
        if(null!=param){
            param=  JSON.stringify(param);
        }
        let  result= await  _api.queryByService(service,param);
        callback(result);
    },
    message(service,data,msgId,callback){
        data.msgId=msgId;
        let dataStr=  JSON.stringify(data);
        _marmotBridge.message(service,dataStr);
        let index = setInterval(function (){
                            let data=  sessionStorage.getItem(msgId);
                            if(data){
                                clearInterval(index);
                               callback(data);
                            }},200)
    },
    postJson:async function (reqUrl, header, data, callback,errBack) {
        let headerStr=  JSON.stringify(header);
        let dataStr=  JSON.stringify(data);
        let result ="505";
        try{
             result= await _marmotBridge.postJson(reqUrl,headerStr,dataStr);
        }catch (e){
            console.error(e);
        }
        let isError=this.errorWork(result,errBack);
        if(isError){
            return;
        }
       callback(result);
    },
    getHtml: async function(reqUrl, header, callback,errBack) {
        let headerStr=  JSON.stringify(header);
        let result="505";
        try{
            result= await _marmotBridge.getHtml(reqUrl,headerStr);
        }catch (e){
            console.error(e);
        }
        let isError=this.errorWork(result,errBack);
        if(isError){
            return;
        }
        callback(result);
    },
    getJson: async function (reqUrl, header, callback,errBack) {
        let headerStr=  JSON.stringify(header);
        let result="505";
        try{
             result= await _marmotBridge.getJson(reqUrl,headerStr);
        }catch (e){
            console.error(e);
        }
        let isError=this.errorWork(result,errBack);
        if(isError){
            return;
        }
        callback(result);
    },
    errorWork(result,errBack){
        if(result==="500"||result==="400"||result==="505"){
            if(errBack){
                errBack(result);
                let  extMsg="";
                if(result==="505"){
                    extMsg="请检查网络";
                }
                _layer.notify("请求接口失败 "+extMsg)
            }
            return true;
        }
        return false;
    }
};

var _layer={
    init(id,html,index,idName,...classNames){
        var myDiv = document.createElement("div");
        if(!idName){
            idName="tv-index-"+index;
        }
        myDiv.id = idName;
        myDiv.style.zIndex=index;
        myDiv.style.visibility="hidden";//hidden  visible
        myDiv.style.maxWidth="100vw";
        myDiv.style.overflowY="auto";
        if(classNames.length==0){
            myDiv.className="tv-index";
        }else{
            myDiv.className=classNames.join(" ");
        }
        myDiv.innerHTML = html;
        if(null==id){
            document.body.appendChild(myDiv);
        }else{
            let chooseId=id.substring(1);
            if(id.startsWith("#")){
                document.getElementById(chooseId).appendChild(myDiv);
            }
            if(id.startsWith(".")){
                document.getElementsByClassName(chooseId)[0].appendChild(myDiv);
            }
            if(id.startsWith("&")){
                document.getElementsByTagName(chooseId)[0].appendChild(myDiv);
            }
        }
        return idName;
    },
    initMenu(id,html,index,idName,...classNames){
        let menuId=this.init(id,html,index,idName,...classNames);
        window._tv_menuId=menuId;
        return menuId;
    },
    toggle(id){
        let elem=document.getElementById(id);
        if(elem){
            if(elem.style.visibility=="hidden"){
                elem.style.visibility="visible";
                return true;
            }
            elem.style.visibility="hidden";
        }
        return false;
    },
    isShow(id){
        if(typeof(id)=='undefined'||null==id){
            return false;
        }
        if(null!=document.getElementById(id)){
            return document.getElementById(id).style.visibility==="visible";
        }
        return false;
    },
    show(id){
        if(document.getElementById(id)){
            document.getElementById(id).style.visibility="visible";
        }
    },
    hide(id){
        if(document.getElementById(id)){
            document.getElementById(id).style.visibility="hidden";
        }
    },
    open(html,index,idName,...classNames){
        var myDiv = document.createElement("div");
        if(!idName){
            idName="tv-index-"+index;
        }
        myDiv.id = idName;
        myDiv.style.zIndex=index;
        if(classNames.length==0){
            myDiv.className="tv-index";
        }else{
            myDiv.className=classNames.join(" ");
        }
        myDiv.innerHTML = html;
        document.body.appendChild(myDiv);
        return idName;
    },
    openById(id,html,index,idName,className){
        var myDiv = document.createElement("div");
        if(!idName){
            idName="tv-index-"+index;
        }
        myDiv.id = idName;
        myDiv.style.zIndex=index;
        if(!className){
            className="tv-index";
        }
        myDiv.className=className;
        myDiv.innerHTML = html;
        document.getElementById(id).appendChild(myDiv);
        return idName;
    },
    notify(text,index,time){
        if(!time){
            time=2.5;
        }
        if(!index){
            index=9999999;
        }
        let html='<div>'+text+'</div>';
        let id= this.open(html,index,null,"tv-notify");
        setTimeout(function(){
            _layer.close(id);
        },time*1000);
    },
    notifyLess(text,index,time){
        if(!time){
            time=1;
        }
        if(!index){
            index=9999998;
        }
        let html='<div style="color: white;">'+text+'</div>';
        let lastId="tv-index-"+index;
        this.close(lastId);
        let id= this.open(html,index,null,"notify-less");
        setTimeout(function(){
            _layer.close(id);
        },time*1000);
    },
    wait(text,index){
        if(!index){
            index=9999999;
        }
        let html='<div style="color: white;">'+text+'</div>';
        let id= this.open(html,index,null,"tv-wait");
        return id;
    },
    close(id){
        if(document.getElementById(id)){
            document.getElementById(id).remove();
        }
    }
}
var _tvMsg={
    notVip:" 建议在拼多多/抖音/淘宝等购物软件里搜索购买"
}
function extractDomain(url) {
    const match = url.match(/^(https?:\/\/[^/?#]+)/i);
    return match ? match[1] : null;
}
function decodeUnicodeBase64(base64Str) {
    return decodeURIComponent(escape(atob(base64Str)));
}
//_apiX.msg("videoQuality",[]);
class VideoStallDetector {
    constructor(videoElement, options = {}) {
        this.video = videoElement;
        this.checkInterval = options.checkInterval || 1000 * 60;
        // 默认60秒检查一次
        this.stallTimeThreshold = options.stallTimeThreshold || 60;
        // 默认60秒未更新时间视为卡顿
        this.lastTimeUpdate = 0;
        this.isStalled = false;

        this.init();
    }

    init() {
        this.bindEvents();
        this.startPeriodicCheck();
    }

    bindEvents() {
        this.video.addEventListener('waiting', this.handleWaiting.bind(this));
        this.video.addEventListener('timeupdate', this.handleTimeUpdate.bind(this));
    }

    handleWaiting() {
        //console.log('视频正在缓冲，可能卡住了');
        //if (this.onStall) this.onStall('网络掉线，正在缓冲');
        if (this.onStall)
            this.onStall('Buffering');
    }

    handleTimeUpdate() {
        const currentTime = this.video.currentTime;
        if (currentTime === this.lastTimeUpdate) {
            // 如果连续两次时间未更新，可能卡住了
            if (!this.isStalled) {
                this.isStalled = true;
                setTimeout(() => {
                    if (this.video.currentTime === currentTime) {
                        //console.log(`视频卡住超过 ${this.stallTimeThreshold} 秒`);
                        //if (this.onStall) this.onStall('timeupdate');
                        if (this.onStall)
                            this.onStall(`视频进度更新超 ${this.stallTimeThreshold} 秒`);
                    }
                    this.isStalled = false;
                }, this.stallTimeThreshold * 1000);
            }
        } else {
            this.isStalled = false;
        }
        this.lastTimeUpdate = currentTime;
    }

    startPeriodicCheck() {
        setInterval(() => {
            if (!this.video.paused && this.video.readyState < this.video.HAVE_FUTURE_DATA) {
                //console.log('视频可能卡住了：没有足够的数据可以播放');
                //if (this.onStall) this.onStall('没有足够的数据可以播放');
                if (this.onStall)
                    this.onStall('readyState');
            }
        }, this.checkInterval);
    }

    // 设置回调函数
    onStall(callback) {
        this.onStall = callback;
    }
};

// TV 视频控制器：提供视频播放控制方法
// 使用示例：_tvController.play()、_tvController.pause()、_tvController.mute() 等
const _tvController={
    // 获取当前视频信息并推送给客户端
    videoState(){
        let video = _tvFunc.getVideo();
        if(null==video){
            return null;
        }
        let state =  {
            paused: video.paused,
            readyState: video.readyState,
            volume: video.volume,
            muted: video.muted,
            currentTime: video.currentTime,
            duration: video.duration
        }
        _apiX.postMessage({
            type: 'videoState',
            data: { state }
        });
    },
    videoStateSend(){
        // 立即发送一次状态
        _tvController.videoState();
        // 每 10 秒定时发送视频状态
        if (window._videoStateInterval) {
            clearInterval(window._videoStateInterval);
        }
        window._videoStateInterval = setInterval(function() {
            _tvController.videoState();
        }, 10000);
    },
    // 切换播放/暂停
    togglePlay(){
        let video = _tvFunc.getVideo();
        if(null==video){
            return;
        }
        if(video.paused){
            video.play();
        }else{
            video.pause();
        }
    },
    // 跳转到指定时间点（单位：秒）
    seek(time){
        let video = _tvFunc.getVideo();
        if(null==video){
            return;
        }
        video.currentTime = time;
    },
    // 快进/快退（单位：秒，正数快进，负数快退）
    forward(seconds){
        let video = _tvFunc.getVideo();
        if(null==video){
            return;
        }
        video.currentTime += seconds;
    },
    // 设置音量（范围：0.0-1.0）
    volume(vol){
        let video = _tvFunc.getVideo();
        if(null==video){
            return;
        }
        video.volume = vol;
    },
    // 切换静音/取消静音
    toggleMute(){
        let video = _tvFunc.getVideo();
        if(null==video){
            return;
        }
        video.muted = !video.muted;
    },
    // 设置播放速率（如 0.5x, 1.0x, 1.5x, 2.0x）
    rate(speed){
        let video = _tvFunc.getVideo();
        if(null==video){
            return;
        }
        // speed 可以是数字（如 1.5）或字符串（如 "1.5x"）
        let rate = typeof speed === 'string' ? parseFloat(speed) : speed;
        if (!isNaN(rate) && rate > 0) {
            video.playbackRate = rate;
        }
    }
}


function setupVideo(video) {
    const container = document.createElement('div');
    container.style.position = 'fixed';
    container.style.top = '0';
    container.style.left = '0';
    container.style.width = '100vw';
    container.style.height = '100vh';
    container.style.zIndex = '2147483647';
    container.style.backgroundColor = 'black';
    video.style.width = '100%';
    video.style.height = '100%';
    video.style.objectFit = 'contain';
    video.style.transform = 'translateZ(0)';
    container.appendChild(video);
    document.body.appendChild(container);
    document.body.style.overflow = 'hidden';
    document.documentElement.style.overflow = 'hidden';
}

/**
 * 通用视频页面初始化函数
 * 抽象自 cctv/detail.js 和 common/detail.js 的共同逻辑
 * 
 * @param {Object} options 配置选项
 * @param {boolean} options.autoSendState - 是否定时发送视频状态（默认 false）
 * @param {boolean} options.handleUjs - 是否处理 ujs 参数（默认 false）
 * @param {boolean} options.handleULink - 是否处理 u-link=1 跳转（默认 false）
 * @param {Function} options.onVideoReady - 视频播放后的回调（可选，用于画质切换等）
 */
function initVideoPage(options) {
    options = options || {};
    
    // 1. 自动全屏视频初始化
        _tvFunc.waitForVideoElement().then(function(video) {
            if (!video) return;

            _tvFunc.addKeyFullScreen(video);
            _tvFunc.autoWebFullscreen(video);
            // Gecko 端已经由通用 CSS 处理网页全屏，继续发送 F 会触发 adapter fallback 叠加样式。
            let shouldSendFullscreenKey = _tvState.platform !== "gecko";
            if (shouldSendFullscreenKey) {
                _apiX.postMessage({
                    type: 'keyCode',
                    data: "F"
                });
            }
            // 设置视频属性
            video.muted = false;
            video.volume = 1;
            video.playsInline = false;
            video.setAttribute('playsinline', 'false');
            
            try {
                video.play();
                _tvController.videoState();
                
                // 是否定时发送视频状态
                if (options.autoSendState) {
                    if (window._videoStateInterval) {
                        clearInterval(window._videoStateInterval);
                    }
                    window._videoStateInterval = setInterval(function() {
                        _tvController.videoState();
                    }, 10000);
                }
            } catch (e) {
                console.error("video play error:", e);
            }
            
            $$("body").css("min-width", "100%");
            $$("html").css("min-width", "100%");
            
            // 检查视频播放状态，播放后执行回调
            _tvFunc.check(function() {
                let videoPlay = _tvFunc.isVideoPlaying(video);
                if (!videoPlay) {
                    try {
                        _tvFunc.getVideo().play();
                    } catch (e) {}
                }
                return videoPlay;
            }, function() {
                // 视频播放成功后的回调
                if (options.onVideoReady) {
                    options.onVideoReady(video);
                }
            }, 1000, 5);
        });
    // 2. 页面加载完成后的处理
    $$(function() {
        // ujs 参数处理
        if (options.handleUjs) {
            let ujs = _tvFunc.getQueryParams()["ujs"];
            if (ujs) {
                let ujsContent = decodeUnicodeBase64(ujs.replace(/ /g, '+'));
                console.log("ujsContent", ujsContent);
                eval(ujsContent);
            }
        }
        
        // u-link=1 跳转处理
        if (options.handleULink) {
            let url = window.location.href;
            if (url.indexOf("u-link=1") > 0) {
                _tvFunc.check(function() {
                    let utaoLoc = sessionStorage.getItem("u-loc");
                    if (utaoLoc) {
                        console.log("utaoLoc", utaoLoc, url);
                        if (url == utaoLoc) {
                            _marmotBridge.message("videoUrl", sessionStorage.getItem("u-m3u8"));
                            //window.location.href = extractDomain(utaoLoc) + "/tv-web/live.html?url=" + sessionStorage.getItem("u-m3u8");
                            return true;
                        }
                    }
                    return false;
                }, function() {}, 1000, 10);
            }
        }
        
        // viewport 设置（通用）
        const viewportMeta = document.querySelector('meta[name="viewport"]');
        console.log("viewportMeta::", viewportMeta);
        if (viewportMeta) {
            viewportMeta.setAttribute('content', 'width=device-width, initial-scale=1');
        }
    });
}
