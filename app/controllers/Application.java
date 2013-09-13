package controllers;

import java.util.Arrays;
import java.util.ArrayList;
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
import jp.co.flect.heroku.platformapi.model.AppFeature;
import jp.co.flect.heroku.platformapi.model.Account;
import jp.co.flect.heroku.platformapi.model.AccountFeature;
import jp.co.flect.heroku.platformapi.model.Addon;
import jp.co.flect.heroku.platformapi.model.AddonService;
import jp.co.flect.heroku.platformapi.model.Region;
import jp.co.flect.heroku.platformapi.model.Release;
import jp.co.flect.heroku.platformapi.model.Collaborator;
import jp.co.flect.heroku.platformapi.model.Dyno;
import jp.co.flect.heroku.platformapi.model.Range;
import jp.co.flect.heroku.platformapi.model.Plan;

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
		if (list == null) {
			renderText(objectName + " not found");
		}
		String message = new ModelTester().test(list);
		if (message != null) {
			renderArgs.put("message", message);
		}
		list = new ArrayList(list) {
			public AbstractModel getItemForHeader() {
				if (size() == 0) {
					return null;
				}
				AbstractModel ret = (AbstractModel)get(0);
				int size = ret.keys().size();
				for (Object obj : this) {
					AbstractModel m = (AbstractModel)obj;
					int n = m.keys().size();
					if (n > size) {
						ret = m;
						size = n;
					}
				}
				return ret;
			}
		};
		renderTemplate("@list", objectName, list, range, linker);
	}
	
	private static void renderDetail(String objectName, AbstractModel item, String appName, String addition) throws Exception {
		String message = new ModelTester().test(item);
		if (message != null) {
			renderArgs.put("message", message);
		}
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
	
	//Account
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
	
	public static void accountFeatures() throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		List<AccountFeature> list = api.getAccountFeatureList(range);
		renderList("AccountFeatures", list, range, new Linker("accountFeature?name=", "name"));
	}
	
	public static void accountFeature(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail(name, api.getAccountFeature(name), null, "accountFeature.html");
	}
	
	public static void updateAccountFeature(String name, boolean enabled) throws Exception {
		PlatformApi api = getPlatformApi();
		api.updateAccountFeature(name, enabled);
		accountFeature(name);
	}
	
	//App
	public static void apps() throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		List<App> list = api.getAppList(range);
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
	
	//AppFeature
	public static void appFeatures(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		List<AppFeature> list = api.getAppFeatureList(name, range);
		renderList("AppFeatures", list, range, new Linker("appFeature?name=" + name + "&feature=", "name"));
	}
	
	public static void appFeature(String name, String feature) throws Exception {
		PlatformApi api = getPlatformApi();
		AppFeature f = api.getAppFeature(name, feature);
		renderDetail("AppFeature " + feature + " of " + name, f, name, "appFeature.html");
	}
	
	public static void updateAppFeature(String name, String feature, boolean enabled) throws Exception {
		PlatformApi api = getPlatformApi();
		api.updateAppFeature(name, feature, enabled);
		appFeature(name, feature);
	}
	
	//Release
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
	
	//Collaborator
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
	
	//Config var
	public static void setConfigVar(String app, String name, String value) throws Exception {
		if (app == null || name == null) {
			badRequest();
		}
		PlatformApi api = getPlatformApi();
		api.setConfigVar(app, name, value == null || value.length() == 0 ? null : value);
		app(app);
	}
	
	//Addon
	public static void addons(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList(name + " addons", api.getAddonList(name, range), range, new Linker("addon?name=" + name + "&id=", "id"));
	}
	
	public static void addon(String name, String id) throws Exception {
		PlatformApi api = getPlatformApi();
		Addon addon = api.getAddon(name, id);
		String[] names = addon.getPlanName().split(":");
		List<Plan> plans = api.getAddonPlanList(names[0]);
		renderArgs.put("plans", plans);
		renderDetail("Addon " + id, addon, name, "addon.html");
	}
	
	public static void addAddon(String name, String plan) throws Exception {
		PlatformApi api = getPlatformApi();
		Addon addon = api.addAddon(name, plan);
		if (plan.startsWith("heroku-postgresql")) {
			addon.setConfig("version", "9.3");
		}
		addon(name, addon.getId());
	}
	
	public static void deleteAddon(String name, String id) throws Exception {
		PlatformApi api = getPlatformApi();
		Addon addon = api.deleteAddon(name, id);
		addons(name);
	}
	
	public static void updateAddon(String name, String id, String plan) throws Exception {
		PlatformApi api = getPlatformApi();
		Addon addon = new Addon(plan);
		addon = api.updateAddon(name, id, addon);
		addon(name, addon.getId());
	}
	
	//AddonService
	public static void addonServices() throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList("AddonServices", api.getAddonServiceList(range), range, new Linker("addonservice?name=", "name"));
	}
	
	public static void addonService(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("AddonService " + name, api.getAddonService(name), null, "addonservice.html");
	}
	
	public static void addonPlans(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList(name + " plan", api.getAddonPlanList(name, range), range, new Linker("addonplan?name=" + name + "&plan=", "name"));
	}
	
	public static void addonPlan(String name, String plan) throws Exception {
		PlatformApi api = getPlatformApi();
		int idx = plan.indexOf(":");
		if (idx != -1) {
			plan = plan.substring(idx+1);
		}
		try {
			Range range = new Range();
			range.setSortOrder("name", true);
			List<App> apps = api.getAppList(range);
			renderArgs.put("apps", apps);
		} catch (Exception e) {
			renderArgs.put("message", e.getMessage());
		}
		renderDetail(name + " plan " + plan, api.getAddonPlan(name, plan), null, "addonplan.html");
	}
	
	//Formation
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
	
	//Dyno
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
		api.killDyno(name, dyno);
		dynos(name);
	}
	
	public static void restart(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		api.restart(name);
		dynos(name);
	}
	
	public static void runDyno(String name, String command) throws Exception {
		PlatformApi api = getPlatformApi();
		Dyno dyno = api.runDyno(name, command);
		dynos(name);
	}
	
	//Region
	public static void regions() throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList("Regions", api.getRegionList(range), range, new Linker("region?name=", "name"));
	}
	
	public static void region(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("Region " + name, api.getRegion(name), null, null);
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