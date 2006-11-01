package sim;
import sim.messages.*;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashSet;

public class Peer implements EventTarget
{
	private Node node; // The local node
	public int address; // The remote node's address
	public double location; // The remote node's routing location
	private double latency; // The latency of the connection in seconds
	
	// Retransmission parameters
	public final static double RTO = 4.0; // Retransmission timeout in RTTs
	public final static double FRTO = 1.5; // Fast retx timeout in RTTs
	public final static double RTT_DECAY = 0.9; // Exp moving average
	public final static double LINK_IDLE = 8.0; // RTTs without transmitting
	
	// Coalescing
	private final static double MAX_DELAY = 0.1; // Max coalescing delay
	private final static double MIN_SLEEP = 0.01; // Poll the b/w limiter
	
	// Out-of-order delivery with duplicate detection
	public final static int SEQ_RANGE = 1000;
	
	// Sender state
	private double rtt = 5.0; // Estimated round-trip time in seconds
	private int txSeq = 0; // Sequence number of next outgoing data packet
	private int txMaxSeq = SEQ_RANGE - 1; // Highest sequence number
	private LinkedList<Packet> txBuffer; // Retransmission buffer
	private DeadlineQueue<Ack> ackQueue; // Outgoing acks
	private DeadlineQueue<Message> searchQueue; // Outgoing search messages
	private DeadlineQueue<Message> transferQueue; // Outgoing transfers
	private CongestionWindow window; // AIMD congestion window
	private double lastTransmission = Double.POSITIVE_INFINITY; // Time
	private int searchBytesSent = 0, transferBytesSent = 0;
	private boolean timerRunning = false; // Coalescing timer
	
	// Receiver state
	private HashSet<Integer> rxDupe; // Detect duplicates by sequence number
	private int rxSeq = 0; // Sequence number of next in-order incoming pkt
	
	public Peer (Node node, int address, double location, double latency)
	{
		this.node = node;
		this.address = address;
		this.location = location;
		this.latency = latency;
		txBuffer = new LinkedList<Packet>();
		ackQueue = new DeadlineQueue<Ack>();
		searchQueue = new DeadlineQueue<Message>();
		transferQueue = new DeadlineQueue<Message>();
		window = new CongestionWindow (this);
		rxDupe = new HashSet<Integer>();
	}
	
	// Queue a message for transmission
	public void sendMessage (Message m)
	{
		m.deadline = Event.time() + MAX_DELAY;
		if (m instanceof Block) {
			log (m + " added to transfer queue");
			transferQueue.add (m);
		}
		else {
			log (m + " added to search queue");
			searchQueue.add (m);
		}
		// Start the coalescing timer
		startTimer();
		// Send as many packets as possible
		while (send());
	}
	
	// Queue an ack for transmission
	private void sendAck (int seq)
	{
		log ("ack " + seq + " added to ack queue");
		ackQueue.add (new Ack (seq, Event.time() + MAX_DELAY));
		// Start the coalescing timer
		startTimer();
		// Send as many packets as possible
		while (send());
	}
	
	// Start the coalescing timer
	private void startTimer()
	{
		if (timerRunning) return;
		timerRunning = true;
		log ("starting coalescing timer");
		Event.schedule (this, MAX_DELAY, CHECK_DEADLINES, null);
	}
	
	// Try to send a packet, return true if a packet was sent
	private boolean send()
	{
		int waiting = ackQueue.size+searchQueue.size+transferQueue.size;
		log (waiting + " bytes waiting");
		if (waiting == 0) return false;
		// Return to slow start when the link is idle
		double now = Event.time();
		if (now - lastTransmission > LINK_IDLE * rtt) window.reset();
		lastTransmission = now;
		// How many bytes of messages can we send?
		int available = Math.min (window.available(),
					node.bandwidth.available());
		log (available + " bytes available for packet");
		// If there are no urgent acks, and no urgent messages or no
		// room to send them, and not enough messages for a large
		// packet or no room to send a large packet, give up!
		if (ackQueue.deadline() > now
		&& (searchQueue.deadline() > now
		|| searchQueue.headSize() > available)
		&& (transferQueue.deadline() > now
		|| transferQueue.headSize() > available)
		&& (waiting < Packet.SENSIBLE_PAYLOAD
		|| available < Packet.SENSIBLE_PAYLOAD)) {
			log ("not sending a packet");
			return false;
		}
		// Construct a packet
		Packet p = new Packet();
		while (ackQueue.size > 0) p.addAck (ackQueue.pop());
		int space = Math.min (available, Packet.MAX_SIZE - p.size);
		addPayload (p, space);
		// Don't send empty packets
		if (p.acks == null && p.messages == null) return false;
		// Transmit the packet
		log ("sending packet " + p.seq + ", " + p.size + " bytes");
		node.net.send (p, address, latency);
		node.bandwidth.remove (p.size);
		// If the packet contains data, buffer it for retransmission
		if (p.messages != null) {
			p.sent = now;
			txBuffer.add (p);
			node.startTimer(); // Start the retransmission timer
			window.bytesSent (p.size);
		}
		return true;
	}
	
	// Allocate a payload number and add messages to a packet
	private void addPayload (Packet p, int space)
	{
		log (space + " bytes available for messages");
		if (txSeq > txMaxSeq) {
			log ("waiting for ack " + (txMaxSeq - SEQ_RANGE + 1));
			return;
		}
		p.seq = txSeq++;
		// Searches get priority unless transfers are starving
		if (searchBytesSent < transferBytesSent) {
			while (searchQueue.size > 0
			&& searchQueue.headSize() <= space) {
				Message m = searchQueue.pop();
				searchBytesSent += m.size();
				space -= m.size();
				p.addMessage (m);
			}
			while (transferQueue.size > 0
			&& transferQueue.headSize() <= space) {
				Message m = transferQueue.pop();
				transferBytesSent += m.size();
				space -= m.size();
				p.addMessage (m);
			}
		}
		else {
			while (transferQueue.size > 0
			&& transferQueue.headSize() <= space) {
				Message m = transferQueue.pop();
				transferBytesSent += m.size();
				space -= m.size();
				p.addMessage (m);
			}
			while (searchQueue.size > 0
			&& searchQueue.headSize() <= space) {
				Message m = searchQueue.pop();
				searchBytesSent += m.size();
				space -= m.size();
				p.addMessage (m);
			}
		}
		if (p.messages == null) log ("no messages added");
		else log (p.messages.size() + " messages added");
	}
	
	// Called by Node when a packet arrives
	public void handlePacket (Packet p)
	{
		if (p.acks != null) for (Ack a : p.acks) handleAck (a);
		if (p.messages != null) handleData (p);
	}
	
	private void handleData (Packet p)
	{
		log ("received " + p + ", " + p.size + " bytes");
		sendAck (p.seq);
		if (p.seq < rxSeq || rxDupe.contains (p.seq)) {
			log (p + " is a duplicate");
		}
		else if (p.seq == rxSeq) {
			log (p + " is in order");
			// Find the sequence number of the next missing packet
			int was = rxSeq;
			while (rxDupe.remove (++rxSeq));
			log ("rxSeq was " + was + ", now " + rxSeq);
			// Deliver the packet
			unpack (p);
		}
		else if (p.seq < rxSeq + SEQ_RANGE * 2) {
			log (p + " is out of order - expected " + rxSeq);
			if (rxDupe.add (p.seq)) unpack (p);
			else log (p + " is a duplicate");
		}
		// This indicates a misbehaving sender - discard the packet
		else log ("warning: received " + p.seq + " before " + rxSeq);
	}
	
	private void handleAck (Ack a)
	{
		int seq = a.id;
		log ("received ack " + seq);
		double now = Event.time();
		Iterator<Packet> i = txBuffer.iterator();
		while (i.hasNext()) {
			Packet p = i.next();
			double age = now - p.sent;
			// Explicit ack
			if (p.seq == seq) {
				log ("packet " + p.seq + " acknowledged");
				i.remove();
				// Update the congestion window
				window.bytesAcked (p.size);
				// Update the average round-trip time
				rtt = rtt * RTT_DECAY + age * (1.0 - RTT_DECAY);
				log ("round-trip time " + age);
				log ("average round-trip time " + rtt);
				break;
			}
			// Fast retransmission
			if (p.seq < seq && age > FRTO * rtt + MAX_DELAY) {
				p.sent = now;
				log ("fast retransmitting packet " + p.seq);
				node.net.send (p, address, latency);
				window.fastRetransmission (now);
			}
		}
		// Recalculate the maximum sequence number
		if (txBuffer.isEmpty()) txMaxSeq = txSeq + SEQ_RANGE - 1;
		else txMaxSeq = txBuffer.peek().seq + SEQ_RANGE - 1;
		log ("maximum sequence number " + txMaxSeq);
		// Send as many packets a possible
		if (timerRunning) while (send());
		else checkDeadlines();
	}
	
	// Remove messages from a packet and deliver them to the node
	private void unpack (Packet p)
	{
		if (p.messages == null) return;
		for (Message m : p.messages) node.handleMessage (m, this);
	}
	
	// Check retx timeouts, return true if there are packets in flight
	public boolean checkTimeouts()
	{
		log (txBuffer.size() + " packets in flight");
		if (txBuffer.isEmpty()) return false;
		
		double now = Event.time();
		for (Packet p : txBuffer) {
			if (now - p.sent > RTO * rtt + MAX_DELAY) {
				// Retransmission timeout
				log ("retransmitting packet " + p.seq);
				p.sent = now;
				node.net.send (p, address, latency);
				window.timeout (now);
			}
		}
		return true;
	}
	
	// Event callback: wake up, send packets, go back to sleep
	private void checkDeadlines()
	{
		// Send as many packets as possible
		while (send());
		// Find the next coalescing deadline - ignore message
		// deadlines if there isn't room in the congestion window
		// (we have to wait for an ack before sending them)
		double dl = ackQueue.deadline();
		if (searchQueue.headSize() <= window.available())
			dl = Math.min (dl, searchQueue.deadline());
		if (transferQueue.headSize() <= window.available())
			dl = Math.min (dl, transferQueue.deadline());
		// If there's no deadline, stop the timer
		if (dl == Double.POSITIVE_INFINITY) {
			if (timerRunning) {
				log ("stopping coalescing timer");
				timerRunning = false;
			}
			return;
		}
		// Schedule the next check
		double sleep = Math.max (dl - Event.time(), MIN_SLEEP);
		if (waitingForBandwidth()) {
			log ("waiting for bandwidth");
			sleep = MIN_SLEEP; // Poll the bandwidth limiter
		}
		timerRunning = true;
		log ("sleeping for " + sleep + " seconds");
		Event.schedule (this, sleep, CHECK_DEADLINES, null);
	}
	
	// Are there any messages blocked by the bandwidth limiter?
	private boolean waitingForBandwidth()
	{
		int bandwidth = node.bandwidth.available();
		double now = Event.time();
		if (searchQueue.headSize() > bandwidth
		&& searchQueue.deadline() <= now) return true;
		if (transferQueue.headSize() > bandwidth
		&& transferQueue.deadline() <= now) return true;
		return false;
	}
	
	public void log (String message)
	{
		Event.log (node.net.address + ":" + address + " " + message);
	}
	
	public String toString()
	{
		return Integer.toString (address);
	}
	
	// EventTarget interface
	public void handleEvent (int type, Object data)
	{
		if (type == CHECK_DEADLINES) checkDeadlines();
	}
	
	private final static int CHECK_DEADLINES = 1;
}
