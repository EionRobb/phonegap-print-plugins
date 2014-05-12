package com.phonegap.plugins;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UnknownFormatConversionException;

import org.json.JSONArray;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.drawable.PictureDrawable;
import android.net.Uri;
import android.os.Environment;
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

public class PrintPlugn extends Plugin {


  String printAppIds[] = {"kr.co.iconlab.BasicPrintingProfile",
							"com.blueslib.android.app",
							"com.brother.mfc.brprint",
							"com.brother.ptouch.sdk",
							"jp.co.canon.bsd.android.aepp.activity",
							"com.pauloslf.cloudprint",
							"com.dlnapr1.printer",
							"com.dell.mobileprint",
							"com.printjinni.app.print",
							"epson.print",
							"jp.co.fujixerox.prt.PrintUtil.PCL",
							"jp.co.fujixerox.prt.PrintUtil.Karin",
							"com.hp.android.print",
							"com.blackspruce.lpd",
							"com.threebirds.notesprint",
							"com.xerox.mobileprint",
							"com.zebra.kdu",
							"net.jsecurity.printbot",
							"com.dynamixsoftware.printhand",
							"com.dynamixsoftware.printhand.premium",
							"com.sec.print.mobileprint",
							"com.rcreations.send2printer",
							"com.ivc.starprint",
							"com.threebirds.easyviewer",
							"com.woosim.android.print",
							"com.woosim.bt.app",
							"com.zebra.android.zebrautilities",
							};
/*
	String printAppIds[] = ["Bluetooth Smart Printing" "kr.co.iconlab.BasicPrintingProfile",
							"Bluetooth SPP Printer API" "com.blueslib.android.app",
							"Brother iPrint&Scan" "com.brother.mfc.brprint",
							"Brother Print Library" "com.brother.ptouch.sdk",
							"Canon Easy-PhotoPrint" "jp.co.canon.bsd.android.aepp.activity",
							"Cloud Print" "com.pauloslf.cloudprint",
							"CMC DLNA Print Client" "com.dlnapr1.printer",
							"Dell Mobile Print" "com.dell.mobileprint",
							"PrintJinni" "com.printjinni.app.print",
							"Epson iPrint" "epson.print",
							"Fuji Xerox Print Utility" "jp.co.fujixerox.prt.PrintUtil.PCL",
							"Fuji Xeros Print&Scan (S)" "jp.co.fujixerox.prt.PrintUtil.Karin",
							"HP ePrint" "com.hp.android.print",
							"Let's Print Droid" "com.blackspruce.lpd",
							"NotesPrint print your notes" "com.threebirds.notesprint",
							"Print Portal (Xerox)" "com.xerox.mobileprint",
							"Print Station (Zebra)" "com.zebra.kdu",
							"PrintBot" "net.jsecurity.printbot",
							"PrintHand Mobile Print" "com.dynamixsoftware.printhand",
							"PrintHand Mobile Print Premium" "com.dynamixsoftware.printhand.premium",
							"Samsung Mobile Print" "com.sec.print.mobileprint",
							"Send 2 Printer" "com.rcreations.send2printer",
							"StarPrint, Just for Print!" "com.ivc.starprint",
							"WiFi Print - EasyReader" "com.threebirds.easyviewer",
							"Woosim BT printer" "com.woosim.android.print",
							"WoosimPrinter" "com.woosim.bt.app",
							"Zebra Utilities" "com.zebra.android.zebrautilities",
							];
*/
	
    public static File saveBitmapToTempFile( Bitmap b, Bitmap.CompressFormat format )
    throws IOException, UnknownFormatConversionException
    {
    	File tempFile = null;
    	    	
    	// save to temporary file
    	File dir = new File( Environment.getExternalStorageDirectory(), "temp" );
		if( dir.exists() || dir.mkdirs() )
    	{
			FileOutputStream fos = null;
			try
			{
				String strExt = null;
				switch( format )
				{
					case PNG:
						strExt = ".pngx";
						break;
						
					case JPEG:
						strExt = ".jpgx";
						break;
						
					default:
						throw new UnknownFormatConversionException( "unknown format: " + format );
				}
				File f = File.createTempFile( "bitmap", strExt, dir );
				fos = new FileOutputStream( f );
				b.compress( format, 100, fos );
				tempFile = f;
			}
			finally
			{
				try
				{
					fos.close();
				}
				catch( Exception e ) {}
			}
    	}		
    	
    	return tempFile;
    }

	@Override
	public PluginResult execute(String action, final JSONArray args, final String callbackId) {
		if (action.equals("scan")) {
			JSONArray obj = new JSONArray();
			PackageManager pm = this.ctx.getPackageManager();
			for(int i = 0; i < printAppIds.length; i++)
			{
				try { 
					PackageInfo pi = pm.getPackageInfo( printAppIds[i], 0 );
					if( pi != null )
					{
						obj.put(printAppIds[i]);
					}
				} catch (PackageManager.NameNotFoundException e) {}
			}
			
			return new PluginResult(PluginResult.Status.OK, obj);
		}
		
		if (action.equals("print")) {
			final PhonegapActivity ctx = this.ctx;
			final PrintAppScanner self = this;
			Runnable runnable = new Runnable() {
				public void run() {
					String printIntent = "android.intent.action.SEND";
					String dataType = "image/png";
				
					String htmlData = args.optString(0, "<html></html>");
					String optionalAppId = args.optString(1);
					
					//Check for special cases that can receive HTML
					if (optionalAppId.equals("com.rcreations.send2printer") ||
						optionalAppId.equals("com.dynamixsoftware.printershare")) {
						dataType = "text/html";
					}
					
					/*//Check for special cases that have special Intent's
					if (optionalAppId.equals("com.rcreations.send2printer")) {
						printIntent = "com.rcreations.send2printer.print";
					} else if (optionalAppId.equals("com.dynamixsoftware.printershare")) {
						printIntent = "android.intent.action.VIEW";
					} else if (optionalAppId.equals("com.hp.android.print")) {
						printIntent = "org.androidprinting.intent.action.PRINT";
					}*/
					
					final Intent i = new Intent( printIntent );
					if (!optionalAppId.isEmpty()) {
						i.setPackage(optionalAppId);
					}
					i.setType( dataType );
					if (dataType.equals("text/html")) {
						i.putExtra( Intent.EXTRA_TEXT, htmlData );
						try {
							ctx.startActivity( i );
							self.success(new PluginResult(PluginResult.Status.OK, ""), callbackId);
						} catch (Exception e) {
							e.printStackTrace();
							self.error(new PluginResult(PluginResult.Status.ERROR, "Couldn't start activity"), callbackId);
						}
					} else {
						//Create WebView
						WebView wv = new WebView(ctx);
						wv.setVisibility(View.INVISIBLE);
						wv.getSettings().setJavaScriptEnabled(false);
						wv.getSettings().setDatabaseEnabled(true);
						
						wv.setPictureListener(new WebView.PictureListener() {
							@Deprecated
							public void onNewPicture(WebView view, Picture picture) {
								if(picture != null)
								{
									try
									{
										Bitmap bitmap = Bitmap.createBitmap(picture.getWidth(), picture.getHeight(), Bitmap.Config.ARGB_8888);
										
										Canvas canvas = new Canvas(bitmap);
										picture.draw(canvas);
										
										File tempFile = saveBitmapToTempFile(bitmap, Bitmap.CompressFormat.PNG);
										Uri uri = Uri.fromFile( tempFile );
										i.putExtra(Intent.EXTRA_STREAM, uri);
										ctx.startActivity( i );
										
										self.success(new PluginResult(PluginResult.Status.OK, ""), callbackId);
									}
									catch(Exception e)
									{
										e.printStackTrace();
										self.error(new PluginResult(PluginResult.Status.ERROR, "Can't save picture"), callbackId);
									}
								} else {
									self.error(new PluginResult(PluginResult.Status.ERROR, "No picture"), callbackId);
								}
								
								ViewGroup vg = (ViewGroup)(view.getParent());
								vg.removeView(view);
							}
						});

						wv.setWebViewClient(new WebViewClient() {
							public void onPageFinished(WebView webview, String url) {
								Picture picture = webview.capturePicture();
							}
						});
						
						//Set base URI to the assets/www folder
						String baseURL = self.webView.getUrl();
						baseURL = baseURL.substring(0, baseURL.lastIndexOf('/') + 1);
						
						//Set content of WebView to htmlData
						ctx.addContentView(wv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
						
						wv.loadDataWithBaseURL(baseURL, htmlData, "text/html", "UTF-8", null);
					}
				}
			};
			this.ctx.runOnUiThread(runnable);
			

			PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
			r.setKeepCallback(true);
			return r;
		}


		return new PluginResult(PluginResult.Status.INVALID_ACTION);

	}
	
	public boolean isSynch(String action)
	{
		if (action.equals("print")) {
			return true;
		}
		return false;
	}

}
