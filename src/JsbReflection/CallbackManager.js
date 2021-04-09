/**
 * 用于管理从游戏到原生的调用并返回一个异步的结果
 * @type {{}}
 */
var CallbackManager = CallbackManager || {
    callbackMap:{},
};

CallbackManager.callCallback = function (callbackId, resultData, autoRemove = true) {
    //not a function 就 return
    let hasCallback = !SXTCommonUtils.isEmpty(this.callbackMap[callbackId]);
    if (!hasCallback){
        return;
    }

    if (resultData == '' || resultData == null){
        this.callbackMap[callbackId](null);
    } else {
        let isJson = VerifyUtil.isJSON(resultData);
        //如果是json返回json 不是的话正常返回
        if (isJson){
            let jsonData = JSON.parse(resultData);
            this.callbackMap[callbackId](jsonData);

        } else {
            this.callbackMap[callbackId](resultData);
        }
    }

    autoRemove && this.removeCallback(callbackId)
};
CallbackManager.addCallback = function(callback) {
    let timestamp = (new Date()).valueOf().toString();
    this.callbackMap[timestamp] = callback;
    return timestamp;
};
CallbackManager.removeCallback = function (callbackId) {
    delete this.callbackMap[callbackId]
};