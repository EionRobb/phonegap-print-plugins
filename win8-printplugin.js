var printManager = Windows.Graphics.Printing.PrintManager.getForCurrentView();
function onPrintTaskRequested(printEvent) {
  var printTask = printEvent.request.createPrintTask(document.title, function (args) {
		args.setSource(MSApp.getHtmlPrintDocumentSource(document));
	});
	printManager.removeEventListener("printtaskrequested", onPrintTaskRequested);
}
window.print = function() {
	printManager.addEventListener("printtaskrequested", onPrintTaskRequested);
	Windows.Graphics.Printing.PrintManager.showPrintUIAsync();
}
