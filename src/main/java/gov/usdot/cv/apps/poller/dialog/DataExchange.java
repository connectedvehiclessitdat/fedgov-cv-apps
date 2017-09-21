package gov.usdot.cv.apps.poller.dialog;

import gov.usdot.cv.security.SecureConfig;
import gov.usdot.cv.security.SecurityHelper;
import gov.usdot.cv.security.crypto.CryptoProvider;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

public abstract class DataExchange {
	protected byte [] requestBytes = null;
	
	protected String warehouseIP;
	protected int warehousePort;
	protected int fromPort;
	
	protected abstract void buildDataRequest() throws DialogException;
	
	public abstract int storeDataBundles(List<DatagramPacket> packets, SecureConfig config, CryptoProvider cryptoProvider);
	public abstract List<?> getResultSet();
	
	public void sendDataRequest(SecureConfig config, CryptoProvider cryptoProvider, byte[] certId8) throws DialogException {
		if (this.requestBytes == null) {
			buildDataRequest();
		}
		if (config != null && config.secure.enable) {
			this.requestBytes = SecurityHelper.encrypt(this.requestBytes, certId8, cryptoProvider, config.secure.psid);
		}
		
		DatagramSocket sock = null;
		try {
			sock = new DatagramSocket(this.fromPort);
			DatagramPacket requestPacket = new DatagramPacket(
				requestBytes, 
				requestBytes.length, 
				InetAddress.getByName(this.warehouseIP), 
				this.warehousePort);
			sock.send(requestPacket);
			
			// System.out.println("Sent Data Request: " + new String(Hex.encodeHex(requestBytes)));
		} catch (Exception ex) {
			throw new DialogException("Failed to send service request.", ex);
		} finally {
			if (sock != null) {
				if (! sock.isClosed() ) sock.close();
				sock = null;
			}
		}
	}
	
}