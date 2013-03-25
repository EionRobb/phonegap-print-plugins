
window.print = function() {
	var printManager = Windows.Graphics.Printing.PrintManager.getForCurrentView();
	function onPrintTaskRequested(printEvent) {
		var printTask = printEvent.request.createPrintTask(document.title, function (args) {
			args.setSource(MSApp.getHtmlPrintDocumentSource(document));
		});
		printManager.removeEventListener("printtaskrequested", onPrintTaskRequested);
	}
	try {
		printManager.addEventListener("printtaskrequested", onPrintTaskRequested);
	} catch (e) {}
	Windows.Graphics.Printing.PrintManager.showPrintUIAsync();
}
