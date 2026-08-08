package com.cinemaseat.booking;

import com.cinemaseat.showseat.ShowSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract test for the service Agent 2 will call from the payment callback
 * handler. Pure unit test — no DB, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class BookingStateServiceTest {

    @Mock BookingRepository bookings;
    @Mock ShowSeatRepository showSeats;

    @InjectMocks BookingStateServiceImpl svc;

    private Booking booking;

    @BeforeEach
    void setUp() {
        booking = new Booking();
        booking.setBookingRef("BK-abc");
        booking.setShowSeatId(42L);
        booking.setUserId("alice");
    }

    @Test
    void confirmTransitionsToBooked() {
        when(bookings.findByBookingRef("BK-abc")).thenReturn(Optional.of(booking));
        when(bookings.markConfirmed("BK-abc")).thenReturn(1);

        svc.confirmBooking("BK-abc");

        verify(bookings).markConfirmed("BK-abc");
        verify(showSeats).confirmHold(42L, "alice");
    }

    @Test
    void confirmDuplicateCallbackIsNoop() {
        when(bookings.findByBookingRef("BK-abc")).thenReturn(Optional.of(booking));
        when(bookings.markConfirmed("BK-abc")).thenReturn(0);   // already terminal

        svc.confirmBooking("BK-abc");

        verify(bookings, times(1)).markConfirmed("BK-abc");
        verify(showSeats, never()).confirmHold(any(Long.class), anyString());
    }

    @Test
    void confirmUnknownBookingDoesNotThrow() {
        when(bookings.findByBookingRef("nope")).thenReturn(Optional.empty());

        assertThatCode(() -> svc.confirmBooking("nope")).doesNotThrowAnyException();
        verify(bookings, never()).markConfirmed(anyString());
        verify(showSeats, never()).confirmHold(any(Long.class), anyString());
    }

    @Test
    void failPaymentReleasesSeat() {
        when(bookings.findByBookingRef("BK-abc")).thenReturn(Optional.of(booking));
        when(bookings.markPaymentFailed("BK-abc")).thenReturn(1);

        svc.failPayment("BK-abc");

        verify(bookings).markPaymentFailed("BK-abc");
        verify(showSeats).releaseHold(42L, "alice");
    }

    @Test
    void expireBookingReleasesSeat() {
        when(bookings.findByBookingRef("BK-abc")).thenReturn(Optional.of(booking));
        when(bookings.markExpired("BK-abc")).thenReturn(1);

        svc.expireBooking("BK-abc");

        verify(bookings).markExpired("BK-abc");
        verify(showSeats).releaseHold(42L, "alice");
    }
}