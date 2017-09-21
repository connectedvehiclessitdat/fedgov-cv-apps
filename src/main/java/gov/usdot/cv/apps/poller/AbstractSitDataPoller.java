package gov.usdot.cv.apps.poller;

import java.io.FileInputStream;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;

public abstract class AbstractSitDataPoller {
	private static final String CONFIG_FILENAME = "settings.properties";
	private static final String GROUP_ID 		= "GroupID";
	private static final String WAREHOUSE_IP 	= "WarehouseIP";
	private static final String WAREHOUSE_PORT 	= "WarehousePort";
	private static final String RECV_PORT 		= "RecvPort";
	private static final String FROM_PORT 		= "FromPort";
	private static final String NW_LAT 			= "NWLatitude";
	private static final String NW_LON 			= "NWLongitude";
	private static final String SE_LAT 			= "SELatitude";
	private static final String SE_LON 			= "SELongitude";
	private static final String TIME_BOUND		= "TimeBound";
	private static final String DIST_TYPE		= "DistType";
	private static final String SAVE_PATH 		= "SavePath";
	private static final String FILENAME 		= "Filename";
	private static final String POLL_INTERVAL 	= "PollInterval";
	
	class Settings {
		int groupId = 0;
		String warehouseIP;
		int warehousePort = -1;
		int recvPort = -1;
		int fromPort = -1;
		double nwLat;
		double nwLon;
		double seLat;
		double seLon;
		int timeBound = -1;
		int distType = 1;
		String savePath;
		String filename;
		int pollInterval;
	}
	
	protected Settings settings = new Settings();

	protected void loadConfig() throws PollerException {
		try {
			String configPath = System.getProperty("CONFIG_PATH", CONFIG_FILENAME);
			Properties p = new Properties();
			p.load(new FileInputStream(configPath));
			
			if (p.containsKey(GROUP_ID)) {
				this.settings.groupId = Integer.valueOf(p.getProperty(GROUP_ID));
			}
			
			this.settings.warehouseIP = p.getProperty(WAREHOUSE_IP);
			if (StringUtils.isBlank(this.settings.warehouseIP)) {
				throw new PollerException(String.format("Missing property '%s'.", WAREHOUSE_IP));
			}
			this.settings.warehouseIP.trim();
			
			this.settings.warehousePort = Integer.valueOf(p.getProperty(WAREHOUSE_PORT));
			if (this.settings.warehousePort < 0 && this.settings.warehousePort > 65535) {
				throw new PollerException(String.format("Invalid warehouse port number '%s'.", this.settings.warehousePort));
			}
			
			this.settings.recvPort = Integer.valueOf(p.getProperty(RECV_PORT));
			if (this.settings.recvPort < 0 && this.settings.recvPort > 65535) {
				throw new PollerException(String.format("Invalid recv port number '%s'.", this.settings.recvPort));
			}
			
			this.settings.fromPort = Integer.valueOf(p.getProperty(FROM_PORT));
			if (this.settings.fromPort < 0 && this.settings.fromPort > 65535) {
				throw new PollerException(String.format("Invalid from port number '%s'.", this.settings.fromPort));
			}
			
			this.settings.nwLat = Double.valueOf(p.getProperty(NW_LAT));
			if (this.settings.nwLat < -90 && this.settings.nwLat > 90) {
				throw new PollerException(String.format("Invalid NW Latitude'%s'.", this.settings.nwLat));
			}
			
			this.settings.nwLon	= Double.valueOf(p.getProperty(NW_LON));
			if (this.settings.nwLon < -180 && this.settings.nwLon > 180) {
				throw new PollerException(String.format("Invalid NW Longitude'%s'.", this.settings.nwLon));
			}
			
			this.settings.seLat = Double.valueOf(p.getProperty(SE_LAT));
			if (this.settings.seLat < -90 && this.settings.seLat > 90) {
				throw new PollerException(String.format("Invalid SE Latitude'%s'.", this.settings.seLat));
			}
			
			this.settings.seLon = Double.valueOf(p.getProperty(SE_LON));
			if (this.settings.seLon < -180 && this.settings.seLon > 180) {
				throw new PollerException(String.format("Invalid SE Longitude'%s'.", this.settings.seLon));
			}
			
			if (p.containsKey(TIME_BOUND)) {
				this.settings.timeBound = Integer.valueOf(p.getProperty(TIME_BOUND));
				if (this.settings.timeBound < 0 && this.settings.timeBound > 30) {
					throw new PollerException(String.format("Invalid time bound '%s'.", this.settings.timeBound));
				}
			}
			
			if (p.containsKey(DIST_TYPE)) {
				this.settings.distType = Integer.valueOf(p.getProperty(DIST_TYPE));
				if (this.settings.distType < 0 && this.settings.distType > Byte.MAX_VALUE) {
					throw new PollerException(String.format("Invalid distibution type value '%s'.", this.settings.distType));
				}
			}
			
			this.settings.savePath = p.getProperty(SAVE_PATH);
			if (StringUtils.isBlank(this.settings.savePath)) {
				throw new PollerException(String.format("Missing property '%s'.", SAVE_PATH));
			}
			this.settings.savePath .trim();
		
			this.settings.filename = p.getProperty(FILENAME);
			if (StringUtils.isBlank(this.settings.filename)) {
				throw new PollerException(String.format("Missing property '%s'.", FILENAME));
			}
			this.settings.filename.trim();
			
			String pollInterval = p.getProperty(POLL_INTERVAL);
			this.settings.pollInterval = (pollInterval == null) ? 30 * 60 * 1000 : Integer.valueOf(pollInterval) * 1000;
		} catch (Exception ex) {
			throw new PollerException(String.format("Failed to load configuration file '%s'.", CONFIG_FILENAME), ex);
		}
	}
}