package sketchFix;

import edSketch.repair.config.ConfigReader.ConfigType;
import edSketch.repair.staticAnalyzer.model.StaticAnalyzer;

/**
 * @author Lisa May 28, 2018 PatchGenerationRunner.java
 */

public class PatchGenerationRunner {

	public static void main(String[] args) {
		StaticAnalyzer analyzer = new StaticAnalyzer();
		analyzer.setConfigFile(ConfigType.SIMPLE, "SimpleConfig.txt");
		analyzer.setFaultLocation("CalendarLogic:11", 0);
		analyzer.setFaultLocation("CalendarLogic:12", 0);
		analyzer.setFaultLocation("CalendarLogic:13", 0);
		analyzer.setFaultLocation("CalendarLogic:14", 0);
	}

}
