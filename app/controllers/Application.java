package controllers;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.Collections;
import models.CacheManager;
import models.ModelTester;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Catch;

import jp.co.flect.heroku.platformapi.PlatformApi;
import jp.co.flect.heroku.platformapi.PlatformApiException;
import jp.co.flect.heroku.platformapi.model.AbstractModel;
import jp.co.flect.heroku.platformapi.model.App;
import jp.co.flect.heroku.platformapi.model.Account;
import jp.co.flect.heroku.platformapi.model.Addon;
import jp.co.flect.heroku.platformapi.model.AddonService;
import jp.co.flect.heroku.platformapi.model.Region;
import jp.co.flect.heroku.platformapi.model.Release;
import jp.co.flect.heroku.platformapi.model.Collaborator;
import jp.co.flect.heroku.platformapi.model.Dyno;
import jp.co.flect.heroku.platformapi.model.Range;

public class Application extends Controller {
	
	@Catch(value=PlatformApiException.class)
	public static void handleException(PlatformApiException e) {
		CacheManager cm = new CacheManager(session.getId());
		cm.setMessage(e.getStatus() + ", " + e.getId() + ", " + e.getMessage());
		index();
	}
	
	private static Range createRange() {
		Range range = params.get("next") != null ? new Range(params.get("next")) : new Range();
		int max = 20;
		if (params.get("max") != null) {
			max = Integer.parseInt(params.get("max"));
		}
		range.setMax(max);
		if (params.get("sort") != null) {
			String field = params.get("sort");
			String order = params.get("order");
			range.setSortOrder(field, !"desc".equals(order));
		}
System.out.println("Range: " + range);
		return range;
	}
	
	private static PlatformApi getPlatformApi() {
		CacheManager cm = new CacheManager(session.getId());
		PlatformApi api = cm.getApi();
		if (api == null) {
			cm.setMessage("Not logined");
			index();
		}
		return api;
	}
	
	private static void renderList(String objectName, List list, Range range, Linker linker) throws Exception {
		if (list == null || list.size() == 0) {
			renderText(objectName + " not found");
		}
		new ModelTester().test(list);
		renderTemplate("@list", objectName, list, range, linker);
	}
	
	private static void renderDetail(String objectName, AbstractModel item, String appName, String addition) throws Exception {
		new ModelTester().test(item);
		renderTemplate("@detail", objectName, item, appName, addition);
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
		renderDetail("Account", api.getAccount(), null, null);
	}
	
	public static void changePassword(String currentPassword, String newPassword) throws Exception {
		PlatformApi api = getPlatformApi();
		api.changePassword(currentPassword, newPassword);
		CacheManager cm = new CacheManager(session.getId());
		cm.setMessage("Password changed");
		index();
	}
	
	public static void apps() throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		List<App> list = api.getAppList(range);
		Collections.sort(list, new Comparator<App>() {
			public int compare(App a1, App a2) {
				return 0 - a1.getUpdatedAt().compareTo(a2.getUpdatedAt());
			}
		});
		renderList("Apps", list, range, new Linker("app?name=", "name"));
	}
	
	public static void createApp(String name, String region) throws Exception {
		Region r = Region.valueOf(region);
		if (name == null || r == null) {
			badRequest();
		}
		PlatformApi api = getPlatformApi();
		App app = api.createApp(name, r);
		app(app.getName());
	}

	public static void app(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		App item = api.getApp(name);
		String objectName = item.getName();
		Map<String, String> configVars = null;
		try {
			configVars = api.getConfigVars(name).getMap();
		} catch (Exception e) {
			configVars = new HashMap<String, String>();
			configVars.put("ConfigVars get error", e.toString());
		}
		render(item, objectName, configVars);
	}
	
	public static void deleteApp(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		App item = api.deleteApp(name);
		String objectName = item.getName();
		renderTemplate("@app", objectName, item);
	}

	public static void renameApp(String name, String newName) throws Exception {
		if (name == null || newName == null) {
			badRequest();
		}
		PlatformApi api = getPlatformApi();
		App app = api.renameApp(name, newName);
		app(app.getName());
	}

	public static void maintainApp(String name, boolean maintain) throws Exception {
		if (name == null) {
			badRequest();
		}
		PlatformApi api = getPlatformApi();
		App app = api.maintainApp(name, maintain);
		app(app.getName());
	}
	
	public static void releases(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		if (params.get("sort") == null) {
			range.setSortOrder("version", false);
		}
		List<Release> list = api.getReleaseList(name, range);
		renderList("Releases of " + name, list, range, new Linker("release?name=" + name + "&version=", "version"));
	}

	public static void release(String name, String version) throws Exception {
		PlatformApi api = getPlatformApi();
		Release r = api.getRelease(name, version);
		renderDetail("Release " + version  + " of " + name, r, name, null);
	}
	
	public static void collaborators(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		List<Collaborator> list = api.getCollaboratorList(name, range);
		renderList("Collaborators of " + name, list, range, new Linker("collaborator?name=" + name + "&email=", "user.email"));
	}
	
	public static void collaborator(String name, String email) throws Exception {
		PlatformApi api = getPlatformApi();
		Collaborator c = api.getCollaborator(name, email);
		renderDetail("Collaborator " + email  + " of " + name, c, name, "collaborator.html");
	}
	
	public static void addCollaborator(String name, String email, boolean silent) throws Exception {
		PlatformApi api = getPlatformApi();
		Collaborator c = api.addCollaborator(name, email, silent);
		collaborators(name);
	}
	
	public static void deleteCollaborator(String name, String email) throws Exception {
		PlatformApi api = getPlatformApi();
		Collaborator c = api.deleteCollaborator(name, email);
		collaborators(name);
	}
	
	public static void setConfigVar(String app, String name, String value) throws Exception {
		if (app == null || name == null) {
			badRequest();
		}
		PlatformApi api = getPlatformApi();
		api.setConfigVar(app, name, value == null || value.length() == 0 ? null : value);
		app(app);
	}
	
	public static void addons(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList(name + " addons", api.getAddonList(name, range), range, new Linker("addon?name=" + name + "&id=", "id"));
	}
	
	public static void addon(String name, String id) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("Addon " + id, api.getAddon(name, id), name, null);
	}
	
	public static void addonServices() throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList("AddonServices", api.getAddonServiceList(range), range, new Linker("addonservice?name=", "name"));
	}
	
	public static void addonService(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("AddonService " + name, api.getAddonService(name), null, null);
	}
	
	public static void formations(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList(name + " formations", api.getFormationList(name, range), range, new Linker("formation?name=" + name + "&type=", "type"));
	}
	
	public static void formation(String name, String type) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("Formation " + name, api.getFormation(name, type), name, "formation.html");
	}
	
	public static void updateFormation(String name, String type, int quantity, int size) throws Exception {
		PlatformApi api = getPlatformApi();
		api.updateFormation(name, type, quantity, size);
		formation(name, type);
	}
	
	public static void dynos(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList(name + " dynos", api.getDynoList(name, range), range, new Linker("dyno?name=" + name + "&dyno=", "name"));
	}
	
	public static void dyno(String name, String dyno) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("Dyno " + dyno + " of " + name, api.getDyno(name, dyno), name, "dyno.html");
	}
	
	public static void deleteDyno(String name, String dyno) throws Exception {
		PlatformApi api = getPlatformApi();
		api.deleteDyno(name, dyno);
		dynos(name);
	}
	
	public static void runDyno(String name, String command) throws Exception {
		PlatformApi api = getPlatformApi();
		Dyno dyno = api.runDyno(name, command);
		dynos(name);
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