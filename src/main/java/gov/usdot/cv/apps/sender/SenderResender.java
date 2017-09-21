package gov.usdot.cv.apps.sender;

import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

import com.oss.asn1.ControlTableNotFoundException;
import com.oss.asn1.InitializationException;

public class SenderResender implements Runnable {
	
	private static final Logger log = Logger.getLogger(SenderResender.class);
	
	final private SenderConfig config;
	final private BlockingQueue<SenderDialog> resendQueue;
	
	public SenderResender(SenderConfig config, BlockingQueue<SenderDialog> resendQueue) {
		this.config = config;
		this.resendQueue = resendQueue;
	}

	public void run() {		
		try {
			SenderDialog dialog;
			while(true) {
				dialog = resendQueue.take();
				if ( dialog == null || SenderDialog.isEndOfDataMarker(dialog) ) {
					log.debug("Got end of data marker. Existing resender thread.");
					break;
				}
				boolean sentSuccessfully = false;
				try {
					dialog.initialize();
					for( int i = 0; i < config.resend.attempts; i++ ) {
						log.debug(String.format("Resend attempt %d of %d for requestID %d", i+1, config.resend.attempts, dialog.requestID));
						if ( SenderDialog.resend(dialog) ) {
							log.debug(String.format("Successfully resent dialog for requestID %d after %d attemps", dialog.requestID, i+1));
							sentSuccessfully = true;
							break;
						}
					}
					if ( !sentSuccessfully ) {
						log.error(String.format("Resend for requestID %d failed after %d attempts", dialog.requestID, config.resend.attempts));
					}
				} finally {
					dialog.dispose();
				}
			}
		} catch (SocketException ex ) {
			log.error("Caught socket exception while resending messages", ex);
		} catch (ControlTableNotFoundException ex ) {
			log.error("Couldn't initialize OSS coder", ex);
		} catch (InitializationException ex) {
			log.error("Couldn't initialize OSS coder", ex);
		} catch (NoSuchAlgorithmException ex) {
			log.error("Couldn't initialize hash algoritm", ex);
		} catch (InterruptedException ignored) {
		}
	}


}
