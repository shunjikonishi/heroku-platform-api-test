package models;

import java.util.List;
import java.lang.reflect.Method;
import jp.co.flect.heroku.platformapi.model.AbstractModel;

public class ModelTester {
	
	public <T extends AbstractModel> void test(List<T> list) throws Exception {
		if (list == null || list.size() == 0) {
			return;
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
		test(model);
	}
	public void test(AbstractModel model) throws Exception {
		for (String key : model.keys()) {
			test(model, key);
		}
	}
	
	private void test(AbstractModel model, String key) throws Exception {
		String methodName = capitalize(key);
System.out.println("test: " + model.getClass().getSimpleName() + ", " + key + ", " + methodName);
		Method m = null;
		try {
			m = model.getClass().getDeclaredMethod(methodName);
		} catch (NoSuchMethodException e) {
			try {
				m = model.getClass().getDeclaredMethod("is" + methodName.substring(3));
			} catch (NoSuchMethodException e2) {
				System.out.println("!!!!!! No such method: " + methodName);
				return;
			}
		}
		Object o1 = m.invoke(model);
		Object o2 = model.get(key);
		if (!eq(o1, o2)) {
			System.out.println("!!!!!! Invalid method: " + methodName + ", " + o1 + ", " + o2);
		}
	}
	
	private static boolean eq(Object o1, Object o2) {
		if (o1 == null) {
			return o2 == null;
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
		return buf.toString();
	}
}
