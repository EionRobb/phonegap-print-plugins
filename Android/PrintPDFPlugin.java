package com.phonegap.plugins;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UnknownFormatConversionException;

import org.json.JSONArray;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.drawable.PictureDrawable;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.print.PageRange;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.print.PrintAttributes;
import android.print.PrintJob;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.phonegap.api.PhonegapActivity;
import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PrintPDFPlugin extends Plugin {

	private static String TAG = "PrintPDFPlugin";
	private File lastPrintedFile;
	
	private String lastCallbackId = null;
	private int lastTotalPageCount = 0;
	
	@Override
	public PluginResult execute(final String action, final JSONArray args, final String callbackId) {
		final PrintPDFPlugin self = this;
		
		if (action.equals("print")) {
			try {
				String htmlData = args.optString(0, "<html></html>");
				
				self.lastCallbackId = callbackId;
				print(htmlData);
				
				
				PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
				r.setKeepCallback(true);
				return r;
				
			} catch (Exception e) {
				return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
			}
		} else if (action.equals("supported") || action.equals("scan")) {
			return new PluginResult(PluginResult.Status.OK, true);
		} else if (action.equals("start")) {
			return new PluginResult(PluginResult.Status.OK);
		} else if (action.equals("stop")) {
			return new PluginResult(PluginResult.Status.OK);
		} else {
			return new PluginResult(PluginResult.Status.INVALID_ACTION);
		}
	}
	
	public void print(final String htmlData) throws RemoteException, IOException {
		
		final PhonegapActivity ctx = this.ctx;
		final PrintPDFPlugin self = this;
		Runnable runnable = new Runnable() {
			public void run() {
				WebView wv = new WebView(ctx);
				wv.setVisibility(View.INVISIBLE);
				wv.getSettings().setJavaScriptEnabled(false);
				wv.getSettings().setDatabaseEnabled(true);
				
				
				// default
				int xDpi = 300;
				int yDpi = 300;
				int paperWidth = 2481;
				int paperHeight = 3507;
				
				wv.setMinimumWidth(paperWidth);
				wv.setInitialScale(xDpi / 72 * 100);
				
				wv.setWebViewClient(new WebViewClient() {
					public boolean shouldOverrideUrlLoading(WebView view, String url) {
						return false;
					}
					public void onPageFinished(final WebView webview, String url) {
						
						final PrintDocumentAdapter pda = webview.createPrintDocumentAdapter();
						PrintManager printManager = (PrintManager) ctx.getSystemService(Context.PRINT_SERVICE);
						PrintAttributes attributes = null;
						
						File dir = Environment.getExternalStorageDirectory();
						final ParcelFileDescriptor fileDescriptor;
						final File f;
						try {
							f = File.createTempFile("OpmetrixPrint", ".pdf", dir);
							Log.d(TAG, "Created temp print file at " + f.getAbsolutePath());
							fileDescriptor = ParcelFileDescriptor.open(f, (ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_READ_WRITE));
						} catch (Exception e) {
							e.printStackTrace();
							return;
						}
						self.lastPrintedFile = f;
						
						PrintAttributes.Builder builder = new PrintAttributes.Builder();
						//builder.setColorMode(PrintAttributes.COLOR_MODE_COLOR);
						//builder.setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME);
						builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4);
						//builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape());
						//builder.setMinMargins(new PrintAttributes.Margins(394, 394, 394, 394)); //10mm margin
						builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS);
						builder.setResolution(new PrintAttributes.Resolution("300x300", "300x300", 300, 300));
						attributes = builder.build();
						
						// Hack the PrintDocumentAdapter to save as a PDF
						pda.onStart();
						pda.onLayout(null, attributes, new CancellationSignal(), new android.print.LayoutResultCallbackWrapper() {
							@Override
							public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
								try {
									pda.onWrite(new PageRange[] {PageRange.ALL_PAGES}, fileDescriptor, new CancellationSignal(), new android.print.WriteResultCallbackWrapper() {
										@Override
										public void onWriteFinished(PageRange[] pages) {
											try {
												fileDescriptor.close();
												
												int totalPageCount = 0;
												if (pages != null && pages.length > 0) {
													// Not supposed to be empty, but hey
													for(int i = 0; i < pages.length; i++) {
														totalPageCount += pages[i].getEnd() - pages[i].getStart() + 1;
													}
												}
												self.lastTotalPageCount = totalPageCount;
												Log.d(TAG, "Total pages to print: " + totalPageCount);
												
												String title = webview.getTitle();
												if (title.isEmpty())
													title = "Print Document";
												
												//send intent to open pdf
												final Intent intent = new Intent( Intent.ACTION_SEND );
												intent.setType("application/pdf");
												intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
												intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
												self.ctx.startActivity(Intent.createChooser(intent, "Print using..."));
												
												f.deleteOnExit();
												pda.onFinish();
												
												ViewGroup vg = (ViewGroup)(webview.getParent());
												if (vg != null)
													vg.removeView(webview);
												
												if (self.lastCallbackId != null) {
													self.success(new PluginResult(PluginResult.Status.OK, ""), self.lastCallbackId);
													self.lastCallbackId = null;
												}
											} catch (Exception e) {
												e.printStackTrace();
												if (self.lastCallbackId != null) {
													self.error(new PluginResult(PluginResult.Status.ERROR, e.getMessage()), self.lastCallbackId);
													self.lastCallbackId = null;
												}
											}
										}
									});
								} catch (Exception e) {
									e.printStackTrace();
									if (self.lastCallbackId != null) {
										self.error(new PluginResult(PluginResult.Status.ERROR, e.getMessage()), self.lastCallbackId);
										self.lastCallbackId = null;
									}
								}
							}
						}, null);
						
					}
				});
				
				//Set base URI to the assets/www folder
				String baseURL = self.webView.getUrl();
				baseURL = baseURL.substring(0, baseURL.lastIndexOf('/') + 1);
				
				//Set content of WebView to htmlData
				ctx.addContentView(wv, new ViewGroup.LayoutParams(paperWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
				
				wv.loadDataWithBaseURL(baseURL, htmlData, "text/html", "UTF-8", null);
			}
		};
		this.ctx.runOnUiThread(runnable);
		
	}
}
