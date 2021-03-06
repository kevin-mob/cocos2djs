/**
 * @module cocos2dx_experimental_webView
 */
var ccui = ccui || {};

/**
 * @class WebView
 */
ccui.WebView = {

/**
 * @method setOpacityWebView
 * @param {float} arg0
 */
setOpacityWebView : function (
float 
)
{
},

/**
 * @method canGoBack
 * @return {bool}
 */
canGoBack : function (
)
{
    return false;
},

/**
 * @method loadHTMLString
 * @param {String} arg0
 * @param {String} arg1
 */
loadHTMLString : function (
str, 
str 
)
{
},

/**
 * @method goForward
 */
goForward : function (
)
{
},

/**
 * @method goBack
 */
goBack : function (
)
{
},

/**
 * @method setScalesPageToFit
 * @param {bool} arg0
 */
setScalesPageToFit : function (
bool 
)
{
},

/**
 * @method setVisible
 * @param {bool} bool
 */
setVisible : function (
    bool
)
{
},

/**
 * @method getOnDidFailLoading
 * @return {function}
 */
getOnDidFailLoading : function (
)
{
    return std::function<void (cocos2d::experimental::ui::WebView , std::string&)>;
},

/**
 * @method loadFile
 * @param {String} arg0
 */
loadFile : function (
str 
)
{
},

/**
 * @method loadURL
* @param {String|String} str
* @param {bool} bool
*/
loadURL : function(
str,
bool 
)
{
},

/**
 * @method setBounces
 * @param {bool} arg0
 */
setBounces : function (
bool 
)
{
},

/**
 * @method evaluateJS
 * @param {String} arg0
 */
evaluateJS : function (
str 
)
{
},

/**
 * @method setBackgroundTransparent
 */
setBackgroundTransparent : function (
)
{
},

/**
 * @method setOnJSCallback
 * @param {function} arg0
 */
setOnJSCallback : function (
    func
)
{
},

/**
 * @method getOnJSCallback
 * @return {function}
 */
getOnJSCallback : function (
)
{
    return std::function<void (cocos2d::experimental::ui::WebView , std::string&)>;
},

/**
 * @method canGoForward
 * @return {bool}
 */
canGoForward : function (
)
{
    return false;
},

/**
 * @method getOnShouldStartLoading
 * @return {function}
 */
getOnShouldStartLoading : function (
)
{
    return std::function<bool (cocos2d::experimental::ui::WebView , std::string&)>;
},

/**
 * @method stopLoading
 */
stopLoading : function (
)
{
},

/**
 * @method getOpacityWebView
 * @return {float}
 */
getOpacityWebView : function (
)
{
    return 0;
},

/**
 * @method reload
 */
reload : function (
)
{
},

/**
 * @method setJavascriptInterfaceScheme
 * @param {String} arg0
 */
setJavascriptInterfaceScheme : function (
str 
)
{
},


/**
 * @method setOnDidFinishLoading
 * @param {function} arg0
 */
setOnDidFinishLoading : function (
func
)
{
},

/**
 * @method getOnDidFinishLoading
 * @return {function}
 */
getOnDidFinishLoading : function (
)
{
    return std::function<void (cocos2d::experimental::ui::WebView , std::string&)>;
},

/**
 * @method create
 * @return {cc.experimental::ui::WebView}
 */
create : function (
)
{
    return cc.experimental::ui::WebView;
},

/**
 * @method WebView
 * @constructor
 */
WebView : function (
)
{
},

};
