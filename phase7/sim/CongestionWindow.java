// AIMD congestion control

package sim;

class CongestionWindow
{
	public final static boolean LOG = false;
	
	public final static int MIN_CWIND = Packet.MAX_SIZE; // Min window size
	public final static int MAX_CWIND = 1000000; // Max window size
	public final static double ALPHA = 0.3125; // AIMD increase parameter
	public final static double BETA = 0.875; // AIMD decrease parameter
	public final static double GAMMA = 3.0; // Slow start divisor
	
	private double cwind = MIN_CWIND; // Size of window in bytes
	private int inflight = 0; // Bytes sent but not acked
	private boolean slowStart = true; // Are we in the slow start phase?
	private Peer peer; // The owner
	
	public CongestionWindow (Peer peer)
	{
		this.peer = peer;
	}
	
	public void reset()
	{
		cwind = MIN_CWIND;
		slowStart = true;
		if (LOG) {
			peer.log ("congestion window decreased to " + cwind);
			peer.log ("returning to slow start");
		}
	}
	
	public int available()
	{
		return (int) cwind - inflight;
	}
	
	// Put bytes in flight
	public void bytesSent (int bytes)
	{
		inflight += bytes;
		if (LOG) peer.log (inflight + " bytes in flight");
	}
	
	// Take bytes out of flight
	public void bytesAcked (int bytes)
	{
		inflight -= bytes;
		// Increase the window
		if (slowStart) cwind += bytes / GAMMA;
		else cwind += bytes * bytes * ALPHA / cwind;
		if (cwind > MAX_CWIND) cwind = MAX_CWIND;
		if (LOG) {
			peer.log (inflight + " bytes in flight");
			peer.log ("congestion window increased to " + cwind);
		}
	}
	
	// Decrease the window when a packet is fast retransmitted
	public void fastRetransmission (double now)
	{
		cwind *= BETA;
		if (cwind < MIN_CWIND) cwind = MIN_CWIND;
		if (LOG) {
			peer.log (inflight + " bytes in flight");
			peer.log ("congestion window decreased to " + cwind);
		}
		// The slow start phase ends when the first packet is lost
		if (slowStart) {
			if (LOG) peer.log ("leaving slow start");
			slowStart = false;
		}
	}
	
	// Decrease the window when a packet is retransmitted due to a timeout
	public void timeout (double now)
	{
		if (LOG) peer.log (inflight + " bytes in flight");
		if (slowStart) fastRetransmission (now); // Leave slow start
		else reset(); // Reset the window and return to slow start
	}
}
