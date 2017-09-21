package gov.usdot.cv.apps.sender;
import static org.junit.Assert.*;

import gov.usdot.cv.apps.sender.SenderConfig;
import gov.usdot.cv.common.util.UnitTestHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;


public class SenderConfigTest {
	
	static final private boolean isDebugOutput = false;
	
	@BeforeClass
	public static void init() throws Exception {
		UnitTestHelper.initLog4j(isDebugOutput);
	}

	@Test //@org.junit.Ignore
	public void load() throws URISyntaxException, IOException {
		String[] config_files = {
				"/isd_sender_config_file.json",
				"/isd_sender_config_folder.json",
				"/isd_sender_config_pipe.json",
				"/isd_sender_config.json",	
				"/isd_sender_config_base64_file.json",
				"/isd_sender_config_invalid.json",
				"/secure_config.json"
		};
		for ( String config_file : config_files )
			load(config_file);
	}
	
	public void load(String config_file ) throws URISyntaxException, IOException {	
		System.out.println("loading " + config_file);
		File file = new File("./src/test/resource" + config_file);
		assertTrue(file.exists());
		JSONObject config = createJsonFromFile(file.getAbsolutePath());
		assertNotNull("Test file valid JSON file: '" + config_file + "'\n", config);
		SenderConfig senderConfig = new SenderConfig(config);
		assertNotNull("Test sender config valid", senderConfig);
		System.out.println(senderConfig);
	}
	
	public static JSONObject createJsonFromFile(String file ) {
    	try {
    		FileInputStream fis = new FileInputStream(file);
    		String jsonTxt = IOUtils.toString(fis);
    		return (JSONObject) JSONSerializer.toJSON(jsonTxt);
		} catch (FileNotFoundException ex) {
			System.out.print(String.format("Couldn't create JSONObject from file '%s'.\nReason: %s\n", file, ex.getMessage()));
		} catch (IOException ex) {
			System.out.print(String.format("Couldn't create JSONObject from file '%s'.\nReason: %s\n", file, ex.getMessage()));
		} catch ( Exception ex) {
			System.out.print(String.format("Couldn't create JSONObject from file '%s'.\nReason: %s\n", file, ex.getMessage()));
		}
    	return null;
	}

}
