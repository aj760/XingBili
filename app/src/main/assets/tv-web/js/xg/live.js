let url = window.location.href;
let index= url.indexOf("url=");
let link = url.substring(index+4,url.length);
link=link.replace("&usave=1","")
const config = {
    "id": "mse",
    "url": link,
    "playsinline": true,
    "plugins": [],
    "isLive": true,
    "autoplay": true,
    volume: 1,
    "width": "100%",
    "height": "100%"
}
let player = new HlsJsPlayer(config);

