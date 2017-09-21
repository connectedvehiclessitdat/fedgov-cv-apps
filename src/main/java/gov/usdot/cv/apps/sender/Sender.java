package gov.usdot.cv.apps.sender;

import gov.usdot.cv.common.util.UnitTestHelper;
import gov.usdot.cv.security.cert.CertificateException;
import gov.usdot.cv.security.cert.CertificateManager;
import gov.usdot.cv.security.cert.FileCertificateStore;
import gov.usdot.cv.security.crypto.CryptoException;
import gov.usdot.cv.security.crypto.CryptoProvider;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

public class Sender {
	
	private static final Logger log = Logger.getLogger(SenderConfig.class);
	
	final private SenderConfig config;
	final private SenderReader reader;
	final private SenderWorker worker;
	final private SenderResender resender;
	final private BlockingQueue<byte[]> messageQueue = new LinkedBlockingQueue<byte[]>();
	final private BlockingQueue<SenderDialog> resendQueue = new LinkedBlockingQueue<SenderDialog>();
	
	public Sender(SenderConfig config) throws DecoderException, CertificateException, IOException, CryptoException {
		this.config = config;
		if ( this.config.secure.enable == true )
			loadCertificates();
		switch(config.source.sourceType) {
		case PORT:
			reader = new SenderReadPort(config);
			break;
		case FILE:
			reader = new SenderReadFile(config);
			break;
		case FOLDER:
			reader = new SenderReadFiles(config);
			break;
		case PIPE:
			reader = new SenderReadPipe(config);
			break;
		default:
			log.error("Unexpected source type");
			reader = null;
		}
		if ( reader != null ) {
			reader.setMessageQueue(messageQueue);
			worker = new SenderWorker(config, messageQueue, resendQueue);
			resender = new SenderResender(config, resendQueue);
		} else {
			worker = null;
			resender = null;
		}
	}
	
	public void send() {
		if ( reader == null || worker == null || resender == null )
			return;
		Thread resend = new Thread(resender);
		resend.start();
		Thread sender = new Thread(worker);
		sender.start();
		reader.run();
		try {
			messageQueue.put(SenderReader.getEndOfDataMarker());
			sender.join();
			resendQueue.put(SenderDialog.getEndOfDataMarker(config));
			resend.join();
		} catch (InterruptedException ignored) {
		}
	}
	
	private void loadCertificates() throws DecoderException, CertificateException, IOException, CryptoException {
		CertificateManager.clear();
		CryptoProvider cryptoProvider = new CryptoProvider();
		if ( config.secure.certs != null ) {
			for ( SenderConfig.CertEntry cert : config.secure.certs ) {
				if ( cert.key == null )
					FileCertificateStore.load(cryptoProvider, cert.name, cert.path);
				else
					FileCertificateStore.load(cryptoProvider, cert.name, cert.path, cert.key);
			}
		}
	}
	
	private static void usage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("Sender options", options);
	}
	
	public static void main(String[] args) {
	    final CommandLineParser parser = new BasicParser();
	    final Options options = new Options();
	    options.addOption("f", "file", true, "The name of the file to send (optional)");
	    options.addOption("c", "config", true, "Configuration file for the sender that contains execution options (mandatory)");
	    options.addOption("v", "verbose", false, "Print more debug information (optional, default: false)"); 
	    options.addOption("l", "log4j", false, "Use internal log4j configuration (optional, default: false)"); 
		
		String file = null;
		JSONObject conf = null;
		Boolean verbose = null;
		Boolean internalLog4j = null;
		
	    try {
			final CommandLine commandLine = parser.parse(options, args);
			
		    if (commandLine.hasOption('c')) {
		        String configFile = commandLine.getOptionValue('c');
		        conf = createJsonFromFile(configFile);
		    }
		    
	        if ( conf == null ) {
	    		usage(options);
	    		return;
	    	}
			
		    if (commandLine.hasOption('f')) {
		        String strFile = commandLine.getOptionValue('f');
		        if ( !StringUtils.isBlank(strFile) )
		        	file = strFile;
		        else
		        	System.out.println("Invalid parameter: Ignoring blank File parameter value");
		    } 
				    	    
		    if ( commandLine.hasOption('v') ) {
		    	verbose = true;
		    }
		    
			if ( commandLine.hasOption('l') ) {
				internalLog4j = true;
			}

		} catch (ParseException ex) {
			System.out.println("Command line arguments parsing failed. Reason: " + ex.getMessage());
			usage(options);
			return;
		}
	    
	    if ( internalLog4j != null && internalLog4j == true )
	    	UnitTestHelper.initLog4j(verbose != null && verbose == true ? true : false);
		
		try {
			
			if ( file != null ) 
				set(conf, "source", "file", file);
			
			if ( verbose != null ) 
				set(conf, "other", "verbose", file);
			
			SenderConfig config = new SenderConfig(conf);
			if ( config.other.verbose )
				System.out.println(config.toString());
			
			Sender sender = new Sender(config);
			sender.send();

		} catch ( Exception ex ) {
			System.out.println("Unexpected exception while sending packets. Reason: " + ex.getMessage());
		} 
	}
	
	private static JSONObject createJsonFromFile(String file ) {
    	try {
    		FileInputStream fis = new FileInputStream(file);
    		String jsonTxt = IOUtils.toString(fis);
    		return (JSONObject) JSONSerializer.toJSON(jsonTxt);
		} catch (FileNotFoundException ex) {
			System.out.print(String.format("Couldn't create JSONObject from file '%s'.\nReason: %s\n", file, ex.getMessage()));
		} catch (IOException ex) {
			System.out.print(String.format("Couldn't create JSONObject from file '%s'.\nReason: %s\n", file, ex.getMessage()));
		} catch ( Exception ex) {
			System.out.print(String.format("Couldn't create JSONObject from file '%s'.\nReason: %s\n", file, ex.getMessage()));
		}
    	return null;
	}
	
	private static void set(JSONObject conf, String parent, String field, Object value ) {
		assert(conf != null && parent != null && field != null && value != null );
		JSONObject jsonParent = conf.has(parent) ? conf.getJSONObject(parent) : new JSONObject();
		jsonParent.put(field,  value);
		conf.put(parent, jsonParent);
	}

}
