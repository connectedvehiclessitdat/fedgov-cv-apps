package gov.usdot.cv.apps.sender;

import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.cv.common.dialog.TrustEstablishmentException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.oss.asn1.ControlTableNotFoundException;
import com.oss.asn1.InitializationException;

public class SenderWorker implements Runnable {
	
	private static final Logger log = Logger.getLogger(SenderReader.class);
	
	final private SenderConfig config;
	final private BlockingQueue<byte[]> messageQueue;
	final private BlockingQueue<SenderDialog> resendQueue;
	
	public SenderWorker(SenderConfig config, BlockingQueue<byte[]> messageQueue, BlockingQueue<SenderDialog> resendQueue) {
		this.config = config;
		this.messageQueue = messageQueue;
		this.resendQueue = resendQueue;
	}

	public void run() {
		SenderDialog senderDialog;
		switch((int)config.dialog.dialogID.longValue()) {
		case (int)SemiDialogID.Value.intersectionSitDataDep:
			senderDialog = new SenderDialogISD(config);
			log.debug("Instantiated sender for dialog type intersectionSitDataDep");
			break;
		case (int)SemiDialogID.Value.vehSitData:
			senderDialog = new SenderDialogVSD(config);
			log.debug("Instantiated sender for dialog type vehSitData");
			break;
		case (int)SemiDialogID.Value.advSitDataDep:
			senderDialog = new SenderDialogASD(config);
			log.debug("Instantiated sender for dialog type advSitDataDep");
			break;
		case (int)SemiDialogID.Value.objReg:
			senderDialog = new SenderDialogORD(config);
			log.debug("Instantiated sender for dialog type objReg");
			break;
		case (int)SemiDialogID.Value.objDisc:
			senderDialog = new SenderDialogODDR(config);
			log.debug("Instantiated sender for dialog type objDisc");
			break;
		default:
			log.error("Unsupported dialog type");
			return;
		}
		try {
			senderDialog.initialize();
			byte[] message;
			senderDialog.open();
			int recordsCount = 0;
			while(true) {
				message = messageQueue.poll(config.dialog.timeout, TimeUnit.MILLISECONDS);
				if ( message == null ) {
					if ( !senderDialog.close() )
						resend(senderDialog);
					senderDialog.open();
				} else if ( isEndOfDataMarker(message) ) {
					if ( !senderDialog.close() ) 
						resend(senderDialog);
					log.debug("Got end of data marker. Existing sender thread.");
					break;
				} else {
					senderDialog.send(message);
					if ( ++recordsCount >= config.dialog.records ) {
						if ( !senderDialog.close() ) 
							resend(senderDialog);
						senderDialog.open();
						recordsCount = 0;
					}
				}
			}
		} catch (TrustEstablishmentException ex ) {
			log.error("Couldn't establish trust", ex);
		} catch (ControlTableNotFoundException ex ) {
			log.error("Couldn't initialize OSS coder", ex);
		} catch (InitializationException ex) {
			log.error("Couldn't initialize OSS coder", ex);
		} catch (IOException ex) {
			log.error("Caught IOException while sedning messages", ex);
		} catch (NoSuchAlgorithmException ex) {
			log.error("Couldn't initialize hash algoritm", ex);
		} catch (InterruptedException ignored) {
		} finally {
			senderDialog.dispose();
			log.debug("Disposed sender thread.");
		}
	}
	
	private void resend(SenderDialog dialog) throws InterruptedException {
		if ( dialog != null ) {
			SenderDialog resend;
			if ( dialog instanceof SenderDialogISD ) {
				log.debug("Resending via SenderDialogISD object type.");
				resend = new SenderDialogISD(dialog);
			} else {
				log.debug("Resending via SenderDialog object type.");
				resend = new SenderDialog(dialog);
			}
			
			log.debug(String.format("Adding dialog with requestID=%d, isResending=%s, records=%d to the resend queue.", 
					resend.requestID, resend.isResending ? "true" : "false", resend.messages != null ? resend.messages.size() : -1));
			resendQueue.put(resend);
		}
	}
	
	private boolean isEndOfDataMarker(byte[] message) {
		return message == null || message.length == 0;
	}

}
