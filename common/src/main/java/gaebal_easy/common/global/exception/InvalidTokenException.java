package gaebal_easy.common.global.exception;

public class InvalidTokenException extends BaseException {
    public InvalidTokenException(Code code) {
        super(code);
    }
}
