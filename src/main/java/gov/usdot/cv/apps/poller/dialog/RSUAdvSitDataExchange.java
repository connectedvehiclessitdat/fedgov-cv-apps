package gov.usdot.cv.apps.poller.dialog;

import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;

import com.oss.asn1.AbstractData;
import com.oss.asn1.EncodeFailedException;
import com.oss.asn1.EncodeNotSupportedException;
import com.oss.asn1.INTEGER;

import gov.usdot.asn1.generated.j2735.dsrc.Latitude;
import gov.usdot.asn1.generated.j2735.dsrc.Longitude;
import gov.usdot.asn1.generated.j2735.dsrc.Position3D;
import gov.usdot.asn1.generated.j2735.semi.AdvisoryBroadcast;
import gov.usdot.asn1.generated.j2735.semi.AdvisorySituationBundle;
import gov.usdot.asn1.generated.j2735.semi.AdvisorySituationBundle.AsdRecords;
import gov.usdot.asn1.generated.j2735.semi.AdvisorySituationDataDistribution;
import gov.usdot.asn1.generated.j2735.semi.AdvisorySituationDataDistribution.AsdBundles;
import gov.usdot.asn1.generated.j2735.semi.DataRequest;
import gov.usdot.asn1.generated.j2735.semi.DistributionType;
import gov.usdot.asn1.generated.j2735.semi.GeoRegion;
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

public class RSUAdvSitDataExchange extends DataExchange {
	private SemiDialogID dialogId;
	private SemiSequenceID sequenceId;
	private GroupID groupId;
	private int requestId;
	private double nwLat;
	private double nwLon;
	private double seLat;
	private double seLon;
	private int timeBound;
	private DistributionType distType;
	
	private static final Logger log = Logger.getLogger(RSUAdvSitDataExchange.class);
	
	private class ResultSet {
		long bundleId = -1;
		int recordCount = -1;
		int bundleCount = -1;
		int bundlesRemaining = 1;
		List<AdvisorySituationBundle> bundles;
	}
	
	private ResultSet rs;
	
	public RSUAdvSitDataExchange(
		SemiDialogID dialogId,
		SemiSequenceID sequenceId,
		GroupID groupId,
		String warehouseIP, 
		int warehousePort,
		int fromPort,
		int requestId,
		double nwLat, 
		double nwLon, 
		double seLat,
		double seLon,
		int timeBound,
		DistributionType distType) {
		this.dialogId = dialogId;
		this.sequenceId = sequenceId;
		this.groupId = groupId;
		this.warehouseIP = warehouseIP;
		this.warehousePort = warehousePort;
		this.fromPort = fromPort;
		this.requestId = requestId;
		this.nwLat = nwLat;
		this.nwLon = nwLon;
		this.seLat = seLat;
		this.seLon = seLon;
		this.timeBound = timeBound;
		this.distType = distType;
	}
	
	protected void buildDataRequest() throws DialogException {
		DataRequest dataRequest = new DataRequest(
			this.dialogId, 
			this.sequenceId,
			this.groupId,
			TemporaryIDHelper.toTemporaryID(this.requestId), 
			new GeoRegion(
				new Position3D(
					new Latitude(
						J2735Util.convertGeoCoordinateToInt(this.nwLat)), 
					new Longitude(
						J2735Util.convertGeoCoordinateToInt(this.nwLon))),
				new Position3D(
					new Latitude(
						J2735Util.convertGeoCoordinateToInt(this.seLat)), 
					new Longitude(
						J2735Util.convertGeoCoordinateToInt(this.seLon)))),
			this.distType);
		
		if (this.timeBound >= 1 && this.timeBound <= 30) {
			dataRequest.setTimeBound(new INTEGER(this.timeBound));
		}
		
		try {
			this.requestBytes = TravelerSampleMessageBuilder.messageToEncodedBytes(dataRequest);
		} catch (EncodeFailedException ex) {
			throw new DialogException("Couldn't encode RsuAdvisorySituationDataRequest message because encoding failed.", ex);
		} catch (EncodeNotSupportedException ex) {
			throw new DialogException("Couldn't encode RsuAdvisorySituationDataRequest message because encoding is not supported.", ex);
		}
	}
	
	@Override
	public List<AdvisoryBroadcast> getResultSet() {
		if (this.rs == null || this.rs.bundles == null) {
			return Collections.emptyList();
		}
		
		List<AdvisoryBroadcast> holder = new ArrayList<AdvisoryBroadcast>();
		for (AdvisorySituationBundle bundle : this.rs.bundles) {
			AsdRecords asdRecords = bundle.getAsdRecords();
			if (asdRecords != null) {
				for (int i = 0; i < asdRecords.getSize(); i++) {
					holder.add(asdRecords.get(i));
				}
			}
		}
		return holder;
	}
	
	@Override
	public int storeDataBundles(List<DatagramPacket> packets, SecureConfig config, CryptoProvider cryptoProvider) {
		if (packets != null) {
			log.debug(String.format("Storing %d packets", packets != null ? packets.size() : -1));
			for (DatagramPacket packet : packets) {
				final byte [] data = packet.getData();
				if (data == null) continue;
				
				final int length = packet.getLength();
				if (length <= 0) continue;
			
				byte [] bundleBytes = Arrays.copyOfRange(data, packet.getOffset(), length);
				
				if (config != null && config.secure.enable) {
					bundleBytes = SecurityHelper.decrypt(bundleBytes, cryptoProvider);
				}
			
				log.debug(String.format("Received Data: %s", new String(Hex.encodeHex(bundleBytes))));
				
				AdvisorySituationDataDistribution bundle = null;
				try {
					AbstractData pdu = J2735Util.decode(J2735CoderHolder.getCoder(), bundleBytes);
					
					if (pdu == null || ! (pdu instanceof AdvisorySituationDataDistribution)) {
						log.debug(String.format("Message is %s", pdu == null ? "null" : " not of type AdvisorySituationDataDistribution"));
						if ( pdu != null )
							log.debug(String.format("Dropping unexpected message of type: %s", pdu.getClass().getName()));
						continue;
					}
					
					bundle = (AdvisorySituationDataDistribution) pdu;
				} catch (Exception ex){
					log.warn("Decoding of the AdvisorySituationDataDistribution message failed.", ex);
					continue;
				}
			
				long expectedDialogID = SemiDialogID.advSitDatDist.longValue();
				if (expectedDialogID != bundle.getDialogID().longValue()) {
					log.warn(String.format("Dropping message with unexpected dialogID. Expected value: %d. Actual value: %d", expectedDialogID, bundle.getDialogID().longValue()));
					continue;
				}
			
				long expectedSeqID = SemiSequenceID.data.longValue();
				if (expectedSeqID != bundle.getSeqID().longValue()) {
					log.warn(String.format("Dropping message with unexpected seqID. Expected value: %d. Actual value: %d", expectedSeqID, bundle.getSeqID().longValue()));
					continue;
				}
				
				int bundleGroupId = GroupIDHelper.fromGroupID(bundle.getGroupID());
				int expectedGroupId = GroupIDHelper.fromGroupID(this.groupId);
				if ( bundleGroupId != expectedGroupId ) {
					log.warn(String.format("Dropping message with unexpected groupID. Expected value: %d. Actual value: %d", expectedSeqID, bundleGroupId));
					continue;
				}
			
				int responseRequestId = TemporaryIDHelper.fromTemporaryID(bundle.getRequestID());
				if (this.requestId == responseRequestId) {
					int recordCount = (int) bundle.getRecordCount();
					int bundleCount = (int) bundle.getBundleCount();
					long bundleId = (bundle.getAsdBundles() != null && bundle.getAsdBundles().getSize() > 0) ? 
						TemporaryIDHelper.fromTemporaryID(bundle.getAsdBundles().get(0).getBundleId()) : -1;
					
					if (this.rs == null) {
						this.rs = new ResultSet();
						this.rs.recordCount = (int) recordCount;
						this.rs.bundleCount = (int) bundleCount;
						this.rs.bundleId = bundleId;
						this.rs.bundles = new ArrayList<AdvisorySituationBundle>();
					}
				
					if (this.rs.bundleId != bundleId) {
						log.warn(String.format("Dropping message with unexpected bundleID. Expected value: %d. Actual value: %d", this.rs.bundleId, bundleId));
						continue;
					}
				
					AsdBundles asdBundles = bundle.getAsdBundles();
					if (asdBundles != null && asdBundles.getSize() > 0) {
						log.debug(String.format("Processing bundle of size %d",asdBundles.getSize()));
						for (int i = 0; i < asdBundles.getSize(); i++) {
							this.rs.bundles.add(asdBundles.get(i));
						}
					} else {
						log.warn(String.format("Got %s bundle.", asdBundles == null ? "null" : "empty"));
					}
				}
			}
		}	
		
		if (this.rs == null) {
			log.debug("this.rs is null! Returning 1." );
			return 1;
		}
		
		log.debug(String.format("rcount=%d, bundleId=%d, bundleCount=%d, collected=%d", 
				this.rs.recordCount, this.rs.bundleId, this.rs.bundleCount, this.rs.bundles.size()));
		
		this.rs.bundlesRemaining = this.rs.bundleCount - this.rs.bundles.size();
		return this.rs.bundlesRemaining;
	}
}