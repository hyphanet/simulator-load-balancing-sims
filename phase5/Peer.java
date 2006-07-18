import java.util.LinkedList;
import java.util.Iterator;
import java.util.NoSuchElementException;

class Peer implements EventTarget
{
	public int address; // The remote node's address
	private double latency; // Latency of the connection in seconds
	private Node owner; // The local node
	
	// Retransmission parameters
	public final static double TIMER = 0.5; // Coarse-grained timer, seconds
	public final static double RTO = 4.0; // Retransmission timeout in RTTs
	public final static double FRTO = 1.5; // Fast retx timeout in RTTs
	public final static double RTT_DECAY = 0.9; // Exp moving average
	
	// Congestion control parameters
	public final static int MIN_CWIND = 3000; // Minimum congestion window
	public final static int MAX_CWIND = 100000; // Maximum congestion window
	// Note: RWIND must be at least 2 * FRTO * MAX_CWIND
	public final static int RWIND = 400000; // Maximum bytes buffered at rx
	public final static double ALPHA = 0.1615; // AIMD increase parameter
	public final static double BETA = 0.9375; // AIMD decrease parameter
	public final static double GAMMA = 3.0; // Slow start divisor
	
	// Sender state
	private double cwind = MIN_CWIND; // Congestion window in bytes
	private boolean slowStart = true; // Are we in the slow start phase?
	private double rtt = 3.0; // Estimated round-trip time in seconds
	private double lastTransmission = 0.0; // Clock time
	private double lastCongestionDecrease = 0.0; // Clock time
	private boolean timerRunning = false; // Is the retx timer running?
	private int inflight = 0; // Bytes sent but not acked
	private int txSeq = 0; // Sequence number of next outgoing packet
	private LinkedList<DataPacket> txBuffer; // Retransmission buffer
	private LinkedList<Message> txQueue; // Messages waiting to be sent
	private int txQueueSize = 0; // Size of transmission queue in bytes
	private int txRemaining = 0; // Bytes of current message unsent
	
	// Receiver state
	private int rxSeq = 0; // Sequence number of next in-order packet
	private LinkedList<DataPacket> rxBuffer; // Reassembly buffer
	private int rxBufferSize = 0; // Size of reassembly buffer in bytes
	private LinkedList<Message> rxQueue; // Messages waiting to be collected
	
	public Peer (int address, double latency, Node owner)
	{
		this.address = address;
		this.latency = latency;
		this.owner = owner;
		txBuffer = new LinkedList<DataPacket>();
		txQueue = new LinkedList<Message>();
		rxBuffer = new LinkedList<DataPacket>();
		rxQueue = new LinkedList<Message>();
	}
	
	// Returns the first message in the queue or null if the queue is empty
	public Message receiveMessage()
	{
		try { return rxQueue.removeFirst(); }
		catch (NoSuchElementException nse) { return null; }
	}
	
	// Queue a message for transmission
	public void sendMessage (Message m)
	{
		// Warning: until token-passing is implemented the length of
		// the transmission queue is unlimited
		if (txQueue.isEmpty()) txRemaining = m.size;
		txQueue.add (m);
		txQueueSize += m.size;
		log (txQueue.size() + " messages waiting to be sent");
		// Send as many packets as possible
		while (send());
	}
	
	// Try to send a packet, return true if a packet was sent
	private boolean send()
	{
		if (txQueueSize == 0) {
			log ("no messages to send");
			return false;
		}
		
		if (inflight == cwind) {
			log ("no room in congestion window");
			return false;
		}
		
		// Return to slow start when the link is idle
		double now = Event.time();
		if (now - lastTransmission > RTO * rtt) {
			log ("returning to slow start");
			cwind = MIN_CWIND;
			slowStart = true;
		}
		lastTransmission = now;
		
		// Work out how large a packet we can send
		int size = Packet.MAX_PAYLOAD;
		if (size > txQueueSize) size = txQueueSize;
		if (size > cwind - inflight) size = (int) cwind - inflight;
		
		// Nagle's algorithm - try to coalesce small packets
		if (size < Packet.MAX_PAYLOAD && inflight > 0) {
			log ("delaying transmission of " + size + " bytes");
			return false;
		}
		
		DataPacket p = new DataPacket (size);
		// Put as many messages as possible in the packet
		while (txRemaining <= size) {
			try {
				Message m = txQueue.removeFirst();
				p.messages.add (m);
				size -= txRemaining;
				txQueueSize -= txRemaining;
				// Move on to the next message
				txRemaining = txQueue.getFirst().size;
			}
			catch (NoSuchElementException nse) {
				// No more messages in the txQueue
				txRemaining = 0;
				break;
			}
		}
		// Fill the rest of the packet with part of the current message
		if (txRemaining > 0) {
			txRemaining -= size;
			txQueueSize -= size;
		}
		// Send the packet
		p.seq = txSeq++;
		log ("sending packet " + p.seq + ", " + p.size + " bytes");
		owner.net.send (p, address, latency);
		// Buffer the packet for retransmission
		p.sent = now;
		inflight += p.size;
		log (inflight + " bytes in flight");
		txBuffer.add (p);
		// Start the coarse-grained retransmission timer if necessary
		if (!timerRunning) {
			log ("starting timer");
			Event.schedule (this, TIMER, CHECK_TIMEOUTS, null);
			timerRunning = true;
		}
		return true;
	}
	
	private void sendAck (int seq)
	{
		Ack a = new Ack (seq);
		log ("sending ack " + seq);
		owner.net.send (a, address, latency);
	}
	
	// Called by Node when a packet arrives
	public void handlePacket (Packet p)
	{
		if (p instanceof DataPacket) handleData ((DataPacket) p);
		else if (p instanceof Ack) handleAck ((Ack) p);
	}
	
	private void handleData (DataPacket p)
	{
		log ("received packet " + p.seq + ", " + p.size + " bytes");
		// Is this the packet we've been waiting for?
		if (p.seq == rxSeq) {
			log ("packet in order");
			rxSeq++;
			rxQueue.addAll (p.messages);
			// Reassemble contiguous packets
			Iterator<DataPacket> i = rxBuffer.iterator();
			while (i.hasNext()) {
				DataPacket q = i.next();
				if (q.seq == rxSeq) {
					log ("adding packet " + q.seq);
					i.remove();
					rxBufferSize -= q.size;
					rxQueue.addAll (p.messages);
					rxSeq++;
				}
				else break;
			}
			log (rxBufferSize + " bytes buffered");
			log ("expecting packet " + rxSeq);
			// Tell the node there are messages to be collected
			owner.messagesWaiting (this);
		}
		else if (p.seq > rxSeq) {
			log ("packet out of order, expected " + rxSeq);
			// Buffer the packet until all previous packets arrive
			int index;
			Iterator<DataPacket> i = rxBuffer.iterator();
			for (index = 0; i.hasNext(); index++) {
				DataPacket q = i.next();
				if (q.seq == p.seq) {
					// Already buffered
					log ("duplicate packet " + p.seq);
					sendAck (p.seq);
					return;
				}
				if (q.seq > p.seq) break;
			}
			if (rxBufferSize + p.size > RWIND) {
				// This shouldn't happen under normal conditions
				log ("no space in buffer - packet dropped");
				return;
			}
			rxBuffer.add (index, p);
			rxBufferSize += p.size;
			log (rxBufferSize + " bytes buffered");
			// DEBUG
			if (!rxBuffer.isEmpty()) {
				for (Packet z : rxBuffer)
					System.out.print (z.seq + " ");
				System.out.println();
			}
		}
		else log ("duplicate packet " + p.seq);
		sendAck (p.seq); // Ack may have been lost
	}
	
	private void handleAck (Ack a)
	{
		log ("received ack " + a.seq);
		double now = Event.time();
		boolean windowIncreased = false;
		Iterator<DataPacket> i = txBuffer.iterator();
		while (i.hasNext()) {
			DataPacket p = i.next();
			double age = now - p.sent;
			// Explicit ack
			if (p.seq == a.seq) {
				log ("packet " + p.seq + " acknowledged");
				i.remove();
				inflight -= p.size;
				log (inflight + " bytes in flight");
				// Increase the congestion window
				if (slowStart) cwind += p.size / GAMMA;
				else cwind += p.size * p.size * ALPHA / cwind;
				if (cwind > MAX_CWIND) cwind = MAX_CWIND;
				log ("congestion window increased to " + cwind);
				// Update the average round-trip time
				rtt = rtt * RTT_DECAY + age * (1.0 - RTT_DECAY);
				log ("round-trip time " + age);
				log ("average round-trip time " + rtt);
				windowIncreased = true;
				break;
			}
			// Fast retransmission
			if (p.seq < a.seq && age > FRTO * rtt) {
				p.sent = now;
				log ("fast retransmitting packet " + p.seq);
				log (inflight + " bytes in flight");
				owner.net.send (p, address, latency);
				decreaseCongestionWindow (now);
			}
		}
		if (windowIncreased) while (send());
	}
	
	private void decreaseCongestionWindow (double now)
	{
		// The congestion window should only be decreased once per RTT
		if (now - lastCongestionDecrease < rtt) return;
		lastCongestionDecrease = now;
		cwind *= BETA;
		if (cwind < MIN_CWIND) cwind = MIN_CWIND;
		log ("congestion window decreased to " + cwind);
		// The slow start phase ends when the first packet is lost
		if (slowStart) {
			log ("leaving slow start");
			slowStart = false;
		}
	}
	
	private void log (String message)
	{
		Event.log (owner.net.address + ":" + address + " " + message);
	}
	
	// Event callback
	private void checkTimeouts()
	{
		log ("checking timeouts");
		// If there are no packets in flight, stop the timer
		if (txBuffer.isEmpty()) {
			log ("stopping timer");
			timerRunning = false;
			return;
		}
		double now = Event.time();
		for (DataPacket p : txBuffer) {
			// Slow retransmission
			if (now - p.sent > RTO * rtt) {
				p.sent = now;
				log ("retransmitting packet " + p.seq);
				log (inflight + " bytes in flight");
				owner.net.send (p, address, latency);
				decreaseCongestionWindow (now);
			}
		}
		// Reset the timer
		Event.schedule (this, TIMER, CHECK_TIMEOUTS, null);
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		if (type == CHECK_TIMEOUTS) checkTimeouts();
	}
	
	// Each EventTarget class has its own event codes
	private final static int CHECK_TIMEOUTS = 1;
}
