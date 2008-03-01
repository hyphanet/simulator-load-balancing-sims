// This software has been placed in the public domain by its author

// Limited-capacity LRU cache

package sim;
import java.util.LinkedHashSet;

class LruCache<Key>
{
	public final static boolean LOG = false;
	
	public final int capacity;
	private LinkedHashSet<Key> set;
	
	public LruCache (int capacity)
	{
		this.capacity = capacity;
		set = new LinkedHashSet<Key> (capacity);
	}
	
	public boolean get (Key key)
	{
		if (LOG) log ("searching cache for key " + key);
		if (set.remove (key)) {
			set.add (key); // Move the key to the fresh end
			return true;
		}
		return false;
	}
	
	public void put (Key key)
	{
		if (set.remove (key)) {
			if (LOG) log ("key " + key + " already in cache");
		}
		else {
			if (LOG) log ("adding key " + key + " to cache");
			if (set.size() == capacity) {
				// Discard the oldest element
				Key oldest = set.iterator().next();
				if (LOG) log ("discarding key " + oldest);
				set.remove (oldest);
			}
		}
		set.add (key); // Add or move the key to the fresh end
	}
	
	private void log (String message)
	{
		Event.log (message);
	}
}
