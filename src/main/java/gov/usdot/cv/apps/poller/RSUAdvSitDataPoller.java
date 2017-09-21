package gov.usdot.cv.apps.poller;

import gov.usdot.asn1.generated.j2735.semi.AdvisoryBroadcast;
import gov.usdot.cv.apps.poller.executor.DialogExecutor;
import gov.usdot.cv.apps.poller.executor.RSUAdvSitDataRequestDialogExecutor;
import gov.usdot.cv.apps.poller.writer.RSUAdvSitDataFileWriter;
import gov.usdot.cv.security.SecureConfig;
import gov.usdot.cv.security.SecurityHelper;

import java.io.File;
import java.util.List;

public class RSUAdvSitDataPoller extends AbstractSitDataPoller {
	private static SecureConfig config;
	private RSUAdvSitDataFileWriter writer;
	
	public void start() throws PollerException {
		loadConfig();
		poll();
	}
	
	private void poll() {
		while (true) {
			System.out.println("=======================================");
			System.out.println("Initiating RSU advisory situation data request dialog ...");
			long start = System.currentTimeMillis();
			writeToFile(performDialog());
			long end = System.currentTimeMillis();
			
			long duration = end - start;
			System.out.println(String.format("RSU advisory situation data request dialog completed in %s seconds.", duration / 1000));
			
			long remaining = settings.pollInterval - duration;
			if (remaining > 0) {
				System.out.println("Next poll will occur in " + (remaining / 1000) + " seconds.");
				try { Thread.sleep(remaining); } catch (InterruptedException ignore) {}
			} 
			System.out.println("=======================================");
		}
	}
	
	@SuppressWarnings("unchecked")
	private List<AdvisoryBroadcast> performDialog() {
		RSUAdvSitDataRequestDialogExecutor.Builder builder = new RSUAdvSitDataRequestDialogExecutor.Builder();
		builder
			.setGroupId(this.settings.groupId)
			.setWarehouseIP(this.settings.warehouseIP)
			.setWarehousePort(this.settings.warehousePort)
			.setRecvPort(this.settings.recvPort)
			.setFromPort(this.settings.fromPort)
			.setNWLat(this.settings.nwLat)
			.setNWLon(this.settings.nwLon)
			.setSELat(this.settings.seLat)
			.setSELon(this.settings.seLon)
			.setDistType(this.settings.distType)
			.setConfig(config);
		
		if (this.settings.timeBound >= 1 && this.settings.timeBound <=30) {
			builder.setTimeBound(this.settings.timeBound);
		}
		
		DialogExecutor executor = null;
		try {
			executor = builder.build();
			executor.performDialog();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		return (List<AdvisoryBroadcast>) executor.getResultSet();
	}
	
	private void writeToFile(List<AdvisoryBroadcast> records) {
		System.out.println(String.format("Writing advisory broadcast records to '%s%s%s' ...", 
			this.settings.savePath, File.separator, this.settings.filename));
		try {
			int numRecords = (records != null) ? records.size() : 0;
			if (this.writer == null) {
				this.writer = new RSUAdvSitDataFileWriter(this.settings.savePath, this.settings.filename);
			}
			this.writer.write(records);
			System.out.println(String.format("Wrote %s advisory broadcast record(s) to file %s.", 
				numRecords, this.settings.filename));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public static void main(String [] args) {
		try {
			String configFile = args.length > 0 ? args[0] : null;
			if (configFile != null) {
				config = new SecureConfig(configFile);
				SecurityHelper.loadCertificates(config);
			}
			new RSUAdvSitDataPoller().start();
		} catch (Exception ex) {
			System.err.println("RSUAdvSitDataPoller failed to execute.");
			ex.printStackTrace();
		}
	}
	
}