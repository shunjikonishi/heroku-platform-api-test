package controllers;

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.Collections;
import models.CacheManager;
import models.ModelTester;
import models.SshKey;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Catch;

import jp.co.flect.heroku.platformapi.PlatformApi;
import jp.co.flect.heroku.platformapi.PlatformApiException;
import jp.co.flect.heroku.platformapi.model.AbstractModel;
import jp.co.flect.heroku.platformapi.model.App;
import jp.co.flect.heroku.platformapi.model.AppTransfer;
import jp.co.flect.heroku.platformapi.model.AppFeature;
import jp.co.flect.heroku.platformapi.model.Account;
import jp.co.flect.heroku.platformapi.model.AccountFeature;
import jp.co.flect.heroku.platformapi.model.Addon;
import jp.co.flect.heroku.platformapi.model.AddonService;
import jp.co.flect.heroku.platformapi.model.Region;
import jp.co.flect.heroku.platformapi.model.Release;
import jp.co.flect.heroku.platformapi.model.Collaborator;
import jp.co.flect.heroku.platformapi.model.Domain;
import jp.co.flect.heroku.platformapi.model.Dyno;
import jp.co.flect.heroku.platformapi.model.Range;
import jp.co.flect.heroku.platformapi.model.Plan;
import jp.co.flect.heroku.platformapi.model.LogDrain;
import jp.co.flect.heroku.platformapi.model.LogSession;
import jp.co.flect.heroku.platformapi.model.OAuthClient;

public class Application extends Controller {
	
	@Catch
	public static void handleException(Exception e) {
		String msg = e.getMessage();
		if (e instanceof PlatformApiException) {
			PlatformApiException pae = (PlatformApiException)e;
			msg = pae.getStatus() + ", " + pae.getId() + ", " + pae.getMessage();
		}
		CacheManager cm = new CacheManager(session.getId());
		cm.setMessage(msg);
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
				PlatformApi api = PlatformApi.fromOAuth(secret, code);
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
	
	public static void loginByToken(String username, String token) {
		if (username == null || token == null) {
			badRequest();
		}
		CacheManager cm = new CacheManager(session.getId());
		PlatformApi api = PlatformApi.fromApiKey(username, token);
		api.setDebug(true);
		cm.setApi(api);
		index();
	}
	
	public static void loginByPassword(String username, String password) throws Exception {
		if (username == null || password == null) {
			badRequest();
		}
		CacheManager cm = new CacheManager(session.getId());
		PlatformApi api = PlatformApi.fromPassword(username, password);
		api.setDebug(true);
		cm.setApi(api);
		index();
	}
	
	public static void loginByRefreshToken(String token) throws Exception {
		if (token == null) {
			badRequest();
		}
		CacheManager cm = new CacheManager(session.getId());
		String secret = System.getenv().get("HEROKU_OAUTH_SECRET");
		PlatformApi api = PlatformApi.fromRefreshToken(secret, token);
		api.setDebug(true);
		cm.setApi(api);
		index();
	}
	
	public static void reset() {
		CacheManager cm = new CacheManager(session.getId());
		cm.setApi(null);
		index();
	}
	
	public static void refreshToken() {
		PlatformApi api = getPlatformApi();
		renderText(api.getRefreshToken());
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
	
	//Stack
	public static void stacks() throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList("Stacks", api.getStackList(range), range, new Linker("stack?name=", "name"));
	}
	
	public static void stack(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("Stack " + name, api.getStack(name), null, null);
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
	
	//OAuthClient
	public static void oauthClients() throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList("OAuthClients", api.getOAuthClientList(range), range, new Linker("oauthClient?id=", "id"));
	}
	
	public static void oauthClient(String id) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("OAuthClient " + id, api.getOAuthClient(id), null, "oauthClient.html");
	}
	
	public static void createOAuthClient(String name, String redirect_uri) throws Exception {
		PlatformApi api = getPlatformApi();
		OAuthClient oc = api.addOAuthClient(name, redirect_uri);
		oauthClient(oc.getId());
	}
	
	public static void deleteOAuthClient(String id) throws Exception {
		PlatformApi api = getPlatformApi();
		api.deleteOAuthClient(id);
		oauthClients();
	}
	
	public static void updateOAuthClient(String id, String name, String redirect_uri) throws Exception {
		PlatformApi api = getPlatformApi();
		OAuthClient oc = new OAuthClient();
		oc.setId(id);
		if (name != null && name.length() > 0) {
			oc.setName(name);
		}
		if (redirect_uri != null && redirect_uri.length() > 0) {
			oc.setRedirectUri(redirect_uri);
		}
		oc = api.updateOAuthClient(oc);
		oauthClient(oc.getId());
	}
	
	//Key
	public static void keys() throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList("Keys", api.getKeyList(range), range, new Linker("key?id=", "id"));
	}
	
	public static void key(String id) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("Key " + id, api.getKey(id), null, "key.html");
	}
	
	public static void generateKey() throws Exception {
		File dir = new File("tmp");
		String uuid = java.util.UUID.randomUUID().toString();
		SshKey ssh = new SshKey(dir, "id_rsa", uuid);
		int n = ssh.generate();
		keys();
	}
	
	public static void deleteKey(String id) throws Exception {
		PlatformApi api = getPlatformApi();
		api.deleteKey(id);
		keys();
	}
	
	//AppTransfer
	public static void appTransfers() throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList("AppTransfers", api.getAppTransferList(range), range, new Linker("apptransfer?id=", "id"));
	}
	
	public static void appTransfer(String id) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("AppTransfer " + id, api.getAppTransfer(id), null, "apptransfer.html");
	}
	
	public static void doAppTransfer(String name, String recipient) throws Exception {
		PlatformApi api = getPlatformApi();
		AppTransfer at = api.createAppTransfer(name, recipient);
		appTransfer(at.getId());
	}
	
	public static void updateAppTransfer(String id, String state) throws Exception {
		PlatformApi api = getPlatformApi();
		AppTransfer at = api.updateAppTransfer(id, AppTransfer.strToState(state));
		appTransfer(at.getId());
	}
	
	public static void deleteAppTransfer(String id) throws Exception {
		PlatformApi api = getPlatformApi();
		AppTransfer at = api.deleteAppTransfer(id);
		appTransfers();
	}
	
	//Domain
	public static void domains(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList("Domains", api.getDomainList(name, range), range, new Linker("domain?name=" + name + "&id=", "id"));
	}
	
	public static void domain(String name, String id) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("Domain " + id, api.getDomain(name, id), name, "domain.html");
	}
	
	public static void addDomain(String name, String hostname) throws Exception {
		PlatformApi api = getPlatformApi();
		Domain domain = api.addDomain(name, hostname);
		domain(name, domain.getId());
	}
	
	public static void deleteDomain(String name, String id) throws Exception {
		PlatformApi api = getPlatformApi();
		Domain domain = api.deleteDomain(name, id);
		domains(name);
	}
	
	//LogDrain
	public static void logDrains(String name) throws Exception {
		PlatformApi api = getPlatformApi();
		Range range = createRange();
		renderList("LogDrains", api.getLogDrainList(name, range), range, new Linker("logDrain?name=" + name + "&id=", "id"));
	}
	
	public static void logDrain(String name, String id) throws Exception {
		PlatformApi api = getPlatformApi();
		renderDetail("logDrain " + id, api.getLogDrain(name, id), name, "logDrain.html");
	}
	
	public static void addLogDrain(String name, String url) throws Exception {
		PlatformApi api = getPlatformApi();
		LogDrain LogDrain = api.addLogDrain(name, url);
		logDrain(name, LogDrain.getId());
	}
	
	public static void deleteLogDrain(String name, String id) throws Exception {
		PlatformApi api = getPlatformApi();
		LogDrain LogDrain = api.deleteLogDrain(name, id);
		logDrains(name);
	}
	
	//LogSession
	
	public static void logSession(String name, String dyno, String source, int lines, boolean tail) throws Exception {
		PlatformApi api = getPlatformApi();
		LogSession option = new LogSession();
		if (dyno != null && dyno.length() > 0) {
			option.setDyno(dyno);
		}
		if (source != null && source.length() > 0) {
			option.setSource(source);
		}
		option.setLines(lines);
		option.setTail(tail);
		LogSession ls = api.createLogSession(name, option);
		renderDetail("LogSession " + ls.getId(), ls, name, null);
	}
}