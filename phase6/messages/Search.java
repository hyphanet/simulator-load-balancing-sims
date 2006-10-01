package messages;

public class Search extends Message
{
	public final static int MAX_HTL = 10; // Maximum amount of backtracking
	
	public final int key; // The target of the search
	public double closest; // The closest location seen so far
	public int htl; // Hops to live for backtracking
	
	// Start a new search
	public Search (int key, double location)
	{
		id = Message.nextId++;
		this.key = key;
		closest = location;
		htl = MAX_HTL;
		size = Message.HEADER_SIZE + Message.KEY_SIZE;
	}
	
	// Forward a search
	public Search (int id, int key, double closest, int htl)
	{
		this.id = id;
		this.key = key;
		this.closest = closest;
		this.htl = htl;
		size = Message.HEADER_SIZE + Message.KEY_SIZE;
	}
	
	public String toString()
	{
		return new String ("search (" +id+ "," +key+ "," +htl+ ")");
	}
}
