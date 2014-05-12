package com.phonegap.plugins;

import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.print.PrintAttributes;
import android.print.PrintJob;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.os.Build;
import android.content.Context;

import com.phonegap.api.PhonegapActivity;
import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class KitKatPrintPlugin extends Plugin {

	@Override
	public PluginResult execute(String action, final JSONArray args, final String callbackId) {
		if (action.equals("supported")) {
			boolean supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
			return new PluginResult(PluginResult.Status.OK, supported);
		}
		
		if (action.equals("print")) {
            final PhonegapActivity ctx = this.ctx;
			final Plugin self = this;

            Runnable runnable = new Runnable() {
                public void run() {
                    String htmlData = args.optString(0, "<html></html>");
			WebView wv = new WebView(ctx);
			wv.setVisibility(View.INVISIBLE);
			wv.getSettings().setJavaScriptEnabled(false);
			wv.getSettings().setDatabaseEnabled(true);
			wv.setWebViewClient(new WebViewClient() {
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return false;
                }
				public void onPageFinished(WebView webview, String url) {
					PrintDocumentAdapter pda = webview.createPrintDocumentAdapter();
					PrintManager printManager = (PrintManager) ctx.getSystemService(Context.PRINT_SERVICE);
					PrintAttributes attributes = null;
					
					PrintAttributes.Builder builder = new PrintAttributes.Builder();
					//builder.setColorMode(PrintAttributes.COLOR_MODE_COLOR);
					//builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4);
					//builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape());
					//builder.setMinMargins(new PrintAttributes.Margins(394, 394, 394, 394)); //10mm margin
					builder.setMinMargins(PrintAttributes.Margins.NO_MARGINS);
					//builder.setResolution();
					attributes = builder.build();
					
					String title = webview.getTitle();
					if (title.isEmpty() || title.equals("about:blank"))
						title = "Print Document";
					PrintJob pj = printManager.print(title, pda, attributes);
					
					if (pj == null || pj.isCancelled() || pj.isFailed()) {
						self.error(new PluginResult(PluginResult.Status.ERROR, "Couldn't print"), callbackId);
					} else {
						self.success(new PluginResult(PluginResult.Status.OK, ""), callbackId);
					}
				}
			});
			
			//Set base URI to the assets/www folder
			String baseURL = self.webView.getUrl();
			baseURL = baseURL.substring(0, baseURL.lastIndexOf('/') + 1);

			wv.loadDataWithBaseURL(baseURL, htmlData, "text/html", "UTF-8", null);
                }};
            this.ctx.runOnUiThread(runnable);
			
			PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
			r.setKeepCallback(true);
			return r;
		}
		
		return new PluginResult(PluginResult.Status.INVALID_ACTION);
	}
}
