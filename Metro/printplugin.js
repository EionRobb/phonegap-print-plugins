window.print = function() {
	var printManager = Windows.Graphics.Printing.PrintManager.getForCurrentView();
	function onPrintTaskRequested(printEvent) {
		var printTask = printEvent.request.createPrintTask(document.title || "Print Document", function (args) {
			args.setSource(MSApp.getHtmlPrintDocumentSource(document));
		});
		printManager.removeEventListener("printtaskrequested", onPrintTaskRequested);
	}
	try {
		printManager.addEventListener("printtaskrequested", onPrintTaskRequested);
	} catch (e) {}
	Windows.Graphics.Printing.PrintManager.showPrintUIAsync();
}

var PrintPlugin = function() {};

PrintPlugin.prototype.print = function(printHTML, successCB, failCB, options) {
	var document = window.document.createDocumentFragment();
	var htmlElment = document.appendChild(window.document.createElement('html'));
	MSApp.execUnsafeLocalFunction(function () {
		htmlElment.innerHTML = printHTML;
	});
	
	var printManager = Windows.Graphics.Printing.PrintManager.getForCurrentView();
	function onPrintTaskRequested(printEvent) {
		var printTask = printEvent.request.createPrintTask(options.title||document.title||"Print Document", function (args) {
			args.setSource(MSApp.getHtmlPrintDocumentSource(document));
			if (options.landscape)
				printTask.options.orientation = Windows.Graphics.Printing.PrintOrientation.landscape;
			printTask.oncompleted = function(printTaskCompletionEvent) {
				if (printTaskCompletionEvent.completion === Windows.Graphics.Printing.PrintTaskCompletion.submitted) {
					if (typeof successCB == "function")
						successCB({'success':true});
				} else {
					var error = '';
					switch(printTaskCompletionEvent.completion) {
						case Windows.Graphics.Printing.PrintTaskCompletion.abandoned: error = "Abandoned"; break;
						case Windows.Graphics.Printing.PrintTaskCompletion.canceled: error = "Cancelled"; break;
						case Windows.Graphics.Printing.PrintTaskCompletion.failed: error = "Failed"; break;
					}
					if (typeof failCB == "function")
						failCB({'error':error});
				}
			};
		});
		printManager.removeEventListener("printtaskrequested", onPrintTaskRequested);
	}
	try {
		printManager.addEventListener("printtaskrequested", onPrintTaskRequested);
	} catch (e) {}
	Windows.Graphics.Printing.PrintManager.showPrintUIAsync();
}

PrintPlugin.prototype.isPrintingAvailable = function(callback) {
	var dummyFunc = function(a){};
	var available = true;
	
	try {
		var printManager = Windows.Graphics.Printing.PrintManager.getForCurrentView();
		printManager.addEventListener("printtaskrequested", dummyFunc);
		printManager.removeEventListener("printtaskrequested", dummyFunc);
	} catch(e) {
		available = false;
	}
	
	callback(available);
};

window.plugins = window.plugins || {};
window.plugins.printPlugin = new PrintPlugin();
