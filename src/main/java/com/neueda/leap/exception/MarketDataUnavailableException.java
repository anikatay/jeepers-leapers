package com.neueda.leap.exception;

public class MarketDataUnavailableException extends RuntimeException{
	public MarketDataUnavailableException(String message, Throwable cause){
		super(message,cause);
	}
	public MarketDataUnavailableException(String message){
		super(message);
	}
}
