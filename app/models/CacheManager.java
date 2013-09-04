package models;

import play.cache.Cache;
import jp.co.flect.heroku.platformapi.PlatformApi;

public class CacheManager {
	
	private String sessionId;
	
	public CacheManager(String sessionId) {
		this.sessionId = sessionId;
	}
	
	private String get(String key) {
		return get(key, false);
	}
	private String get(String key, boolean delete) {
		key = this.sessionId + "-" + key;
		String ret = (String)Cache.get(key);
		if (delete) {
			Cache.delete(key);
		}
		return ret;
	}
	
	private void set(String key, String value) {
		key = this.sessionId + "-" + key;
		Cache.set(key, value, "10min");
	}
	
	public PlatformApi getApi() { 
		String key = this.sessionId + "-api";
		PlatformApi api = (PlatformApi)Cache.get(key);
		//ToDo check timeout
		return api;
	}
	
	public void setApi(PlatformApi api) {
		String key = this.sessionId + "-api";
		Cache.set(key, api, "2h");
	}
	
	public String getEventPageUrl() {
		return get("eventId");
	}
	
	public void setEventPageUrl(String url) {
		set("eventId", url);
	}
	
	public String getMessage() {
		return get("message", true);
	}
	
	public void setMessage(String msg) {
		set("message", msg);
	}
	
	public String getAction() {
		return get("action");
	}
	
	public void setAction(String action) {
		set("action", action);
	}
	
	public String getOrigin() {
		return get("origin");
	}
	
	public void setOrigin(String v) {
		set("origin", v);
	}
	
}
