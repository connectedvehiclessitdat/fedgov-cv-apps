package gov.usdot.cv.apps.sender;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import gov.usdot.cv.apps.sender.SenderConfig.ContentType;

public class SenderReadFile extends SenderReader {
	
	private static final Logger log = Logger.getLogger(SenderReadFile.class);
	
	public SenderReadFile(SenderConfig config) {
		super(config);
	}
	
	@Override
	public void run() {
		try {
			putFile(config.source.path);
		} catch (IOException ex ) {
			log.error("Error sending file: '" + config.source.path + "'.", ex);
		}
	}
	
	protected void putFile(String fileName) throws IOException {
		if ( config.source.contentType == ContentType.UPER ) {
			put(FileUtils.readFileToByteArray(new File(fileName)));
		} else {
			putLines(fileName);
		}
	}
	
	protected void putLines(String fileName) throws IOException {
		FileInputStream fs = null;
		BufferedReader  br = null;
		try {
			fs = new FileInputStream(config.source.path);
			br = new BufferedReader(new InputStreamReader(fs));
			String line;
			while ((line = br.readLine()) != null)
				put(line);
		} finally {
			if ( br != null ) {
				br.close();
				br = null;
			}
			if ( fs != null ) {
				fs.close();
				fs = null;
			}
		}
	}

}
