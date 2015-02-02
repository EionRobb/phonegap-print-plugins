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

import com.dynamixsoftware.intentapi.*;

public class PrintHandPlugin extends Plugin {

	private static String TAG = "PrintHandPlugin";
	private IntentAPI intentAPI;
	private File lastPrintedFile;
	
	public boolean connected = false;
	private String lastCallbackId = null;
	private int lastTotalPageCount = 0;
	
	private ProgressDialog progressBar = null;
	private boolean progressBarCancelled = false;
	
	@Override
	public PluginResult execute(final String action, final JSONArray args, final String callbackId) {
		final PrintHandPlugin self = this;
		
		if (action.equals("print")) {
			progressStart("Printing...", null);
			if (connected) {
				try {
					if (intentAPI.getCurrentPrinter() == null) {
						intentAPI.setupCurrentPrinter();
						if (intentAPI.getCurrentPrinter() == null) {
							return new PluginResult(PluginResult.Status.ERROR, "Need to set up printer");
						}
					}
					
					String htmlData = args.optString(0, "<html></html>");
					
					self.lastCallbackId = callbackId;
					print(htmlData);
					
					PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
					r.setKeepCallback(true);
					return r;
					
				} catch (Exception e) {
					return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
				}
			} else {
				if (!isPrintHandInstalled()) {
					openPrintHandPlayStorePage();
					progressStop();
					return new PluginResult(PluginResult.Status.ERROR, "PrintHand is not installed");
				}
				
				startService();
				
				// Wait for the service to be connected
				Runnable runnable = new Runnable() {
					public void run() {
						new Handler().postDelayed( new Runnable() {
							@Override
							public void run() {
								PluginResult result = execute(action, args, callbackId);
								if (result.getStatus() == PluginResult.Status.ERROR.ordinal()) {
									self.error(result, callbackId);
								} else if (result.getStatus() == PluginResult.Status.OK.ordinal()) {
									self.success(result, callbackId);
								}
							}
						}, 2000);
					}
				};
				this.ctx.runOnUiThread(runnable);
				
				PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
				r.setKeepCallback(true);
				return r;
			}
		} else if (action.equals("supported") || action.equals("scan")) {
			return new PluginResult(PluginResult.Status.OK, this.isPrintHandInstalled());
		} else if (action.equals("setup")) {
			try {
				if (!connected) {
					startService();
				}
				intentAPI.changePrinterOptions();
				return new PluginResult(PluginResult.Status.OK);
			} catch (Exception e) {
				return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
			}
		} else if (action.equals("start")) {
			startService();
			return new PluginResult(PluginResult.Status.OK);
		} else if (action.equals("stop")) {
			stopService();
			return new PluginResult(PluginResult.Status.OK);
		} else {
			return new PluginResult(PluginResult.Status.INVALID_ACTION);
		}
	}
	
	public boolean isPrintHandInstalled() {
		boolean found = false;
		
		PackageManager pm = this.ctx.getPackageManager();
		try {
			PackageInfo pi = pm.getPackageInfo( "com.dynamixsoftware.printhand.premium", 0 );
			if(pi != null)
			{
				found = true;
			}
		} catch (PackageManager.NameNotFoundException e) {}
		
		
		if (!found) {
			try {
				PackageInfo pi = pm.getPackageInfo( "com.dynamixsoftware.printhand", 0 );
				if(pi != null)
				{
					found = true;
				}
			} catch (PackageManager.NameNotFoundException e) {}
		}
		
		return found;
	}
	
	public void openPrintHandPlayStorePage() {
		try {
			this.ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.dynamixsoftware.printhand")));
		} catch (android.content.ActivityNotFoundException anfe) {
			this.ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.dynamixsoftware.printhand")));
		}
	}
	
	public void startService() {
		final PrintHandPlugin self = this;
		if (intentAPI == null || !intentAPI.isServiceRunning()) {
			intentAPI = new IntentAPI(this.ctx);
			try {
				intentAPI.runService(new IServiceCallback.Stub() {
					private int numberOfLibrariesToDownload = 0;
					private int numberOfLibrariesDownloaded = 0;
					
					@Override
					public void onServiceDisconnected() {
						connected = false;
						Log.d(TAG, "Service disconnected");
					}
					
					@Override
					public void onServiceConnected() {
						connected = true;
						Log.d(TAG, "Service connected");
						
						try {
							intentAPI.setCallback(new IPrintCallback.Stub() {
								// Order of calls: start, startingPrintJob, preparePage, sendingPage, finishingPrintJob, finish
							
								@Override
								public void finish(Result result, int pagesPrinted) {
									Log.d(TAG, "finish, Result " + result + "; Result type " + result.getType() + "; Result message " + result.getType().getMessage() + "; pages printed " + pagesPrinted);
									
									progressStop();
									self.sendJavascript("PhoneGap.fireWindowEvent('afterprint');");
								}
								
								@Override
								public void finishingPrintJob() {
									Log.d(TAG, "finishingPrintJob");
								}
								
								@Override
								public boolean needCancel() {
									Log.d(TAG, "needCancel");
									
									return self.progressBarCancelled;
								}
								
								@Override
								public void preparePage(int pageNum) {
									Log.d(TAG, "preparePage pageNum " + pageNum);
									
									int progress = pageNum * 100 / self.lastTotalPageCount;
									self.progressValue(progress);
									
									self.sendJavascript("PhoneGap.fireWindowEvent('printhandprintprogress', " + progress + ");");
								}
								
								@Override
								public void sendingPage(int pageNum, int pageProgress) {
									Log.d(TAG, "sendingPage pageNum " + pageNum + " pageProgress " + pageProgress);
									
									int progress = (pageProgress * (pageNum + 1) / self.lastTotalPageCount) + (pageNum * 100 / self.lastTotalPageCount);
									self.progressValue(progress);
									
									self.sendJavascript("PhoneGap.fireWindowEvent('printhandprintprogress', " + progress + ");");
								}
								
								@Override
								public void start() {
									Log.d(TAG, "start");
								}
								
								@Override
								public void startingPrintJob() {
									Log.d(TAG, "startingPrintJob");
									
									self.progressStart("Printing...", null);
									self.sendJavascript("PhoneGap.fireWindowEvent('printhandprintprogress', 0);");
								}
								
							});
						} catch (RemoteException e) {
							Log.e(TAG, "Error during setCallback() " + e.getMessage());
							e.printStackTrace();
						}
					}

					@Override
					public void onFileOpen(int progress, int finished) {
						Log.d(TAG, "onFileOpen progress " + progress + "; finished " + (finished == 1 ? true : false));
						
						if (finished == 1 && numberOfLibrariesToDownload == 0) {
							//Delete temporary file
							self.lastPrintedFile.delete();
						}
					}

					@Override
					public void onLibraryDownload(int progress) throws RemoteException {
						Log.d(TAG, "onLibraryDownload progress " + progress);
						
						if (self.getProgressBar() == null) {
							// Unexpected
							self.progressStart(null, "PrintHand is downloading more required libraries...");
							numberOfLibrariesToDownload = 1;
						}
						
						self.sendJavascript("PhoneGap.fireWindowEvent('printhandlibrarydownload', " + progress + ");");
						self.progressValue(progress / numberOfLibrariesToDownload + 100 * numberOfLibrariesDownloaded / numberOfLibrariesToDownload);
						
						if (progress == 99) {
							numberOfLibrariesDownloaded++;
							if (numberOfLibrariesDownloaded >= numberOfLibrariesToDownload) {
								self.progressStop();
								numberOfLibrariesDownloaded = numberOfLibrariesToDownload = 0;
							}
						}
					}

					@Override
					public boolean onRenderLibraryCheck(boolean hasRenderLibrary, boolean hasFontsLibrary) throws RemoteException {
						Log.d(TAG, "onRenderLibraryCheck render library " + hasRenderLibrary + "; fonts library " + hasFontsLibrary);
						
						numberOfLibrariesDownloaded = numberOfLibrariesToDownload = 0;
						
						if (!hasRenderLibrary) numberOfLibrariesToDownload++;
						if (!hasFontsLibrary) numberOfLibrariesToDownload++;
						
						if (numberOfLibrariesToDownload <= 0)
							numberOfLibrariesToDownload = 1;
						
						self.progressStart(null, "PrintHand is downloading required libraries...");
						
						return true;
					}

				});
			} catch (RemoteException e) {
				Log.e(TAG, "Error during startService() " + e.getMessage());
				e.printStackTrace();
			}
		}
	}
	
	public void stopService() {
		if (intentAPI != null && intentAPI.isServiceRunning()) {
			intentAPI.stopService(null);
		}
		intentAPI = null;
	}
	
	public void print(final String htmlData) throws RemoteException, IOException {
		if (intentAPI != null && intentAPI.isServiceRunning() && connected) {
		
			final PhonegapActivity ctx = this.ctx;
			final PrintHandPlugin self = this;
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

					IPrinterInfo printer = null;
					try {
						printer = intentAPI.getCurrentPrinter();
						if (printer != null) {
							xDpi = printer.getPrinterContext().getHResolution();
							yDpi = printer.getPrinterContext().getVResolution();

							// in dots
							paperWidth = printer.getPrinterContext().getPaperWidth() * xDpi / 72;
							paperHeight = printer.getPrinterContext().getPaperHeight() * yDpi / 72;
						}
					} catch (RemoteException e) {}
					
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
								f = File.createTempFile("PrintHand", ".pdf", dir);
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
													intentAPI.print(title, "application/pdf", Uri.fromFile(f));
													
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
		} else {
			throw new RemoteException("Not connected to PrintHand");
		}
	}
	
	public ProgressDialog getProgressBar() {
		return this.progressBar;
	}
	
	public void progressStart(final String title, final String message) {
		if (this.progressBar != null) {
			this.progressBar.dismiss();
			this.progressBar = null;
		}
		
		final PrintHandPlugin self = this;
		this.progressBarCancelled = false;
		
		Runnable runnable = new Runnable() {
			public void run() {
				self.progressBar = new ProgressDialog(self.ctx);
				self.progressBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				self.progressBar.setTitle(title);
				self.progressBar.setMessage(message);
				self.progressBar.setCancelable(true);
				self.progressBar.setMax(100);
				self.progressBar.setProgress(0);
				self.progressBar.setProgressNumberFormat(null);
				self.progressBar.setOnCancelListener(
					new DialogInterface.OnCancelListener() { 
						public void onCancel(DialogInterface dialog) {
							self.progressBarCancelled = true;
							self.progressBar = null;
						}
					});
				self.progressBar.show();
			}
		};
		this.ctx.runOnUiThread(runnable);
	}
	
	public void progressValue(int value) {
		if (this.progressBar != null) {
			this.progressBar.setProgress(value);
		}		
	}
	
	public void progressStop() {
		if (this.progressBar != null) {
			this.progressBar.dismiss();
			this.progressBar = null;
		}
	}
}
