package gov.usdot.cv.apps.poller;

public class PollerException extends Exception {

	private static final long serialVersionUID = -8128571648390959753L;
	
	public PollerException(String message) {
		super(message);
	}
	
	public PollerException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public PollerException(Throwable cause) {
		super(cause);
	}
	
}