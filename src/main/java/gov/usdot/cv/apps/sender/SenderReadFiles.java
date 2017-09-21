package gov.usdot.cv.apps.sender;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.apache.commons.io.FilenameUtils;

import org.apache.log4j.Logger;

public class SenderReadFiles extends SenderReadFile {
	
	private static final Logger log = Logger.getLogger(SenderReadFiles.class);

	public SenderReadFiles(SenderConfig config) {
		super(config);
	}
	
	@Override
	public void run() {
		try {
			putFiles(config.source.path);
		} catch (IOException ex ) {
			log.error("Error sending files from folder: '" + config.source.path + "'.", ex);
		}
	}
	
	private void putFiles(String directory) throws IOException {
		File dir = new File(directory);

		File[] files = dir.listFiles(new FilenameFilter() {
			public boolean accept(File folder, String name) {
				return FilenameUtils.wildcardMatch(name, config.source.filter);
			}
		});
		
		for (File file : files) {
			String path = file.getAbsolutePath();
			log.debug("Sending file '" + path + "'.");
			putFile(path);
		}
	}
}
