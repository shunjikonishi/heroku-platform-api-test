package models;

import java.util.List;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import jp.co.flect.heroku.platformapi.model.AbstractModel;

public class ModelTester {
	
	public <T extends AbstractModel> String test(List<T> list){
		if (list == null || list.size() == 0) {
			return null;
		}
		T model = list.get(0);
		int size = model.keys().size();
		for (T m : list) {
			int n = m.keys().size();
			if (n > size) {
				model = m;
				size = n;
			}
		}
		return test(model);
	}
	public String test(AbstractModel model){
		StringBuilder buf = new StringBuilder();
		for (String key : model.keys()) {
			String str = test(model, key);
			if (str != null) {
				buf.append(str);
			}
		}
		return buf.length() == 0 ? null : buf.toString();
	}
	
	private String test(AbstractModel model, String key) {
		String methodName = capitalize(key);
		try {
			Method m = findMethod(model.getClass(), methodName);
			Object o1 = m.invoke(model);
			if (o1 instanceof Enum) {
				o1 = o1.toString();
			}
			Object o2 = model.get(key);
			if (!eq(o1, o2)) {
				return "!!!!!! Invalid method: " + methodName + ", " + o1 + ", " + o2;
			}
		} catch (IllegalAccessException e) {
			return e.toString();
		} catch (InvocationTargetException e) {
			return e.toString();
		} catch (NoSuchMethodException e) {
			return e.toString();
		}
		return null;
	}
	
	private Method findMethod(Class clazz, String methodName) throws NoSuchMethodException {
		try {
			return clazz.getMethod(methodName);
		} catch (NoSuchMethodException e) {
			return clazz.getMethod("is" + methodName.substring(3));
		}
	}
	
	private static boolean eq(Object o1, Object o2) {
		if (o1 == null) {
			return o2 == null ||
				(o2 instanceof Number && ((Number)o2).intValue() == 0) ||
				(o2 instanceof Boolean && ((Boolean)o2).booleanValue() == false);
		} else if (o2 == null) {
			return (o1 instanceof Number && ((Number)o1).intValue() == 0) ||
				(o1 instanceof Boolean && ((Boolean)o1).booleanValue() == false);
		} else {
			return o1.equals(o2);
		}
	}
	
	private static String capitalize(String str) {
		StringBuilder buf = new StringBuilder();
		buf.append("get");
		boolean bUpper = true;
		for (int i=0; i<str.length(); i++) {
			char c = str.charAt(i);
			switch (c) {
				case '_':
				case '.':
					bUpper = true;
					break;
				default:
					if (bUpper) {
						c = Character.toUpperCase(c);
					}
					buf.append(c);
					bUpper = false;
					break;
			}
		}
		String ret = buf.toString();
		ret = ret.replaceAll("name", "Name");
		return ret;
	}
}
