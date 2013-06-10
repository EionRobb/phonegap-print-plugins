function PrintPlugin() {}
PrintPlugin.prototype.print = function(printHTML, success, fail, options) {
    if (typeof printHTML != 'string'){
        console.log("Print function requires an HTML string. Not an object");
        return;
    }
  
	return (PhoneGap || cordova || Cordova).exec(success, fail, "PrintPlugin", "print", [printHTML, (options&&options.appid||"")]);
};

/*
 * Callback function returns {available: true/false}
 */
PrintPlugin.prototype.isPrintingAvailable = function(callback) {
    return (PhoneGap || cordova || Cordova).exec(callback, null, "PrintPlugin", "scan");
};

(PhoneGap || cordova || Cordova).addPlugin("printPlugin", new PrintPlugin());




  window.print = function() {
		var htmlTag = document.body.parentNode;
		var docHtml = htmlTag.innerHTML;
		var win = window.parent || window;
		var left = win.getWindowWidth() / 2;
		var top = win.getWindowHeight() / 2;
		win.plugins.printPlugin.print("<html>" + docHtml + "</html>", null, null, {'dialogOffset':{'left':left,'top':top}});
	};
