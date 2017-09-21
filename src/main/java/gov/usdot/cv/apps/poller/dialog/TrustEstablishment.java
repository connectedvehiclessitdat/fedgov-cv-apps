package gov.usdot.cv.apps.poller.dialog;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.GregorianCalendar;

import com.oss.asn1.AbstractData;
import com.oss.asn1.EncodeFailedException;
import com.oss.asn1.EncodeNotSupportedException;

import gov.usdot.asn1.generated.j2735.dsrc.DDateTime;
import gov.usdot.asn1.generated.j2735.dsrc.TemporaryID;
import gov.usdot.asn1.generated.j2735.semi.ConnectionPoint;
import gov.usdot.asn1.generated.j2735.semi.GroupID;
import gov.usdot.asn1.generated.j2735.semi.PortNumber;
import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.asn1.generated.j2735.semi.SemiSequenceID;
import gov.usdot.asn1.generated.j2735.semi.ServiceRequest;
import gov.usdot.asn1.generated.j2735.semi.ServiceResponse;
import gov.usdot.asn1.j2735.CVSampleMessageBuilder;
import gov.usdot.asn1.j2735.J2735Util;
import gov.usdot.cv.apps.poller.util.J2735CoderHolder;
import gov.usdot.cv.common.asn1.GroupIDHelper;
import gov.usdot.cv.common.asn1.TemporaryIDHelper;
import gov.usdot.cv.common.dialog.TrustEstablishmentException;
import gov.usdot.cv.security.SecureConfig;
import gov.usdot.cv.security.cert.Certificate;
import gov.usdot.cv.security.cert.CertificateManager;
import gov.usdot.cv.security.crypto.CryptoProvider;
import gov.usdot.cv.security.msg.IEEE1609p2Message;

public class TrustEstablishment {
	private static final String DIGEST_ALGORITHM_NAME = "SHA-256"; 
	
	private byte [] requestBytes = null;
	private byte [] requestHash = null;	
	
	private SemiDialogID dialogId;
	private String warehouseIP;
	private int warehousePort;
	private int fromPort;
	private int responsePort;
	private int requestId;
	private GroupID groupId;
	private byte[] certId8;
	private IEEE1609p2Message msg1609p2;
	
	public TrustEstablishment(
		SemiDialogID dialogId,
		String warehouseIP, 
		int warehousePort,
		int recvPort,
		int responsePort,
		int groupId,
		int requestId) {
		this.dialogId = dialogId;
		this.warehouseIP = warehouseIP;
		this.warehousePort = warehousePort;
		this.fromPort = fromPort;
		this.responsePort = responsePort;
		this.groupId = GroupIDHelper.toGroupID(groupId);
		this.requestId = requestId;
	}
	
	private void buildServiceRequest() throws DialogException {
		TemporaryID tempId = TemporaryIDHelper.toTemporaryID(requestId);

		ConnectionPoint destConnection = new ConnectionPoint();
		destConnection.setPort(new PortNumber(this.responsePort));
		
		ServiceRequest svcRequest = CVSampleMessageBuilder.buildServiceRequest(
			tempId, 
			this.dialogId, 
			destConnection,
			this.groupId);
		
		try {
			MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM_NAME);
			this.requestBytes = CVSampleMessageBuilder.messageToEncodedBytes(svcRequest);
			this.requestHash =  messageDigest.digest(requestBytes);
		} catch (EncodeFailedException ex) {
			throw new DialogException("Couldn't encode ServiceRequest message because encoding failed.", ex);
		} catch (EncodeNotSupportedException ex) {
			throw new DialogException("Couldn't encode ServiceRequest message because encoding is not supported.", ex);
		} catch (NoSuchAlgorithmException ex) {
			throw new DialogException(String.format("Couldn't instantiate digest algorithm %s.", DIGEST_ALGORITHM_NAME), ex );
		}
	}
	
	public void sendServiceRequest(SecureConfig config, CryptoProvider cryptoProvider) throws DialogException {
		if (requestBytes == null) {
			buildServiceRequest();
		}
		
		if (config != null && config.secure.enable) {
			msg1609p2 = new IEEE1609p2Message(cryptoProvider);
			msg1609p2.setPSID(config.secure.psid);
			
			try {
				assert(msg1609p2 != null);
				requestBytes = msg1609p2.sign(requestBytes);
			} catch (Exception ex) {
				throw new DialogException("Couldn't create signed 1609.2 message. Reason: " + ex.getMessage(), ex);
			}
		}
		
		DatagramSocket sock = null;
		try {
			sock = new DatagramSocket(this.warehousePort);
			DatagramPacket requestPacket = new DatagramPacket(
				requestBytes, 
				requestBytes.length, 
				InetAddress.getByName(this.warehouseIP), 
				this.warehousePort);
			sock.send(requestPacket);
			
			// System.out.println("Sent Service Request: " + new String(Hex.encodeHex(requestBytes)));
		} catch (Exception ex) {
			throw new DialogException("Failed to send service request.", ex);
		} finally {
			if (sock != null) {
				if (! sock.isClosed() ) sock.close();
				sock = null;
			}
		}
	}
	
	public boolean isServiceResponseValid(SemiDialogID semiDialogId, DatagramPacket responsePacket, SecureConfig config, CryptoProvider cryptoProvider) throws DialogException {
		try {
			if (responsePacket == null) return false;

			final byte [] data = responsePacket.getData();
			if (data == null) return false;
				
			final int length = responsePacket.getLength();
			if (length <= 0) return false;
				
			byte [] responseBytes = Arrays.copyOfRange(data, responsePacket.getOffset(), length);
			
			if (config != null && config.secure.enable) {
				try {
					IEEE1609p2Message response = IEEE1609p2Message.parse(responseBytes, cryptoProvider);
					assert(response != null);
					Certificate cert = response.getCertificate();
					assert(cert != null);
					certId8 = cert.getCertID8();
					responseBytes = response.getPayload();
					assert(CertificateManager.get(certId8) != null);
				} catch (Exception ex) {
					throw new TrustEstablishmentException("Couldn't parse secure ServiceResponse. Reason: " + ex.getMessage(), ex);
				}
			}
			
			// System.out.println("Received Service Response: " + new String(Hex.encodeHex(responseBytes)));
			
			AbstractData pdu = J2735Util.decode(J2735CoderHolder.getCoder(), responseBytes);
			
			if (pdu == null || ! (pdu instanceof ServiceResponse)) {
				System.out.print(String.format("Received unexpected response message of type '%s'.", 
					pdu != null ? pdu.getClass().getName() : "Unknown"));
				return false;
			}
			
			ServiceResponse svcResponse = (ServiceResponse) pdu;
				
			long expectedDialogID = semiDialogId.longValue();
			if (expectedDialogID != svcResponse.getDialogID().longValue()) {
				System.out.println(String.format("Unexpected service response dialog id. Expected '%d' but got '%s'.",	
					expectedDialogID, svcResponse.getDialogID().longValue()));
				return false;
			}
			
			long expectedSequenceID = SemiSequenceID.svcResp.longValue();
			if (expectedSequenceID != svcResponse.getSeqID().longValue()) {
				System.out.println(String.format("Unexpected service response sequence id. Expected '%d' but got '%s'.",	
					expectedSequenceID, svcResponse.getSeqID().longValue()));
				return false;
			}
			
			int responseGroupId = GroupIDHelper.fromGroupID(svcResponse.getGroupID());
			int expectedGroupId = GroupIDHelper.fromGroupID(this.groupId);
			if ( responseGroupId != expectedGroupId ) {
				System.out.println(String.format("Unexpected service response group id. Expected '%d' but got '%s'.", 
						expectedGroupId, responseGroupId));
					return false;
			}
			
			int responseRequestId = TemporaryIDHelper.fromTemporaryID(svcResponse.getRequestID());
			if (requestId != responseRequestId) {
				System.out.println(String.format("Unexpected service response request id. Expected '%d' but got '%s'.", 
					requestId, responseRequestId));
				return false;
			}
			
			DDateTime expiration = svcResponse.getExpiration();
			if (J2735Util.isExpired(expiration) ) {
				System.out.println(String.format("Service response message has expired. Expiration time: %s. Current time: %s.",
					J2735Util.formatCalendar(J2735Util.DDateTimeToCalendar(expiration)), 
					J2735Util.formatCalendar(GregorianCalendar.getInstance())));
				return false;
			}
			
			byte [] hash = svcResponse.getHash().byteArrayValue();
			if (! Arrays.equals(requestHash, hash) ) {
				System.out.println("Service request message hash validation failed.");
				return false;
			}

			return true;
		} catch (Exception ex) {
			throw new DialogException(String.format("Failed to validate service response for requestId '%s'.", requestId), ex);
		}
	}
	
	public byte[] getCertId8() {
		return certId8;
	}
}