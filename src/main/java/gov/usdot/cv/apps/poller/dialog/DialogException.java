package gov.usdot.cv.apps.poller.dialog;

public class DialogException extends Exception {
	private static final long serialVersionUID = 8639088060195188054L;

	public DialogException(String message) {
		super(message);
	}
	
	public DialogException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public DialogException(Throwable cause) {
		super(cause);
	}
}