package gov.usdot.cv.apps.sender;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.log4j.Logger;

public class SenderReadPipe extends SenderReader {
	
	private static final Logger log = Logger.getLogger(SenderReadPipe.class);

	public SenderReadPipe(SenderConfig config) {
		super(config);
	}

	@Override
	public void run() {
		try {
		    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		    String line;
		    while ((line = br.readLine()) != null && line.length() != 0)
		      put(line);
		} catch (IOException ex ) {
			log.error("Error sending from standard input.", ex);
		}
	}

}
