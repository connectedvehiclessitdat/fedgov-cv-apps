package gov.usdot.cv.apps.sender;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import gov.usdot.asn1.generated.j2735.semi.IntersectionRecord;
import gov.usdot.asn1.generated.j2735.semi.IntersectionSituationData;
import gov.usdot.asn1.generated.j2735.semi.IntersectionSituationDataAcceptance;
import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.asn1.generated.j2735.semi.SemiSequenceID;
import gov.usdot.asn1.generated.j2735.semi.SpatRecord;
import gov.usdot.asn1.j2735.CVSampleMessageBuilder;
import gov.usdot.asn1.j2735.J2735Util;
import gov.usdot.cv.common.asn1.TemporaryIDHelper;

import org.apache.log4j.Logger;

import com.oss.asn1.AbstractData;
import com.oss.asn1.DecodeFailedException;
import com.oss.asn1.DecodeNotSupportedException;
import com.oss.asn1.EncodeFailedException;
import com.oss.asn1.EncodeNotSupportedException;
import com.oss.asn1.INTEGER;

public class SenderDialogISD extends SenderDialog {

	private static final Logger log = Logger.getLogger(SenderDialogISD.class);
	
	public SenderDialogISD(SenderConfig config) {
		super(config, SemiDialogID.intersectionSitDataDep);
	}
	
	protected SenderDialogISD(SenderDialog senderDialog) {
		super(senderDialog);
	}
	
	private IntersectionSituationData buildMessage(byte[] message) throws DecodeFailedException, DecodeNotSupportedException {
		IntersectionSituationData isd = new IntersectionSituationData();
		isd.setDialogID(SemiDialogID.intersectionSitDataDep);
		isd.setSeqID(SemiSequenceID.data);
		isd.setGroupID(config.dialog.groupID);
		isd.setRequestID(TemporaryIDHelper.toTemporaryID(requestID));
		isd.setBundleNumber(new INTEGER(1));
		if ( config.dialog.custom.ttl != null)
			isd.setTimeToLive(config.dialog.custom.ttl);
		isd.setServiceRegion(config.dialog.custom.region);
		IntersectionRecord intersectionRecord = (IntersectionRecord)coder.decode(new ByteArrayInputStream(message), new IntersectionRecord());
		if ( config.other.relaxed ) {
			log.warn("Request for fixing TimeMark does not apply to J2735 3/2016 and will be ignored");
		}
		isd.setIntersectionRecord(intersectionRecord);
		return isd;
	}
	
	@Override
	protected AbstractData prepareMessage(byte[] message) {
		try {
			boolean mustBuild = config.dialog.custom.record != null && config.dialog.custom.record == true;
			AbstractData pdu = mustBuild ? buildMessage(message) : J2735Util.decode(coder, message);
			if ( pdu instanceof IntersectionSituationData ) {
				IntersectionSituationData isd = (IntersectionSituationData)pdu;
				if ( !mustBuild ) {
					isd.setGroupID(config.dialog.groupID);
					isd.setRequestID(TemporaryIDHelper.toTemporaryID(requestID));
					if ( config.dialog.custom.ttl != null)
						isd.setTimeToLive(config.dialog.custom.ttl);
					if ( config.dialog.custom.region != null )
						isd.setServiceRegion(config.dialog.custom.region);
				}
				if ( config.other.timestamp ) 
					resetTimestamp(isd);
				return isd;
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
			log.debug("Closing intersectionSitDataDep dialog");
			Boolean rcode = sendDataAcceptance();
			if ( rcode == null )
				return true;
			if ( rcode == true && receiveDataReceipt() ) {
				log.debug(String.format("Successfully closed intersectionSitDataDep dialog for requestID %d.", requestID));
				return true;
			} else {
				log.debug(String.format("Couldn't close %sintersectionSitDataDep dialog for requestID %d.", isResending ? "resending " : "", requestID));
				return false;
			}
		} finally {
			super.close();
		}
	}
	
	private Boolean sendDataAcceptance() {
		int recordsSent =  messages != null ? messages.size() : -1;
		if ( recordsSent <= 0 ) {
			log.debug("Skipped sending DataAcceptance because no messages were sent during this intersectionSitDataDep dialog.");
			return null;
		}
		log.debug(String.format("Sending IntersectionSituationDataAcceptance message with recordsSent value of %d", recordsSent));
		IntersectionSituationDataAcceptance dataAcceptance = new IntersectionSituationDataAcceptance(
				SemiDialogID.intersectionSitDataDep,
				SemiSequenceID.accept,
				config.dialog.groupID,
				TemporaryIDHelper.toTemporaryID(requestID),
				recordsSent);
		try {
			recvSocket.setSoTimeout(config.resend.timeout); // to save time in subsequent receiveDataReceipt() call
			send(CVSampleMessageBuilder.messageToEncodedBytes(dataAcceptance), false, false);
			return true;
		} catch (EncodeFailedException ex) {
			log.error("Couldn't encode IntersectionSituationDataAcceptance message because encoding failed", ex);
		} catch (EncodeNotSupportedException ex) {
			log.error("Couldn't encode IntersectionSituationDataAcceptance message because encoding is not supported", ex);
		} catch ( IOException ex ) {
			log.error("Couldn't send IntersectionSituationDataAcceptance message due to IOError.", ex);
		}
		return false;
	}
	
	// this is only for simulation/testing -- do not use in production
	private void resetTimestamp(IntersectionSituationData isd) {
		IntersectionRecord rec = isd.getIntersectionRecord();
		SpatRecord spatData = rec.getSpatData();
		spatData.setTimestamp(J2735Util.expireInMin(0));
		rec.setSpatData(spatData);
		isd.setIntersectionRecord(rec);
	}

}
