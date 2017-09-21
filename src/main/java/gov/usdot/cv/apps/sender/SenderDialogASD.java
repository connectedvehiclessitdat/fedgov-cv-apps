package gov.usdot.cv.apps.sender;

import gov.usdot.asn1.generated.j2735.dsrc.TemporaryID;
import gov.usdot.asn1.generated.j2735.semi.AdvisoryBroadcastType;
import gov.usdot.asn1.generated.j2735.semi.AdvisoryDetails;
import gov.usdot.asn1.generated.j2735.semi.AdvisorySituationData;
import gov.usdot.asn1.generated.j2735.semi.DataAcceptance;
import gov.usdot.asn1.generated.j2735.semi.DistributionType;
import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.asn1.generated.j2735.semi.SemiSequenceID;
import gov.usdot.asn1.j2735.CVSampleMessageBuilder;
import gov.usdot.asn1.j2735.CVTypeHelper;
import gov.usdot.asn1.j2735.J2735Util;
import gov.usdot.cv.common.asn1.TemporaryIDHelper;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import com.oss.asn1.AbstractData;
import com.oss.asn1.DecodeFailedException;
import com.oss.asn1.DecodeNotSupportedException;
import com.oss.asn1.EncodeFailedException;
import com.oss.asn1.EncodeNotSupportedException;
import com.oss.asn1.OctetString;

public class SenderDialogASD extends SenderDialog {

	private static final Logger log = Logger.getLogger(SenderDialogASD.class);
	
	public SenderDialogASD(SenderConfig config) {
		super(config, SemiDialogID.advSitDataDep);
		hasDataConfirmation = true;
	}
	
	protected SenderDialogASD(SenderDialog senderDialog) {
		super(senderDialog);
		hasDataConfirmation = true;
	}
	
	private AdvisorySituationData buildMessage(byte[] message) throws DecodeFailedException, DecodeNotSupportedException {
		AdvisorySituationData asd = new AdvisorySituationData();
		asd.setDialogID(SemiDialogID.advSitDataDep);
		asd.setSeqID(SemiSequenceID.data);
		asd.setGroupID(config.dialog.groupID);
		asd.setRequestID(TemporaryIDHelper.toTemporaryID(requestID));
		if ( config.dialog.custom.ttl != null)
			asd.setTimeToLive(config.dialog.custom.ttl);
		asd.setServiceRegion(config.dialog.custom.region);
		
		AdvisoryDetails asdmDetails = new AdvisoryDetails();
		asdmDetails.setAsdmID(new TemporaryID(ByteBuffer.allocate(4).putInt(5555).array()));
		asdmDetails.setAsdmType(AdvisoryBroadcastType.tim);
		DistributionType dt = new DistributionType(CVTypeHelper.DistributionType.RSU.arrayValue());
		asdmDetails.setDistType(dt);
		asdmDetails.setAdvisoryMessage(new OctetString(message));
		asd.setAsdmDetails(asdmDetails);
		return asd;
	}
	
	@Override
	protected AbstractData prepareMessage(byte[] message) {
		try {
			boolean mustBuild = config.dialog.custom.record != null && config.dialog.custom.record == true;
			AbstractData pdu = mustBuild ? buildMessage(message) : J2735Util.decode(coder, message);
			if ( pdu instanceof AdvisorySituationData ) {
				AdvisorySituationData asd = (AdvisorySituationData)pdu;
				if ( !mustBuild ) {
					asd.setGroupID(config.dialog.groupID);
					asd.setRequestID(TemporaryIDHelper.toTemporaryID(requestID));
					if ( config.dialog.custom.ttl != null)
						asd.setTimeToLive(config.dialog.custom.ttl);
					if ( config.dialog.custom.region != null )
						asd.setServiceRegion(config.dialog.custom.region);
				}
				return asd;
			} else if ( pdu != null ){
				log.error("Couldn't update fields in an unexpected message of type " + pdu.getClass().getName());
			} else {
				log.error("Couldn't update requestID in null PDU");
			}
		} catch (DecodeFailedException ex) {
			log.error("Couldn't decode J2735 ASN.1 UPER message because decoding failed", ex);
		} catch (DecodeNotSupportedException ex) {
			log.error("Couldn't decode J2735 ASN.1 UPER message because decoding is not supported", ex);
		}
		return null;
	}
	
	@Override
	public boolean close() {
		try {
			log.debug("Closing advSitDataDep dialog");
			Boolean rcode = sendDataAcceptance();
			if ( rcode == null )
				return true;
			if ( rcode == true && receiveDataReceipt() ) {
				log.debug(String.format("Successfully closed advSitDataDep dialog for requestID %d.", requestID));
				return true;
			} else {
				log.debug(String.format("Couldn't close %sadvSitDataDep dialog for requestID %d.", isResending ? "resending " : "", requestID));
				return false;
			}
		} finally {
			super.close();
		}
	}
	
	private Boolean sendDataAcceptance() {
		int recordsSent =  messages != null ? messages.size() : -1;
		if ( recordsSent <= 0 ) {
			log.debug("Skipped sending DataAcceptance because no messages were sent during this advSitDataDep dialog.");
			return null;
		}
		log.debug(String.format("Sending AdvisorySituationDataAcceptance message with recordsSent value of %d", recordsSent));
		DataAcceptance dataAcceptance = new DataAcceptance(
				SemiDialogID.advSitDataDep,
				SemiSequenceID.accept,
				config.dialog.groupID,
				TemporaryIDHelper.toTemporaryID(requestID));
		try {
			recvSocket.setSoTimeout(config.resend.timeout); // to save time in subsequent receiveDataReceipt() call
			sendOnly(CVSampleMessageBuilder.messageToEncodedBytes(dataAcceptance));
			return true;
		} catch (EncodeFailedException ex) {
			log.error("Couldn't encode AdvisorySituationDataAcceptance message because encoding failed", ex);
		} catch (EncodeNotSupportedException ex) {
			log.error("Couldn't encode AdvisorySituationDataAcceptance message because encoding is not supported", ex);
		} catch ( IOException ex ) {
			log.error("Couldn't send AdvisorySituationDataAcceptance message due to IOError.", ex);
		}
		return false;
	}
}
