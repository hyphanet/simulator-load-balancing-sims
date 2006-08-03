import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashSet;

class Peer
{
	private Node node; // The local node
	public int address; // The remote node's address
	public double location; // The remote node's routing location
	private double latency; // The latency of the connection in seconds
	
	// Retransmission parameters
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
	
	// Out-of-order delivery with eventual detection of missing packets
	public final static int MAX_INFLIGHT = 1000; // Packets
	
	// Sender state
	private double cwind = MIN_CWIND; // Congestion window in bytes
	private boolean slowStart = true; // Are we in the slow start phase?
	private double rtt = 3.0; // Estimated round-trip time in seconds
	private double lastTransmission = 0.0; // Clock time
	private double lastLeftSlowStart = 0.0; // Clock time
	private int inflight = 0; // Bytes sent but not acked
	private int txSeq = 0; // Sequence number of next outgoing packet
	private int txMaxSeq = MAX_INFLIGHT - 1; // Highest sequence number
	private LinkedList<DataPacket> txBuffer; // Retransmission buffer
	private LinkedList<Message> txQueue; // Messages waiting to be sent
	private int txQueueSize = 0; // Size of transmission queue in bytes
	
	// Receiver state
	private HashSet<Integer> rxDupe; // Detect duplicates by sequence number
	private int rxSeq = 0; // Sequence number of next in-order incoming pkt
	
	public Peer (Node node, int address, double location, double latency)
	{
		this.node = node;
		this.address = address;
		this.location = location;
		this.latency = latency;
		txBuffer = new LinkedList<DataPacket>();
		txQueue = new LinkedList<Message>();
		rxDupe = new HashSet<Integer> (MAX_INFLIGHT);
	}
	
	// Queue a message for transmission
	public void sendMessage (Message m)
	{
		log (m + " added to transmission queue");
		// Warning: until token-passing is implemented the length of
		// the transmission queue is unlimited
		txQueue.add (m);
		txQueueSize += m.size;
		log (txQueue.size() + " messages in transmission queue");
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
		
		// Don't send packet n+MAX_INFLIGHT until packet n is acked
		if (txSeq > txMaxSeq) {
			log ("waiting for ack " + (txMaxSeq - MAX_INFLIGHT +1));
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
		
		if (cwind - inflight <= Packet.HEADER_SIZE) {
			log ("no room in congestion window");
			return false;
		}
		
		// Work out how large a packet we can send
		int payload = Packet.MAX_PAYLOAD;
		if (payload > txQueueSize) payload = txQueueSize;
		if (payload > cwind - inflight - Packet.HEADER_SIZE)
			payload = (int) cwind - inflight - Packet.HEADER_SIZE;
		
		// Nagle's algorithm - try to coalesce small packets
		if (payload < Packet.SENSIBLE_PAYLOAD && inflight > 0) {
			log ("delaying transmission of " + payload + " bytes");
			return false;
		}
		
		// Put as many messages as possible in the packet
		DataPacket p = new DataPacket (payload);
		Iterator<Message> i = txQueue.iterator();
		while (i.hasNext()) {
			Message m = i.next();
			if (m.size > payload) break;
			i.remove();
			txQueueSize -= m.size;
			p.addMessage (m);
			payload -= m.size;
		}
		
		// Don't send empty packets
		if (p.messages == null) {
			log ("message too large for congestion window");
			return false;
		}
		
		// Send the packet
		p.seq = txSeq++;
		log ("sending packet " + p.seq + ", " + p.size + " bytes");
		node.net.send (p, address, latency);
		// Buffer the packet for retransmission
		p.sent = now;
		inflight += p.size;
		log (inflight + " bytes in flight");
		txBuffer.add (p);
		// Start the node's retransmission timer if necessary
		node.startTimer();
		return true;
	}
	
	private void sendAck (int seq)
	{
		Ack a = new Ack (seq);
		log ("sending ack " + seq);
		node.net.send (a, address, latency);
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
		if (p.seq < rxSeq || rxDupe.contains (p.seq)) {
			log ("duplicate packet");
			sendAck (p.seq); // Original ack may have been lost
		}
		else if (p.seq == rxSeq) {
			log ("packet in order");
			// Find the sequence number of the next missing packet
			int was = rxSeq;
			while (rxDupe.remove (++rxSeq));
			log ("rxSeq was " + was + ", now " + rxSeq);
			// Deliver the packet
			unpack (p);
			sendAck (p.seq);
		}
		else if (p.seq < rxSeq + MAX_INFLIGHT) {
			log ("packet out of order - expected " + rxSeq);
			rxDupe.add (p.seq);
			// Deliver the packet
			unpack (p);
			sendAck (p.seq);
		}
		// This indicates a misbehaving sender - discard the packet
		else log ("warning: received " + p.seq + " before " + rxSeq);
	}
	
	private void handleAck (Ack a)
	{
		log ("received ack " + a.ack);
		double now = Event.time();
		Iterator<DataPacket> i = txBuffer.iterator();
		while (i.hasNext()) {
			DataPacket p = i.next();
			double age = now - p.sent;
			// Explicit ack
			if (p.seq == a.ack) {
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
				break;
			}
			// Fast retransmission
			if (p.seq < a.ack && age > FRTO * rtt) {
				p.sent = now;
				log ("fast retransmitting packet " + p.seq);
				log (inflight + " bytes in flight");
				node.net.send (p, address, latency);
				decreaseCongestionWindow (now);
			}
		}
		// Recalculate the maximum sequence number
		if (txBuffer.isEmpty()) txMaxSeq = txSeq + MAX_INFLIGHT - 1;
		else txMaxSeq = txBuffer.peek().seq + MAX_INFLIGHT - 1;
		log ("maximum sequence number " + txMaxSeq);
		// Send as many packets as possible
		while (send());
	}
	
	private void decreaseCongestionWindow (double now)
	{
		cwind *= BETA;
		if (cwind < MIN_CWIND) cwind = MIN_CWIND;
		log ("congestion window decreased to " + cwind);
		// The slow start phase ends when the first packet is lost
		if (slowStart) {
			log ("leaving slow start");
			slowStart = false;
			lastLeftSlowStart = now;
		}
	}
	
	// Remove messages from a packet and deliver them to the node
	private void unpack (DataPacket p)
	{
		if (p.messages == null) return;
		for (Message m : p.messages) node.handleMessage (m, this);
	}
	
	private void log (String message)
	{
		Event.log (node.net.address + ":" + address + " " + message);
	}
	
	// Called by Node
	public boolean checkTimeouts()
	{
		log ("checking timeouts");
		if (txBuffer.isEmpty()) {
			log ("no packets in flight");
			return false;
		}
		double now = Event.time();
		for (DataPacket p : txBuffer) {
			if (now - p.sent > RTO * rtt) {
				// Retransmission timeout
				p.sent = now;
				log ("retransmitting packet " + p.seq);
				log (inflight + " bytes in flight");
				node.net.send (p, address, latency);
				// Return to slow start
				if (!slowStart &&
				now - lastLeftSlowStart > RTO * rtt) {
					log ("returning to slow start");
					cwind = MIN_CWIND;
					slowStart = true;
				}
				else {
					log ("not returning to slow start");
					decreaseCongestionWindow (now);
				}
			}
		}
		return true;
	}
}
