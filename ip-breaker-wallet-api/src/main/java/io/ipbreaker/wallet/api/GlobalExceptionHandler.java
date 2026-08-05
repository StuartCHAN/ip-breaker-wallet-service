package io.ipbreaker.wallet.api;

import io.ipbreaker.wallet.application.address.AddressPoolExhaustedException;
import io.ipbreaker.wallet.application.address.DepositAddressNotFoundException;
import io.ipbreaker.wallet.application.deposit.DepositNotFoundException;
import io.ipbreaker.wallet.common.api.ApiResponse;
import io.ipbreaker.wallet.common.error.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ApiResponse<Void>> handleValidation(Exception exception) {
        ErrorCode error = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.badRequest().body(ApiResponse.failure(error.code(), error.defaultMessage()));
    }

    @ExceptionHandler(DepositAddressNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleAddressNotFound(DepositAddressNotFoundException exception) {
        ErrorCode error = ErrorCode.DEPOSIT_ADDRESS_NOT_FOUND;
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(error.code(), error.defaultMessage()));
    }

    @ExceptionHandler(DepositNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleDepositNotFound(DepositNotFoundException exception) {
        ErrorCode error = ErrorCode.DEPOSIT_NOT_FOUND;
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(error.code(), error.defaultMessage()));
    }

    @ExceptionHandler(AddressPoolExhaustedException.class)
    ResponseEntity<ApiResponse<Void>> handlePoolExhausted(AddressPoolExhaustedException exception) {
        ErrorCode error = ErrorCode.ADDRESS_POOL_EXHAUSTED;
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure(error.code(), error.defaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        ErrorCode error = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(error.code(), error.defaultMessage()));
    }
}
