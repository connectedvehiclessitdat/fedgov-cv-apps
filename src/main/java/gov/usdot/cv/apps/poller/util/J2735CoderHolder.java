package gov.usdot.cv.apps.poller.util;

import gov.usdot.asn1.generated.j2735.J2735;

import com.oss.asn1.Coder;
import com.oss.asn1.ControlTableNotFoundException;
import com.oss.asn1.InitializationException;

public class J2735CoderHolder {
	private static final Object LOCK = new Object();
	private static Coder CODER_INSTANCE;
	
	private J2735CoderHolder() {}
	
	public static Coder getCoder() throws ControlTableNotFoundException, InitializationException {
		if (CODER_INSTANCE == null) {
			synchronized(LOCK) {
				if (CODER_INSTANCE == null) {
					J2735.initialize();
					CODER_INSTANCE = J2735.getPERUnalignedCoder();
					if (Boolean.valueOf(System.getProperty("DEBUG", "false"))) {
						CODER_INSTANCE.enableEncoderDebugging();
					}
				}
			}
		}
		return CODER_INSTANCE;
	}
}