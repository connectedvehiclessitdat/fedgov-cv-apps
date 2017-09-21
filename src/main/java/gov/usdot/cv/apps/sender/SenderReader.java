package gov.usdot.cv.apps.sender;

import java.util.concurrent.BlockingQueue;

import gov.usdot.cv.apps.sender.SenderConfig.ContentType;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

abstract public class SenderReader implements Runnable {
	
	private static final Logger log = Logger.getLogger(SenderReader.class);
	
	protected SenderConfig config;
	private BlockingQueue<byte[]> messageQueue;
	
	public SenderReader(SenderConfig config) {
		this.config = config;
	}

	abstract public void run();
	
	public void setMessageQueue(BlockingQueue<byte[]> messageQueue) {
		this.messageQueue = messageQueue;
	}
	
	protected void put(byte[] message) {
		try {
			messageQueue.put(message);
		} catch (InterruptedException ignored) {
		}
	}
	
	static public byte[] getEndOfDataMarker() {
		return new byte[0];
	}
	
	protected void put(String message) {
		if ( StringUtils.isBlank(message) )
			return;
		if ( config.source.contentType == ContentType.HEX ) {
			int len = message.length();
			char[] dst = new char[len];
			message.getChars(0, len, dst , 0);
			try {
				put(Hex.decodeHex(dst));
			} catch (DecoderException ex) {
				log.error("Couldn't decode HEX message: '" + message + "'.", ex);
			}
		} else {
			assert(config.source.contentType == ContentType.BASE64);
			put(Base64.decodeBase64(message));
		}
	}

}
