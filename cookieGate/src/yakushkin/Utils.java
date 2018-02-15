package yakushkin;

public class Utils {
	public static String escapeUsername(String str) {
		return str.replace('{', '_')
				  .replace('}', '_')
				  .replace('\\', '_')
				  .replace(',', '_')
				  .replace('"', '_')
				  .replace('<', '_')
				  .replace(';', '_')
				  .replace('>', '_')
				  .replace('/', '_')
				  .replace('\'', '_');
	}
}
