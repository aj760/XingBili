
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
// 这里已经是文档加载后的了
(function(){
    _tvFunc.waitForVideoElement().then(video => {
        let param=_tvFunc.getQueryParams();
        _tvFunc.fixedW("body");
        _tvFunc.fullscreen("video");
        $$("video").css("position","fixed !important");
        video.muted = false;
        video.volume = 1;
        video.playsInline = false;
        video.setAttribute('playsinline', 'false');
        try {
            video.play();
        } catch (e) {
        }
        $$(".mobile-num").remove();
        //_data.hzList(video);
        _tvFunc.check(function (){
            let videoPlay=_tvFunc.isVideoPlaying(video);
            if(!videoPlay){
                try{
                    _tvFunc.getVideo().play();
                }catch(e){}
            }
            return videoPlay},function (){_data.hzList(video);},1000,5);

    });
    const viewportMeta = document.querySelector('meta[name="viewport"]');
    console.log("viewportMeta::",viewportMeta);
    if (viewportMeta) {
        viewportMeta.setAttribute('content', `width=device-width, initial-scale=1`);
    }
})();



