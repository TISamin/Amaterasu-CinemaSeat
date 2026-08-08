/**
 * Port: ShowSeatPaymentPort
 *
 * Agent 2 uses this port to move the show_seat through its payment-driven transitions.
 * The allowed transitions are documented in STATE_MACHINE.md §2-§4.
 *
 * Agent 1 ships a concrete implementation; Agent 2 ships a NoopShowSeatAdapter.
 */

export type ShowSeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED';

export interface ShowSeatPaymentPort {
  /**
   * Book the seat (HELD -> BOOKED).
   * - Must be idempotent (BOOKED -> BOOKED is a no-op).
   * - Returns the show_seat_id for the booking so the caller can verify.
   */
  bookForBooking(bookingRef: string): Promise<{ show_seat_id: number } | null>;

  /**
   * Release the held seat (HELD -> AVAILABLE) on payment failure.
   * - Idempotent: if the seat is not HELD, no-op.
   */
  releaseForBooking(bookingRef: string): Promise<void>;
}

export class NoopShowSeatAdapter implements ShowSeatPaymentPort {
  public readonly calls: Array<{ method: string; bookingRef: string }> = [];

  async bookForBooking(bookingRef: string): Promise<{ show_seat_id: number } | null> {
    this.calls.push({ method: 'bookForBooking', bookingRef });
    return { show_seat_id: 0 };
  }

  async releaseForBooking(bookingRef: string): Promise<void> {
    this.calls.push({ method: 'releaseForBooking', bookingRef });
  }
}
