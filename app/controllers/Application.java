package controllers;

import java.util.Arrays;
import java.util.List;
import models.CacheManager;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Catch;

import jp.co.flect.heroku.platformapi.PlatformApi;
import jp.co.flect.heroku.platformapi.PlatformApiException;
import jp.co.flect.heroku.platformapi.model.AbstractModel;
import jp.co.flect.heroku.platformapi.model.Account;
import jp.co.flect.heroku.platformapi.model.Addon;
import jp.co.flect.heroku.platformapi.model.AddonService;
import jp.co.flect.heroku.platformapi.model.Region;

public class Application extends Controller {
	
	@Catch(value=PlatformApiException.class)
	public static void handleException(PlatformApiException e) {
		CacheManager cm = new CacheManager(session.getId());
		cm.setMessage(e.getStatus() + ", " + e.getId() + ", " + e.getMessage());
		index();
	}
	
	private static PlatformApi getPlatformApi() {
		CacheManager cm = new CacheManager(session.getId());
		PlatformApi api = cm.getApi();
		if (api == null) {
			renderText("Not logined");
		}
		return api;
	}
	
	private static void renderList(String objectName, List list, Linker linker) {
		if (list == null || list.size() == 0) {
			renderText(objectName + " not found");
		}
		renderTemplate("@list", objectName, list, linker);
	}
	
	private static void renderDetail(String objectName, AbstractModel item) {
		renderTemplate("@detail", objectName, item);
	}
	
	public static void direct() throws Exception {
		String apikey = System.getenv().get("HEROKU_AUTHTOKEN");
		
		PlatformApi api = new PlatformApi(apikey);
		renderJSON(api.getAccount());
	}
	
	public static void index() {
		String appId = System.getenv().get("HEROKU_OAUTH_ID");
		String url = PlatformApi.getOAuthUrl(appId, PlatformApi.Scope.Read);
		url = url.substring(0, url.indexOf("&scope="));
		CacheManager cm = new CacheManager(session.getId());
		PlatformApi api = cm.getApi();
		String message = cm.getMessage();
		render(url, api, message);
	}
	
	public static void login(String code, String error) throws Exception {
		Logger.info("login: code=" + (code == null ? "null" : "xxx") + ", error=" + error);
		if (code == null && error == null) {
			badRequest();
		}
		CacheManager cm = new CacheManager(session.getId());
		String message = error;
		if (code != null) {
			try {
				String secret = System.getenv().get("HEROKU_OAUTH_SECRET");
				PlatformApi api = PlatformApi.authenticate(secret, code);
				api.setDebug(true);
				cm.setApi(api);
			} catch (Exception e) {
				e.printStackTrace();
				message = e.toString();
			}
		}
		cm.setMessage(message);
		index();
	}
	
	public static void reset() {
		CacheManager cm = new CacheManager(session.getId());
		cm.setApi(null);
		index();
	}
	
	public static void account() throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("Account", api.getAccount());
	}
	
	public static void apps() throws Exception {
		PlatformApi api = getPlatformApi();
		renderList("Apps", api.getAppList(), null);
	}
	
	public static void createApp(String name, String region) throws Exception {
		Region r = Region.valueOf(region);
		if (name == null || r == null) {
			badRequest();
		}
		PlatformApi api = getPlatformApi();
		renderDetail("CreateApp", api.createApp(name, r));
		
	}
	
	public static void addons(String app) throws Exception {
		PlatformApi api = getPlatformApi();
		renderList(app + " addons", api.getAddonList(app), new Linker("addon?app=" + app + "&id=", "id"));
	}
	
	public static void addon(String app, String id) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("Addon " + id, api.getAddon(app, id));
	}
	
	public static void addonServices() throws Exception {
		PlatformApi api = getPlatformApi();
		renderList("AddonServices", api.getAddonServiceList(), new Linker("addonservice?name=", "name"));
	}
	
	public static void addonService(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("AddonService " + name, api.getAddonService(name));
	}
	
	public static class Linker {
		
		private String prefix;
		private String name;
		
		public Linker(String prefix, String name) {
			this.prefix = prefix;
			this.name = name;
		}
		
		public String getName() { return this.name;}
		
		public String getLink(AbstractModel model) {
			return this.prefix + model.getAsString(this.name);
		}
	}
}