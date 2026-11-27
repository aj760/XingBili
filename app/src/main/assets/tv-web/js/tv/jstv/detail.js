
(function(){
    _tvFunc.fixedW("body");
    _tvFunc.check(function (){return $$("#programMain .title").length>0},function (){
         let tag =  _tvFunc.getQueryParams()["tag"];
         console.log(tag);
         let currentTag="";
         let tagIndex=0;
        $$("#programMain .swiper-slide").each(function (i,item){
             let active=  $$(item).hasClass("active");
             let text = $$(item).find(".title").text().trim();
             //console.log(text);
             if(active){
                 currentTag=text;
             }
             if(tag===text){
                 tagIndex=i;
             }
        });
        console.log(currentTag);
        let fullscreenApplied = false;
        let applyVideoFullscreen = function () {
            if (fullscreenApplied) {
                return;
            }
            fullscreenApplied = true;
            _tvFunc.fixedW("body");
            // $("#programMain .title")[1].click()
            _tvFunc.check(function (){return document.getElementsByTagName("video").length>0;},function (){
                //document.getElementsByTagName("video")[0].classList.add("utv-video-full");
                let video = _tvFunc.getVideo();
                _tvFunc.addKeyFullScreen(video);
                _tvFunc.autoWebFullscreen(video);
                _apiX.postMessage({type: 'keyCode', data: "F"});
            });
        };
        if(tag!==currentTag){
            $$("#programMain .title")[tagIndex].click();
            _tvFunc.check(function (){
                return $$("#programMain .swiper-slide.active .title").text().trim() === tag;
            }, applyVideoFullscreen, 200, 20);
            setTimeout(applyVideoFullscreen, 4500);
        } else {
            applyVideoFullscreen();
        }
    });
    _tvFunc.volume100();
})();
