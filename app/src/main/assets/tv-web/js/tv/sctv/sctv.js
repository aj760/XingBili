
let tag =  _tvFunc.getQueryParams()["tag"];
let playUrl=null;
let initPlayer=function (){
    _apiX.getJson("http://api.vonchange.com/utao/sctv?tag="+tag,   { "User-Agent": _apiX.userAgent(false), "tv-ref": "http://api.vonchange.com" },function(data){
        console.log(data);
        if(data&&data.trim()!==""){
            playUrl=data;
            const config = {
                "id": "mse",
                "url": data,
                "hlsOpts": {
                    xhrSetup: function(xhr, url) {
                    }
                },
                "playsinline": true,
                "plugins": [],
                "isLive": true,
                "autoplay": true,
                volume: 1,
                "width": "100%",
                "height": "100%"
            }
            player = new HlsJsPlayer(config);
        }
    });

}
let reloadLive=function (){
    _apiX.getJson("http://api.vonchange.com/utao/sctv?tag="+tag,   { "User-Agent": _apiX.userAgent(false), "tv-ref": "http://api.vonchange.com" },function(data){
        console.log(data);
        if(data&&data.trim()!==""){
            if(data!==playUrl){
                playUrl=data;
                player.src=data;
            }
        }
    });
}


initPlayer();
setInterval(function(){console.log("reloadLive");reloadLive();},1000*60*5);
