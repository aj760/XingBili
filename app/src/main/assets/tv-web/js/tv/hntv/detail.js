(function () {

    //window.libreTV_selectItem = selectItem; //将函数赋值到全局对象 libreTV_selectItem('政法频道')
   // let url = window.location.href;
    //let index= url.indexOf("tag=");
    let tag =  _tvFunc.getQueryParams()["tag"];
    console.log(tag);
    document.querySelectorAll('video').forEach(v => v.pause());

    _tvFunc.waitForVideoElement().then(video => {
        if (video == null) {
           // _apiX.msg("tobackup", "{}");
        }
        else {
            document.querySelectorAll('video').forEach(v => v.pause());

            //tag = decodeURIComponent(_tvLoadRes.getCookie("libreTag"));

            selectItem(tag);
        }
    });

    function selectItem(tag) {
        var findtag = false;

        $$(".channels .list a").each(function (i, item) {
            let channelname = $$(item).text();
            if (channelname.indexOf(tag) >= 0) {
                findtag = true;
                $$(item).click();

                //const computedStyle = window.getComputedStyle($("video")[0]);
                //if (!(computedStyle.position === "fixed")) _app.waitForVideoPlay();
                _app.waitForVideoPlay();

                return false;
            }
        });

        if (!findtag) {
            _app.monitorChannelAndClick(tag);//监听是否有对应的频道并点击

            async function clickTabsWithDelay() {
                const tabs = $$(".tabs .item");
                for (let i = 0; i < tabs.length; i++) {
                    const tab = tabs.eq(i);
                    if (!tab.hasClass("selected")) {
                        tab.click();        //点击频道类型
                        await new Promise(resolve => setTimeout(resolve, 300)); // 等待
                    }
                }
            }
            clickTabsWithDelay();
        }
    }

    let _app = {
        monitorChannelAndClick(tag) {
            // 目标节点（整个文档）
            const targetNode = document.getElementsByClassName('channels')[0];
            // 配置观察选项
            //childList: true,    // 观察目标子节点的添加/删除
            //subtree: true,      // 观察目标节点所有后代节点的变化
            //attributes: false,  // 属性变化（默认:false）
            const config = { childList: true, subtree: true, attributes: false };
            // 当观察到变动时执行的回调函数
            const callback = function (mutationsList, observer) {
                for (let mutation of mutationsList) {
                    if (mutation.type === 'childList') {
                        $$(".channels .list a").each(function (i, item) {
                            let channelname = $$(item).text();
                            if (channelname.indexOf(tag) >= 0) {
                                $$(item).click();

                                //const computedStyle = window.getComputedStyle($("video")[0]);
                                //if (!(computedStyle.position === "fixed")) _app.waitForVideoPlay();
                                _app.waitForVideoPlay();

                                observer.disconnect();

                                return false;
                            }
                        });
                    }
                }
            };
            // 创建一个观察器实例并传入回调函数
            const observer = new MutationObserver(callback);
            // 开始观察目标节点
            observer.observe(targetNode, config);
            // 如果后续想停止观察，可以调用 observer.disconnect();
        },
        waitForVideoPlay(){
            setTimeout(() => {
                const video1 = document.querySelector('video');
                _tvFunc.waitForVideoPlay(video1).then(isplay => {
                    if (isplay) {
                        $$("div").removeAttr("style").attr("class", "preserve-content");
                        //$$("div:not(.player-container)").removeAttr("style").attr("class", "preserve-content");
                        //$$("#m-topheader").hide();

                        _tvFunc.fixedW("body");
                        _tvFunc.fullscreenWW("video");
                    }
                    else {
                        _apiX.msg("tobackup", "{}");
                    }
                });
            }, 1000);
        }
    };

})();
