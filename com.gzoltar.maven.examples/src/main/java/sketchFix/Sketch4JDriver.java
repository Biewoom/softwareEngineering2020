package sketchFix;

import sketch4j.executor.SketchExecutor;
import sketch4j.request.Sketch4J;

public class Sketch4JDriver {

	public static void main(String[] arg) throws ClassNotFoundException {
		org.junit.runner.JUnitCore core = new org.junit.runner.JUnitCore();
		org.junit.runner.Result result1 = null;
		Class target1 = Class.forName("CalendarTest");

		do {
			Sketch4J.initialize();
			try {
				result1 = core.run(target1);
				if (result1.wasSuccessful() ) {
					System.out.println("Found solution:" + Sketch4J.getString());
					break;
				} else {
				}
			} catch (Exception e) {
			}
		} while (SketchExecutor.incrementCounter());

	}

}
