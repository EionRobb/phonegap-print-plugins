
#import <Foundation/Foundation.h>

#ifdef PHONEGAP_FRAMEWORK
#import <PhoneGap/PGPlugin.h>
#else
#import "PGPlugin.h"
#endif


@interface PrintPDFPlugin : PGPlugin <UIWebViewDelegate, UIDocumentInteractionControllerDelegate> {
    NSString* successCallback;
    NSString* failCallback;
    NSString* printHTML;
	BOOL greyscale;
	BOOL landscape;
    
    //Options
    NSInteger dialogLeftPos;
    NSInteger dialogTopPos;
    
    UIDocumentInteractionController *doccon;
}

@property (nonatomic, copy) NSString* successCallback;
@property (nonatomic, copy) NSString* failCallback;
@property (nonatomic, copy) NSString* printHTML;

//Print Settings
@property NSInteger dialogLeftPos;
@property NSInteger dialogTopPos;

//Print HTML
- (void) print:(NSMutableArray*)arguments withDict:(NSMutableDictionary*)options;

//Find out whether printing is supported on this platform.
- (void) isPrintingAvailable:(NSMutableArray*)arguments withDict:(NSMutableDictionary*)options;

@end


@interface PrintPDFPlugin (Private)
-(void) doPrint;
-(void) callbackWithFuntion:(NSString *)function withData:(NSString *)value;
- (BOOL) isPrintServiceAvailable;
@end

@interface ZeroMarginPageRenderer : UIPrintPageRenderer

@end


@implementation ZeroMarginPageRenderer

- (CGRect)paperRect {
    //return UIGraphicsGetPDFContextBounds();
    //return [super paperRect];
    //return CGRectMake(0.0f, 0.0f, 8.27f * 72, 11.69f * 72);
    return CGRectMake(0.0f, 0.0f, 595.0f, 842.0f);
}

- (CGRect)printableRect {
    return [self paperRect];
}

@end


@implementation PrintPDFPlugin

@synthesize successCallback, failCallback, printHTML, dialogTopPos, dialogLeftPos;

/*
 Is printing available. Callback returns true/false if printing is available/unavailable.
 */
- (void) isPrintingAvailable:(NSMutableArray*)arguments withDict:(NSMutableDictionary*)options{
    NSUInteger argc = [arguments count];
	
	if (argc < 1) {
		return;	
	}
    
    
    NSString *callBackFunction = [arguments objectAtIndex:0];
    [self callbackWithFuntion:callBackFunction withData:
            [NSString stringWithFormat:@"{available: %@}", ([self isPrintServiceAvailable] ? @"true" : @"false")]];
    
}

- (void) print:(NSMutableArray*)arguments withDict:(NSMutableDictionary*)options{
    NSUInteger argc = [arguments count];
	
	if (argc < 1) {
		return;	
	}
    self.printHTML = [arguments objectAtIndex:0];
    
    if (argc >= 2){
        self.successCallback = [arguments objectAtIndex:1];
    }
    
    if (argc >= 3){
        self.failCallback = [arguments objectAtIndex:2];
    }
    
    if (argc >= 4){
        self.dialogLeftPos = [[arguments objectAtIndex:3] intValue];
    }
    
    if (argc >= 5){
        self.dialogTopPos = [[arguments objectAtIndex:4] intValue];
    }
	
	if (argc >= 6){
		self.landscape = [[arguments objectAtIndex:5] boolValue];
	}
	
	if (argc >= 7){
		self.greyscale = [[arguments objectAtIndex:6] boolValue];
	}
	
    [self doPrint];
}

- (void) doPrint{
    if (![self isPrintServiceAvailable]){
        [self callbackWithFuntion:self.failCallback withData: @"{success: false, available: false}"];
        
        return;
    }
    
    UIPrintInteractionController *controller = [UIPrintInteractionController sharedPrintController];
    
    if (!controller){
        return;
    }
    
	if ([UIPrintInteractionController isPrintingAvailable]) {
		//Set the printer settings
		UIPrintInfo *printInfo = [UIPrintInfo printInfo];
		if (self.greyscale)
			printInfo.outputType = UIPrintInfoOutputGrayscale;
		else
			printInfo.outputType = UIPrintInfoOutputGeneral;
		if (self.landscape)
			printInfo.orientation = UIPrintInfoOrientationLandscape;
		else
			printInfo.orientation = UIPrintInfoOrientationPortrait;
        controller.printInfo = printInfo;
        
        //Set the base URL to be the www directory.
        NSString *dbFilePath = [[NSBundle mainBundle] pathForResource:@"www" ofType:nil ];
        NSURL *baseURL = [NSURL fileURLWithPath:dbFilePath];
                
        //Load page into a webview and use its formatter to print the page 
		UIWebView *webViewPrint = [[UIWebView alloc] init];
        webViewPrint.scalesPageToFit = YES;
		webViewPrint.dataDetectorTypes = UIDataDetectorTypeNone;
		webViewPrint.userInteractionEnabled = NO;
		webViewPrint.autoresizingMask = (UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight)
		[webViewPrint setDelegate:self];
        [webViewPrint loadHTMLString:printHTML baseURL:baseURL];
		// Code continues in webViewDidFinishLoad
	}
}

- (void)webViewDidFinishLoad:(UIWebView *)webViewPrint
{
	//Get formatter for web (note: margin not required - done in web page)
	UIViewPrintFormatter *viewFormatter = [webViewPrint viewPrintFormatter];
	viewFormatter.contentInsets = UIEdgeInsetsMake(0.0f, 0.0f, 0.0f, 0.0f);

	ZeroMarginPageRenderer *renderer = [[ZeroMarginPageRenderer alloc] init];
	renderer.headerHeight = 0.0f;
	renderer.footerHeight = 0.0f;
	[renderer addPrintFormatter:viewFormatter startingAtPageAtIndex:0];
	controller.printPageRenderer = renderer;
	controller.showsPageRange = NO;

	NSMutableData *pdfData = [NSMutableData data];
	CGSize pageSize = CGSizeMake(595,842); //kPaperSizeA4;
	UIGraphicsBeginPDFContextToData(pdfData, CGRectMake(0, 0, pageSize.width, pageSize.height), nil);
	[renderer prepareForDrawingPages:NSMakeRange(0, renderer.numberOfPages)];
	CGRect bounds = UIGraphicsGetPDFContextBounds();
	for(int i = 0; i < renderer.numberOfPages; i++)
	{
		UIGraphicsBeginPDFPage();
		[renderer drawPageAtIndex:i inRect:bounds];
	}
	UIGraphicsEndPDFContext();
	
	//NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
	//NSString *documentsDirectory = [paths objectAtIndex:0];
	//NSString *tempfile = [documentsDirectory stringByAppendingPathComponent:@"print.pdf"];
	NSString *tempfile = [NSTemporaryDirectory() stringByAppendingPathComponent:@"print.pdf"];
	[pdfData writeToFile:tempfile atomically:YES];
	NSURL *printfileurl = [NSURL fileURLWithPath:tempfile];
	doccon = [UIDocumentInteractionController interactionControllerWithURL:printfileurl];
	doccon.delegate = self;
	doccon.UTI = @"com.adobe.pdf";
	doccon.name = [webViewPrint stringByEvaluatingJavaScriptFromString:@"document.title||'Print Document'"];
	
	[doccon presentOptionsMenuFromRect:CGRectMake(self.dialogLeftPos, self.dialogTopPos, 0, 0) inView:self.webView animated:YES];
	//[doccon presentOpenInMenuFromRect:CGRectMake(self.dialogLeftPos, self.dialogTopPos, 0, 0) inView:self.webView animated:YES];
	[doccon retain]; //TODO release later
}

-(BOOL) isPrintServiceAvailable{
  
    Class myClass = NSClassFromString(@"UIPrintInteractionController");
    if (myClass) {
        UIPrintInteractionController *controller = [UIPrintInteractionController sharedPrintController];
        return (controller != nil) && [UIPrintInteractionController isPrintingAvailable];
    }
  
    
    return NO;
}

#pragma mark -
#pragma mark Return messages
                 
-(void) callbackWithFuntion:(NSString *)function withData:(NSString *)value{
    if (!function || [@"" isEqualToString:function]){
        return;
    }
    
    NSString* jsCallBack = [NSString stringWithFormat:@"%@(%@);", function, value];
    [self writeJavascript: jsCallBack];
}

- (BOOL) documentInteractionController: (UIDocumentInteractionController *) controller canPerformAction: (SEL) action
{
    if (action == @selector (print:) &&
        [UIPrintInteractionController canPrintURL: controller.URL]) {
        return YES;
    } else {
        return NO;
    }
}

- (UIViewController *) documentInteractionControllerViewControllerForPreview: (UIDocumentInteractionController *) controller
{
    return [self appViewController];
}

- (void) documentInteractionController:(UIDocumentInteractionController *)controller didEndSendingToApplication:(NSString *)application
{
	[self callbackWithFuntion:self.successCallback withData: @"{success: true, available: true}"];
	[controller release];
}

- (void) documentInteractionControllerDidDismissOptionsMenu:(UIDocumentInteractionController *)controller
{
    [self callbackWithFuntion:self.failCallback withData:@"{success: false, available: true, error: \"dismissed\"}"];
	[controller release];
}

- (void) documentInteractionControllerDidDismissOpenInMenu:(UIDocumentInteractionController *)controller
{
    [self callbackWithFuntion:self.failCallback withData:@"{success: false, available: true, error: \"dismissed\"}"];
	[controller release];
}

- (BOOL) documentInteractionController:(UIDocumentInteractionController *)controller performAction:(SEL)action
{
    if (action == @selector(print:))
    {
        // Check if the item can be printed
        if (![UIPrintInteractionController canPrintURL:controller.URL])
        {
            [self callbackWithFuntion:self.failCallback withData:[NSString stringWithFormat:@"{success: false, available: true, error: \"Item is not printable: %@\"}", error.controller.URL]];
			[controller release];
            return NO;
        }
        
        
		void (^completionHandler)(UIPrintInteractionController *, BOOL, NSError *) =
		^(UIPrintInteractionController *printController, BOOL completed, NSError *error) {
            if (!completed || error) {
                [self callbackWithFuntion:self.failCallback withData:
                 [NSString stringWithFormat:@"{success: false, available: true, error: \"%@\"}", error.localizedDescription]];
			}
            else{
                [self callbackWithFuntion:self.successCallback withData: @"{success: true, available: true}"];
            }
			[controller release];
        };
        
        [UIPrintInteractionController sharedPrintController].printingItem = controller.URL;
        if (UI_USER_INTERFACE_IDIOM() == UIUserInterfaceIdiomPad) {
            [[UIPrintInteractionController sharedPrintController] presentFromRect:CGRectMake(self.dialogLeftPos, self.dialogTopPos, 0, 0) inView:self.webView animated:YES completionHandler:completionHandler];
        } else {
            [[UIPrintInteractionController sharedPrintController] presentAnimated:YES completionHandler:completionHandler];
        }
    }
    
    return YES;
}

@end


