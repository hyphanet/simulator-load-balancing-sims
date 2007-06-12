package sim;
import java.util.TreeSet; // Gotta love the collections framework...

public class Event implements Comparable
{
	// Static variables and methods for the event queue
	
	private static TreeSet<Event> queue = new TreeSet<Event>();
	private static double now = 0.0;
	private static double lastLogTime = Double.POSITIVE_INFINITY;
	private static int nextId = 0;
	public static double duration = Double.POSITIVE_INFINITY;
	public static boolean blankLine = false; // Blank line between events?
	
	public static void reset()
	{
		queue.clear();
		now = 0.0;
		lastLogTime = Double.POSITIVE_INFINITY;
		nextId = 0;
		duration = Double.POSITIVE_INFINITY;
	}
	
	public static void schedule (EventTarget target, double delay,
					int type, Object data)
	{
		queue.add (new Event (target, delay + now, type, data));
	}
	
	public static boolean nextEvent()
	{
		try {
			Event e = queue.first();
			// Return if the simulation's alloted time has run out
			if (e.time > duration) {
				now = duration;
				return false;
			}
			// Update the clock
			now = e.time;
			// Pass the packet to the target's callback method
			queue.remove (e);
			e.target.handleEvent (e.type, e.data);
			return true;
		}
		catch (java.util.NoSuchElementException x) {
			// No more events to dispatch
			return false;
		}
	}
	
	public static double time()
	{
		return now;
	}
	
	public static void log (String message)
	{
		// Print a blank line between events
		if (blankLine && now > lastLogTime) System.out.println();
		lastLogTime = now;
		System.out.print (now + " " + message + "\n");
	}
	
	// Run until the duration expires or there are no more events to process
	public static void run()
	{
		while (nextEvent()) {}
	}
	
	// Instance variables and methods for individual events
	
	private EventTarget target;
	private double time;
	private int id;
	private int type;
	private Object data;
	
	private Event (EventTarget target, double time, int type, Object data)
	{
		this.target = target;
		this.time = time;
		this.type = type;
		this.data = data;
		id = nextId++;
	}
	
	// Must be consistent with compareTo()
	public boolean equals (Object o)
	{
		Event e = (Event) o;
		if (e.time == time && e.id == id) return true;
		return false;
	}
	
	// Must be consistent with equals()
	public int compareTo (Object o)
	{
		Event e = (Event) o;
		// Sort events by time (order of occurrence)
		if (e.time > time) return -1;
		if (e.time < time) return 1;
		// Break ties by ID (order of scheduling)
		if (e.id > id) return -1;
		if (e.id < id) return 1;
		return 0;
	}
}
