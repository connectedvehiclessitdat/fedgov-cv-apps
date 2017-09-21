package gov.usdot.cv.apps.poller.executor;

import gov.usdot.asn1.generated.j2735.semi.AdvisoryBroadcast;
import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.asn1.generated.j2735.semi.SemiSequenceID;
import gov.usdot.cv.apps.poller.PollerException;
import gov.usdot.cv.security.SecureConfig;

import java.util.List;
import java.util.Random;

public class RSUAdvSitDataRequestDialogExecutor extends DialogExecutor {
	private static final int RSU_ADV_DATA_REQUEST_MAX_PACKETS = 10;
	
	private RSUAdvSitDataRequestDialogExecutor(
		int groupId,
		String warehouseIP,
		int warehousePort,
		int recvPort,
		int fromPort,
		double nwLat,
		double nwLon,
		double seLat,
		double seLon,
		int timeBound,
		int distType,
		SecureConfig config) {
		super(groupId, warehouseIP, warehousePort, recvPort, fromPort, nwLat, nwLon, 
			seLat, seLon, timeBound, distType, RSU_ADV_DATA_REQUEST_MAX_PACKETS, config);
	}
	
	@Override
	public void performDialog() throws PollerException {
		Random r = new Random();
		int requestId = r.nextInt();
		
		try {
			performTrustEstablishment(SemiDialogID.advSitDatDist, requestId);
			
			this.listener = new UDPSocketListener(this.recvPort);
			this.listener_t = new Thread(this.listener);
			this.listener_t.start();
			
			// Give some time for the listener to start listening on the response port
			try { Thread.sleep(1000); } catch (InterruptedException ie) {}
			
			performDataExchange(SemiDialogID.advSitDatDist, SemiSequenceID.dataReq, requestId);
			performNonRepudiation(SemiDialogID.advSitDatDist, SemiSequenceID.accept, requestId);
		} catch (Exception ex) {
			throw new PollerException(ex);
		} finally {
			cleanup();
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public List<AdvisoryBroadcast> getResultSet() {
		return (List<AdvisoryBroadcast>) this.resultSet;
	}
	
	public static class Builder {
		private int groupId;
		private String warehouseIP;
		private int warehousePort = -1;
		private int recvPort = -1;
		private int fromPort = -1;
		private double nwLat;
		private double nwLon;
		private double seLat;
		private double seLon;
		private int timeBound;
		private int distType;
		private SecureConfig config;
		
		public Builder setGroupId(int groupId) {
			this.groupId = groupId;
			return this;
		}
		
		public Builder setWarehouseIP(String warehouseIP) {
			this.warehouseIP = warehouseIP;
			return this;
		}
		
		public Builder setWarehousePort(int warehousePort) {
			this.warehousePort = warehousePort;
			return this;
		}
		
		public Builder setRecvPort(int recvPort) {
			this.recvPort = recvPort;
			return this;
		}
		
		public Builder setFromPort(int fromPort) {
			this.fromPort = fromPort;
			return this;
		}
		
		public Builder setNWLat(double nwLat) {
			this.nwLat = nwLat;
			return this;
		}
		
		public Builder setNWLon(double nwLon) {
			this.nwLon = nwLon;
			return this;
		}
		
		public Builder setSELat(double seLat) {
			this.seLat = seLat;
			return this;
		}
		
		public Builder setSELon(double seLon) {
			this.seLon = seLon;
			return this;
		}
		
		public Builder setTimeBound(int timeBound) {
			this.timeBound = timeBound;
			return this;
		}
		
		public Builder setDistType(int distType) {
			this.distType = distType;
			return this;
		}
		
		public Builder setConfig(SecureConfig config) {
			this.config = config;
			return this;
		}
		
		public RSUAdvSitDataRequestDialogExecutor build() {
			if (this.recvPort == -1) {
				this.recvPort = this.warehousePort;
			}
			
			return new RSUAdvSitDataRequestDialogExecutor(
				this.groupId,
				this.warehouseIP,
				this.warehousePort,
				this.recvPort,
				this.fromPort,
				this.nwLat,
				this.nwLon,
				this.seLat,
				this.seLon,
				this.timeBound,
				this.distType,
				this.config);
		}
	}
	
}