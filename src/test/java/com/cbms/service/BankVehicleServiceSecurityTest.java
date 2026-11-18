package com.cbms.service;

import com.cbms.dto.BankVehicleStatusRequest;
import com.cbms.dto.BankVehicleRequest;
import com.cbms.entity.BankVehicle;
import com.cbms.entity.Customer;
import com.cbms.repository.BankVehicleRepository;
import com.cbms.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import java.time.LocalDate;

class BankVehicleServiceSecurityTest {
    @Test
    void cannotDeactivateVehicleReferencedByRegisteredCollateral() {
        BankVehicleRepository vehicles = mock(BankVehicleRepository.class);
        BankVehicle vehicle = mock(BankVehicle.class);
        when(vehicles.lockById(7L)).thenReturn(Optional.of(vehicle));
        when(vehicles.isVehicleSecured(7L)).thenReturn(true);

        BankVehicleService service = new BankVehicleService(vehicles, mock(CustomerRepository.class));
        Authentication actor = mock(Authentication.class);
        when(actor.isAuthenticated()).thenReturn(true);
        when(actor.getName()).thenReturn("admin");

        assertThrows(ResponseStatusException.class,
                () -> service.changeStatus(7L, new BankVehicleStatusRequest("INACTIVE"), actor));
        verify(vehicles, never()).saveAndFlush(any(BankVehicle.class));
    }

    @Test
    void cannotChangeChassisOfVehicleReferencedByRegisteredCollateral() {
        BankVehicleRepository vehicles = mock(BankVehicleRepository.class);
        BankVehicle vehicle = mock(BankVehicle.class);
        Customer owner = mock(Customer.class);
        when(vehicles.lockById(7L)).thenReturn(Optional.of(vehicle));
        when(vehicles.isVehicleSecured(7L)).thenReturn(true);
        when(vehicle.getOwner()).thenReturn(owner);
        when(owner.getCustId()).thenReturn(22L);
        when(vehicle.getChassisNo()).thenReturn("CHASSIS-OLD");
        when(vehicle.getRegistrationNo()).thenReturn("WXY1234");
        when(vehicle.getEngineNo()).thenReturn("ENG-1");
        when(vehicle.getVehicleMake()).thenReturn("Make");
        when(vehicle.getVehicleModel()).thenReturn("Model");
        when(vehicle.getVehicleVariant()).thenReturn("Base");
        when(vehicle.getManufactureYear()).thenReturn(2025);
        when(vehicle.getVehicleType()).thenReturn("CAR");

        BankVehicleService service = new BankVehicleService(vehicles, mock(CustomerRepository.class));
        BankVehicleRequest changedIdentity = new BankVehicleRequest(22L, "WXY1234", LocalDate.of(2025, 1, 1),
                "CHASSIS-NEW", "ENG-1", "Make", "Model", "Base", 2025, "CAR", "USED", null, null);

        assertThrows(ResponseStatusException.class, () -> service.update(7L, changedIdentity, mock(Authentication.class)));
        verify(vehicles, never()).saveAndFlush(any(BankVehicle.class));
    }
}
