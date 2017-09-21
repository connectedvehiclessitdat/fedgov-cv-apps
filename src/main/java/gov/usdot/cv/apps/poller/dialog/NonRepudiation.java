package gov.usdot.cv.apps.poller.dialog;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

import com.oss.asn1.AbstractData;
import com.oss.asn1.EncodeFailedException;
import com.oss.asn1.EncodeNotSupportedException;

import gov.usdot.asn1.generated.j2735.semi.DataAcceptance;
import gov.usdot.asn1.generated.j2735.semi.DataReceipt;
import gov.usdot.asn1.generated.j2735.semi.GroupID;
import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.asn1.generated.j2735.semi.SemiSequenceID;
import gov.usdot.asn1.j2735.J2735Util;
import gov.usdot.asn1.j2735.TravelerSampleMessageBuilder;
import gov.usdot.cv.apps.poller.util.J2735CoderHolder;
import gov.usdot.cv.common.asn1.GroupIDHelper;
import gov.usdot.cv.common.asn1.TemporaryIDHelper;
import gov.usdot.cv.security.SecureConfig;
import gov.usdot.cv.security.SecurityHelper;
import gov.usdot.cv.security.crypto.CryptoProvider;

public class NonRepudiation {
	private byte [] requestBytes = null;
	
	private SemiDialogID dialogId;
	private SemiSequenceID sequenceId;
	private GroupID groupId;
	private String warehouseIP;
	private int warehousePort;
	private int fromPort;
	private int requestId;
	
	public NonRepudiation(
		SemiDialogID dialogId,
		SemiSequenceID sequenceId,
		GroupID groupId,
		String warehouseIP, 
		int warehousePort,
		int fromPort,
		int requestId) {
		this.dialogId = dialogId;
		this.sequenceId = sequenceId;
		this.groupId = groupId;
		this.warehouseIP = warehouseIP;
		this.warehousePort = warehousePort;
		this.fromPort = fromPort;
		this.requestId = requestId;
	}
	
	private void buildDataAcceptance() throws DialogException {
		DataAcceptance acceptance = new DataAcceptance(
				this.dialogId, 
				this.sequenceId,
				this.groupId,
				TemporaryIDHelper.toTemporaryID(this.requestId));
		
		try {
			this.requestBytes = TravelerSampleMessageBuilder.messageToEncodedBytes(acceptance);
		} catch (EncodeFailedException ex) {
			throw new DialogException("Couldn't encode RsuAdvisorySituationDataRequest message because encoding failed.", ex);
		} catch (EncodeNotSupportedException ex) {
			throw new DialogException("Couldn't encode RsuAdvisorySituationDataRequest message because encoding is not supported.", ex);
		}
	}
	
	public void sendDataAcceptance(SecureConfig config, CryptoProvider cryptoProvider, byte[] certId8) throws DialogException {
		if (this.requestBytes == null) {
			buildDataAcceptance();
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
			
			// System.out.println("Sent Data Acceptance: " + new String(Hex.encodeHex(requestBytes)));
		} catch (Exception ex) {
			throw new DialogException("Failed to send service request.", ex);
		} finally {
			if (sock != null) {
				if (! sock.isClosed() ) sock.close();
				sock = null;
			}
		}
	}
	
	public boolean isReceiptValid(SemiDialogID semiDialogId, DatagramPacket receiptPacket, SecureConfig config, CryptoProvider cryptoProvider) throws DialogException {
		try{
			if (receiptPacket == null) return false;
			
			final byte [] data = receiptPacket.getData();
			if (data == null) return false;
			
			final int length = receiptPacket.getLength();
			if (length <= 0) return false;
			
			byte [] receiptBytes = Arrays.copyOfRange(data, receiptPacket.getOffset(), length);
			
			if (config != null && config.secure.enable) {
				receiptBytes = SecurityHelper.decrypt(receiptBytes, cryptoProvider);
			}
			// System.out.println("Received Receipt: " + new String(Hex.encodeHex(receiptBytes)));

			AbstractData pdu = J2735Util.decode(J2735CoderHolder.getCoder(), receiptBytes);
			
			if (pdu == null || ! (pdu instanceof DataReceipt)) {
				System.out.print(String.format("Received unexpected response message of type '%s'.", 
						pdu != null ? pdu.getClass().getName() : "Unknown"));
				return false;
			}
			
			DataReceipt receiptResponse = (DataReceipt) pdu;
			
			long expectedDialogID = semiDialogId.longValue();
			if (expectedDialogID != receiptResponse.getDialogID().longValue()) {
				System.out.println(String.format("Unexpected receipt dialog id. Expected '%d' but got '%s'.",	
					expectedDialogID, receiptResponse.getDialogID().longValue()));
				return false;
			}
			
			long expectedSequenceID = SemiSequenceID.receipt.longValue();
			if (expectedSequenceID != receiptResponse.getSeqID().longValue()) {
				System.out.println(String.format("Unexpected receipt sequence id. Expected '%d' but got '%s'.",	
					expectedSequenceID, receiptResponse.getSeqID().longValue()));
				return false;
			}
			
			int responseGroupId = GroupIDHelper.fromGroupID(receiptResponse.getGroupID());
			int expectedGroupId = GroupIDHelper.fromGroupID(this.groupId);
			if ( responseGroupId != expectedGroupId ) {
				System.out.println(String.format("Unexpected receipt group id. Expected '%d' but got '%s'.", 
						expectedGroupId, responseGroupId));
					return false;
			}
			
			int responseRequestId = TemporaryIDHelper.fromTemporaryID(receiptResponse.getRequestID());
			if (requestId != responseRequestId) {
				System.out.print(String.format("Unexpected receipt request id. Expected '%d' but got '%s'.", 
					requestId, responseRequestId));
				return false;
			}
			
			return true;
		} catch (Exception ex) {
			throw new DialogException(String.format("Failed to validate receipt for requestId '%s'.", requestId), ex);
		}
	}
}