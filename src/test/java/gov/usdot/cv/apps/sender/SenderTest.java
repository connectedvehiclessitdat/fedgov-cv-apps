package gov.usdot.cv.apps.sender;

import static org.junit.Assert.*;

import gov.usdot.cv.common.util.UnitTestHelper;
import gov.usdot.cv.security.cert.CertificateException;
import gov.usdot.cv.security.crypto.CryptoException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import net.sf.json.JSONObject;

import org.apache.commons.codec.DecoderException;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

public class SenderTest {

	private static final boolean isDebugOutput = true;
	
	private static final Logger log = Logger.getLogger(SenderTest.class);

	@BeforeClass
	public static void init() throws Exception {
		UnitTestHelper.initLog4j(isDebugOutput);
	}
	
	@Test @org.junit.Ignore
	public void testIsdBinFile()  throws URISyntaxException, DecoderException, CertificateException, IOException, CryptoException {
		System.out.println("Current working directory: " + System.getProperty("user.dir"));
		File file = new File("../fedgov-cv-asn1/src/test/resources/CVMessages/IntersectionSitData.uper");
		assertTrue(file.exists());
		String confFilePath = getResourceFilePath("test_isd_sender_config_ber_file.json");
		JSONObject config = SenderConfigTest.createJsonFromFile(confFilePath);
		assertNotNull("Config file valid JSON", config);
		log.debug(config.toString(3));
		SenderConfig senderConfig = new SenderConfig(config);
		assertNotNull(senderConfig);
		log.debug("\n" + senderConfig.toString());
		Sender sender = new Sender(senderConfig);
		assertNotNull(sender);
		sender.send();
	}

	@Test
	public void testFile() throws URISyntaxException, InterruptedException, DecoderException, CertificateException, IOException, CryptoException {
		testFile("test_vsd_sender_config_base64_file.json", "VehSitDataBase64.txt");
		Thread.sleep(1000);
		testFile("test_vsd_sender_config_hex_file.json", "VehSitDataHex.txt");
		Thread.sleep(1000);
		testFile("test_isd_sender_config_record_hex_file.json", "IntersectionRecordHex.txt");
		//Thread.sleep(1000);
		//testFile("secure_config.json", "VehSitDataHex.txt");
	}

	private void testFile(String confResourceFile, String dataResourceFile) throws URISyntaxException, DecoderException, CertificateException, IOException, CryptoException {
		String confFilePath = getResourceFilePath(confResourceFile);
		String dataFilePath = getResourceFilePath(dataResourceFile);
		JSONObject config = SenderConfigTest.createJsonFromFile(confFilePath);
		assertNotNull("Config file valid JSON", config);
		assertTrue(config.has("source"));
		JSONObject source = config.getJSONObject("source");
		assertNotNull("Source is valid JSON", source);
		source.put("file", dataFilePath);
		log.debug(config.toString(3));
		SenderConfig senderConfig = new SenderConfig(config);
		assertNotNull(senderConfig);
		log.debug(senderConfig.toString());
		Sender sender = new Sender(senderConfig);
		assertNotNull(sender);
		sender.send();
	}
	
	private String getResourceFilePath(String resourceFile ) throws URISyntaxException {
		assertNotNull(resourceFile);
		String resource = resourceFile.startsWith("/") ? resourceFile : "/" + resourceFile;
		File file = new File("./src/test/resource" + resource);
		assertTrue(file.exists());
		String path = file.getAbsolutePath();
		log.debug(path);
		return path;
	}	

}
