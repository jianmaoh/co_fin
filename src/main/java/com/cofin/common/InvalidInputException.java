package com.cofin.common;

/** Input the user can fix; the message is shown to them as-is. */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
