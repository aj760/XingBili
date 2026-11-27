
(function(){
    _tvFunc.fixedW("body");
    _tvFunc.check(function (){return $$(".right_list li").length>0},function (){
        //let url = window.location.href;
        //let index= url.indexOf("tag=");
        let tag =  _tvFunc.getQueryParams()["tag"];
        console.log(tag);
        let currentTag=0;
        $$(".right_list li").each(function (i,item){
            let active=  $$(item).hasClass("active");
            if(active){
                currentTag=i;
            }
        });
        console.log(currentTag);
        if(Number(tag)!==currentTag){
            $$(".right_list li")[Number(tag)].click();
        }
        _tvFunc.check(function (){return document.getElementsByTagName("video").length>0;},function (){
            //document.getElementsByTagName("video")[0].classList.add("utv-video-full");
            _tvFunc.fullscreen("video");
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
