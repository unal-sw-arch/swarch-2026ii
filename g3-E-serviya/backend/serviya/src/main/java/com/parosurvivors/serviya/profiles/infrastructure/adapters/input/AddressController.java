package com.parosurvivors.serviya.profiles.infrastructure.adapters.input;

import com.parosurvivors.serviya.profiles.application.dto.result.CoordinatesResult;
import com.parosurvivors.serviya.profiles.application.ports.input.AddressServicePort;
import com.parosurvivors.serviya.profiles.infrastructure.adapters.input.api.AddressApi;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.CreateAddressForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.UpdateAddressForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.form.VerifyAddressForm;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.AddressResponse;
import com.parosurvivors.serviya.profiles.infrastructure.dto.response.AddressVerificationResponse;
import com.parosurvivors.serviya.profiles.infrastructure.mappers.AddressWebMapper;
import com.parosurvivors.serviya.shared.ratelimit.RateLimit;
import com.parosurvivors.serviya.shared.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador de entrada (REST) de direcciones. Placeholder funcional; documentacion en {@link AddressApi}.
 */
@RestController
@RequiredArgsConstructor
public class AddressController implements AddressApi {

    private final AddressServicePort addressService;
    private final AddressWebMapper mapper;

    @Override
    @GetMapping("/api/v1/users/me/addresses")
    public ResponseEntity<List<AddressResponse>> getUserAddresses() {
        return ResponseEntity.ok(mapper.toResponses(addressService.getUserAddresses(currentUserId())));
    }

    @Override
    @PostMapping("/api/v1/users/me/addresses")
    @RateLimit("address-verify")
    public ResponseEntity<AddressResponse> createAddress(@Valid @RequestBody CreateAddressForm form) {
        AddressResponse response = mapper.toResponse(
                addressService.createAddress(mapper.toCommand(form, currentUserId())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @PatchMapping("/api/v1/addresses/{id}")
    @RateLimit("address-verify")
    public ResponseEntity<AddressResponse> updateAddress(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateAddressForm form) {
        return ResponseEntity.ok(mapper.toResponse(
                addressService.updateAddress(mapper.toCommand(form, id), currentUserId(), CurrentUser.isAdmin())));
    }

    @Override
    @DeleteMapping("/api/v1/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        addressService.deleteAddress(id, currentUserId(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/api/v1/addresses/verify")
    @RateLimit("address-verify")
    public ResponseEntity<AddressVerificationResponse> verifyAddress(@Valid @RequestBody VerifyAddressForm form) {
        boolean valid = addressService.verifyAddress(form.addressLine(), form.city());
        CoordinatesResult coordinates = addressService.getCoordinates(form.addressLine(), form.city());
        return ResponseEntity.ok(mapper.toVerificationResponse(valid, coordinates));
    }

    private Long currentUserId() {
        return CurrentUser.id();
    }
}
