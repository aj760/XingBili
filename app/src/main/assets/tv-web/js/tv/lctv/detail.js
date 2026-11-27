
function initVideoPlayerMy(index) {
    console.log("当前标签："+index)

    //if(index==curIndex)return;
    curIndex=index;

    let liveInfo=liveInfos[curIndex];

    //设置标题
    $("#lName").text(liveInfo.lname);
    $("#rowLogo img").removeClass("active");
    $("#logo"+index).addClass("active");

    const dplayerConfig={
        //播放器的一些参数
        container: document.getElementById('playerId'),
        live:true,
        autoplay: true, //是否自动播放
        theme: '#FADFA3', //主题色
        loop: true, //视频是否循环播放
        lang: 'zh-cn',
        screenshot: false, //是否开启截图
        hotkey: true, //是否开启热键
        preload: 'auto', //视频是否预加载
        volume: 1, //默认音量
        mutex: true, //阻止多个播放器同时播放，当前播放器播放时暂停其他播放器
        video: {
            url: liveInfo.livestreamurl , //视频地址

            customType: {
                hls: function(video, player) {
                    if (Hls.isSupported()) {
                        var hls = new Hls();
                        hls.loadSource(liveInfo.livestreamurl );
                        hls.attachMedia(video);
                    }
                }
            },
        },
    }

    dplayerConfig['video']['type']='hls'
    console.log("已经顺利执行到这里")
    const player=new DPlayer(dplayerConfig);

}
(function(){
    _tvFunc.fixedW("body");
    _tvFunc.check(function (){return initVideoPlayer&&Hls},function (){
        let url = window.location.href;
         let index= url.indexOf("tag=");
         let tag = decodeURI(url.substring(index+4,url.length));
         console.log(tag);
        initVideoPlayerMy(tag);
        _tvFunc.check(function (){return document.getElementsByTagName("video").length>0;},function (){
            console.log("video found")
            // document.getElementsByTagName("video")[0].classList.add("utv-video-full");
            _tvFunc.fullscreen("video");
            $$("video").css("position","fixed !important")
        });
        // $("#programMain .title")[1].click()
    });
    _tvFunc.volume100(function (){
        document.getElementsByTagName("video")[0].classList.add("utv-video-full");
        setTimeout(function (){
            document.getElementsByTagName("video")[0].volume=1;
            document.getElementsByTagName("video")[0].classList.add("utv-video-full");
        },3000);
    });

})();
