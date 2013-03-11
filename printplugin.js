
function PrintPlugin() {}
PrintPlugin.prototype.print = function(printHTML, success, fail, options) {
    if (typeof printHTML != 'string'){
        console.log("Print function requires an HTML string. Not an object");
        return;
    }
  
	return PhoneGap.exec(success, fail, "PrintAppScanner", "print", [printHTML, (options&&options.appid||"")]);
};

/*
 * Callback function returns {available: true/false}
 */
PrintPlugin.prototype.isPrintingAvailable = function(callback) {
    return PhoneGap.exec(callback, null, "PrintAppScanner", "scan");
};

PhoneGap.addPlugin("printPlugin", new PrintPlugin());




  window.print = function() {
		var htmlTag = document.body.parentNode
		if (!features.platform.android) {
			var scriptTags = htmlTag.getElementsByTagName('script');
			for(var i = scriptTags.length - 1; i >= 0; i--)
			{
				scriptTags[i].parentNode.removeChild(scriptTags[i]);
			}
		}
		var docHtml = htmlTag.innerHTML;
		var win = window.parent || window;
		var left = win.getWindowWidth() / 2;
		var top = win.getWindowHeight() / 2;
		win.plugins.printPlugin.print("<html>" + docHtml + "</html>", null, null, {'dialogOffset':{'left':left,'top':top}});
	};
