package moneytransfer.models;

public class ErrorResult {
    private ErrorCode errorCode;

    public ErrorResult(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
